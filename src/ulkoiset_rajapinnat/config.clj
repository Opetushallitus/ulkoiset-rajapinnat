(ns ulkoiset-rajapinnat.config
  (:require [clojure.edn :as edn]
            [clj-log4j2.core :as log]
            [clojure.string :as str]
            [environ.core :refer [env]]))

(def ulkoiset-rajapinnat-property-key :ulkoiset-rajapinnat-properties)

(defn ^:private read-config-path-from-args [args]
  (first (filter some?
                 (map (fn [arg]
                        (if (str/starts-with? arg (name ulkoiset-rajapinnat-property-key))
                          (subs arg (+ 1 (count (name ulkoiset-rajapinnat-property-key)))) nil)) args))))

(defn ^:private read-config-path-from-env []
  (env ulkoiset-rajapinnat-property-key))

(defn ^:private read-args-or-env
  [args]
  (if-let [from-args (read-config-path-from-args args)]
    (do
      (log/info "Using config property {} from main args!" from-args)
      from-args)
    (if-let [from-env (read-config-path-from-env)]
      (do
        (log/info "Using config property {} from env.vars!" from-env)
        from-env)
      (throw (Exception. (str (name ulkoiset-rajapinnat-property-key) " is mandatory! Either give it in args or set env.var!"))))))

(defn read-configuration-file-first-from-varargs-then-from-env-vars
  "Reads configuration file. File path&name is searched from varargs and env.variables. Key is 'ulkoiset-rajapinnat-properties'."
  [& args]
  (edn/read-string (slurp (read-args-or-env args))))
