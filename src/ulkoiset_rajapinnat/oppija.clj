(ns ulkoiset-rajapinnat.oppija
  (:require [full.async :refer :all]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [full.async :refer :all]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [ulkoiset-rajapinnat.utils.rest :refer [exception-response get-as-channel post-as-channel parse-json-body status body body-and-close exception-response parse-json-body-stream to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [strip-version-from-tarjonta-koodisto-uri]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-service-ticket-channel]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(defn fetch-hakurekisteri-service-ticket-channel [] (fetch-service-ticket-channel "/suoritusrekisteri"))


(defn fetch-oppijat-for-hakemus-with-ensikertalaisuus-channel
  ([haku-oid oppija-oids]
   (fetch-oppijat-for-hakemus-with-ensikertalaisuus-channel
     haku-oid oppija-oids (fetch-hakurekisteri-service-ticket-channel)))
  ([haku-oid oppija-oids service-ticket-channel]
   (go-try
     (let [service-ticket (<? service-ticket-channel)
           url (resolve-url :suoritusrekisteri-service.ensikertalaisuudet haku-oid service-ticket)]
       (<? (post-as-channel url (to-json oppija-oids) {:headers {"CasSecurityTicket" service-ticket}}
                            parse-json-body))))))