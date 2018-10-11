(ns ulkoiset-rajapinnat.fixture
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [clojure.core.async.impl.protocols :as protocols]
            [clojure.core.async.impl.concurrent :as conc]
            [clojure.core.async.impl.exec.threadpool :as tp]
            [ulkoiset-rajapinnat.freeport :refer :all]
            [ulkoiset-rajapinnat.generate-edn :refer :all]
            [ulkoiset-rajapinnat.utils.config :refer :all]
            [ulkoiset-rajapinnat.freeport :refer :all]
            [ulkoiset-rajapinnat.core :refer :all])
  (:import java.util.concurrent.Executors))

(def fake-user {:personOid "1.2.246.562.24.1234567890"})

(defn resource [name]
  (let [n (if (.exists (java.io.File. name)) name (str "../../" name))]
    (slurp n)))

(def test-configs {:urls {:host-virkailija "http://fake.virkailija.opintopolku.fi"
                          :host-virkailija-internal "http://fake.internal.virkailija.opintopolku.fi"}
                   :server {:port (get-free-port)
                            :base-url "/ulkoiset-rajapinnat"}})
(defn api-call [path] (str "http://localhost:" (-> test-configs :server :port) (-> test-configs :server :base-url) path
                           (if (str/includes? path "?") "&" "?") "ticket=faketicket"))

(defn test-edn []
  (let [tmp-test-edn-file (data-to-tmp-edn-file test-configs)]
    (str (name ulkoiset-rajapinnat-property-key) "=" tmp-test-edn-file)))

(defonce my-executor
         (let [executor-svc (Executors/newFixedThreadPool
                              1
                              (conc/counted-thread-factory "async-dispatch-%d" false))]
           (reify protocols/Executor
             (protocols/exec [this r]
               (.execute executor-svc ^Runnable r)))))


(defn fixture [f]
  (let [server-close-handle (start-server [(test-edn)])]
    (alter-var-root #'clojure.core.async.impl.dispatch/executor
                    (constantly (delay my-executor)))
    (f)
    (server-close-handle)))