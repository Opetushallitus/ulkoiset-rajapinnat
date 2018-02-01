(ns ulkoiset-rajapinnat.core-test
  (:require [clojure.test :refer :all]
            [clj-log4j2.core :as log]
            [clj-http.client :as client]
            [ulkoiset-rajapinnat.fixture :refer :all]))

(use-fixtures :once fixture)

(deftest a-test
  (testing "Health check API"
    (let [response (client/get (api-call "/api/healthcheck"))
          status (-> response :status)]
      (is (= status 200)))))

