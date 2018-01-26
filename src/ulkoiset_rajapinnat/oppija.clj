(ns ulkoiset-rajapinnat.oppija
  (:require [full.async :refer :all]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.core.async :refer [go]]
            [full.async :refer :all]
            [schema.core :as s]
            [ulkoiset-rajapinnat.utils.rest :refer [exception-response get-as-channel post-as-channel parse-json-body status body body-and-close exception-response parse-json-body-stream to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [strip-version-from-tarjonta-koodisto-uri]]
            [ulkoiset-rajapinnat.utils.cas :refer [service-ticket-channel]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(def ensikertalaisuudet-api "%s/suoritusrekisteri/rest/v1/oppijat?ensikertalaisuudet=true&haku=%s&ticket=%s")

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