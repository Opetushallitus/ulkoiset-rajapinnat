package ulkoiset_rajapinnat.suoritusrekisteri

import fi.vm.sade.javautils.nio.cas.CasClientBuilder
import fi.vm.sade.javautils.nio.cas.CasConfig
import fi.vm.sade.properties.OphProperties
import ulkoiset_rajapinnat.config.Headers
import ulkoiset_rajapinnat.suoritusrekisteri.dto.Ensikertalaisuus
import ulkoiset_rajapinnat.suoritusrekisteri.dto.Oppija
import ulkoiset_rajapinnat.util.BaseCasClient
import ulkoiset_rajapinnat.util.sequentialBatches
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture

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

    fun fetchOppijatForPersonOidsInBatches(hakuOid: String, hakemusOids: List<String>, fetchEnsikertalaisuudet: Boolean): CompletableFuture<List<Oppija>> {
        val MAX_BATCH_SIZE = 5000
        return sequentialBatches(
            { oids: List<String> -> fetchOppijatForPersonOids(hakuOid, oids, fetchEnsikertalaisuudet) },
            completedFuture(Pair(hakemusOids.chunked(MAX_BATCH_SIZE), listOf()))
        )
    }

    fun fetchHaunEnsikertalaisuudet(hakuOid: String): CompletableFuture<List<Ensikertalaisuus>> {
        return fetch(url("suoritusrekisteri-service.cas.haun.ensikertalaiset", hakuOid, true))
    }

    fun fetchOppijatForPersonOids(hakuOid: String, personOids: List<String>, fetchEnsikertalaisuudet: Boolean): CompletableFuture<List<Oppija>> {
        return fetch(url("suoritusrekisteri-service.cas.oppijat", fetchEnsikertalaisuudet, hakuOid ), personOids)
    }

}