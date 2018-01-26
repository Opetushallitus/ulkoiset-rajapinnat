(ns ulkoiset-rajapinnat.utils.cas
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.core.async :refer [put! go promise-chan]]
            [full.async :refer :all]
            [ulkoiset-rajapinnat.utils.rest :refer [get-as-channel post-form-as-channel]]
            [jsoup.soup :refer :all]))

(def tgt-api "%s/cas/v1/tickets")

(defn read-action-attribute-from-cas-response [response]
  (let [html (response :body)
        action-attribute (attr "action" ($ (parse html) "form"))]
    (first action-attribute)))

(defn tgt-request-channel
  [host username password]
  (post-form-as-channel (format tgt-api host)
                        {:username username
                         :password password}
                        read-action-attribute-from-cas-response))

(defn- parse-service-ticket [service response]
  (if-let [st (response :body)]
    st
    (RuntimeException. (format "Unable to parse service ticket for service %s!" service))))

(defn st-request-channel
  [service host]
  (post-form-as-channel host
                        {:service service}
                        (partial parse-service-ticket service)))

(defn service-ticket-channel
  ([host service username password as-absolute-service?]
   (let [p-chan (promise-chan)]
     (go
       (let [absolute-service (if as-absolute-service?
                                (str host service)
                                (str host service "/j_spring_cas_security_check"))
             tgt (<? (tgt-request-channel host username password))
             st (<? (st-request-channel absolute-service tgt))]
         (put! p-chan st)))
     p-chan))
  ([host service username password]
   (service-ticket-channel host service username password false)))

(defn parse-jsessionid
  ([response]
    (parse-jsessionid response #"JSESSIONID=(\w*);"))
  ([response regex]
  (if-let [sid (nth (re-find regex ((response :headers) :set-cookie)) 1)]
    sid
    (RuntimeException. (format "Unable to parse session ID! Uri = %s" (get-in response [:opts :url]))))))

(defn jsessionid-channel
  [host service service-ticket]
  (get-as-channel (str host service)
                  {:headers {"CasSecurityTicket" service-ticket}}
                  parse-jsessionid))

(defn fetch-jsessionid-channel
  [host service username password]
  (go-try
    (let [st (<? (service-ticket-channel host service username password))]
      (<? (jsessionid-channel host service st)))))

(defn jsessionid-fetcher-channel [host username password]
  (fn [service]
    (fetch-jsessionid-channel host service username password)))