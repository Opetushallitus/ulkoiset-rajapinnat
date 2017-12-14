(ns ulkoiset-rajapinnat.utils.ataru
  (:require [clojure.string :as str]
            [full.async :refer :all]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid-channel fetch-jsessionid parse-jsessionid jsessionid-channel]]
            [clojure.core.async :refer [<! promise-chan >! go put! close!]]
            [clojure.tools.logging :as log]
            [ulkoiset-rajapinnat.utils.config :refer :all]
            [ulkoiset-rajapinnat.utils.cas :refer [service-ticket-channel]]
            [ulkoiset-rajapinnat.utils.rest :refer :all]))

(def ataru-cas-logout "%s/lomake-editori/auth/logout")
(def ataru-cas-api "%s/lomake-editori/auth/cas?ticket=%s")
(def ataru-api "%s/lomake-editori/api/external/tilastokeskus?hakuOid=%s")

(defn fetch-hakemukset-from-ataru [config haku-oid]
  (go
    (let [host (-> config :ataru-host-virkailija)
          url (format ataru-api host haku-oid)
          username (-> config :ulkoiset-rajapinnat-cas-username)
          password (-> config :ulkoiset-rajapinnat-cas-password)
          ticket (<? (service-ticket-channel host "/lomake-editori/auth/cas" username password true))
          response (<? (get-as-channel (format ataru-cas-api host ticket) {:follow-redirects false}))]
      (try
        (let [hakemukset (<? (get-as-channel
                               (format ataru-api host haku-oid)
                               {:headers {"Cookie" (-> response :headers :set-cookie)}}
                               parse-json-body))
              logout (<! (get-as-channel (format ataru-cas-logout host) {:headers {"Cookie" (-> response :headers :set-cookie)}}))]
          hakemukset)))))
