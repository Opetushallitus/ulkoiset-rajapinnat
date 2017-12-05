(ns ulkoiset-rajapinnat.fixture
  (:require [clojure.test :refer :all]
            [ulkoiset-rajapinnat.freeport :refer :all]
            [ulkoiset-rajapinnat.generate-edn :refer :all]
            [ulkoiset-rajapinnat.fongo :refer :all]
            [ulkoiset-rajapinnat.utils.config :refer :all]
            [ulkoiset-rajapinnat.freeport :refer :all]
            [ulkoiset-rajapinnat.core :refer :all]
            ))
(def fake-mongo-port (get-free-port))

(def test-configs {:host-virkailija "http://fake.virkailija.opintopolku.fi"
                   :vastaanotto-host-virkailija "http://fake.virkailija.opintopolku.fi"
                   :hakuapp-mongo {:uri (str "mongodb://localhost:" fake-mongo-port "/hakulomake")}
                   :server {:port (get-free-port)
                            :base-url "/ulkoiset-rajapinnat"}})
(defn api-call [path] (str "http://localhost:" (-> test-configs :server :port) (-> test-configs :server :base-url) path))

(defn test-edn []
  (let [tmp-test-edn-file (data-to-tmp-edn-file test-configs)]
    (str (name ulkoiset-rajapinnat-property-key) "=" tmp-test-edn-file)))

(defn fixture [f]
  (let [close-fake-mongo-handle (start-fake-mongo fake-mongo-port)
        server-close-handle (start-server [(test-edn)])]
    (f)
    (server-close-handle)
    (close-fake-mongo-handle)))