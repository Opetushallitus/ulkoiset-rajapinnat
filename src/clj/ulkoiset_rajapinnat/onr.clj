(ns ulkoiset-rajapinnat.onr
  (:require [clojure.core.async :refer [go]]
            [clojure.tools.logging :as log]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid-channel]]
            [ulkoiset-rajapinnat.utils.rest :refer [mime-application-json post-as-channel parse-json-body-stream to-json]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(defn onr-sessionid-channel [] (fetch-jsessionid-channel "/oppijanumerorekisteri-service"))

(defn log-fetch [number-of-oids start-time response]
  (log/debugf "Fetching 'henkilot' (size = %s) ready with status %s! Took %sms!" number-of-oids (response :status) (- (System/currentTimeMillis) start-time))
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
                                    "Cookie"       (str "JSESSIONID=" sid)}
                          :as :stream}
                         parse-json-body-stream))
      (go (RuntimeException. "Trying to fetch 'henkilot' with nil JSESSIONID!")))))
