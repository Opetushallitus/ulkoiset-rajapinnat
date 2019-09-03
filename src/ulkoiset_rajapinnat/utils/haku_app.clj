(ns ulkoiset-rajapinnat.utils.haku_app
  (:require [full.async :refer :all]
            [clj-http.client :as client]
            [cheshire.core :refer :all]
            [clojure.core.async :refer [chan promise-chan >! go put! close! alts! timeout <!]]
            [clojure.tools.logging :as log]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [ulkoiset-rajapinnat.utils.read_stream :refer [read-json-stream-to-channel]]
            [ulkoiset-rajapinnat.utils.rest :refer [to-json get-as-channel parse-json-body body-and-close]]
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
