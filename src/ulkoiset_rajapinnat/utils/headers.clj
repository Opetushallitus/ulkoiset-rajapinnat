(ns ulkoiset-rajapinnat.utils.headers
  (:require [clj-time.core :as t]
            [clj-time.format :as f]))

(defn- find-first [m & keys]
  (if-let [f (first keys)]
    (if-let [v (m f)]
      v
      (apply find-first (into [m] (rest keys))))
    nil))

(defn- if-or [& args]
  (if-let [a (first args)]
    a
    (if (empty? (rest args))
      nil
      (apply if-or (rest args)))))

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

(defn user-agent-from-request [request]
  ((request :headers) "user-agent"))

(defn remote-addr-from-request [request]
  (if-or
    (find-first (request :headers) "x-real-ip" "x-forwarded-for")
    (request :remote-addr)))

(defn- optional-value-or-dash [name headers]
  (or (headers name) "-"))

(def custom-time-formatter (f/with-zone (f/formatter "yyyy-MM-dd HH:mm:ss")
                                        (t/default-time-zone)))

(defn parse-request-headers [request closed-by start-time]
  (let [duration (- (System/currentTimeMillis) start-time)
        time-now (t/now)
        time-now-local (f/unparse custom-time-formatter time-now)
        method (get-method-from-request request)
        path-info (request :uri)
        query-string (request :query-string)
        headers (request :headers)]
    {:user-agent    (user-agent-from-request request)
     :remote-addr   (remote-addr-from-request request)
     :x-real-ip (optional-value-or-dash "x-real-ip" headers)
     :x-forwarded-for (optional-value-or-dash "x-forwarded-for" headers)
     :timestamp     time-now
     :timestamp-local time-now-local
     :customer      "OPH"
     :service       "ulkoiset-rajapinnat"
     :closed-by     closed-by
     :request       (str method " " path-info "?" query-string)
     :requestMethod method
     :responseTime  (str duration)
     :caller-id (optional-value-or-dash "caller-id" headers)
     :clientSubSystemCode (optional-value-or-dash "clientsubsystemcode" headers)
     }))
