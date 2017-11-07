(ns ulkoiset-rajapinnat.fongo
  (:import (de.flapdoodle.embed.mongo MongodStarter)
           (de.flapdoodle.embed.mongo.config MongodConfigBuilder Net)
           (de.flapdoodle.embed.mongo.distribution Version)
           (de.flapdoodle.embed.process.runtime Network)))

(defn start-fake-mongo [port]
  (let [starter (MongodStarter/getDefaultInstance)
        mongod-config (-> (new MongodConfigBuilder)
                          (.version Version/V3_4_1)
                          (.net (new Net "localhost" port (Network/localhostIsIPv6)))
                          (.build))
        mongod-executable (.prepare starter mongod-config)
        mongod-process (.start mongod-executable)]
    (fn [] (.stop mongod-executable))))
