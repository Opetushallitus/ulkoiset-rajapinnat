(ns ulkoiset-rajapinnat.utils.audit
  (:require [clojure.tools.logging.impl :as impl])
  (:import (fi.vm.sade.auditlog Audit User Logger Operation Target$Builder ApplicationType Changes$Builder)
           (java.net InetAddress)
           (org.ietf.jgss Oid)))

(def ^{:private true} slf4j-logger (impl/get-logger (impl/find-factory) "AUDIT"))

(defn create-audit-logger [] (Audit.
                               (reify Logger (log [this message]
                                               (.info slf4j-logger message)))
                               "ulkoiset-rajapinnat"
                               (ApplicationType/BACKEND)))

(defn create-user [sessionId uid inetAddress userAgent]
  (User. (Oid. uid) (InetAddress/getByName inetAddress) sessionId userAgent))

(defn audit [audit-logger operation sessionId uid inetAddress userAgent]
  (let [user (create-user sessionId uid inetAddress userAgent)
        op (reify Operation (name [this] operation))
        target-builder (Target$Builder.)
        changes-builder (Changes$Builder.)]
    (.log audit-logger user op (.build target-builder) (.build changes-builder))))
