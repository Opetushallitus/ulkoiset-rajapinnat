(ns ulkoiset-rajapinnat.utils.rest
  "Rest Utils"
  (:require [org.httpkit.client :as http]
            [cheshire.core :refer :all]
            [full.async :refer :all]
            [clojure.core.async :refer [>! promise-chan >! go put! close!]]
            [clojure.tools.logging :as log]
            [org.httpkit.server :refer :all])
  (:import (clojure.lang IExceptionInfo)))

(def mime-application-json "application/json; charset=utf-8")

(defn parse-json-request [request]
  (parse-stream (new java.io.InputStreamReader (request :body))))

(defn- handle-unexpected-response [response]
  (let [url (get-in response [:opts :url])
        status (response :status)
        error (response :error)
        throw-msg (fn [msg] (log/error msg) (throw (RuntimeException. msg)))]

    (if (not (nil? status)) (throw-msg (str "Unexpected response status " status " from url " url)))
    (if (instance? IExceptionInfo error) (throw-msg (str "Unexpected error from url " url " -> " ((ex-data error) :message))))
    (if (instance? Exception error) (throw-msg (str "Unexpected error from url " url " -> " (.getMessage error))))
    (throw-msg (str "Unexpected response form url " url))))

(defn parse-json-body [response]
  (if (= (response :status) 200)
    (try
      (parse-string (response :body))
      (catch Exception e
        (log/error "Failed to read JSON!" (get-in response [:opts :url]) e)
        (throw e)))
    (handle-unexpected-response response)))

(defn parse-json-body-stream [response]
  (if (= (response :status) 200)
    (try
      (doall (parse-stream (new java.io.InputStreamReader (response :body))))
      (catch Exception e
        (log/error "Failed to read JSON! Url = " (get-in response [:opts :url]) e)
        (throw e)))
    (handle-unexpected-response response)))

(defn to-json
  ([obj] (generate-string obj))
  ([obj pretty] (if pretty (generate-string obj {:pretty true}) (to-json obj)))
  )

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
  (let [p (promise-chan)
        options-with-ids (update-in options [:headers] assoc
                                    "Caller-Id" "fi.opintopolku.ulkoiset-rajapinnat"
                                    "clientSubSystemCode" "fi.opintopolku.ulkoiset-rajapinnat")]
    (method url options-with-ids
      #(go
        (do (>! p (transform-response mapper %))
          (close! p))))
    p))

(defn get-as-channel
  ([url]
   (get-as-channel url {}))
  ([url options]
   (get-as-channel url options nil))
  ([url options mapper]
   (log/info (str "GET -> " url))
   (call-as-channel http/get url options mapper)))

(defn post-as-channel
  ([url body]
   (post-as-channel url body {} nil))
  ([url body options]
   (post-as-channel url body options nil))
  ([url body options mapper]
   (log/info (str "POST -> " url))
   (call-as-channel http/post url (merge-with into options {:body body}) mapper)))

(defn post-form-as-channel [url form mapper]
  (post-as-channel url nil {:form-params form} mapper))

(defn post-json-options
  ([] {:as :stream :timeout 200000 :headers {"Content-Type" mime-application-json}})
  ([jsession-id] {:as :stream :timeout 200000 :headers {"Content-Type" mime-application-json "Cookie" (str "JSESSIONID=" jsession-id)}}))

(defn post-json-as-channel
  ([url data mapper]
   (post-as-channel url (to-json data) (post-json-options) mapper))
  ([url data mapper j-session-id]
   (post-as-channel url (to-json data) (post-json-options j-session-id) mapper)))

(defn status [channel status]
  (send! channel {:status  status
                  :headers {"Content-Type" mime-application-json}} false)
  channel)

(defn body [channel body]
  (send! channel body false)
  channel)

(defn body-and-close [channel body]
  (send! channel body false)
  (close channel)
  channel)

(defn exception-response [channel]
  (fn [exception]
    (do
      (log/error "Internal server error!" exception)
      (-> channel
          (status 500)
          (body (to-json {:error (.getMessage exception)}))
          (close)))))
