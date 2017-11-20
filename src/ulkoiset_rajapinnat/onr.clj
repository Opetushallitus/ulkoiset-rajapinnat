(ns ulkoiset-rajapinnat.onr
  (:require [manifold.deferred :refer [let-flow catch chain]]
            [clojure.string :as str]
            [clj-log4j2.core :as log]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid]]
            [ulkoiset-rajapinnat.utils.rest :refer [post-json-as-promise get-as-promise status body body-and-close exception-response parse-json-body to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [fetch-koodisto strip-version-from-tarjonta-koodisto-uri]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(def oppijanumerorekisteri-api "%s/oppijanumerorekisteri-service/henkilo/henkilotByHenkiloOidList")

(defn fetch-onr-sessionid [config]
  (let [username (-> config :ulkoiset-rajapinnat-cas-username)
        password (-> config :ulkoiset-rajapinnat-cas-password)
        host (-> config :oppijanumerorekisteri-host-virkailija)]
    (fetch-jsessionid host "/oppijanumerorekisteri-service" username password)))

(defn log-fetch [number-of-oids start-time response]
  (log/debug "Fetching 'henkilot' (size = {}) ready with status {}! Took {}ms!" number-of-oids (response :status) (- (System/currentTimeMillis) start-time))
  response)

(defn fetch-henkilot-promise [config jsessionid henkilo-oids]
  (let [host (-> config :oppijanumerorekisteri-host-virkailija)
        url (format oppijanumerorekisteri-api host)
        start-time (System/currentTimeMillis)]
    (-> (post-json-as-promise url henkilo-oids {:headers {"Cookie" (str "JSESSIONID=" jsessionid )}})
        (chain (partial log-fetch (count henkilo-oids) start-time))
        (chain parse-json-body))))
