(ns ulkoiset-rajapinnat.core-test
  (:require [clojure.test :refer :all]
            [clj-log4j2.core :as log]
            [clj-http.client :as client]
            [ulkoiset-rajapinnat.utils.rest :refer :all]
            [ulkoiset-rajapinnat.fixture :refer :all]))

(use-fixtures :once fixture)

(deftest a-test
  (testing "Health check API"
    (let [response (client/get (api-call "/api/healthcheck"))
          status (-> response :status)]
      (is (= status 200)))))

(deftest buildversion-text
  (testing "Health check API"
    (let [response (client/get (api-call "/buildversion.txt"))
          status (-> response :status)
          body (-> response :body)]
      (is (= status 200))
      ;(is (re-find #"ref" body))
      (is (re-find #"branch" body)))))

(deftest unexpected-response-test
  (testing "Not 200 status code"
    (try
      (parse-json-body {:status 404 :opts {:url "http://my.fake.url"}})
      (is (= true false))
      (catch Exception e
        (is (= (.getMessage e) "Unexpected response status 404 from url http://my.fake.url")))))
  (testing "Normal Exception"
    (try
      (parse-json-body {:error (NullPointerException. "Got null") :opts {:url "http://my.fake.url"}})
      (is (= true false))
      (catch Exception e
        (is (= (.getMessage e) "Unexpected error from url http://my.fake.url -> Got null")))))
  (testing "Exception info"
    (try
      (parse-json-body {:error (ex-info "Really bad exception" { :message "Really bad exception"}) :opts {:url "http://my.fake.url"}})
      (is (= true false))
      (catch Exception e
        (is (= (.getMessage e) "Unexpected error from url http://my.fake.url -> Really bad exception")))))
  (testing "Unexpected error situation"
    (try
      (parse-json-body {:opts {:url "http://my.fake.url"}})
      (is (= true false))
      (catch Exception e
        (is (= (.getMessage e) "Unexpected response form url http://my.fake.url"))))))
