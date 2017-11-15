(ns ulkoiset-rajapinnat.onr
  (:require [manifold.deferred :refer [let-flow catch chain]]
            [clojure.string :as str]
            [clj-log4j2.core :as log]
            [ulkoiset-rajapinnat.rest :refer [get-as-promise status body body-and-close exception-response parse-json-body to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [fetch-koodisto strip-version-from-tarjonta-koodisto-uri]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(def oppijanumerorekisteri-api "%s/oppijanumerorekisteri-service/henkilo/henkilotByHenkiloOidList")

(defn fetch-henkilot-promise [host henkilo-oids]
  (get-as-promise (format oppijanumerorekisteri-api host)))
