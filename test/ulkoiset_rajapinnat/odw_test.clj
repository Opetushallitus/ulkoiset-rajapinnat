(ns ulkoiset-rajapinnat.odw-test
  (:require [manifold.deferred :as d]
            [clojure.test :refer :all]
            [clj-log4j2.core :as log]
            [clj-http.client :as client]
            [ulkoiset-rajapinnat.utils.rest :refer [get-as-promise]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid]]
            [ulkoiset-rajapinnat.fixture :refer :all]))

(use-fixtures :once fixture)

(deftest odw-api-test
  (testing "Odw -> hakukohde not found"
    (with-redefs [get-as-promise (fn [a b] (d/future {:status 404 :body "[]"}))
                  fetch-jsessionid (fn [a b c d] (str "FAKEJSESSIONID"))]
      (let [response (client/post (api-call "/api/odw/hakukohde") {:body "[\"1.2.3.444\"]" :content-type :json})
            status (-> response :status)
            body (-> response :body)]
        (is (= status 200))
        (is (= body "[]"))))))

(run-tests)