(ns ulkoiset-rajapinnat.utils.kayttooikeus
  (:require [clojure.string :as str]
            [clojure.core.async :refer [go]]
            [full.async :refer [<?]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid-channel]]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [ulkoiset-rajapinnat.utils.config :refer [config]]
            [ulkoiset-rajapinnat.utils.rest :refer [get-as-channel status body-and-close exception-response to-json parse-json-body-stream parse-json-body body parse-json-request]]
            [cheshire.core :refer [parse-string]])
  (:import (com.unboundid.ldap.sdk LDAPConnection SearchRequest SearchScope Filter)))

(defn fetch-user-from-kayttooikeus-service [username]
  (go
    (let [jsession-id (<? (fetch-jsessionid-channel "/kayttooikeus-service"))
            kayttooikeus-url (resolve-url :kayttooikeus-service.kayttooikeus.kayttaja username)
            kayttooikeus-response-ch (get-as-channel kayttooikeus-url
                                {:timeout 30000 :headers {"Cookie" (str "JSESSIONID=" jsession-id)}})
            kayttooikeus-response-body (-> kayttooikeus-response-ch <? parse-json-body first)]
      (println "XXX GOT BODY" kayttooikeus-response-body)
      {:username (kayttooikeus-response-body "username")
       :organisaatiot (kayttooikeus-response-body "organisaatiot")
       :personOid (kayttooikeus-response-body "oidHenkilo")})))


(defn fxetch-user-ldap [username]
  (let [uid-filter (Filter/createEqualityFilter "uid" username)
        search-request (SearchRequest. (-> @config :ldap :basedn)
                                       (SearchScope/SUB)
                                       uid-filter
                                       (into-array String
                                                   ["description"
                                                    "uid"
                                                    "sn"
                                                    "givenName"
                                                    "employeeNumber"]))
        connection (LDAPConnection.)]
    (try
      (.connect connection (-> @config :ldap :host) (-> @config :ldap :port))
      (.bind connection (-> @config :ldap :binddn) (-> @config :ldap :password))
      (let [result (.search connection search-request)
            entry (-> result
                      (.getSearchEntries)
                      (.iterator)
                      (.next))]
        {:username username
         :roles (set (parse-string (.getValue (.getAttribute entry "description"))))
         :personOid (.getValue (.getAttribute entry "employeeNumber"))})
      (finally (.close connection)))))
