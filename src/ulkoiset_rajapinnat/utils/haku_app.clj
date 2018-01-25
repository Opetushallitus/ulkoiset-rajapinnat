(ns ulkoiset-rajapinnat.utils.haku_app
  (:require [clojure.string :as str]
            [full.async :refer :all]
            [clj-http.client :as client]
            [cheshire.core :refer :all]
            [ulkoiset-rajapinnat.utils.rest :refer [to-json]]
            [ulkoiset-rajapinnat.utils.cas :refer [service-ticket-channel]]
            [clojure.core.async :refer [chan promise-chan >! go put! close! alts! timeout <!]]
            [clojure.tools.logging :as log]
            [clojure.core.async :as async])
  (:import (com.fasterxml.jackson.core JsonFactory JsonToken)
           (com.fasterxml.jackson.databind ObjectMapper)))

(def haku-streaming-api "/haku-app/streaming/applications/listfull")
(defn- hakemukset-for-hakukohde-oids-query [haku-oid hakukohde-oids] {"searchTerms" ""
                                                                      "asIds"       [haku-oid]
                                                                      "aoOids"      hakukohde-oids
                                                                      "states"      ["ACTIVE", "INCOMPLETE"]
                                                                      })

(defn fetch-hakemukset-from-haku-app-as-streaming-channel
  ([config haku-oid hakukohde-oids]
   (fetch-hakemukset-from-haku-app-as-streaming-channel config haku-oid hakukohde-oids 500))
  ([config haku-oid hakukohde-oids batch-size]
   (let [host (config :host-virkailija)
         service "/haku-app"
         username (config :ulkoiset-rajapinnat-cas-username)
         password (config :ulkoiset-rajapinnat-cas-password)
         query (hakemukset-for-hakukohde-oids-query haku-oid hakukohde-oids)
         service-ticket-channel (service-ticket-channel host service username password)
         channel (chan 1)]
     (go
       (let [st (<? service-ticket-channel)
             response (client/post (str host haku-streaming-api) {:headers {"CasSecurityTicket" st
                                                                            "Content-Type"      "application/json"}
                                                                  :as      :stream
                                                                  :body    (to-json query)})
             body-stream (response :body)
             mapper (ObjectMapper.)
             parser (-> (doto (JsonFactory.)
                          (.setCodec mapper))
                        (.createParser (clojure.java.io/reader body-stream)))]
         (try
           (case (.nextToken parser)
             (JsonToken/START_ARRAY))
           (let [batch (java.util.ArrayList. batch-size)
                 drain-to-vector (fn []
                                   (let [v (vec (.toArray batch))]
                                     (.clear batch)
                                     (if (not-empty v)
                                       v
                                       nil)))]
             (while (= (.nextToken parser) (JsonToken/START_OBJECT))
               (let [obj (-> mapper
                             (.readValue parser java.util.HashMap))]
                 (-> batch (.add obj))
                 (if (= (count batch) batch-size)
                   (if (not (>! channel (drain-to-vector)))
                     (throw (RuntimeException. "Client disconnected! Releasing resources!"))))))
             (when-let [last-batch (drain-to-vector)]
               (>! channel last-batch)))
           (catch Exception e
             (do
               (log/error "Failed to read hakemus json from 'haku-app'!" (.getMessage e))
               (>! channel e)
               (throw e)))
           (finally
             (.close body-stream)
             (close! channel)))))
     channel)))
