(ns ulkoiset-rajapinnat.utils.access
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [ulkoiset-rajapinnat.utils.rest :refer [to-json]]
            [clojure.tools.logging.impl :as impl]
            [ring.util.http-response :refer :all]
            [org.httpkit.server :refer :all]))

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

(defn- get-method-from-request [request]
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

(defn- do-logging [start-time response-code request]
  (let [duration (- (System/currentTimeMillis) start-time)
        method (get-method-from-request request)
        path-info (request :uri)
        user-agent (get-user-agent request)]
    (.info logger
           (to-json {:timestamp (t/now)
                     :customer "OPH"
                     :service "ulkoiset-rajapinnat"
                     :responseCode response-code
                     :request (str method " " path-info)
                     :requestMethod method
                     :responseTime (str duration)
                     :user-agent user-agent
          }))))

(defn access-log [response]
  (fn [request]
    (let [start-time (System/currentTimeMillis)]
      (do-logging start-time (response :status) request)
      response)))

(defn access-log-with-ticket-check-with-channel [ticket operation]
  (fn [request]
    (if-let [some-ticket ticket]
      (let [start-time (System/currentTimeMillis)]
        (with-channel request channel
                    (on-close channel (fn [status] (do-logging start-time
                                                               (case status
                                                                 :server-close "200"
                                                                 "Closed by client!")
                                                               request)))
                    (operation request channel)))
      (access-log (bad-request! "Ticket parameter required!")))))
