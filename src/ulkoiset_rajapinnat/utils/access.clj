(ns ulkoiset-rajapinnat.utils.access
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [ulkoiset-rajapinnat.utils.rest :refer [to-json]]
            [clojure.tools.logging.impl :as impl]))

(def ^{:private true} logger (impl/get-logger (impl/find-factory) "ACCESS"))
(comment
  Missing fields
  {
   "timestamp" "2017-11-20T00:00:00.773+0200"
   "environment" "-"
   "caller-id" "-"
   "x-forwarded-for" "-"
   "remote-ip" "x.x.x.x"
   "session" "-"
   "response-size" "bytes"
   "referer" "-"
   }
  )

(defn- get-user-agent [request]
  (if-let [user-agent (first (filter #(= (.getKey %) "user-agent") (request :headers)))]
    (.getValue user-agent)
    "-"))

(defn- get-method [request]
  (let [conversion-table {:get "GET"
                          :options "OPTIONS"
                          :post "POST"
                          :put "PUT"
                          :delete "DELETE"
                          :head "HEAD"
                          :connect "CONNECT"
                          :trace "TRACE"}
        kit-method (request :request-method)]
    (get conversion-table kit-method)))

(defn- do-logging [start-time request]
  (let [duration (- (System/currentTimeMillis) start-time)
        method (get-method request)
        path-info (request :uri)
        user-agent (get-user-agent request)]
  (.info logger
         (to-json {:timestamp (t/now)
                   :customer "OPH"
                   :service "ulkoiset-rajapinnat"
                   :responseCode "-"
                   :request (str method " " path-info)
                   :requestMethod method
                   :responseTime duration
                   :user-agent user-agent
          }))))

(defn access-log [operation]
  (fn [request]
    (let [start-time (System/currentTimeMillis)]
      (try
        (operation request)
        (finally (do-logging start-time request))))))

