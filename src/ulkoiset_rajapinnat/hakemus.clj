(ns ulkoiset-rajapinnat.hakemus
  (:require [manifold.deferred :refer [let-flow catch chain]]
            [clojure.string :as str]
            [clj-log4j2.core :as log]
            [ulkoiset-rajapinnat.utils.mongo :as m]
            [ulkoiset-rajapinnat.rest :refer [get-as-promise status body body-and-close exception-response response-to-json to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [fetch-koodisto strip-version-from-tarjonta-koodisto-uri]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(comment
  ; missing fields
  pohjakoulutus_2aste
  pohjakoulutus_kk
  lahtokoulun_organisaatio_oid
  lahtokoulun_kuntakoodi
  harkinnanvarainen_valinta
  ensikertalaisuus
  hakijan_koulusivistyskieli
  ulkomailla_suoritetun_toisen_asteen_tutkinnon_suoritusmaa)

(defn hakutoiveet-from-hakemus [document]
  (let [preference "preference"
        koulutus-id "-Koulutus-id"
        hakutoiveet (filter #(.endsWith (.getKey %) koulutus-id) (seq (get-in document ["answers" "hakutoiveet"])))
        p-to-k (fn [key] (Integer/parseInt (.replace (.replace key preference "") koulutus-id "")))
        converted-hakutoiveet (map (fn [a] {:sija (p-to-k (.getKey a)) :oid (.getValue a)}) hakutoiveet)]
    {:hakutoiveet converted-hakutoiveet}))

(defn write-hakemus [count document]
  (let [json (to-json
               (merge
                 (hakutoiveet-from-hakemus document)
                 {"hakemus_oid" (get document "oid")
                  "henkilo_oid" (get document "personOid")
                  "haku_oid" (get document "applicationSystemId")
                  "hakemus_tila" (get document "state")}))]
    (if (= count 1) json (str "," json))))

(defn fetch-hakemukset-for-haku [haku-oid mongo-client channel]
  (let [is-headers-written (atom false)
        count (atom 0)
        database (.getDatabase mongo-client "hakulomake")
        collection (.getCollection database "application")
        publisher (.find collection
                         (m/and
                           (m/in "state" "ACTIVE" "INCOMPLETE")
                           (m/eq "applicationSystemId" haku-oid)))]
    (.subscribe publisher
                (m/subscribe (fn [s]
                             (fn
                               ([]
                                (body channel "]")
                                (close channel))
                               ([document]
                                (if (compare-and-set! is-headers-written false true)
                                  (do
                                    (status channel 200)
                                    (body channel "[")))
                                (-> channel
                                    (body (write-hakemus (swap! count inc) document))))
                               ([_ throwable]
                                (log/error "Failed to fetch stuff!" throwable)
                                (close channel))))))))

(defn hakemus-resource [config mongo-client haku-oid request]
  (with-channel request channel
                (on-close channel (fn [status] (log/debug "Channel closed!" status)))
                (fetch-hakemukset-for-haku haku-oid mongo-client channel)
                (schedule-task (* 1000 60 60) (close channel))))
