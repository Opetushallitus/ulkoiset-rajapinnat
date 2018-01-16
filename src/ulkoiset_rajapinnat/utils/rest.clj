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
      (log/error "Expected 200 OK! But got STATUS =" (response :status) " from url =" (get-in response [:opts :url]) "!")
      (throw (new RuntimeException "Expected 200 OK!")))))

(defn parse-json-body-stream [response]
  (if (= (response :status) 200)
    (try
      (parse-stream (new java.io.InputStreamReader (response :body)))
      (catch Exception e
        (log/error "Failed to read JSON!" e)))
    (do
      (log/error "Expected 200 OK! But got STATUS =" (response :status) " from url =" (get-in response [:opts :url]) "!")
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
  ([url data]
   (post-json-as-promise url data {}))
  ([url data options]
   (let [deferred (d/deferred)
         json-options {:body (to-json data)
                       :headers {"Content-Type" mime-application-json}}
         merged-options (merge-with into json-options options)]
     (log/info (str "POST " url))
     (http/post url merged-options (fn [resp] (d/success! deferred resp)))
     deferred)))

(defn get-as-promise
  ([url]
   (get-as-promise url {}))
  ([url options]
   (let [deferred (d/deferred)]
     (log/info (str "GET " url))
     (http/get url options (fn [resp]
                        (d/success! deferred resp)
                        ))
     deferred)))

(defn transform-response [optional-mapper response]
  (if-let [f optional-mapper]
    (try
      (if-let [t (f response)]
        t
        (RuntimeException.
          (format "Transformer returned nil for response (status = %s): Url = %s" (response :status) (get-in response [:opts :url]))))
      (catch Exception e e))
    response))

(defn- call-as-channel [method url options mapper]
  (let [p (promise-chan)]
    (method url options #(do
                           (put! p (transform-response mapper %))
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

(defn post-form-as-channel [url form mapper]
  (post-as-channel url nil {:form-params form} mapper))

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
          (body (to-json {:error (.getMessage exception)}))
          (close)))))

(defn handle-json-response [url response]
  (log/info (str url " " (response :status)))
  (case (response :status)
    200 (parse-json-body response)
    404 nil
    (throw (RuntimeException. (str "Calling " url " failed: status=" (response :status) ", msg=" (response :body) ", error=" (response :error))))))

(defn post-json-with-cas
  ([url session-id body]
    (let [promise (post-json-as-promise url body {:headers {"Cookie" (str "JSESSIONID=" session-id)}})]
      (log/debug (str url "(JSESSIONID=" session-id ")"))
      (d/chain promise #(handle-json-response url %))))
  ([host session-id url-template body]
    (post-json-with-cas (format url-template host) session-id body)))

(defn get-json-with-cas
  [url session-id]
  (let [promise (get-as-promise url {:timeout 200000 :headers {"Cookie" (str "JSESSIONID=" session-id)}})]
    (log/debug (str url "(JSESSIONID=" session-id ")"))
    (d/chain promise #(handle-json-response url %))))