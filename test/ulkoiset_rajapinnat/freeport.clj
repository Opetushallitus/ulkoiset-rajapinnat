(ns ulkoiset-rajapinnat.freeport)

(defn get-free-port []
  (let [socket (java.net.ServerSocket. 0)]
    (.close socket)
    (.getLocalPort socket)))
