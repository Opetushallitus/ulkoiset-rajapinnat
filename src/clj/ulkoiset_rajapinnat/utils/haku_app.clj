(ns ulkoiset-rajapinnat.utils.haku_app
  (:require [full.async :refer :all]
            [clj-http.client :as client]
            [cheshire.core :refer :all]
            [clojure.core.async :refer [chan >! go close!]]
            [clojure.tools.logging :as log]
            [ulkoiset-rajapinnat.utils.async_safe :refer :all]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [ulkoiset-rajapinnat.utils.rest :refer [to-json parse-json-body]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-service-ticket-channel]]
            ))

(defn- hakemus-oids-for-hakuoid-and-hakukohde-oids-query [haku-oid hakukohde-oids] {"searchTerms" ""
                                                  "asIds"       [haku-oid]
                                                  "aoOids"      hakukohde-oids
                                                  "states"      ["ACTIVE", "INCOMPLETE"]
                                                  "keys"        ["oid"]
                                                  })

(defn- log-fetch [number-of-oids start-time response]
  (log/info "Fetching 'hakemus' (size =" number-of-oids ") ready with status" (response :status) "! Took " (- (System/currentTimeMillis) start-time) "ms!")
  response)

(defn fetch-hakemus-batches-recursively
  [batches channel result-mapper]
  (go
    (log/info (str "Hakemus-oid batches remaining: " (count batches)))
    (try (if (empty? batches)
           (do
             (log/info "Finished fetching hakemus-oid batches, closing channel.")
             (close! channel))
           (let [batch (first batches)
                 post-body (to-json batch)
                 st (<? (fetch-service-ticket-channel "/haku-app"))
                 response (client/post (resolve-url :haku-app.hakemus-by-oids)
                                       {:headers {"CasSecurityTicket" st
                                                  "Caller-Id" "1.2.246.562.10.00000000001.ulkoiset-rajapinnat"
                                                  "CSRF" "1.2.246.562.10.00000000001.ulkoiset-rajapinnat"
                                                  "Content-Type"      "application/json"}
                                        :cookies {"CSRF" {:value "1.2.246.562.10.00000000001.ulkoiset-rajapinnat" :path "/"}}
                                        :body    post-body})
                 response-body (-> (parse-json-body response))
                 result (result-mapper response-body)]
             (>! channel result)
             (fetch-hakemus-batches-recursively (rest batches) channel result-mapper)))
         (catch Exception e
           (log/error e (format "Problem when reading haku-app for hakemus batch"))
           (>! channel e)
           (close! channel))))
  channel)

(defn fetch-hakemus-in-batch-channel
  ([hakemus-oids hakukohde-oids channel batch-size result-mapper]
   (if (empty? hakemus-oids)
     (go
       (>! channel [])
       (close! channel))
     (go-try
       (let [partitions (partition-all batch-size hakemus-oids)]
         (fetch-hakemus-batches-recursively partitions channel result-mapper))))))

(defn fetch-hakemukset-from-haku-app-in-batches
  [haku-oid hakukohde-oids batch-size result-mapper]
  (let [query (hakemus-oids-for-hakuoid-and-hakukohde-oids-query haku-oid hakukohde-oids)
        service-ticket-channel (fetch-service-ticket-channel "/haku-app")
        channel (chan 1)]
      (if (nil? haku-oid)
      ((log/info "haku-oid is nil")
       (go []))
      (go
        (try
          (log/info (str "Start reading 'haku-app' for haku-oid " haku-oid))
          (let [st (<? service-ticket-channel)
                response (let [url (resolve-url :haku-app.listfull)
                               post-body (to-json query)]
                           (log/debugf "POST -> %" url post-body)
                           (client/post url {:headers {"CasSecurityTicket" st
                                                       "Caller-Id" "fi.opintopolku.ulkoiset-rajapinnat"
                                                       "Content-Type"      "application/json"}
                                             :body    post-body}))
                body (-> (parse-json-body response))
                hakemus-oids (map #(get % "oid") body)
                foo (log/info (str "Haettiin haku-appista " (count hakemus-oids) " oidia"))]
                (fetch-hakemus-in-batch-channel hakemus-oids hakukohde-oids channel batch-size result-mapper))
          (catch Exception e
            (log/errorf e "Problem when reading haku-app for haku %s" haku-oid)
            (>! channel e)
            (close! channel)))))
      channel))
