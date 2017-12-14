(ns ulkoiset-rajapinnat.utils.cas_validator
  (:require [clojure.string :as str]
            [clojure.data.xml :refer :all]
            [full.async :refer :all]
            [clojure.core.async :refer [promise-chan >! go put! close!]]
            [clojure.tools.logging :as log]
            [ulkoiset-rajapinnat.utils.config :refer :all]
            [ulkoiset-rajapinnat.utils.ldap :refer :all]
            [ulkoiset-rajapinnat.utils.cas :refer [service-ticket-channel]]
            [ulkoiset-rajapinnat.utils.rest :refer :all]))

(def cas-validate-api "%s/cas/serviceValidate?ticket=%s&service=%s")

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
  [config for-service service-ticket]
  (let [host-virkailija (-> config :host-virkailija)
        url (format cas-validate-api host-virkailija service-ticket for-service)]
    (go-try (<? (get-as-channel url {} parse-cas-response)))))
