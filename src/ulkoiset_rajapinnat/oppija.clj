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
            [org.httpkit.timer :refer :all]
            [clj-time.core :as t]
            [clj-time.format :as f]))

(defn fetch-hakurekisteri-service-ticket-channel [] (fetch-service-ticket-channel "/suoritusrekisteri"))

(def opiskelu-date-formatter (f/formatter "yyyy-MM-dd'T'HH:mm:ss.SSS'Z"))
(def valmistuminen-date-formatter (f/formatter "dd.MM.yyyy"))

(defn fetch-oppijat-for-hakemus-with-ensikertalaisuus-channel
  ([haku-oid oppija-oids ensikertalaisuus?]
   (fetch-oppijat-for-hakemus-with-ensikertalaisuus-channel
     haku-oid oppija-oids ensikertalaisuus? (fetch-hakurekisteri-service-ticket-channel)))
  ([haku-oid oppija-oids ensikertalaisuus? service-ticket-channel]
   (go-try
     (let [post-body (to-json (doall oppija-oids))
           service-ticket (<? service-ticket-channel)
           url (resolve-url :suoritusrekisteri-service.oppijat-with-ticket ensikertalaisuus? haku-oid service-ticket)]
       (log/info (str "Calling suoritusrekisteri url " url " with " (count oppija-oids) " oppijas and ticket " service-ticket))
       (<? (post-as-channel url post-body {:headers {"CasSecurityTicket" service-ticket}}
                            parse-json-body))))))