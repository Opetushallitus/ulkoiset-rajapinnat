(ns ulkoiset-rajapinnat.utils.ataru
  (:require [clojure.string :as str]
            [full.async :refer :all]
            [clojure.tools.logging :as log]
            [clojure.core.async :refer [chan promise-chan >! go put! close! alts! timeout <!]]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [ulkoiset-rajapinnat.utils.read_stream :refer [read-json-stream-to-channel]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-service-ticket-channel]]
            [ulkoiset-rajapinnat.utils.rest :refer :all]))

(defn fetch-hakemukset-from-ataru [haku-oid batch-size result-mapper]
  (let [channel (chan 1)]
    (go
      (try
        (let [ticket (<? (fetch-service-ticket-channel "/lomake-editori/auth/cas" true))
              response (<? (get-as-channel (resolve-url :lomake-editori.cas-by-ticket ticket) {:follow-redirects false}))
              hakemukset (<? (get-as-channel (resolve-url :lomake-editori.tilastokeskus-by-haku-oid haku-oid)
                                             {:headers {"Cookie" (-> response :headers :set-cookie)} :as :stream}))
              body-stream (hakemukset :body)]
          (read-json-stream-to-channel body-stream channel batch-size result-mapper))
        (catch Exception e
          (do
            (log/error e "Failed to read hakemus json from 'ataru'!")
            (>! channel e)
            (close! channel)))))
    channel))
