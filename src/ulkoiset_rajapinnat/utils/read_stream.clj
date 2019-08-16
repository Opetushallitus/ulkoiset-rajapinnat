(ns ulkoiset-rajapinnat.utils.read_stream
  (:require [full.async :refer :all]
            [cheshire.core :refer :all]
            [clojure.core.async :refer [>! go close!]]
            [clojure.tools.logging :as log])
  (:import (com.fasterxml.jackson.databind ObjectMapper)
           (com.fasterxml.jackson.core JsonFactory JsonToken)))

(defn read-json-stream-to-channel [input-stream channel batch-size result-mapper]
  (go
    (try
      (let [mapper (ObjectMapper.)
            parser (-> (doto (JsonFactory.)
                         (.setCodec mapper))
                       (.createParser (clojure.java.io/reader input-stream)))
            first-token (.nextToken parser)]
        (log/info "Starting to read JSON stream")
        (when-not (= JsonToken/START_ARRAY first-token)
          (throw (RuntimeException. (format "Expected JSON stream to start with %s but was %s" JsonToken/START_ARRAY (.nextToken parser)))))
        (let [batch (java.util.ArrayList. batch-size)
              drain-to-vector (fn []
                                (let [v (vec (.toArray batch))]
                                    (.clear batch)
                                    (when (not-empty v)
                                      (result-mapper v))))]
          (try
            (while (= (.nextToken parser) (JsonToken/START_OBJECT))
              (log/info "About to read value from parser")
              (let [obj (-> mapper
                            (.readValue parser java.util.HashMap))]
                (log/info (format "Value read and mapped from parser: %s" obj))
                (-> batch (.add obj))
                (log/info (format "Added to batch obj %s" obj))
                (if (= (count batch) batch-size)
                  (if (not (>! channel (drain-to-vector)))
                    (throw (RuntimeException. "Channel was closed before reading stream completed!"))))
                (log/info "looping again")))
            (catch Exception e
              (log/error "Exception in read-stream while loop" e)
              (throw e)))
          (when-let [last-batch (drain-to-vector)]
            (>! channel last-batch))))
      (catch Exception e
        (log/error "Exception when reading stream" e)
        (>! channel e))
      (finally
        (if (instance? java.io.InputStream input-stream)
          (try
            (log/info "Closing input stream")
            (.close input-stream)
            (catch Exception e
              (log/error "Exception when closing input stream" e)
              (>! channel e)
              ))
          )
        (log/info "Closing channel")
        (close! channel)))))
