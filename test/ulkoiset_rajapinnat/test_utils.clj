(ns ulkoiset-rajapinnat.test_utils
  (:require [clojure.core.async :refer [promise-chan put! >!! close!]])
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
    (close! p)
      (catch Exception e (>!! p e)))
    p))