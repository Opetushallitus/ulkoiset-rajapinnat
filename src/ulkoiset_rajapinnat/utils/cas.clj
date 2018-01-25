(ns ulkoiset-rajapinnat.utils.cas
  (:require [manifold.deferred :refer [let-flow catch chain]]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [full.async :refer :all]
            [ulkoiset-rajapinnat.utils.rest :refer [get-as-channel post-form-as-channel get-as-promise post-form-as-promise status body body-and-close exception-response parse-json-body to-json]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]
            [jsoup.soup :refer :all]))

(def tgt-api "%s/cas/v1/tickets")

(defn read-action-attribute-from-cas-response [response]
  (let [html (response :body)
        action-attribute (attr "action" ($ (parse html) "form"))]
    (first action-attribute)))

(defn post-tgt-request
  [host username password]
  (chain (post-form-as-promise (format tgt-api host)
                        {:username username
                         :password password})
         read-action-attribute-from-cas-response))

(defn tgt-request-channel
  [host username password]
  (post-form-as-channel (format tgt-api host)
                        {:username username
                         :password password}
                        read-action-attribute-from-cas-response))

(defn post-st-request
  [service host]
  (chain (post-form-as-promise host {:service service}) #(% :body)))

(defn- parse-service-ticket [service response]
  (if-let [st (response :body)]
    st
    (RuntimeException. (format "Unable to parse service ticket for service %s!" service))))

(defn st-request-channel
  [service host]
  (post-form-as-channel host
                        {:service service}
                        (partial parse-service-ticket service)))

(defn fetch-service-ticket
  [host service username password]
  (let [absolute-service (str host service "/j_spring_cas_security_check")
        st-promise (chain (post-tgt-request host username password)
                          (partial post-st-request absolute-service))]
    st-promise))

(defn service-ticket-channel
  ([host service username password as-absolute-service?]
   (go-try
     (let [absolute-service (if as-absolute-service?
                              (str host service)
                              (str host service "/j_spring_cas_security_check"))
           tgt (<? (tgt-request-channel host username password))
           st (<? (st-request-channel absolute-service tgt))]
       st)))
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
    (let [st (<? (service-ticket-channel host service username password))
          jid (<? (jsessionid-channel host service st))]
      jid)))

(defn jsessionid-fetcher-channel [host username password]
  (fn [service]
    (fetch-jsessionid-channel host service username password)))