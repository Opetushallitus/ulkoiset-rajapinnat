(ns ulkoiset-rajapinnat.utils.cas
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.core.async :refer [>! go promise-chan close!]]
            [full.async :refer :all]
            [ulkoiset-rajapinnat.utils.config :refer [config]]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [ulkoiset-rajapinnat.utils.rest :refer [get-as-channel post-form-as-channel]]
            [jsoup.soup :refer :all]))

(defn ^:private read-action-attribute-from-cas-response [response]
  (let [html (response :body)
        action-attribute (attr "action" ($ (parse html) "form"))
        first-action-attribute (first action-attribute)]
    (if (str/blank? first-action-attribute)
      (RuntimeException. (str "No 'action' attribute found in CAS response: " response))
      first-action-attribute)))

(defn ^:private tgt-request-channel
  [host username password]
  (post-form-as-channel (resolve-url :cas-client.tgt)
                        {:username username
                         :password password}
                        read-action-attribute-from-cas-response))

(defn ^:private parse-service-ticket [service response]
  (if-let [st (response :body)]
    (if (instance? java.io.InputStream st) (new String (.bytes st) "UTF-8") st)
    (RuntimeException. (format "Unable to parse service ticket for service %s!" service))))

(defn ^:private st-request-channel
  [service host]
  (post-form-as-channel host
                        {:service service}
                        (partial parse-service-ticket service)))

(defn ^:private service-ticket-channel
  ([host service username password as-absolute-service?]
   (let [p-chan (promise-chan)]
     (go
       (try
         (log/info "Getting service ticket channel for service " service)
         (let [absolute-service (if as-absolute-service?
                                  (str host service)
                                  (str host service "/j_spring_cas_security_check"))
               tgt (<? (tgt-request-channel host username password))
               st (<? (st-request-channel absolute-service tgt))]
           (>! p-chan st))
         (catch Exception e (>! p-chan e))
         (finally (close! p-chan))))
     p-chan))
  ([host service username password]
   (service-ticket-channel host service username password false)))

(defn ^:private parse-jsessionid
  ([response]
   (parse-jsessionid response #"JSESSIONID=([\w\._-]*);"))
  ([response regex]
   (if-let [sid (nth (re-find regex ((response :headers) :set-cookie)) 1)]
     sid
     (RuntimeException. (format "Unable to parse session ID! Uri = %s" (get-in response [:opts :url]))))))

(defn ^:private jsessionid-channel
  [host service service-ticket]
  (get-as-channel (str host service)
                  {:headers {"CasSecurityTicket" service-ticket}}
                  parse-jsessionid))

(defn fetch-jsessionid-channel
  ([service]
   (let [host (resolve-url :cas-client.host)
         username (@config :ulkoiset-rajapinnat-cas-username)
         password (@config :ulkoiset-rajapinnat-cas-password)]
     (fetch-jsessionid-channel host service username password)))
  ([host service username password]
   (go-try
     (let [st (<? (service-ticket-channel host service username password))]
       (<? (jsessionid-channel host service st))))))

(defn fetch-service-ticket-channel
  ([service] (fetch-service-ticket-channel service false))
  ([service as-absolute-service?]
   (let [host (resolve-url :cas-client.host)
         username (@config :ulkoiset-rajapinnat-cas-username)
         password (@config :ulkoiset-rajapinnat-cas-password)]
     (service-ticket-channel host service username password as-absolute-service?))))
