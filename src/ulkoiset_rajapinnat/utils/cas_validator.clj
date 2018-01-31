(ns ulkoiset-rajapinnat.utils.cas_validator
  (:require [clojure.string :as str]
            [clojure.data.xml :refer :all]
            [full.async :refer :all]
            [clojure.tools.logging :as log]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [ulkoiset-rajapinnat.utils.ldap :refer :all]
            [ulkoiset-rajapinnat.utils.rest :refer :all]))

(defn parse-cas-response
  [response]
  (try (let [body (response :body)
             xml (parse-str body)
             authentication-element (first (:content xml))]
         (if (= (:tag authentication-element) :authenticationSuccess)
           (first (:content (first (:content authentication-element))))
           (do
             (log/error "Service ticket was not valid!" body)
             (RuntimeException. "Service ticket was not valid!"))))
       (catch Exception e (RuntimeException. "Failed to validate CAS-ticket!"))))

(defn validate-service-ticket
  [for-service service-ticket]
  (let [host-virkailija (resolve-url :cas-client.host)
        url (resolve-url :cas-client.service-validate service-ticket for-service)]
    (go-try (<? (get-as-channel url {} parse-cas-response)))))
