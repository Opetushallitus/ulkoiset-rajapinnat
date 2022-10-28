package ulkoiset_rajapinnat

import com.google.common.base.Joiner
import com.google.common.collect.MapDifference
import com.google.common.collect.Maps
import io.github.infeez.kotlinmockserver.dsl.http.mock
import io.github.infeez.kotlinmockserver.dsl.http.okhttp.okHttpMockServer
import io.github.infeez.kotlinmockserver.junit4.extensions.asRule
import io.github.infeez.kotlinmockserver.mockmodel.MockWebResponse
import io.github.infeez.kotlinmockserver.server.ServerConfiguration
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.test.runTest
import org.apache.commons.io.IOUtils
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import ulkoiset_rajapinnat.response.HakukohdeResponse
import ulkoiset_rajapinnat.testutils.*
import ulkoiset_rajapinnat.testutils.ApiJsonUtil.asMap
import ulkoiset_rajapinnat.testutils.CasMocks.cas
import ulkoiset_rajapinnat.testutils.KoodistoMocks.koodistot
import java.nio.charset.Charset

class HakukohdeForOldHakuTest {
    private val mockServer = testMockServer()

    init {
        with(mockServer) {
            addAll(koodistot)
            addAll(cas)
            addAll(
                mock("/tarjonta-service/rest/v1/haku/1.2.246.562.29.11787797049") {
                    body(ResourcesUtil.resource("haku/haku_1.2.246.562.29.11787797049.json"))
                },
                mock("/tarjonta-service/rest/v1/haku/1.2.246.562.29.11787797049/hakukohdeTulos?hakukohdeTilas=JULKAISTU&count=-1") {
                    body(ResourcesUtil.resource("haku/hakukohdeTulos.json"))
                },
                mock("/tarjonta-service/rest/v1/hakukohde/search?hakuOid=1.2.246.562.29.11787797049&tila=JULKAISTU") {
                    body(ResourcesUtil.resource("haku/search.json"))
                },
                mock("/tarjonta-service/rest/v1/koulutus/search?hakuOid=1.2.246.562.29.11787797049") {
                    body(ResourcesUtil.resource("haku/koulutus.json"))
                },
                mock("/tarjonta-service/rest/hakukohde/tilastokeskus") {
                    body(ResourcesUtil.resource("haku/tilastokeskus.json"))
                },
                mock("/organisaatio-service/rest/organisaatio/v4/findbyoids") {
                    body(ResourcesUtil.resource("organisaatio/organisaatiot.json"))
                }
            )
        }
    }

    @get:Rule
    val mockServerRule = mockServer.asRule()
    val ulkoisetRajapinnatApi = UlkoisetRajapinnatApi(TestOphProperties, TestConfig)

    val expectedResultForOldHaku: Map<String, Map<String, Any>> =
        asMap(
            IOUtils.toString(
                ResourcesUtil.resource("results/hakukohde-for-haku_1.2.246.562.29.11787797049.json"),
                Charset.forName("UTF-8")
            )
        )
            .map { it.get("hakukohteen_oid").toString() to it }.toMap()

    @Test
    fun fetchHakukohteetForOldHaku() = runTest {
        val oldHakuOid = "1.2.246.562.29.11787797049"

        val data: List<HakukohdeResponse> = ulkoisetRajapinnatApi.findHakukohteetForHaku(oldHakuOid)
            .asDeferred().await()

        val resultJsonAsMap = asMap(ApiJsonUtil.asApiJson(data))

        resultJsonAsMap.forEach { haku ->
            val oid = haku.get("hakukohteen_oid").toString()
            val difference: MapDifference<String, Any> = Maps.difference(haku, expectedResultForOldHaku.get(oid))
            if (difference.entriesOnlyOnLeft().isNotEmpty()) {
                val unexpected = Joiner.on(",").withKeyValueSeparator("=").join(difference.entriesOnlyOnLeft())
                Assert.fail("Haku ${oid} contained unexpected results: ${unexpected}")
            }
            if (difference.entriesOnlyOnRight().filterValues { it != null }.isNotEmpty()) {
                val unexpected = Joiner.on(",").withKeyValueSeparator("=").join(difference.entriesOnlyOnRight())
                Assert.fail("Haku ${oid} contained unexpected results: ${unexpected}")
            }
        }
    }
}