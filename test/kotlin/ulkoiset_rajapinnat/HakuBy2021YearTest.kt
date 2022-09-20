package ulkoiset_rajapinnat

import com.google.common.base.Joiner
import com.google.common.collect.MapDifference
import com.google.common.collect.Maps
import com.google.gson.Gson
import io.github.infeez.kotlinmockserver.dsl.http.mock
import io.github.infeez.kotlinmockserver.junit4.extensions.asRule
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.test.runTest

import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import ulkoiset_rajapinnat.testutils.CasMocks.cas
import ulkoiset_rajapinnat.testutils.KoodistoMocks.koodistot
import ulkoiset_rajapinnat.testutils.ResourcesUtil.resource
import ulkoiset_rajapinnat.testutils.TestConfig
import ulkoiset_rajapinnat.testutils.TestOphProperties
import ulkoiset_rajapinnat.testutils.testMockServer
import java.nio.charset.Charset
import org.apache.commons.io.IOUtils
import ulkoiset_rajapinnat.kouta.dto.HakuInternal
import ulkoiset_rajapinnat.kouta.dto.HakuResult
import ulkoiset_rajapinnat.testutils.ApiJsonUtil.asApiJson
import ulkoiset_rajapinnat.testutils.ApiJsonUtil.asMap

class HakuBy2021YearTest {
    private val mockServer = testMockServer()

    init {
        mockServer.addAll(koodistot)
        mockServer.addAll(cas)
        mockServer.addAll(
            mock("/kouta-internal/auth/login") {
                body("OK")
                headers(Pair("Set-Cookie", "session=session"))
            },
            mock("/kouta-internal/haku/search?tarjoaja=1.2.246.562.10.00000000001%2C1.2.246.562.10.00000000002&includeHakukohdeOids=true&vuosi=2021") {
                body(Gson().toJson(listOf<HakuInternal>()))
            },
            mock("/tarjonta-service/rest/v1/haku/findByAlkamisvuosi/2021") {
                body(resource("haku/findByAlkamisvuosi_2021.json"))
            },
        )
    }

    @get:Rule
    val mockServerRule = mockServer.asRule()
    val ulkoisetRajapinnatApi = UlkoisetRajapinnatApi(TestOphProperties, TestConfig)

    val expectedResultForYear2021: Map<String, Map<String, Any>> =
        asMap(IOUtils.toString(resource("results/haku_for_year_2021.json"), Charset.forName("UTF-8")))
            .map { it.get("haku_oid").toString() to it }.toMap()

    @Test
    fun findHakuByYear2021() = runTest {
        val resultJsonAsMap = asMap(asApiJson(ulkoisetRajapinnatApi.findHakuByYear(2021)
            .asDeferred().await()))

        Assert.assertEquals("Expected same size results", expectedResultForYear2021.size, resultJsonAsMap.size)

        resultJsonAsMap.forEach {
            haku ->
            val oid = haku.get("haku_oid").toString()
            val difference: MapDifference<String, Any> = Maps.difference(haku, expectedResultForYear2021.get(oid))
            if(difference.entriesOnlyOnLeft().isNotEmpty()) {
                val unexpected = Joiner.on(",").withKeyValueSeparator("=").join(difference.entriesOnlyOnLeft())
                Assert.fail("Haku ${oid} contained unexpected results: ${unexpected}")
            }
            if(difference.entriesOnlyOnRight().filterValues { it != null }.isNotEmpty()) {
                val unexpected = Joiner.on(",").withKeyValueSeparator("=").join(difference.entriesOnlyOnRight())
                Assert.fail("Haku ${oid} contained unexpected results: ${unexpected}")
            }
        }

    }
}