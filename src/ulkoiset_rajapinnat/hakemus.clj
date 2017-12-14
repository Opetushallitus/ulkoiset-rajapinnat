(ns ulkoiset-rajapinnat.hakemus
  (:require [clojure.string :as str]
            [clojure.core.async :refer :all]
            [clojure.tools.logging :as log]
            [full.async :refer :all]
            [ulkoiset-rajapinnat.onr :refer :all]
            [ulkoiset-rajapinnat.utils.mongo :as m]
            [ulkoiset-rajapinnat.organisaatio :refer [fetch-organisations-for-oids]]
            [ulkoiset-rajapinnat.utils.rest :refer [get-as-promise status body body-and-close exception-response to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [koodisto-as-channel fetch-koodisto strip-version-from-tarjonta-koodisto-uri]]
            [org.httpkit.server :refer :all]
            [ulkoiset-rajapinnat.utils.ataru :refer [fetch-hakemukset-from-ataru]]
            [ulkoiset-rajapinnat.utils.snippets :refer [remove-nils]]
            [org.httpkit.timer :refer :all]
            [clojure.core.async.impl.protocols :as impl])
  (:refer-clojure :rename {merge core-merge
                           loop  core-loop}))

(comment
  TODO fetch ataru hakemukset (fetch-hakemukset-from-ataru config "1.2.246.562.29.68110837611"))

(def size-of-henkilo-batch-from-onr-at-once 500)

(comment
  ; missing fields
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
    {:hakukohde_oid             (get-endswith "-Koulutus-id")
     :harkinnanvarainen_valinta (get-endswith "-discretionary-follow-up")
     :sija                      (.getKey preferences)}))

(defn oppija-data-from-henkilo [henkilo-opt]
  (let [henkilo (if (nil? henkilo-opt) {} henkilo-opt)]
    {:yksiloity       (get henkilo "yksiloity")
     :henkilotunnus   (get henkilo "hetu")
     :syntyma_aika    (get henkilo "syntymaaika")
     :etunimet        (get henkilo "etunimet")
     :sukunimi        (get henkilo "sukunimi")
     :sukupuoli_koodi (get henkilo "sukupuoli")
     :aidinkieli      (get henkilo "aidinkieli")}))

(defn hakutoiveet-from-hakemus [document]
  (let [pref-keys-by-sija (collect-preference-keys-by-sija document)]
    {:hakutoiveet (map (partial convert-hakutoive document) pref-keys-by-sija)}))

(defn henkilotiedot-from-hakemus [document]
  (let [henkilotiedot (get-in document ["answers" "henkilotiedot"])]
    {:hakijan_asuinmaa     (get henkilotiedot "asuinmaa")
     :hakijan_kotikunta    (get henkilotiedot "kotikunta")
     :hakijan_kansalaisuus (get henkilotiedot "kansalaisuus")}))

(defn koulutustausta-from-hakemus [pohjakoulutuskkodw document]
  (let [koulutustausta (get-in document ["answers" "koulutustausta"])
        koulusivistyskieli (remove nil? [(get koulutustausta "lukion_kieli")
                                         (get koulutustausta "perusopetuksen_kieli")])
        lahtokoulun_organisaatio_oid (get koulutustausta "lahtokoulu")
        pohjakoulutus_2aste (get koulutustausta "POHJAKOULUTUS")
        pohjakoulutus_kk (first (filter #(contains? koulutustausta %) pohjakoulutuskkodw))
        ulkomailla_suoritetun_toisen_asteen_tutkinnon_suoritusmaa (get koulutustausta "pohjakoulutus_ulk_suoritusmaa")]
    {:hakijan_koulusivistyskieli                                (first koulusivistyskieli)
     :pohjakoulutus_2aste                                       pohjakoulutus_2aste
     :pohjakoulutus_kk                                          pohjakoulutus_kk
     :lahtokoulun_organisaatio_oid                              lahtokoulun_organisaatio_oid
     :ulkomailla_suoritetun_toisen_asteen_tutkinnon_suoritusmaa ulkomailla_suoritetun_toisen_asteen_tutkinnon_suoritusmaa}))

(defn convert-hakemus [pohjakoulutuskkodw palauta-null-arvot? henkilo document]
  (let [data (core-merge
               (hakutoiveet-from-hakemus document)
               (oppija-data-from-henkilo henkilo)
               (henkilotiedot-from-hakemus document)
               (koulutustausta-from-hakemus pohjakoulutuskkodw document)
               {:hakemus_oid  (get document "oid")
                :henkilo_oid  (get document "personOid")
                :haku_oid     (get document "applicationSystemId")
                :hakemus_tila (get document "state")})]
    (if palauta-null-arvot?
      data
      (remove-nils data))))

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
  (core-loop [oldval @atom]
    (if (>= (count oldval) batch)
      (let [spl (split-at batch oldval)]
        (if (compare-and-set! atom oldval (vec (second spl)))
          (vec (first spl))
          (recur @atom)))
      nil)))

(defn drain! [atom]
  (core-loop [oldval @atom]
    (if (compare-and-set! atom oldval [])
      oldval
      (recur @atom))))

(defn handle-document-batch [document-batch document-batch-channel]
  (let [last-batch? false]
    (if-let [batch (atomic-take! document-batch size-of-henkilo-batch-from-onr-at-once)]
      (do
        (go
          (log/debug "Putting batch of size {}!" (count batch))
          (>! document-batch-channel [last-batch? batch]))))))

(defn document-batch-to-henkilo-oid-list
  [batch]
  (map #(get % "personOid") batch))

(defn fetch-hakemukset-for-haku
  [config haku-oid palauta-null-arvot? mongo-client channel]
  (let [start-time (System/currentTimeMillis)
        counter (atom 0)
        host-virkailija (config :host-virkailija)
        pohjakoulutuskkodw-channel (koodisto-as-channel config "pohjakoulutuskkodw")
        jsessionid-channel (onr-sessionid-channel config)
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
                                (close channel)))))]
    (go
      (try
        (let [jsessionid (<<?? jsessionid-channel)
              pohjakoulutuskkodw (<<?? pohjakoulutuskkodw-channel)]
        (doseq [batch (<<?? (m/publisher-as-channel publisher size-of-henkilo-batch-from-onr-at-once))]
          (let [henkilo-oids (document-batch-to-henkilo-oid-list batch)
                henkilot (<? (fetch-henkilot-channel config (first jsessionid) henkilo-oids))
                henkilo-by-oid (group-by #(get % "oidHenkilo") henkilot)]
            (doseq [hakemus batch]
              (write-object-to-channel
                is-first-written
                (convert-hakemus (first pohjakoulutuskkodw) palauta-null-arvot? (get henkilo-by-oid (get hakemus "personOid")) hakemus)
                channel)
              ))
          (let [bs (int (count batch))]
            (swap! counter (partial + bs)))))
        (close-channel)
        (log/info "Returned successfully" @counter "'hakemusta'! Took" (- (System/currentTimeMillis) start-time) "ms!")
        (catch Exception e
          (do
            (log/error "Failed to write 'hakemukset'!" e)
            (write-object-to-channel is-first-written
                                     {:error (.getMessage e)}
                                     channel)
            (close-channel)))
        ))))

(defn hakemus-resource [config mongo-client haku-oid palauta-null-arvot? request channel]
  (fetch-hakemukset-for-haku config haku-oid palauta-null-arvot? mongo-client channel)
  (schedule-task (* 1000 60 60 12) (close channel)))
