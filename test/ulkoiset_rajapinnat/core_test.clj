(ns ulkoiset-rajapinnat.core-test
  (:require [clojure.test :refer :all]
            [clj-log4j2.core :as log]
            [clj-http.client :as client]
            [ulkoiset-rajapinnat.fongo :refer :all]
            [ulkoiset-rajapinnat.config :refer :all]
            [ulkoiset-rajapinnat.freeport :refer :all]
            [ulkoiset-rajapinnat.generate-edn :refer :all]
            [ulkoiset-rajapinnat.core :refer :all]))

(def fake-mongo-port (get-free-port))

(def test-configs {:hakuapp-mongo {:uri (str "mongodb://localhost:" fake-mongo-port "/hakulomake")}
                   :server {:port (get-free-port)
                            :base-url "/ulkoiset-rajapinnat"}})
(defn api-call [path] (str "http://localhost:" (-> test-configs :server :port) (-> test-configs :server :base-url) path))
(defn start-test-server [] (start-server [(str (name ulkoiset-rajapinnat-property-key) "=" (data-to-tmp-edn-file test-configs))]))

(defn fixture [f]
  (let [close-fake-mongo-handle (start-fake-mongo fake-mongo-port)
        server (start-test-server)]
    (f)
    (close-fake-mongo-handle)
    (-> (meta server)
        :server
        (.stop 100))))

(use-fixtures :once fixture)

(deftest a-test
  (testing "Health check API"
    (let [response (client/get (api-call "/api/healthcheck"))
          status (-> response :status)]
      (is (= status 200)))))

;(run-tests)