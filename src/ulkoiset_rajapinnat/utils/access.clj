(ns ulkoiset-rajapinnat.utils.access
  (:require [clojure.tools.logging :as log]
            [full.async :refer :all]
            [clojure.core.async :refer [to-chan]]
            [ulkoiset-rajapinnat.utils.rest :refer [status body to-json]]
            [clojure.core.async :refer [promise-chan >! go put! close!]]
            [clojure.tools.logging.impl :as impl]
            [ulkoiset-rajapinnat.utils.headers :refer [user-agent-from-request remote-addr-from-request parse-request-headers]]
            [ring.util.http-response :refer [unauthorized bad-request internal-server-error]]
            [ulkoiset-rajapinnat.utils.kayttooikeus :refer [fetch-user-from-kayttooikeus-service]]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [ulkoiset-rajapinnat.utils.cas_validator :refer :all]
            [ulkoiset-rajapinnat.utils.config :refer [config]]
            [org.httpkit.server :refer :all]))

(def root-organization-oid "1.2.246.562.10.00000000001")
(def ulkoiset-rajapinnat-app-name "ULKOISETRAJAPINNAT")
(def read-access-name "READ")

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

(defn write-access-log
  ([start-time response-code request]
   (write-access-log start-time response-code request nil))

  ([start-time response-code request error-message]
   (.info logger (to-json (parse-request-headers request response-code start-time error-message)))))

(defn access-log
  ([response]
   (let [start-time (System/currentTimeMillis)]
     (access-log response start-time)))
  ([response start-time]
   (fn [request]
     (write-access-log start-time (str (response :status)) request)
     response)))

(defn find-root-organisation-permissions [kayttooikeus-user]
  (let [all-orgs (:organisaatiot kayttooikeus-user)
        root-org (first (filter #(= (get % "organisaatioOid") root-organization-oid) all-orgs))]
    (get root-org "kayttooikeudet")))

(defn- do-real-authentication-and-authorisation-check [ticket]
  (go-try
   (let [host-virkailija (resolve-url :cas-client.host)
         service (str host-virkailija "/ulkoiset-rajapinnat")
         username (<? (validate-service-ticket service ticket))
         kayttooikeus-user (<? (fetch-user-from-kayttooikeus-service username))
         root-org-permissions (find-root-organisation-permissions kayttooikeus-user)]
     (if (some #(and
                  (= ulkoiset-rajapinnat-app-name (% "palvelu"))
                  (= read-access-name (% "oikeus")))
               root-org-permissions)
       kayttooikeus-user
       (do
         (log/error "User" username "is missing required access" read-access-name
                    "for application" ulkoiset-rajapinnat-app-name ". Whole user data:" kayttooikeus-user)
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
    (write-access-log current-time 400 request (.getMessage ex))
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
            log-when-closed-by-client (fn [status] (when (not= status :server-close)
                                                     (write-access-log start-time (format "Closed by client (%s)" status) request)))
            log-when-closed-by-server (fn [response-code error-message]
                                        (write-access-log start-time response-code request error-message))]
        (with-channel request channel
                      (go
                        (try
                          (let [user (<? (check-ticket-is-valid-and-user-has-required-roles ticket))]
                            (on-close channel log-when-closed-by-client)
                            (try
                              (operation request user channel log-when-closed-by-server)
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
