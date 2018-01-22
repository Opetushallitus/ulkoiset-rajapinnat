(ns ulkoiset-rajapinnat.utils.tarjonta
  (:require [clojure.string :as str]
            [full.async :refer :all]
            [clojure.core.async :refer [<! promise-chan >! go put! close!]]
            [clojure.tools.logging :as log]
            [ulkoiset-rajapinnat.utils.config :refer :all]
            [ulkoiset-rajapinnat.utils.rest :refer :all]))

(defn- haku-alkamiskaudella-api
  [host vuosi kausi]
  (format "%s/tarjonta-service/rest/v1/haku/find?HAKUVUOSI=%s&HAKUKAUSI=%s&TILA=NOT_POISTETTU&TARJOAJAOID=1.2.246.562.10.00000000001&addHakukohdes=true" host vuosi kausi))

(defn- filter-only-haku [haku-oid]
  (fn [response]
    (let [data (parse-json-body response)]
      (mapcat #(% "hakukohdeOids") (filter #(= haku-oid (% "oid")) (data "result"))))))

(defn hakukohde-oids-for-kausi-and-vuosi-channel [config haku-oid kausi vuosi]
  (let [url (haku-alkamiskaudella-api (config :host-virkailija) vuosi kausi)]
    (get-as-channel url {} (filter-only-haku haku-oid))))
