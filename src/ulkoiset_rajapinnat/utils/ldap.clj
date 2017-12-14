(ns ulkoiset-rajapinnat.utils.ldap
  (:require [clojure.string :as str]
            [cheshire.core :refer [parse-string]])
  (:import (com.unboundid.ldap.sdk LDAPConnection SearchRequest SearchScope Filter)))

(defn fetch-user-from-ldap [config username]
  (let [uid-filter (Filter/createEqualityFilter "uid" username)
        search-request (SearchRequest. (-> config :ldap :basedn)
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
      (.connect connection (-> config :ldap :host) (-> config :ldap :port))
      (.bind connection (-> config :ldap :binddn) (-> config :ldap :password))
      (let [result (.search connection search-request)
            entry (-> result
                      (.getSearchEntries)
                      (.iterator)
                      (.next))]
        {:username username
         :roles (set (parse-string (.getValue (.getAttribute entry "description"))))
         :personOid (.getValue (.getAttribute entry "employeeNumber"))})
      (finally (.close connection)))))

