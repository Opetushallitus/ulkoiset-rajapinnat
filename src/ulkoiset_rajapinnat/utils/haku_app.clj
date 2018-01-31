(ns ulkoiset-rajapinnat.utils.haku_app
  (:require [clojure.string :as str]
            [full.async :refer :all]
            [clj-http.client :as client]
            [cheshire.core :refer :all]
            [clojure.core.async :refer [chan promise-chan >! go put! close! alts! timeout <!]]
            [clojure.tools.logging :as log]
            [clojure.core.async :as async]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [ulkoiset-rajapinnat.utils.read_stream :refer [read-json-stream-to-channel]]
            [ulkoiset-rajapinnat.utils.rest :refer [to-json]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-service-ticket-channel]]
            )
  (:import (com.fasterxml.jackson.core JsonFactory JsonToken)
           (com.fasterxml.jackson.databind ObjectMapper)))

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
        (let [st (<? service-ticket-channel)
              response (client/post (resolve-url :haku-app.streaming-listfull) {:headers {"CasSecurityTicket" st
                                                                             "Content-Type"      "application/json"}
                                                                   :as      :stream
                                                                   :body    (to-json query)})
              body-stream (response :body)]
          (read-json-stream-to-channel body-stream channel batch-size result-mapper))
        (finally
          (log/info "Done reading 'haku-app'!")
          (close! channel))))
    channel))
