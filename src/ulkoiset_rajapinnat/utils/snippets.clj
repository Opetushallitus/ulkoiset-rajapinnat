(ns ulkoiset-rajapinnat.utils.snippets
  (:require [clojure.string :as str]))

(defn remove-nils
  [m]
  (let [f (fn [[k v]] (when v [k v]))]
    (clojure.walk/postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))

(defn remove-when
  [w-f m]
  (let [f (fn [[k v]] (when (not (w-f v)) [k v]))]
    (clojure.walk/postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))