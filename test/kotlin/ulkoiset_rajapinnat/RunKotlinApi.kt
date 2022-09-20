package ulkoiset_rajapinnat

import clojure.java.api.Clojure
import clojure.lang.PersistentArrayMap
import fi.vm.sade.properties.OphProperties
import ulkoiset_rajapinnat.config.PersistentArrayMapWrapper
import ulkoiset_rajapinnat.kouta.KoutaInternalClient
import ulkoiset_rajapinnat.ohjausparametrit.OhjausparametritClient
import ulkoiset_rajapinnat.organisaatio.OrganisaatioClient

fun readEdn(path: String): PersistentArrayMap {
    val slurp = Clojure.`var`("clojure.core", "slurp")
    val readEdn = Clojure.`var`("clojure.edn", "read-string")
    return readEdn.invoke(slurp.invoke(path)) as PersistentArrayMap
}

/**
 * config=../my.edn
 */
fun main(args: Array<String>) {
    val configPath = System.getenv("config")
    val config = readEdn(configPath)
    val host = PersistentArrayMapWrapper(config).read("urls", "host-virkailija") as String
    val ophProperties = OphProperties("resources/ulkoiset-rajapinnat-oph.properties")
        .addDefault("host-virkailija", host)
        .addDefault("host-virkailija-internal", host)
    val username = PersistentArrayMapWrapper(config).read("ulkoiset-rajapinnat-cas-username") as String
    val password = PersistentArrayMapWrapper(config).read("ulkoiset-rajapinnat-cas-password") as String
    val kiClient = KoutaInternalClient(username, password, ophProperties)
    val oClient = OhjausparametritClient(username, password, ophProperties)
    val orgClient = OrganisaatioClient(ophProperties)
    val vvv = setOf("1.2.246.562.20.00000000000000011016","1.2.246.562.20.00000000000000010806")
    orgClient.fetchOrganisaatiotAndParentOrganisaatiot(vvv.toSet()).thenApply {
        println(it)
    }.join()
    kiClient.findByHakuOid( "1.2.246.562.29.00000000000000002175").thenApply {
        println(it)
    }.join()

}