(ns ulkoiset-rajapinnat.utils.mongo
  (:require [manifold.deferred :refer [let-flow catch chain]]
            [clojure.string :as str]
            [manifold.stream :as s]
            [clj-log4j2.core :as log])
  (:refer-clojure :exclude [and])
  (:import (org.bson.conversions Bson)))

(defn create-mongo-client [mongo-uri] (com.mongodb.reactivestreams.client.MongoClients/create mongo-uri))

(defn in [field & args]
  (com.mongodb.client.model.Filters/in field (into-array String args)))

(defn and ([a1 a2]
           (com.mongodb.client.model.Filters/and (into-array Bson [a1 a2]))))

(defn eq [field val]
  (com.mongodb.client.model.Filters/eq field val))

(defn subscribe [on-subscribe]
  (let [on-event (atom nil)]
    (reify
      org.reactivestreams.Subscriber
      (onSubscribe [this subscription]
        (do
          (reset! on-event (on-subscribe subscription))
          (.request subscription (Long/MAX_VALUE))))
      (onNext [this document]
        (@on-event document))
      (onError [this throwable]
        (do
          (log/error "Mongo subscription failed!" throwable)
          (@on-event nil throwable)))
      (onComplete [this]
        (do
          (log/debug "Subscription completed!")
          (@on-event))))))

(defn publisher-to-stream [publisher on-error]
  (let [stream (s/stream)]
    (.subscribe publisher
                (subscribe (fn [s]
                             (fn
                               ([]
                                (s/close! stream))
                               ([document]
                                (if (s/closed? stream)
                                  (do
                                    (log/warn "Stream cancelled before all documents were read! Client disconnected perhaps.")
                                    (.cancel s))
                                  (s/put! stream document)))
                               ([_ throwable]
                                (log/error "Streaming failed from MongoDB!" throwable)
                                (on-error throwable)
                                (s/close! stream))))))
    stream))
