(ns ulkoiset-rajapinnat.onr
  (:require [clojure.string :as str]
            [clojure.core.async :refer [go]]
            [clojure.tools.logging :as log]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid-channel fetch-jsessionid jsessionid-channel]]
            [ulkoiset-rajapinnat.utils.rest :refer [mime-application-json post-as-channel post-json-as-promise get-as-promise status body body-and-close exception-response parse-json-body to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [fetch-koodisto strip-version-from-tarjonta-koodisto-uri]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(def oppijanumerorekisteri-api "%s/oppijanumerorekisteri-service/henkilo/henkilotByHenkiloOidList")

(defn onr-sessionid-channel [config]
  (let [username (-> config :ulkoiset-rajapinnat-cas-username)
        password (-> config :ulkoiset-rajapinnat-cas-password)
        host (-> config :oppijanumerorekisteri-host-virkailija)]
    (fetch-jsessionid-channel host "/oppijanumerorekisteri-service" username password)))

(defn log-fetch [number-of-oids start-time response]
  (log/debug "Fetching 'henkilot' (size = {}) ready with status {}! Took {}ms!" number-of-oids (response :status) (- (System/currentTimeMillis) start-time))
  response)

(defn fetch-henkilot-channel [config jsessionid henkilo-oids]
  (if-let [sid jsessionid]
    (let [host (-> config :oppijanumerorekisteri-host-virkailija)
          url (format oppijanumerorekisteri-api host)
          start-time (System/currentTimeMillis)]
      (post-as-channel url
                       (to-json henkilo-oids)
                       {:headers {"Content-Type" mime-application-json
                                  "Cookie"       (str "JSESSIONID=" sid)}}
                       parse-json-body))
    (go (RuntimeException. "Trying to fetch 'henkilot' with nil JSESSIONID!"))))
