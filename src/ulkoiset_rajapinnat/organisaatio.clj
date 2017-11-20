(ns ulkoiset-rajapinnat.organisaatio
  (:require [manifold.deferred :refer [let-flow catch chain deferred success!]]
            [clojure.string :as str]
            [clj-log4j2.core :as log]
            [ulkoiset-rajapinnat.utils.rest :refer [post-json-as-promise get-as-promise status body body-and-close exception-response parse-json-body to-json]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(def organisaatio-api "%s/organisaatio-service/rest/organisaatio/v3/findbyoids")

(defn log-fetch [number-of-oids start-time response]
  (log/info "Fetching 'organisaatiot' (size = {}) ready with status {}! Took {}ms!" number-of-oids (response :status) (- (System/currentTimeMillis) start-time))
  response)

(defn fetch-organisations-for-oids [config organisation-oids]
  (if (> (count organisation-oids) 1000)
    (throw (new RuntimeException "Can only fetch 1000 orgs at once!")))
  (if (empty? organisation-oids)
    (let [deferred (deferred)]
      (success! deferred [])
      deferred)
    (let [host (-> config :organisaatio-host-virkailija)
          url (format organisaatio-api host)
          start-time (System/currentTimeMillis)
          promise (-> (post-json-as-promise url organisation-oids)
              (chain (partial log-fetch (count organisation-oids) start-time))
              (chain parse-json-body))]
      (log/info (type promise))
      promise)))