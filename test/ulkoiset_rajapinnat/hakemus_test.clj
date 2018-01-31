(ns ulkoiset-rajapinnat.hakemus-test
  (:require [clojure.test :refer :all]
            [full.async :refer :all]
            [clojure.core.async :refer [<! promise-chan >! go put! close!]]
            [clj-log4j2.core :as log]
            [clj-http.client :as client]
            [ulkoiset-rajapinnat.onr :refer :all]
            [ulkoiset-rajapinnat.utils.haku_app :refer :all]
            [ulkoiset-rajapinnat.utils.ataru :refer :all]
            [ulkoiset-rajapinnat.utils.koodisto :refer :all]
            [ulkoiset-rajapinnat.utils.tarjonta :refer :all]
            [ulkoiset-rajapinnat.utils.cas :refer :all]
            [ulkoiset-rajapinnat.fixture :refer :all]))

(use-fixtures :once fixture)

(defn mock-channel [response]
  (fn [& varargs]
    (go response)))

(defn mock-mapped [response]
  (fn [& varargs]
    (go [[] response (fn [& abc] response)])))

(deftest hakemus-test
  (with-redefs [haku-for-haku-oid-channel (mock-channel {})
                fetch-service-ticket-channel (mock-channel "FAKE-SERVICE-TICKET")
                fetch-jsessionid-channel (mock-channel "FAKE-SESSIONID")
                koodisto-as-channel (mock-channel {})]
    (testing "Fetch hakemukset for haku with no hakemuksia!"
      (with-redefs [fetch-hakemukset-from-ataru (mock-mapped [])
                    fetch-hakemukset-from-haku-app-as-streaming-channel (mock-mapped [])
                    fetch-henkilot-channel (mock-channel [])]
        (let [response (client/get (api-call "/api/hakemus-for-haku/1.2.246.562.29.94986312133?vuosi=2017&kausi=kausi_s%231"))
              status (-> response :status)]
          (is (= status 200)))))

    (testing "Fetch hakemukset for haku with 'ataru' hakemuksia!"
      (with-redefs [fetch-hakemukset-from-ataru (mock-mapped [{"oid" "1.2.3.4"}])
                    fetch-hakemukset-from-haku-app-as-streaming-channel (mock-mapped [])
                    fetch-henkilot-channel (mock-channel [])]
        (let [response (client/get (api-call "/api/hakemus-for-haku/1.2.246.562.29.94986312133?vuosi=2017&kausi=kausi_s%231")
                                   {:as :json})
              status (-> response :status)
              body (response :body)]
          (is (= status 200))
          (is (= (count body) 1))
          )))

    (testing "Fetch hakemukset for haku with 'haku-app' hakemuksia!"
      (with-redefs [fetch-hakemukset-from-ataru (mock-mapped [])
                    fetch-hakemukset-from-haku-app-as-streaming-channel (mock-mapped [{"oid" "1.2.3.4"}])
                    fetch-henkilot-channel (mock-channel [])]
        (let [response (client/get (api-call "/api/hakemus-for-haku/1.2.246.562.29.94986312133?vuosi=2017&kausi=kausi_s%231")
                                   {:as :json})
              status (-> response :status)
              body (response :body)]
          (is (= status 200))
          (is (= (count body) 1))
          )))))

(run-tests)