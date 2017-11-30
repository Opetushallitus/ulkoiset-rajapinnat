(ns ulkoiset-rajapinnat.utils.mongo
  (:require [clojure.string :as str]
            [manifold.stream :as s]
            [clojure.core.async :refer [close! put! chan]]
            [clojure.tools.logging :as log]
            [clojure.core.async.impl.protocols :as impl])
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

(defn atomic-take! [atom batch]
  (loop [oldval @atom]
    (if (>= (count oldval) batch)
      (let [spl (split-at batch oldval)]
        (if (compare-and-set! atom oldval (vec (second spl)))
          (vec (first spl))
          (recur @atom)))
      nil)))

(defn drain! [atom]
  (loop [oldval @atom]
             (if (compare-and-set! atom oldval [])
               oldval
               (recur @atom))))

(defn- handle-document-batch [document-batch document-batch-channel batch-size]
  (if-let [batch (atomic-take! document-batch batch-size)]
    (put! document-batch-channel batch)))

(comment
(do
  (log/error "Got nil batch! This should never happen!")
  (throw (RuntimeException. "Got nil batch! This should never happen!"))))

(defn publisher-as-channel
  ([publisher]
   (publisher-as-channel publisher 500))
  ([publisher batch-size]
   (let [document-batch-channel (chan 2)
         document-batch (atom [])
         handle-incoming-document (fn [s document]
                                    (do
                                      (swap! document-batch conj document)
                                      (handle-document-batch document-batch document-batch-channel batch-size)
                                      ))]
     (.subscribe publisher
                 (subscribe (fn [s]
                              (fn
                                ([]
                                 (let [last-documents (drain! document-batch)]
                                   (if (not (empty? last-documents))
                                     (put! document-batch-channel last-documents)
                                     (put! document-batch-channel []))
                                   (log/debug "Got last hakemus so closing channel!")
                                   (close! document-batch-channel)))
                                ([document]
                                 (if (impl/closed? document-batch-channel)
                                   (do
                                     (log/warn "Channel cancelled before all documents were read! Client disconnected perhaps.")
                                     (.cancel s))
                                   (handle-incoming-document s document)))
                                ([_ throwable]
                                 (log/error "Streaming failed from MongoDB!" throwable)
                                 (put! document-batch-channel throwable)
                                 (close! document-batch-channel))))))
     document-batch-channel)))
