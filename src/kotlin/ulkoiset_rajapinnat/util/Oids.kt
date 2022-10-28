package ulkoiset_rajapinnat.util

object Oids {

    fun isKoutaHaku(oid: String) = oid.length > 30 && oid.startsWith("1.2.246.562.29")

}