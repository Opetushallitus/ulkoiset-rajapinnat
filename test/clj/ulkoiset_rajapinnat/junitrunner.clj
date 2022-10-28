(ns ulkoiset-rajapinnat.junitrunner
  (:import (ulkoiset_rajapinnat HakukohdeForNewHakuTest
                                HakukohdeForOldHakuTest)
           (org.junit.runner JUnitCore)
           (org.junit.internal TextListener)))

(defonce junit (doto (JUnitCore.)
                 (.addListener (TextListener. System/out))))

(.run junit (into-array [HakukohdeForNewHakuTest
                         HakukohdeForOldHakuTest]))