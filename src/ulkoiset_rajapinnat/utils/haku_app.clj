(ns ulkoiset-rajapinnat.utils.haku_app
  (:require [clojure.string :as str]
            [full.async :refer :all]
            [clj-http.client :as client]
            [cheshire.core :refer :all]
            [ulkoiset-rajapinnat.utils.rest :refer [to-json]]
            [ulkoiset-rajapinnat.utils.cas :refer [service-ticket-channel]]
            [clojure.core.async :refer [chan promise-chan >! go put! close! alts! timeout <!]]
            [clojure.tools.logging :as log]
            [clojure.core.async :as async]))

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
     (go-try
       (let [st (<? service-ticket-channel)
             response (client/post (str host haku-streaming-api) {:headers {"CasSecurityTicket" st
                                                                            "Content-Type"      "application/json"}
                                                                  :as      :stream
                                                                  :body    (to-json query)})
             body-stream (response :body)
             lazy-hakemus-seq (parse-stream (clojure.java.io/reader body-stream))
             ]
         (try
           (doseq [hakemus-batch (partition-all batch-size lazy-hakemus-seq)]
             (if-let [error (some #(find % "error") hakemus-batch)]
               (throw (RuntimeException. (str error)))
               (if (not (>! channel hakemus-batch))
                 (throw (RuntimeException. "Client disconnected! Releasing resources!")))))
           (comment
             "Somehow this doesnt work! Memory is not released!"
             (if (not (alts! [[channel hakemus-batch]
                              (timeout (* 30 60 1000))]))
               (throw
                 (RuntimeException.
                   (str
                     "Client disconnected or nobody read a batch from hakemus stream in 30 minutes! "
                     "Closing connection to haku-app to release resources!")))))
           (catch Exception e
             (do
               (log/error "Failed to read hakemus json from 'haku-app'!" (.getMessage e))
               (>! channel e)
               (throw e)))
           (finally
             (.close body-stream)
             (close! channel)))))
     channel)))
