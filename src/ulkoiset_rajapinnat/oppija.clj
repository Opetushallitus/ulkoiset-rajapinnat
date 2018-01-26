(ns ulkoiset-rajapinnat.oppija
  (:require [full.async :refer :all]
            [clojure.core.async :as async]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.core.async :refer [go]]
            [full.async :refer :all]
            [schema.core :as s]
            [ulkoiset-rajapinnat.utils.rest :refer [exception-response get-as-channel status body body-and-close exception-response parse-json-body-stream to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [strip-version-from-tarjonta-koodisto-uri]]
            [ulkoiset-rajapinnat.utils.cas :refer [service-ticket-channel]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(def oppija-api "%s/suoritusrekisteri/rest/v1/oppijat?haku=%s&ticket=%s")
(def ensikertalaisuudet-api "%s/suoritusrekisteri/rest/v1/oppijat?ensikertalaisuudet=true&haku=%s&ticket=%s")

(s/defschema Oppija
  {:henkilo_oid s/Str})

(comment
  henkilo_oid
  yksiloity
  henkilotunnus
  syntyma_aika
  etunimet
  sukunimi
  sukupuoli_koodi
  aidinkieli
  hakijan_kotikunta
  hakijan_asuinmaa
  hakijan_kansalaisuus)

(defn transform-oppija [oppija]
  {"henkilo_oid" (oppija "oid")})

(defn oppija-resource [config haku-oid request channel]
  (let [host (config :suoritusrekisteri-host)
        username (config :ulkoiset-rajapinnat-cas-username)
        password (config :ulkoiset-rajapinnat-cas-password)]
    (async/go
      (try
        (do
          (let [service-ticket (<? (service-ticket-channel host "/suoritusrekisteri" username password false))
                oppijat (<? (get-as-channel (format oppija-api host haku-oid service-ticket) {:headers {"CasSecurityTicket" service-ticket} :as :stream} parse-json-body-stream))
                json (to-json (map transform-oppija oppijat))]
            (-> channel
                (status 200)
                (body-and-close json)))
          (catch Exception
             (log/error (format "Virhe haettaessa oppijaa haulle %s!" haku-oid), e)
             ((exception-response channel) e))))))
  (schedule-task (* 1000 60 60) (close channel)))

(defn fetch-hakurekisteri-service-ticket-channel [config]
  (let [host (config :suoritusrekisteri-host)
        username (config :ulkoiset-rajapinnat-cas-username)
        password (config :ulkoiset-rajapinnat-cas-password)]
    (service-ticket-channel host "/suoritusrekisteri" username password)))


(defn fetch-oppijat-for-hakemus-with-ensikertalaisuus-channel
  ([config haku-oid oppija-oids]
   (fetch-oppijat-for-hakemus-with-ensikertalaisuus-channel
     config haku-oid oppija-oids (fetch-hakurekisteri-service-ticket-channel config)))
  ([config haku-oid oppija-oids service-ticket-channel]
  (let [host (config :suoritusrekisteri-host)
        username (config :ulkoiset-rajapinnat-cas-username)
        password (config :ulkoiset-rajapinnat-cas-password)]
    (go-try
      (let [service-ticket (<? service-ticket-channel)
            url (format ensikertalaisuudet-api host haku-oid service-ticket)]
        (<? (post-as-channel url (to-json oppija-oids) {:headers {"CasSecurityTicket" service-ticket}}
                         parse-json-body)))
      ))))