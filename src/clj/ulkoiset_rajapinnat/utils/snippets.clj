(ns ulkoiset-rajapinnat.utils.snippets)

(defn remove-nils
  [m]
  (let [f (fn [[k v]] (when v [k v]))]
    (clojure.walk/postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))

(defn remove-when
  [w-f m]
  (let [f (fn [[k v]] (when (not (w-f v)) [k v]))]
    (clojure.walk/postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))

(defn find-first-matching
  [k v coll]
  (first (filter #(= v (get % k)) coll)))

(defn merge-if-not-nil
  [m-k m-coll coll]
  (if (nil? m-coll) coll (merge coll {m-k m-coll})))

(defn get-value-if-not-nil
  [k coll]
  (if (nil? coll) nil (get coll k)))

(defn is-valid-year
  [year]
  (when-let [year-digits (re-matches #"^\d\d\d\d$" year)]
    (< 1000 (Integer/parseInt year-digits) 9000)))
