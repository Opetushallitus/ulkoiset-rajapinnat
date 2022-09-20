package ulkoiset_rajapinnat

import io.github.infeez.kotlinmockserver.dsl.http.mock
import io.github.infeez.kotlinmockserver.junit4.extensions.*
import org.junit.Rule
import org.junit.Test
import ulkoiset_rajapinnat.testutils.TestConfig
import ulkoiset_rajapinnat.testutils.TestOphProperties
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import kotlinx.coroutines.future.asDeferred
import ulkoiset_rajapinnat.testutils.CasMocks.cas
import ulkoiset_rajapinnat.testutils.KoodistoMocks.koodistot
import ulkoiset_rajapinnat.testutils.ResourcesUtil.resource
import ulkoiset_rajapinnat.testutils.testMockServer

class HakukohdeForNewHakuTest {
    private val mockServer = testMockServer()
    val newHakuOid = "1.2.246.562.29.00000000000000010805"

    init {
        mockServer.addAll(koodistot)
        mockServer.addAll(cas)
        mockServer.addAll(
            mock("/kouta-internal/auth/login") {
                body("OK")
                headers(Pair("Set-Cookie", "session=session"))
            },
            mock("/kouta-internal/haku/$newHakuOid") {
                body(resource("kouta/haku_$newHakuOid.json"))
            },
            mock("/kouta-internal/hakukohde/search?haku=$newHakuOid&tarjoaja=1.2.246.562.10.00000000001%2C1.2.246.562.10.00000000002&all=true") {
                body(resource("kouta/hakukohde_search_$newHakuOid.json"))
            },
            mock("/kouta-internal/koulutus/search?hakuOid=$newHakuOid") {
                body(resource("kouta/koulutus_search_$newHakuOid.json"))
            },
            mock("/kouta-internal/toteutus/search?hakuOid=$newHakuOid") {
                body(resource("kouta/toteutus_search_$newHakuOid.json"))
            },
            mock("/organisaatio-service/rest/organisaatio/v4/findbyoids") {
                body(resource("organisaatio/organisaatio-service_rest_organisaatio_v4_findbyoids.json"))
            }
        )
    }

    @get:Rule
    val mockServerRule = mockServer.asRule()
    val ulkoisetRajapinnatApi = UlkoisetRajapinnatApi(TestOphProperties, TestConfig)

    @Test
    fun fetchHakukohteetForNewHaku() = runTest {
        val data = ulkoisetRajapinnatApi.findHakukohteetForHaku(newHakuOid)
            .asDeferred().await()

        assertEquals(2, data.size)
    }
}