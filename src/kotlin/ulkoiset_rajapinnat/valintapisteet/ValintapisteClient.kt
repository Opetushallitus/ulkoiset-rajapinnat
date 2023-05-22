package ulkoiset_rajapinnat.valintapisteet

import fi.vm.sade.javautils.nio.cas.CasClientBuilder
import fi.vm.sade.javautils.nio.cas.CasConfig
import fi.vm.sade.properties.OphProperties
import ulkoiset_rajapinnat.config.Headers
import ulkoiset_rajapinnat.util.BaseCasClient
import ulkoiset_rajapinnat.util.sequentialBatches
import ulkoiset_rajapinnat.valintaperusteet.dto.HakukohteenValintaperusteResponse
import ulkoiset_rajapinnat.valintapisteet.dto.HakemuksenValintapisteet
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture

class ValintapisteClient(username: String,
                             password: String,
                             properties: OphProperties) : BaseCasClient(
    properties, CasClientBuilder.build(
        CasConfig.CasConfigBuilder(
            username,
            password,
            "${properties.getProperty("host-virkailija")}/cas",
            "${properties.getProperty("host-virkailija")}/valintapiste-service",
            Headers.CSRF,
            Headers.CALLER_ID,
            "/auth/cas"
        ).setJsessionName("ring-session")
            .build()
    )
) {

    fun fetchValintapisteetForHakemusOidsInBatches(hakemusOids: List<String>): CompletableFuture<List<HakemuksenValintapisteet>> {
        logger.info("Haetaan valintapisteet ${hakemusOids.size} hakemukselle.")
        val MAX_BATCH_SIZE = 30000
        return sequentialBatches(
            this::fetchValintapisteetForHakemusOids,
            completedFuture(Pair(hakemusOids.chunked(MAX_BATCH_SIZE), listOf()))
        )
    }

    fun fetchValintapisteetForHakemusOids(hakemusOids: List<String>): CompletableFuture<List<HakemuksenValintapisteet>> {
        //Tässä ei nyt välitetä eteenpäin kutsujan tietoja, mutta tällä on merkitystä vain valintapiste-servicen auditlokituksen kannalta.
        return fetch(url("valintapiste-service.pisteet-with-hakemusoids", "ulkoiset-rajapinnat", "1.2.3.4", "1.2.3.4", "agent"), hakemusOids)
    }

}