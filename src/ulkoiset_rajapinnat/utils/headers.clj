(ns ulkoiset-rajapinnat.utils.headers
  (:require [clj-time.core :as t]))

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

(defn parse-request-headers [request response-code start-time]
  (let [duration (- (System/currentTimeMillis) start-time)
        method (get-method-from-request request)
        path-info (request :uri)
        headers (request :headers)]
    {:user-agent    (user-agent-from-request request)
     :remote-addr   (remote-addr-from-request request)
     :timestamp     (t/now)
     :customer      "OPH"
     :service       "ulkoiset-rajapinnat"
     :responseCode  response-code
     :request       (str method " " path-info)
     :requestMethod method
     :responseTime  (str duration)
     }))