(ns ulkoiset-rajapinnat.utils.kayttooikeus
  (:require [clojure.core.async :refer [go]]
            [full.async :refer [<?]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid-channel]]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [ulkoiset-rajapinnat.utils.rest :refer [get-as-channel parse-json-body]]))

(defn fetch-user-from-kayttooikeus-service [username]
  (go
    (let [jsession-id (<? (fetch-jsessionid-channel "/kayttooikeus-service"))
          kayttooikeus-url (resolve-url :kayttooikeus-service.kayttooikeus.kayttaja username)
          kayttooikeus-response-ch (get-as-channel kayttooikeus-url
                                                   {:timeout 30000 :headers {"Cookie" (str "JSESSIONID=" jsession-id)}})
          kayttooikeus-response-body (-> kayttooikeus-response-ch <? parse-json-body first)]
      (if kayttooikeus-response-body
        {:username      (kayttooikeus-response-body "username")
         :organisaatiot (kayttooikeus-response-body "organisaatiot")
         :personOid     (kayttooikeus-response-body "oidHenkilo")}
        (RuntimeException. (format "Could not find user '%s' from %s" username kayttooikeus-url))))))
