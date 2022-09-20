package ulkoiset_rajapinnat.util

import ulkoiset_rajapinnat.koodisto.dto.Koodisto

val <K> Map<K, String>.excludeBlankValues: Map<K, String>
    get() {
        return this.filterValues {
            it.isNotEmpty()
        }
    }
fun <K, V> Map<K, V>.getAll(l: Set<K>): List<V> {
    return l.map{ get(it) }.filterNotNull()
}
val String.stripVersion: String
    get() {
        return this.split("#").first()
    }

val String.stripType: String
    get() {
        return this.split("_").last()
    }
val String.stripNull: String?
    get() {
        if ("null" == this) {
            return null
        } else {
            return this
        }
    }

fun Map<String, Koodisto>.arvo(k: String?): String? {
    if (k == null) {
        return null
    } else {

        return get(k.stripVersion)?.koodiArvo
    }
}
