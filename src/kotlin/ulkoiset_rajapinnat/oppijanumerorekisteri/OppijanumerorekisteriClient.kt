package ulkoiset_rajapinnat.oppijanumerorekisteri

import fi.vm.sade.javautils.nio.cas.CasClientBuilder
import fi.vm.sade.javautils.nio.cas.CasConfig
import fi.vm.sade.properties.OphProperties
import ulkoiset_rajapinnat.config.Headers
import ulkoiset_rajapinnat.oppijanumerorekisteri.dto.OnrHenkilo
import ulkoiset_rajapinnat.organisaatio.dto.Organisaatio
import ulkoiset_rajapinnat.util.BaseCasClient
import ulkoiset_rajapinnat.util.sequentialBatches
import ulkoiset_rajapinnat.valinta_tulos_service.dto.HakemuksenValinnanTulos
import ulkoiset_rajapinnat.valintaperusteet.dto.HakukohteenValintaperusteResponse
import ulkoiset_rajapinnat.valintaperusteet.dto.ValintaperusteDTO
import java.util.concurrent.CompletableFuture

class OppijanumerorekisteriClient(username: String,
                                  password: String,
                                  properties: OphProperties) : BaseCasClient(
    properties, CasClientBuilder.build(
        CasConfig.CasConfigBuilder(
            username,
            password,
            "${properties.getProperty("host-virkailija")}/cas",
            "${properties.getProperty("host-virkailija")}/oppijanumerorekisteri-service",
            Headers.CSRF,
            Headers.CALLER_ID,
            "/j_spring_cas_security_check"
        ).setJsessionName("JSESSIONID")
            .build()
    )
) {
    fun fetchHenkilotInBatches(organisaatioOids: Set<String>): CompletableFuture<List<OnrHenkilo>> {
        val MAX_BATCH_SIZE = 5000
        return sequentialBatches(
            { oids: List<String> -> fetchHenkilot(oids) },
            CompletableFuture.completedFuture(Pair(organisaatioOids.chunked(MAX_BATCH_SIZE), listOf()))
        )
    }
    fun fetchHenkilot(personOids: List<String>): CompletableFuture<List<OnrHenkilo>> {
        return fetch(url("oppijanumerorekisteri-service.henkilot-by-henkilo-oids"), personOids)
    }
}