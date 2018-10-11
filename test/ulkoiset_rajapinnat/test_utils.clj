(ns ulkoiset-rajapinnat.test_utils
  (:require [clojure.core.async :refer [promise-chan put! >!! close! go]]
            [clojure.test :refer [is]]
            [clj-log4j2.core :as log]
            [ulkoiset-rajapinnat.utils.access :refer [write-access-log]]
            [picomock.core :as pico])
  (:import (java.io ByteArrayInputStream)))

(defn to-input-stream [string]
  (new ByteArrayInputStream (.getBytes string)))

(defn channel-response
  ([transform url status data input-stream]
   (transform {:opts {:url url} :status status :body (if (true? input-stream) (to-input-stream data) data)}))
  ([transform url status data]
   (channel-response transform url status data true)))

(defn mock-channel [result]
  (let [p (promise-chan)]
    (try
      (>!! p result)
      (catch Exception e (>!! p e))
      (finally
        (close! p)))
    p))

(def default-write-access-log write-access-log)

(def mock-write-access-log (fn [start-time response-code request error-message]
                             (default-write-access-log start-time response-code request error-message)))

(defn assert-access-log-write [access-log-mock expected-status expected-error-message]
  (Thread/sleep 10000)
  (is (= 1 (pico/mock-calls access-log-mock)))
  (is (= expected-status (-> (pico/mock-args access-log-mock) first second)))
  (when expected-error-message
    (is (re-find (re-pattern expected-error-message) (nth (first (pico/mock-args access-log-mock)) 3)))))

(defn mock-haku-not-found-http [url transform]
  (log/info (str "Mocking url " url))
  (def response (partial channel-response transform url))
  (case url
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/haku/INVALID_HAKU" (response 200 "{}")
    (response 404 "[]")))

