package ulkoiset_rajapinnat.config

import clojure.lang.Keyword
import clojure.lang.PersistentArrayMap

class PersistentArrayMapWrapper(private val m: PersistentArrayMap) {
    fun read(vararg path: String): Any? {
        return try {
            var o: Map<*, *>? = m
            var v: Any? = null
            for (head in path) {
                val sm = o!![Keyword.find(head)]
                if (sm is Map<*, *>) {
                    o = sm
                } else {
                    o = null
                    v = sm
                }
            }
            v
        } catch (e: Exception) {
            throw RuntimeException(e.message + ". Path " + java.lang.String.join(".", *path))
        }
    }
}