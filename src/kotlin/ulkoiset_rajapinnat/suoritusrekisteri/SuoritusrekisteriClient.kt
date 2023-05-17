package ulkoiset_rajapinnat.suoritusrekisteri

import fi.vm.sade.javautils.nio.cas.CasClientBuilder
import fi.vm.sade.javautils.nio.cas.CasConfig
import fi.vm.sade.properties.OphProperties
import ulkoiset_rajapinnat.config.Headers
import ulkoiset_rajapinnat.suoritusrekisteri.dto.Oppija
import ulkoiset_rajapinnat.util.BaseCasClient
import java.util.concurrent.CompletableFuture

class SuoritusrekisteriClient(username: String,
                         password: String,
                         properties: OphProperties) : BaseCasClient(
    properties, CasClientBuilder.build(
        CasConfig.CasConfigBuilder(
            username,
            password,
            "${properties.getProperty("host-virkailija")}/cas",
            "${properties.getProperty("host-virkailija")}/suoritusrekisteri",
            Headers.CSRF,
            Headers.CALLER_ID,
            "/j_spring_cas_security_check"
        ).setJsessionName("JSESSIONID")
            .build()
    )
) {

  /*  fun fetchOppijatForPersonOidsInBatches(hakuOid: String, ensikertalaisuudet: Boolean, hakemusOids: List<String>): CompletableFuture<List<Oppija>> {
        val MAX_BATCH_SIZE = 30000
        return sequentialBatches(
            this::fetchOppijatForPersonOids,
            completedFuture(Pair(hakemusOids.chunked(MAX_BATCH_SIZE), listOf()))
        )
    }*/

    fun fetchOppijatForPersonOids(hakuOid: String, personOids: List<String>): CompletableFuture<List<Oppija>> {
        return fetch(url("suoritusrekisteri-service.cas.oppijat", "false", hakuOid ), personOids)
    }

}