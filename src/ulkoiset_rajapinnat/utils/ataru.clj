(ns ulkoiset-rajapinnat.utils.ataru
  (:require [clojure.string :as str]
            [full.async :refer :all]
            [clj-http.client :as client]
            [clojure.tools.logging :as log]
            [clojure.core.async :refer [chan promise-chan >! go put! close! alts! timeout <!]]
            [ulkoiset-rajapinnat.utils.config :refer :all]
            [ulkoiset-rajapinnat.utils.read_stream :refer [read-json-stream-to-channel]]
            [ulkoiset-rajapinnat.utils.cas :refer [service-ticket-channel]]
            [ulkoiset-rajapinnat.utils.rest :refer :all]))

(def ataru-cas-logout "%s/lomake-editori/auth/logout")
(def ataru-cas-api "%s/lomake-editori/auth/cas?ticket=%s")
(def ataru-api "%s/lomake-editori/api/external/tilastokeskus?hakuOid=%s")

(defn fetch-hakemukset-from-ataru [config haku-oid batch-size result-mapper]
  (let [channel (chan 1)]
    (go
      (let [host (-> config :ataru-host-virkailija)
            url (format ataru-api host haku-oid)
            username (-> config :ulkoiset-rajapinnat-cas-username)
            password (-> config :ulkoiset-rajapinnat-cas-password)
            ticket (<? (service-ticket-channel host "/lomake-editori/auth/cas" username password true))
            response (<? (get-as-channel (format ataru-cas-api host ticket) {:follow-redirects false}))]
        (try
          (let [hakemukset (client/get (format ataru-api host haku-oid) {:headers {"Cookie" (-> response :headers :set-cookie)}
                                                                         :as      :stream})
                body-stream (hakemukset :body)]
            (try
              (read-json-stream-to-channel body-stream channel batch-size result-mapper)
              (catch Exception e
                (do
                  (log/error "Failed to read hakemus json from 'haku-app'!" (.getMessage e))
                  (>! channel e)
                  (throw e))))
            )
          (finally
            (<! (get-as-channel (format ataru-cas-logout host) {:headers {"Cookie" (-> response :headers :set-cookie)}}))))))
    channel))
