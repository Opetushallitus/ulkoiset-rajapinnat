(ns ulkoiset-rajapinnat.rest
  "Rest Utils"
  (:require [manifold.deferred :as d]
            [org.httpkit.client :as http]
            [cheshire.core :refer :all]
            [clj-log4j2.core :as log]
            [org.httpkit.server :refer :all]))

(defn parse-json-body [response]
  (parse-string (response :body)))

(defn to-json [obj]
  (generate-string obj))

(defn get-as-promise [url]
  (let [deferred (d/deferred)]
    (http/get url {} (fn [resp]
                       (d/success! deferred resp)
                       ))
    deferred))

(defn status [channel status]
  (send! channel {:status status
                  :headers {"Content-Type" "application/json; charset=utf-8"}} false)
  channel)

(defn body [channel body]
  (send! channel body false)
  channel)

(defn body-and-close [channel body]
  (send! channel body true)
  channel)

(defn exception-response [channel]
  (fn [exception]
    (do
      (log/error "Internal server error!" exception)
      (-> channel
          (status 500)
          (body "{\"error\":\"\"}")
          (close)))))