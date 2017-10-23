(ns ulkoiset-rajapinnat.core-test
  (:require [clojure.test :refer :all]
            [clj-log4j2.core :as log]
            [ulkoiset-rajapinnat.config :refer :all]
            [ulkoiset-rajapinnat.core :refer :all]))

(defn fixture [f]
  (let [server-handle (start-server [(str (name ulkoiset-rajapinnat-property-key) "=" "test.edn")])]
    (f)
    (-> (meta server-handle)
        :server
        (.stop 100))))

(use-fixtures :once fixture)

(deftest a-test
  (testing "FIXME, I fail."
    (is (= 0 0))))
