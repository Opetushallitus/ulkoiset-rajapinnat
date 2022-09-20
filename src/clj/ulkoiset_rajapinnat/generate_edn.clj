(ns ulkoiset-rajapinnat.generate-edn
  (:require [clojure.pprint :refer :all]
            [tempfile.core :refer :all]))

(defn data-to-tmp-edn-file
  [data-structure]
  (.getAbsolutePath (tempfile (pr-str data-structure))))
