package ulkoiset_rajapinnat.valintaperusteet

import fi.vm.sade.javautils.nio.cas.CasClientBuilder
import fi.vm.sade.javautils.nio.cas.CasConfig
import fi.vm.sade.properties.OphProperties
import ulkoiset_rajapinnat.config.Headers
import ulkoiset_rajapinnat.util.BaseCasClient
import ulkoiset_rajapinnat.valinta_tulos_service.dto.HakemuksenValinnanTulos
import ulkoiset_rajapinnat.valintaperusteet.dto.HakukohteenValintaperusteResponse
import ulkoiset_rajapinnat.valintaperusteet.dto.ValintaperusteDTO
import java.util.concurrent.CompletableFuture

class ValintaperusteetClient(username: String,
                                password: String,
                                properties: OphProperties) : BaseCasClient(
    properties, CasClientBuilder.build(
        CasConfig.CasConfigBuilder(
            username,
            password,
            "${properties.getProperty("host-virkailija")}/cas",
            "${properties.getProperty("host-virkailija")}/valintaperusteet-service",
            Headers.CSRF,
            Headers.CALLER_ID,
            "/j_spring_cas_security_check"
        ).setJsessionName("JSESSIONID")
            .build()
    )
) {
    fun fetchValintakokeet(hakukohdeOids: Set<String>): CompletableFuture<List<HakukohteenValintaperusteResponse>> {
        return fetch(url("valintaperusteet-service.hakukohde-avaimet"), hakukohdeOids)
    }
}