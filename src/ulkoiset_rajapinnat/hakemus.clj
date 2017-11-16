(ns ulkoiset-rajapinnat.hakemus
  (:require [manifold.deferred :refer [let-flow catch chain]]
            [clojure.string :as str]
            [clojure.core.async :refer :all]
            [clj-log4j2.core :as log]
            [ulkoiset-rajapinnat.onr :refer :all]
            [ulkoiset-rajapinnat.utils.mongo :as m]
            [ulkoiset-rajapinnat.rest :refer [get-as-promise status body body-and-close exception-response to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [fetch-koodisto strip-version-from-tarjonta-koodisto-uri]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all])
  (:refer-clojure :rename {merge core-merge}))

(comment
  :hakijan_kotikunta ""
  :hakijan_asuinmaa ""
  :hakijan_kansalaisuus "")
(comment
  ; missing fields
  lahtokoulun_organisaatio_oid
  lahtokoulun_kuntakoodi
  ensikertalaisuus)

(defn collect-preference-keys-by-sija [document]
  (let [ht (get-in document ["answers" "hakutoiveet"])
        preference-keys (filter not-empty (map #(re-matches #"preference(\d)-(.*)" %) (keys ht)))]
    (group-by second preference-keys)))

(defn find-ends-with [s endswith]
  (first (filter #(str/ends-with? % endswith) s)))

(defn convert-hakutoive [document preferences]
  (let [hakutoiveet (get-in document ["answers" "hakutoiveet"])
        pref-keys (set (map first (.getValue preferences)))
        get-endswith #(get hakutoiveet (find-ends-with pref-keys %))]
    {:hakukohde_oid (get-endswith "-Koulutus-id")
     :harkinnanvarainen_valinta (get-endswith "-discretionary-follow-up")
     :sija (.getKey preferences)}))


(defn oppija-data-from-henkilo [henkilo-opt]
  (let [henkilo (if (nil? henkilo-opt) {} henkilo-opt)]
    {:yksiloity (get henkilo "yksiloity")
     :henkilotunnus (get henkilo "hetu")
     :syntyma_aika (get henkilo "syntymaaika")
     :etunimet (get henkilo "etunimet")
     :sukunimi (get henkilo "sukunimi")
     :sukupuoli_koodi (get henkilo "sukupuoli")
     :aidinkieli (get henkilo "aidinkieli")
     :hakijan_kotikunta nil
     :hakijan_asuinmaa nil
     :hakijan_kansalaisuus nil}))

(defn hakutoiveet-from-hakemus [document]
  (let [pref-keys-by-sija (collect-preference-keys-by-sija document)]
    {:hakutoiveet (map (partial convert-hakutoive document) pref-keys-by-sija)}))

(defn koulutustausta-from-hakemus [pohjakoulutuskkodw document]
  (let [koulutustausta (get-in document ["answers" "koulutustausta"])
        koulusivistyskieli (remove nil? [(get koulutustausta "lukion_kieli")
                                         (get koulutustausta "perusopetuksen_kieli")])
        pohjakoulutus_2aste (get koulutustausta "POHJAKOULUTUS")
        pohjakoulutus_kk (first (filter #(contains? koulutustausta %) pohjakoulutuskkodw))
        ulkomailla_suoritetun_toisen_asteen_tutkinnon_suoritusmaa (get koulutustausta "pohjakoulutus_ulk_suoritusmaa")]
    {:hakijan_koulusivistyskieli (first koulusivistyskieli)
     :pohjakoulutus_2aste pohjakoulutus_2aste
     :pohjakoulutus_kk pohjakoulutus_kk
     :ulkomailla_suoritetun_toisen_asteen_tutkinnon_suoritusmaa ulkomailla_suoritetun_toisen_asteen_tutkinnon_suoritusmaa}))

(defn remove-nils
  [m]
  (let [f (fn [[k v]] (when v [k v]))]
    (clojure.walk/postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))

(defn convert-hakemus [pohjakoulutuskkodw henkilo document]
  (remove-nils (core-merge
                 (hakutoiveet-from-hakemus document)
                 (oppija-data-from-henkilo henkilo)
                 (koulutustausta-from-hakemus pohjakoulutuskkodw document)
                 {"hakemus_oid" (get document "oid")
                  "henkilo_oid" (get document "personOid")
                  "haku_oid" (get document "applicationSystemId")
                  "hakemus_tila" (get document "state")})))

(defn write-object-to-channel [is-first-written obj channel]
  (let [json (to-json obj)]
    (if (compare-and-set! is-first-written false true)
      (do
        (status channel 200)
        (body channel (str "[" json)))
      (body channel (str "," json)))))

(defn create-hakemus-publisher [mongo-client haku-oid]
  (-> (.getDatabase mongo-client "hakulomake")
      (.getCollection "application")
      (.find (m/and
               (m/in "state" "ACTIVE" "INCOMPLETE")
               (m/eq "applicationSystemId" haku-oid)))))

(defn atomic-take! [atom batch]
  (loop [oldval @atom]
    (if (>= (count oldval) batch)
      (let [spl (split-at batch oldval)]
        (if (compare-and-set! atom oldval (vec (second spl)))
          (first spl)
          (recur @atom)))
      nil)))

(defn drain! [atom]
  (loop [oldval @atom]
    (if (compare-and-set! atom oldval [])
      oldval
      (recur @atom))))

(defn handle-document-batch [document-batch document-batch-channel last-batch?]
  (if last-batch?
    (go
      (>! document-batch-channel [last-batch? @document-batch]))
    (if-let [batch (atomic-take! document-batch 1000)]
      (do
        (go
          (>! document-batch-channel [last-batch? batch]))))))

(defn document-batch-to-henkilo-oid-list
  [batch]
  (map #(get % "personOid") batch))

(defn fetch-hakemukset-for-haku
  [config haku-oid mongo-client channel]
  (let [host-virkailija (config :host-virkailija)
        pohjakoulutuskkodw-promise (chain (fetch-koodisto host-virkailija "pohjakoulutuskkodw") #(vals %))
        jsessionid-promise (fetch-onr-sessionid config)
        document-batch-channel (chan)
        document-batch (atom [])
        is-first-written (atom false)
        publisher (create-hakemus-publisher mongo-client haku-oid)
        close-channel (fn []
                        (do
                          (if (compare-and-set! is-first-written false true)
                            (do (status channel 200)
                                (body channel "[]")
                                (close channel))
                            (do (body channel "]")
                                (close channel)))))

        handle-incoming-document (fn [s document]
                                   (if (open? channel)
                                     (do
                                       (swap! document-batch conj document)
                                       (handle-document-batch document-batch document-batch-channel false))
                                     (do
                                       (log/warn "Stopping everything! Client socket no longer listening!")
                                       (close! document-batch-channel)
                                       (.cancel s))))
        handle-complete (fn []
                          (go
                            (>! document-batch-channel [true (drain! document-batch)])))
        handle-exception (fn [throwable]
                           (log/error "Failed to fetch stuff!" throwable)
                           (close! document-batch-channel)
                           (write-object-to-channel is-first-written {:error (.getMessage throwable)} channel)
                           (close-channel))]
    (let-flow [pohjakoulutuskkodw pohjakoulutuskkodw-promise]
              (go-loop [[last-batch? batch] (<! document-batch-channel)]
                (let-flow [jsessionid jsessionid-promise
                           henkilot (fetch-henkilot-promise config jsessionid (document-batch-to-henkilo-oid-list batch))]
                          (let [henkilo-by-oid (group-by #(get % "oidHenkilo") henkilot)]
                            (doseq [hakemus batch]
                              (write-object-to-channel is-first-written (convert-hakemus pohjakoulutuskkodw (get henkilo-by-oid (get hakemus "personOid")) hakemus) channel))
                            (if last-batch?
                              (do (close-channel)
                                  (close! document-batch-channel))
                              (recur (<! document-batch-channel)))))))
    (.subscribe publisher
                (m/subscribe (fn [s]
                             (fn
                               ([]
                                (handle-complete))
                               ([document]
                                (handle-incoming-document s document))
                               ([_ throwable]
                                  (handle-exception throwable))))))))

(defn hakemus-resource [config mongo-client haku-oid request]
  (with-channel request channel
                (on-close channel (fn [status] (log/debug "Channel closed!" status)))
                (fetch-hakemukset-for-haku config haku-oid mongo-client channel)
                (schedule-task (* 1000 60 60 12) (close channel))
                ))
