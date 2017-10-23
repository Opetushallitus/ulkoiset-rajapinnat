(ns ulkoiset-rajapinnat.core-test
  (:require [clojure.test :refer :all]
            [clj-log4j2.core :as log]
            [ulkoiset-rajapinnat.config :refer :all]
            [ulkoiset-rajapinnat.freeport :refer :all]
            [ulkoiset-rajapinnat.generate-edn :refer :all]
            [ulkoiset-rajapinnat.core :refer :all]))

(def test-configs {:server {:port (get-free-port)}})
(defn start-test-server [] (start-server [(str (name ulkoiset-rajapinnat-property-key) "=" (data-to-tmp-edn-file test-configs))]))

(defn fixture [f]
  (let [server (start-test-server)]
    (f)
    (-> (meta server)
        :server
        (.stop 100))))

(use-fixtures :once fixture)

(deftest a-test
  (testing "FIXME, I fail."
    (is (= 0 0))))

;(run-tests)