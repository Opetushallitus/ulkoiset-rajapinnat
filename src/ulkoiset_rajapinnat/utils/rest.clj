(ns ulkoiset-rajapinnat.utils.rest
  "Rest Utils"
  (:require [manifold.deferred :as d]
            [org.httpkit.client :as http]
            [cheshire.core :refer :all]
            [full.async :refer :all]
            [clojure.core.async :refer [promise-chan >! go put! close!]]
            [clojure.tools.logging :as log]
            [org.httpkit.server :refer :all]))

(def mime-application-json "application/json; charset=utf-8")

(defn parse-json-request [request]
  (parse-stream (new java.io.InputStreamReader (request :body))))

(defn parse-json-body [response]
  (if (= (response :status) 200)
    (try
      (parse-string (response :body))
      (catch Exception e
        (log/error "Failed to read JSON!" e)))
    (do
      (log/error "Expected 200 OK! But got STATUS = {}!" (response :status))
      (throw (new RuntimeException "Expected 200 OK!")))))

(defn to-json [obj]
  (generate-string obj))

(defn post-form-as-promise [url form]
  (let [deferred (d/deferred)]
    (http/post url {:form-params form} (fn [resp]
                       (d/success! deferred resp)
                       ))
    deferred))

(defn post-json-as-promise
  ([url data options]
   (let [deferred (d/deferred)
         json-options {:body (to-json data)
                       :headers {"Content-Type" mime-application-json}}
         merged-options (merge-with into json-options options)]
     (http/post url merged-options (fn [resp] (d/success! deferred resp)))
     deferred))
  ([url data]
    (post-json-as-promise url data {})))

(defn post-as-promise [url session-id body]
  (let [deferred (d/deferred)]
    (http/post url {:headers {"Content-Type" mime-application-json, "Cookie" (str "JSESSIONID=" session-id )} :body (to-json body)} (fn [resp]
      (d/success! deferred resp)))
    deferred))

(defn get-as-promise
  ([url]
   (get-as-promise url {}))
  ([url options]
   (let [deferred (d/deferred)]
     (http/get url options (fn [resp]
                        (d/success! deferred resp)
                        ))
     deferred)))

(defn- call-as-channel [method url options mapper]
  (let [p (promise-chan)]
    (method url options #(do
                           (put! p (if-let [f mapper] (f %) %))
                           (close! p)))
    p))

(defn get-as-channel
  ([url]
   (get-as-channel url {}))
  ([url options]
   (get-as-channel url options nil))
  ([url options mapper]
   (call-as-channel http/get url options mapper)))

(defn post-as-channel
  ([url body]
   (post-as-channel url body {} nil))
  ([url body options]
   (post-as-channel url body options nil))
  ([url body options mapper]
   (call-as-channel http/post url (merge-with into options {:body body}) mapper)))

(defn status [channel status]
  (send! channel {:status status
                  :headers {"Content-Type" mime-application-json}} false)
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