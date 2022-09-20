(ns ulkoiset-rajapinnat.utils.url-helper
  (:require [ulkoiset-rajapinnat.utils.config :refer [config]])
  (:import (fi.vm.sade.properties OphProperties)))

(def ^fi.vm.sade.properties.OphProperties url-properties (atom nil))

(defn- load-config
  []
  (let [{:keys [host-virkailija host-virkailija-internal] :or
               {host-virkailija "" host-virkailija-internal ""}} (:urls @config)]
    (reset! url-properties
            (doto (OphProperties. (into-array String ["/ulkoiset-rajapinnat-oph.properties"]))
              (.addDefault "host-virkailija" host-virkailija)
              (.addDefault "host-virkailija-internal" host-virkailija-internal)))))

(defn resolve-url
  [key & params]
  (when (nil? @url-properties)
    (load-config))
  (.url @url-properties (name key) (to-array (or params []))))