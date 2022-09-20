package ulkoiset_rajapinnat.testutils

import clojure.java.api.Clojure
import clojure.lang.PersistentArrayMap
import fi.vm.sade.properties.OphProperties
import io.github.infeez.kotlinmockserver.dsl.http.context.MockServerContext
import io.github.infeez.kotlinmockserver.dsl.http.okhttp.okHttpMockServer
import io.github.infeez.kotlinmockserver.mockmodel.MockWebResponse
import io.github.infeez.kotlinmockserver.server.ServerConfiguration
import ulkoiset_rajapinnat.config.PersistentArrayMapWrapper

fun readEdn(path: String): PersistentArrayMap {
    val slurp = Clojure.`var`("clojure.core", "slurp")
    val readEdn = Clojure.`var`("clojure.edn", "read-string")
    return readEdn.invoke(slurp.invoke(path)) as PersistentArrayMap
}

val configPath = "test/resources/test.edn"
val TestConfig = readEdn(configPath)
val host = PersistentArrayMapWrapper(TestConfig).read("urls", "host-virkailija") as String

val TestOphProperties = OphProperties("resources/ulkoiset-rajapinnat-oph.properties")
    .addDefault("host-virkailija", host)
    .addDefault("host-virkailija-internal", host)

fun testMockServer(): MockServerContext {
    return okHttpMockServer(ServerConfiguration.custom {
        host = "localhost"
        port = 9999
    }, {
        defaultResponse = MockWebResponse(404, body = "Mock not found!")
    })
}