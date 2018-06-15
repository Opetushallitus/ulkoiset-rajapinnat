(ns ulkoiset-rajapinnat.hakemus-test
  (:require [clojure.test :refer :all]
            [full.async :refer :all]
            [clojure.core.async :refer [<! promise-chan >! go put! close!]]
            [ulkoiset-rajapinnat.utils.access :refer [check-ticket-is-valid-and-user-has-required-roles]]
            [ulkoiset-rajapinnat.oppija :refer [fetch-oppijat-for-hakemus-with-ensikertalaisuus-channel]]
            [clj-log4j2.core :as log]
            [org.httpkit.client :as http]
            [ulkoiset-rajapinnat.utils.rest :refer [parse-json-body to-json to-json]]
            [clj-http.client :as client]
            [cheshire.core :refer [parse-string]]
            [ulkoiset-rajapinnat.onr :refer :all]
            [ulkoiset-rajapinnat.utils.haku_app :refer :all]
            [ulkoiset-rajapinnat.utils.ataru :refer :all]
            [ulkoiset-rajapinnat.utils.koodisto :refer :all]
            [ulkoiset-rajapinnat.utils.tarjonta :refer :all]
            [ulkoiset-rajapinnat.utils.cas :refer :all]
            [ulkoiset-rajapinnat.fixture :refer :all]
            [clojure.data :refer [diff]]
            [ulkoiset-rajapinnat.test_utils :refer :all]))

 (use-fixtures :once fixture)


(defn mock-mapped [response]
  (fn [& varargs]
    (go [[] response (fn [& abc] ((last varargs) response))])))

(defn mock-channel-fn [response]
  (fn [& varargs]
    (go response)))

(deftest hakemus-failure-test
  (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                haku-for-haku-oid-channel (mock-channel-fn {})
                hakukohde-oidit-koulutuksen-alkamiskauden-ja-vuoden-mukaan (mock-channel-fn [])
                fetch-jsessionid-channel (mock-channel-fn "FAKE-SESSIONID")
                koodisto-as-channel (mock-channel-fn {})]
    (testing "Fetch hakemukset for haku with no hakukohde-oids"
      (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                    fetch-hakemukset-from-ataru (mock-mapped [])
                    fetch-hakemukset-from-haku-app-as-streaming-channel (mock-mapped [])
                    fetch-henkilot-channel (mock-channel-fn [])]
        (let [response (client/get (api-call "/api/hakemus-for-haku/foobar?vuosi=2018&kausi=kausi_s%231")
                                   {:as :string})
              bodyjson (-> response :body)
              status (-> response :status)]
              (is (= bodyjson "[{\"error\":\"No hakukohde-oids found for haku foobar with vuosi 2018 and kausi kausi_s#1!\"}"))
              (is (= status 200)))))))

(deftest hakemus-test
  (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                haku-for-haku-oid-channel (mock-channel-fn {})
                hakukohde-oidit-koulutuksen-alkamiskauden-ja-vuoden-mukaan (mock-channel-fn ["1.2.3.4"])
                fetch-jsessionid-channel (mock-channel-fn "FAKE-SESSIONID")
                koodisto-as-channel (mock-channel-fn {})]
    (testing "Fetch hakemukset for haku with no hakemuksia!"
      (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                    fetch-hakemukset-from-ataru (mock-mapped [])
                    fetch-hakemukset-from-haku-app-as-streaming-channel (mock-mapped [])
                    fetch-henkilot-channel (mock-channel-fn [])]
        (let [response (client/get (api-call "/api/hakemus-for-haku/1.2.246.562.29.94986312133?vuosi=2017&kausi=kausi_s%231"))
              status (-> response :status)]
          (is (= status 200)))))

    (testing "Fetch hakemukset for haku with 'ataru' hakemuksia!"
      (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                    fetch-hakemukset-from-ataru (mock-mapped [{"oid" "1.2.3.4"}])
                    fetch-hakemukset-from-haku-app-as-streaming-channel (mock-mapped [])
                    fetch-henkilot-channel (mock-channel-fn [])]
        (let [response (client/get (api-call "/api/hakemus-for-haku/1.2.246.562.29.94986312133?vuosi=2017&kausi=kausi_s%231")
                                   {:as :json})
              status (-> response :status)
              body (response :body)]
          (is (= status 200))
          (is (= (count body) 1))
          )))

    (testing "Fetch hakemukset for haku with 'haku-app' hakemuksia!"
      (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                    fetch-hakemukset-from-ataru (mock-mapped [])
                    fetch-hakemukset-from-haku-app-as-streaming-channel (mock-mapped [{"oid" "1.2.3.4"}])
                    fetch-henkilot-channel (mock-channel-fn [])]
        (let [response (client/get (api-call "/api/hakemus-for-haku/1.2.246.562.29.94986312133?vuosi=2017&kausi=kausi_s%231")
                                   {:as :json})
              status (-> response :status)
              body (response :body)]
          (is (= status 200))
          (is (= (count body) 1))
          )))))

(def haku-json (resource "test/resources/hakemus/haku.json"))
(def tilastokeskus-json (resource "test/resources/hakemus/tilastokeskus.json"))
(def hakemus-json (resource "test/resources/hakemus/hakemus.json"))
(def henkilot-json (resource "test/resources/hakemus/henkilot.json"))
(def oppijat-json (resource "test/resources/hakemus/oppijat.json"))
(def organisaatio-json (resource "test/resources/hakemus/organisaatio.json"))

(defn mock-http [url options transform]
  (log/info (str "Mocking url " url))
  (def response (partial channel-response transform url))
  (case url
    "http://fake.virkailija.opintopolku.fi/koodisto-service/rest/codeelement/codes/pohjakoulutuskkodw/1" (response 200 "[]")
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/haku/1.2.246.562.29.999999" (response 200 haku-json)
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/hakukohde/tilastokeskus" (response 200 tilastokeskus-json)
    "http://fake.virkailija.opintopolku.fi/oppijanumerorekisteri-service/henkilo/henkilotByHenkiloOidList" (response 200 henkilot-json)
    "http://fake.virkailija.opintopolku.fi/organisaatio-service/rest/organisaatio/v3/findbyoids" (response 200 organisaatio-json)
    (response 404 "[]")))

(deftest lahtokoulu-test
  (testing "Fetch lähtökoulu from Sure"
    (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                  fetch-hakemukset-from-ataru (mock-mapped [])
                  fetch-hakemukset-from-haku-app-as-streaming-channel (fn [x y z mapper] (go (mapper (parse-string hakemus-json))))
                  fetch-oppijat-for-hakemus-with-ensikertalaisuus-channel (fn [x y z h] (mock-channel (parse-string oppijat-json)))
                  fetch-service-ticket-channel (mock-channel-fn "FAKEST")
                  http/get (fn [url options transform] (mock-http url options transform))
                  http/post (fn [url options transform] (mock-http url options transform))
                  fetch-jsessionid-channel (mock-channel-fn "FAKEJSESSIONID")]
      (let [response (client/get (api-call "/api/hakemus-for-haku/1.2.246.562.29.999999?vuosi=2017&kausi=kausi_s"))
            status (-> response :status)
            body (-> (parse-json-body response))]
        (is (= status 200))
        (log/info (to-json body true))
        (def expected (parse-string (resource "test/resources/hakemus/result.json")))
        (def difference (diff expected body))
        (is (= [nil nil expected] difference) difference)
        ))))