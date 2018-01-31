(ns ulkoiset-rajapinnat.onr
  (:require [clojure.string :as str]
            [clojure.core.async :refer [go]]
            [clojure.tools.logging :as log]
            [ulkoiset-rajapinnat.utils.config :refer [config]]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid-channel jsessionid-channel]]
            [ulkoiset-rajapinnat.utils.rest :refer [mime-application-json post-as-channel status body body-and-close exception-response parse-json-body to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [strip-version-from-tarjonta-koodisto-uri]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(defn onr-sessionid-channel []
  (let [username (-> @config :ulkoiset-rajapinnat-cas-username)
        password (-> @config :ulkoiset-rajapinnat-cas-password)
        host (resolve-url :cas-client.host)]
    (fetch-jsessionid-channel host "/oppijanumerorekisteri-service" username password)))

(defn log-fetch [number-of-oids start-time response]
  (log/debug "Fetching 'henkilot' (size = {}) ready with status {}! Took {}ms!" number-of-oids (response :status) (- (System/currentTimeMillis) start-time))
  response)

(defn fetch-henkilot-channel [jsessionid henkilo-oids]
  (if (empty? henkilo-oids)
    (go [])
    (if-let [sid jsessionid]
      (let [url (resolve-url :oppijanumerorekisteri-service.henkilot-by-henkilo-oids)
            start-time (System/currentTimeMillis)]
        (post-as-channel url
                         (to-json henkilo-oids)
                         {:headers {"Content-Type" mime-application-json
                                    "Cookie"       (str "JSESSIONID=" sid)}}
                         parse-json-body))
      (go (RuntimeException. "Trying to fetch 'henkilot' with nil JSESSIONID!")))))
