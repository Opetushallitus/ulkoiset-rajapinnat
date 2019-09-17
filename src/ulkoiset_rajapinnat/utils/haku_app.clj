(ns ulkoiset-rajapinnat.utils.haku_app
  (:require [full.async :refer :all]
            [clj-http.client :as client]
            [cheshire.core :refer :all]
            [clojure.core.async :refer [chan promise-chan >! go put! close! alts! timeout <! go]]
            [clojure.tools.logging :as log]
            [ulkoiset-rajapinnat.utils.async_safe :refer :all]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [ulkoiset-rajapinnat.utils.read_stream :refer [read-json-stream-to-channel]]
            [ulkoiset-rajapinnat.utils.rest :refer [to-json get-as-channel parse-json-body body-and-close post-json-as-channel parse-json-body-stream]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-service-ticket-channel]]
            ))

(defn- hakemukset-for-hakukohde-oids-query [haku-oid hakukohde-oids] {"searchTerms" ""
                                                                      "asIds"       [haku-oid]
                                                                      "aoOids"      hakukohde-oids
                                                                      "states"      ["ACTIVE", "INCOMPLETE"]
                                                                      })

(defn fetch-hakemukset-from-haku-app-as-streaming-channel
  [haku-oid hakukohde-oids batch-size result-mapper]
  (let [query (hakemukset-for-hakukohde-oids-query haku-oid hakukohde-oids)
        service-ticket-channel (fetch-service-ticket-channel "/haku-app")
        channel (chan 1)]
    (go
      (try
        (log/info "Start reading 'haku-app'...")
        (let [st (<? service-ticket-channel)
              response (let [url (resolve-url :haku-app.streaming-listfull)]
                         (log/info (str "POST -> " url))
                         (client/post url {:headers {"CasSecurityTicket" st
                                                     "Content-Type"      "application/json"}
                                           :as      :stream
                                           :body    (to-json query)}))
              body-stream (response :body)]
          (read-json-stream-to-channel body-stream channel batch-size result-mapper))
        (catch Exception e
          (log/error e (format "Problem when reading haku-app for haku %s" haku-oid))
          (>! channel e)
          (close! channel))))
    channel))


(defn- hakemus-oids-for-hakuoid-and-hakukohde-oids-query [haku-oid hakukohde-oids] {"searchTerms" ""
                                                  "asIds"       [haku-oid]
                                                  "aoOids"      [hakukohde-oids]
                                                  "states"      ["ACTIVE", "INCOMPLETE"]
                                                  "keys"        ["oid"]
                                                  })

(defn- log-fetch [number-of-oids start-time response]
  (log/info "Fetching 'hakemus' (size =" number-of-oids ") ready with status" (response :status) "! Took " (- (System/currentTimeMillis) start-time) "ms!")
  response)

;(def hakemus-batch-size 500)

(defn fetch-hakemus-in-batch-channel
  ([hakemus-oids hakukohde-oids batch-size result-mapper]
   (if (empty? hakemus-oids)
     (go [])
     (go-try
       (let [url (resolve-url :haku-app.hakemus-by-oids)
             partitions (partition-all batch-size hakemus-oids)
             post (fn [x] (let [start-time (System/currentTimeMillis)
                                mapper (comp parse-json-body-stream (partial log-fetch (count hakemus-oids) start-time))]
                            ;(if (> (count x) 1000)
                            ;  (throw (new RuntimeException "Can only fetch 1000 hakemus at once!")))
                            (post-json-as-channel url x mapper)))
             hakemukset (<? (async-map-safe vector (map #(post %) partitions) []))]
         (apply concat hakemukset))))))

(defn fetch-hakemukset-from-haku-app-in-batches
  [haku-oid hakukohde-oids batch-size result-mapper]
  (let [query (hakemus-oids-for-hakuoid-and-hakukohde-oids-query haku-oid hakukohde-oids)
        service-ticket-channel (fetch-service-ticket-channel "/haku-app")]
    (if (nil? haku-oid)
      ((log/info "haku-oid is nil")
       (go []))
      (go
        (try
          (log/info (str "Start reading 'haku-app' for haku-oid " haku-oid))
          (let [st (<? service-ticket-channel)
                response (let [url (resolve-url :haku-app.listfull)
                               post-body (to-json query)]
                           (log/info (str "POST -> " url post-body))
                           (client/post url {:headers {"CasSecurityTicket" st
                                                       "Content-Type"      "application/json"}
                                             :body    post-body}))
                foo (log/info (str "Haettiin haku-appista oidit: " response))
                body (-> (parse-json-body response))
                hakemus-oids (map #(get % "oid") body)]
            (fetch-hakemus-in-batch-channel hakemus-oids hakukohde-oids batch-size result-mapper))
          (catch Exception e
            (log/error e (format "Problem when reading haku-app for haku %s" haku-oid))
            (throw e)))))))
