(ns ulkoiset-rajapinnat.utils.rest
  "Rest Utils"
  (:require [org.httpkit.client :as http]
            [cheshire.core :refer :all]
            [full.async :refer :all]
            [clojure.core.async :refer [>! promise-chan >! go put! close!]]
            [clojure.string :as s]
            [clojure.tools.logging :as log]
            [org.httpkit.server :refer :all])
  (:import [clojure.lang IExceptionInfo]
           [java.util.concurrent TimeUnit]))

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
      (catch Exception e
        (log/error e (format "Could not transform response '%s'" response))
        e))
    response))

(def ^:private one-hour-ms (* 1000 60 60))

(defn- call-as-channel [method url options mapper]
  (let [p (promise-chan nil (fn [e] (log/warnf e "%s %s failed!" method url)))
        options-with-ids (merge
                          {:socket-timeout     30000
                           :timeout            one-hour-ms}
                          (update-in options [:headers] assoc
                                    "Caller-Id" "1.2.246.562.10.00000000001.ulkoiset-rajapinnat"
                                    "CSRF" "1.2.246.562.10.00000000001.ulkoiset-rajapinnat"
                                    "Cookie" (s/join "; " (remove s/blank?
                                                             [(get-in options [:headers "Cookie"])
                                                              "CSRF=1.2.246.562.10.00000000001.ulkoiset-rajapinnat"]))))
        start-time (System/nanoTime)]
    (method url options-with-ids
      #(go
        (do (>! p (transform-response mapper %))
          (log/info "Response came in" (.toMillis TimeUnit/NANOSECONDS (- (System/nanoTime) start-time)) "ms from" url)
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

(def CSRF_VALUE "1.2.246.562.10.00000000001.ulkoiset-rajapinnat")

(defn post-json-with-cookies
  [cookies]
  {:as :stream :timeout 600000 :headers {"Content-Type" mime-application-json
                                         "CSRF" CSRF_VALUE
                                         "Cookie" (str cookies ";CSRF="CSRF_VALUE)}})

(defn post-json-options
  ([] {:as :stream :timeout 600000 :headers {"Content-Type" mime-application-json
                                             "CSRF" CSRF_VALUE
                                             "Cookie" (str "CSRF="CSRF_VALUE)}})
  ([jsession-id] {:as :stream :timeout 600000 :headers {"Content-Type" mime-application-json
                                                        "CSRF" CSRF_VALUE
                                                        "Cookie" (str "JSESSIONID=" jsession-id "; CSRF=" CSRF_VALUE)}}))

(defn post-json-as-channel-with-cookies
  ([url data mapper cookies]
   (post-as-channel url (to-json data) (post-json-with-cookies cookies) mapper)))

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
