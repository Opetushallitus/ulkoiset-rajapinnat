package ulkoiset_rajapinnat

import com.google.gson.Gson
import io.github.infeez.kotlinmockserver.dsl.http.mock
import io.github.infeez.kotlinmockserver.junit4.extensions.asRule
import kotlinx.coroutines.future.*
import kotlinx.coroutines.test.runTest
import org.apache.commons.io.IOUtils
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import ulkoiset_rajapinnat.kouta.dto.HakuInternal
import ulkoiset_rajapinnat.testutils.*
import ulkoiset_rajapinnat.testutils.ApiJsonUtil.asApiJson
import ulkoiset_rajapinnat.testutils.ApiJsonUtil.asMap
import ulkoiset_rajapinnat.testutils.CasMocks.cas
import ulkoiset_rajapinnat.testutils.KoodistoMocks.koodistot

class HakuBy2022YearTest {
    private val mockServer = testMockServer()

    init {
        mockServer.addAll(koodistot)
        mockServer.addAll(cas)
        mockServer.addAll(
            mock("/kouta-internal/auth/login") {
                body("OK")
                headers(Pair("Set-Cookie", "session=session"))
            },
            mock("/ohjausparametrit-service/j_spring_cas_security_check") {
                body("OK")
                headers(Pair("Set-Cookie", "JSESSIONID=JSESSIONID"))
            },
            mock("/ohjausparametrit-service/api/v1/rest/parametri/oids") {
                body(ResourcesUtil.resource("ohjausparametrit/ohjausparametrit.json"))
            },
            mock("/kouta-internal/haku/search?tarjoaja=1.2.246.562.10.00000000001%2C1.2.246.562.10.00000000002&includeHakukohdeOids=true&vuosi=2022") {
                body("[${IOUtils.toString(ResourcesUtil.resource("kouta/kouta_internal_1.2.246.562.29.00000000000000000800.json"))}]")
                /*
                body(Gson().toJson(listOf(HakuInternal(
                    oid = "1.2.246.562.29.00000000000000002175",
                    tila = "julkaistu",
                    nimi = mapOf("fi" to "Nimi suomeksi"),
                    hakukohdeOids = listOf(),
                    hakutapaKoodiUri = "",
                    hakukohteenLiittamisenTakaraja = "",
                    ajastettuJulkaisu = "",
                    alkamiskausiKoodiUri = "",
                    alkamisvuosi = 2022,
                    hakuvuosi = 2022,
                    hakukausi = "",
                    kohdejoukkoKoodiUri = "",
                    kohdejoukonTarkenneKoodiUri = "",
                    hakukohteenMuokkaamiseenTakaraja = "",
                    hakulomaketyyppi = "",
                    hakulomakeAtaruId = "",
                    hakulomakeKuvaus = mapOf(),
                    hakulomakeLinkki = mapOf(),
                    hakuajat = listOf(),
                    valintakokeet = listOf(),
                    metadata = mapOf(),
                    kielivalinta = listOf(),
                    muokkaaja = "",
                    organisaatioOid = "",
                    externalId = ""
                ))))*/
            },
            mock("/tarjonta-service/rest/v1/haku/findByAlkamisvuosi/2022") {
                body("""{"result": []}""")
            }
        )
    }

    @get:Rule
    val mockServerRule = mockServer.asRule()
    val ulkoisetRajapinnatApi = UlkoisetRajapinnatApi(TestOphProperties, TestConfig)

    @Test
    fun findHakuByYear2022() = runTest {
        val data = ulkoisetRajapinnatApi.findHakuByYear(2022)
            .asDeferred().await()

        Assert.assertTrue("Expected one result from Kouta Internal", data.size == 1)
        Assert.assertTrue("Expected one result from Kouta Internal", data.first().hakukohteidenPriorisointi!!)
        data.forEach { haku ->
            Assert.assertTrue("1.2.246.562.29.00000000000000000800".equals(haku.hakuOid))
        }
    }
}