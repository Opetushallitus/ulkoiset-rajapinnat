(ns ulkoiset-rajapinnat.utils.haku_app
  (:require [clojure.string :as str]
            [full.async :refer :all]
            [clj-http.client :as client]
            [cheshire.core :refer :all]
            [ulkoiset-rajapinnat.utils.rest :refer [to-json]]
            [ulkoiset-rajapinnat.utils.cas :refer [service-ticket-channel]]
            [clojure.core.async :refer [chan promise-chan >! go put! close!]]
            [clojure.tools.logging :as log]
            [clojure.core.async :as async]))

(def haku-streaming-api "/haku-app/streaming/applications/listfull")
(defn- hakemukset-for-hakukohde-oids-query [haku-oid hakukohde-oids] {"searchTerms" ""
                                                                     "asIds" [haku-oid]
                                                                     "aoOids" hakukohde-oids
                                                                     "states" ["ACTIVE","INCOMPLETE"]
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
       (try
         (let [st (<? service-ticket-channel)
               body-stream ((client/post (str host haku-streaming-api) {:headers {"CasSecurityTicket" st
                                                                                  "Content-Type" "application/json"}
                                                                        :as :stream
                                                                        :body (to-json query)}) :body)
               lazy-hakemus-seq (parse-stream (clojure.java.io/reader body-stream))]
           (doseq [hakemus (partition-all batch-size lazy-hakemus-seq)]
             (if-let [error (get hakemus "error")]
               (throw (RuntimeException. error))
               (async/put! channel hakemus (fn [open?]
                                             (if (not open?)
                                               (.close body-stream)))))))
         (catch Exception e (async/put! channel e))))
     channel)))
