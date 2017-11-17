(ns ulkoiset-rajapinnat.hakemus
  (:require [manifold.deferred :refer [let-flow catch chain]]
            [clojure.string :as str]
            [clojure.core.async :refer :all]
            [clj-log4j2.core :as log]
            [ulkoiset-rajapinnat.onr :refer :all]
            [ulkoiset-rajapinnat.utils.mongo :as m]
            [ulkoiset-rajapinnat.organisaatio :refer [fetch-organisations-for-oids]]
            [ulkoiset-rajapinnat.rest :refer [get-as-promise status body body-and-close exception-response to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [fetch-koodisto strip-version-from-tarjonta-koodisto-uri]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all])
  (:refer-clojure :rename {merge core-merge}))

(def size-of-henkilo-batch-from-onr-at-once 500)

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
     :aidinkieli (get henkilo "aidinkieli")}))

(defn hakutoiveet-from-hakemus [document]
  (let [pref-keys-by-sija (collect-preference-keys-by-sija document)]
    {:hakutoiveet (map (partial convert-hakutoive document) pref-keys-by-sija)}))

(defn henkilotiedot-from-hakemus [document]
  (let [henkilotiedot (get-in document ["answers" "henkilotiedot"])]
    {:hakijan_asuinmaa (get henkilotiedot "asuinmaa")
     :hakijan_kotikunta (get henkilotiedot "kotikunta")
     :hakijan_kansalaisuus (get henkilotiedot "kansalaisuus")}))

(defn koulutustausta-from-hakemus [orgs-by-oid pohjakoulutuskkodw kunta document]
  (let [koulutustausta (get-in document ["answers" "koulutustausta"])
        koulusivistyskieli (remove nil? [(get koulutustausta "lukion_kieli")
                                         (get koulutustausta "perusopetuksen_kieli")])
        lahtokoulun_organisaatio_oid (get koulutustausta "lahtokoulu")
        lahtokoulun_organisaatio (first (get orgs-by-oid lahtokoulun_organisaatio_oid))
        kotipaikkaUri (get lahtokoulun_organisaatio "kotipaikkaUri")
        lahtokoulun_kuntakoodi (if kotipaikkaUri (str/replace kotipaikkaUri "kunta_" "") nil)
        pohjakoulutus_2aste (get koulutustausta "POHJAKOULUTUS")
        pohjakoulutus_kk (first (filter #(contains? koulutustausta %) pohjakoulutuskkodw))
        ulkomailla_suoritetun_toisen_asteen_tutkinnon_suoritusmaa (get koulutustausta "pohjakoulutus_ulk_suoritusmaa")]
    {:hakijan_koulusivistyskieli (first koulusivistyskieli)
     :pohjakoulutus_2aste pohjakoulutus_2aste
     :pohjakoulutus_kk pohjakoulutus_kk
     :lahtokoulun_organisaatio_oid lahtokoulun_organisaatio_oid
     :lahtokoulun_kuntakoodi lahtokoulun_kuntakoodi
     :ulkomailla_suoritetun_toisen_asteen_tutkinnon_suoritusmaa ulkomailla_suoritetun_toisen_asteen_tutkinnon_suoritusmaa}))

(defn remove-nils
  [m]
  (let [f (fn [[k v]] (when v [k v]))]
    (clojure.walk/postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))

(defn convert-hakemus [orgs-by-oid pohjakoulutuskkodw kunta palauta-null-arvot? henkilo document]
  (let [data (core-merge
               (hakutoiveet-from-hakemus document)
               (oppija-data-from-henkilo henkilo)
               (henkilotiedot-from-hakemus document)
               (koulutustausta-from-hakemus orgs-by-oid pohjakoulutuskkodw kunta document)
               {:hakemus_oid (get document "oid")
                :henkilo_oid (get document "personOid")
                :haku_oid (get document "applicationSystemId")
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

(defn document-batch-to-organisation-oid-list
  [batch]
  (filter some? (map #(get-in % ["answers" "koulutustausta" "lahtokoulu"]) batch)))

(defn fetch-rest-of-the-missing-data
  [config start-time counter is-first-written pohjakoulutuskkodw kunta palauta-null-arvot? jsessionid-promise document-batch-channel channel close-channel]
  (go
    (let [[last-batch? batch] (<! document-batch-channel)
          jsessionid jsessionid-promise
          henkilo-oids (document-batch-to-henkilo-oid-list batch)
          org-oids (document-batch-to-organisation-oid-list batch)
          henkilot-promise (fetch-henkilot-promise config jsessionid henkilo-oids)
          organisation-promise (fetch-organisations-for-oids config org-oids)]
      (let-flow [henkilot henkilot-promise
                 organisations organisation-promise]
                (let [henkilo-by-oid (group-by #(get % "oidHenkilo") henkilot)
                      orgs-by-oid (group-by #(get % "oid") organisations)]
                  (try
                    (doseq [hakemus batch]
                      (write-object-to-channel is-first-written (convert-hakemus orgs-by-oid pohjakoulutuskkodw kunta palauta-null-arvot? (get henkilo-by-oid (get hakemus "personOid")) hakemus) channel))
                    (catch Exception e
                      (log/error "Failed to write 'hakemukset'!" e)))
                  (swap! counter (partial + (count batch)))
                  (if last-batch?
                    (do (close-channel)
                        (log/info "Returned successfully {} 'hakemusta'! Took {}ms!" @counter (- (System/currentTimeMillis) start-time))
                        (close! document-batch-channel))
                    (do
                      (log/debug "Waiting for next batch!")
                      (fetch-rest-of-the-missing-data config start-time counter is-first-written pohjakoulutuskkodw kunta palauta-null-arvot? jsessionid-promise document-batch-channel channel close-channel))))))))


(defn fetch-hakemukset-for-haku
  [config haku-oid palauta-null-arvot? mongo-client channel]
  (let [start-time (System/currentTimeMillis)
        counter (atom 0)
        host-virkailija (config :host-virkailija)
        pohjakoulutuskkodw-promise (chain (fetch-koodisto host-virkailija "pohjakoulutuskkodw") #(vals %))
        kunta-promise (chain (fetch-koodisto host-virkailija "kunta") #(vals %))
        jsessionid-promise (fetch-onr-sessionid config)
        document-batch-channel (chan 2)
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
                                       (handle-document-batch document-batch document-batch-channel))
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
    (let-flow [pohjakoulutuskkodw pohjakoulutuskkodw-promise
               kunta kunta-promise]
              (fetch-rest-of-the-missing-data
                config
                start-time
                counter
                is-first-written
                pohjakoulutuskkodw
                kunta
                palauta-null-arvot?
                jsessionid-promise
                document-batch-channel
                channel
                close-channel))
    (.subscribe publisher
                (m/subscribe (fn [s]
                             (fn
                               ([]
                                (handle-complete))
                               ([document]
                                (handle-incoming-document s document))
                               ([_ throwable]
                                  (handle-exception throwable))))))))

(defn hakemus-resource [config mongo-client haku-oid palauta-null-arvot? request]
  (with-channel request channel
                (on-close channel (fn [status] (log/debug "Channel closed!" status)))
                (fetch-hakemukset-for-haku config haku-oid palauta-null-arvot? mongo-client channel)
                (schedule-task (* 1000 60 60 12) (close channel))
                ))
