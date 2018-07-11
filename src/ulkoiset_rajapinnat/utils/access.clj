(ns ulkoiset-rajapinnat.utils.access
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [full.async :refer :all]
            [clojure.core.async :refer [to-chan]]
            [ulkoiset-rajapinnat.utils.rest :refer [status body to-json]]
            [clojure.core.async :refer [promise-chan >! go put! close!]]
            [clojure.tools.logging.impl :as impl]
            [ulkoiset-rajapinnat.utils.headers :refer [user-agent-from-request remote-addr-from-request parse-request-headers]]
            [ring.util.http-response :refer [unauthorized bad-request internal-server-error]]
            [ulkoiset-rajapinnat.utils.ldap :refer :all]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [ulkoiset-rajapinnat.utils.cas_validator :refer :all]
            [ulkoiset-rajapinnat.utils.config :refer [config]]
            [org.httpkit.server :refer :all]))

(def ^{:private true} logger (impl/get-logger (impl/find-factory) "ACCESS"))
(comment
  Missing fields
  {
   "timestamp" "2017-11-20T00:00:00.773+0200"
   "environment" "-"
   "caller-id" "-"
   "x-forwarded-for" "-"
   "remote-ip" "x.x.x.x"
   "session" "-"
   "response-size" "bytes"
   "referer" "-"
   }
  )

(defn- do-logging [start-time closed-by request]
  (.info logger
         (to-json (parse-request-headers request closed-by start-time))))

(defn access-log
  ([response]
   (let [start-time (System/currentTimeMillis)]
     (access-log response start-time)))
  ([response start-time]
   (fn [request]
     (do-logging start-time (str (response :status)) request)
     response)))

(defn- do-real-authentication-and-authorisation-check [ticket]
  (go-try
   (let [host-virkailija (resolve-url :cas-client.host)
         service (str host-virkailija "/ulkoiset-rajapinnat")
         username (<? (validate-service-ticket service ticket))
         ldap-user (fetch-user-from-ldap username)
         roles (ldap-user :roles)]
     (if (clojure.set/subset? #{"APP_ULKOISETRAJAPINNAT_READ"} roles)
       ldap-user
       (do
         (log/error "User" username "is missing role APP_ULKOISETRAJAPINNAT_READ!")
         (RuntimeException. "Required roles missing!"))))))

(defn check-ticket-is-valid-and-user-has-required-roles [ticket]
  (if (and
       (boolean (-> @config :enable-dangerous-magic-ticket-access))
       (= ticket (str (-> @config :dangerous-magic-ticket))))
    (do
      (log/warn "Enabling dangerous access with magic ticket value, skipping CAS authentication and authorisation. This must not happen in production!")
      (to-chan '({:username "Dangerous Tester"
                  :roles (set "APP_ULKOISETRAJAPINNAT_READ_1.2.246.562.10.00000000001")
                  :personOid "1.2.246.562.24.50534365452"})))
    (do-real-authentication-and-authorisation-check ticket)))

(defn handle-invalid-request [ex data request]
  (let [message (.getMessage ex)
        current-time (System/currentTimeMillis)]
    (do-logging current-time "Server" request)
    (bad-request {:message message :type :info})))

(defn handle-exception [channel start-time exception]
  (let [message (.getMessage exception)]
    (access-log
      (internal-server-error message)
      start-time)
    (-> channel
        (status 500)
        (body (to-json {:error message}))
        (close))))

(defn handle-unauthorized [channel start-time request]
  (let [message "Ticket was not valid or user doesn't have required roles!"]
  ((access-log
    (unauthorized message)
    start-time) request)
  (-> channel
      (status 401)
      (body (to-json {:error message}))
      (close))))

(defn access-log-with-ticket-check-with-channel [ticket audit-log operation]
  (fn [request]
    (if-let [some-ticket ticket]
      (let [start-time (System/currentTimeMillis)
            on-close-handler (fn [status] (do-logging start-time
                                                      (case status :server-close "Closed by server" "Closed by client!") request))]
        (with-channel request channel
                      (go
                        (try
                          (let [user (<? (check-ticket-is-valid-and-user-has-required-roles ticket))]
                            (on-close channel on-close-handler)
                            (try
                              (operation request user channel)
                              (catch Exception e (do
                                                   (log/error "Uncaught exception in request handler!")
                                                   (log/error e)
                                                   (handle-exception channel start-time e)))
                              (finally
                                (audit-log "-"
                                           (user :personOid)
                                           (remote-addr-from-request request)
                                           (user-agent-from-request request)))))
                          (catch Exception e (do
                                               (log/error e "Exception in authorize check")
                                               (handle-unauthorized channel start-time request)))))))
      (access-log (bad-request "Ticket parameter required!")))))
