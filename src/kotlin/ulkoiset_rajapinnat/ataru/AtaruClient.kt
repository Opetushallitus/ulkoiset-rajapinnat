package ulkoiset_rajapinnat.ataru

import com.github.benmanes.caffeine.cache.Caffeine
import fi.vm.sade.javautils.nio.cas.CasClientBuilder
import fi.vm.sade.javautils.nio.cas.CasConfig
import fi.vm.sade.properties.OphProperties
import ulkoiset_rajapinnat.ataru.dto.Ataruhakemus
import ulkoiset_rajapinnat.config.Headers
import ulkoiset_rajapinnat.util.BaseCasClient
import ulkoiset_rajapinnat.util.sequentialBatches
import ulkoiset_rajapinnat.valinta_tulos_service.dto.HakemuksenValinnanTulos
import ulkoiset_rajapinnat.valintaperusteet.dto.HakukohteenValintaperusteResponse
import ulkoiset_rajapinnat.valintapisteet.dto.HakemuksenValintapisteet
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class AtaruClient(username: String,
                         password: String,
                         properties: OphProperties) : BaseCasClient(
    properties, CasClientBuilder.build(
        CasConfig.CasConfigBuilder(
            username,
            password,
            "${properties.getProperty("host-virkailija")}/cas",
            "${properties.getProperty("host-virkailija")}/lomake-editori",
            Headers.CSRF,
            Headers.CALLER_ID,
            "/auth/cas"
        ).setJsessionName("ring-session")
            .build()
    ), 1000 * 60 * 30 // 2. asteen yhteishaulle kasvata 1000 * 60 * 60 * 4
) {

    fun fetchHaunHakemukset(hakuOid: String): CompletableFuture<List<Ataruhakemus>> {
        return fetch(url("lomake-editori.tilastokeskus-by-haku-oid", hakuOid))
    }

    // Näitä käytetään vain 2. asteen yhteishaun datalle

    fun fetchHaunHakemuksetHakukohteella(hakuOid: String, hakukohdeOid: String): CompletableFuture<List<Ataruhakemus>> {
        logger.info("Haetaan haun $hakuOid hakukohteen $hakukohdeOid hakemukset Atarusta")
        return fetch(url("lomake-editori.tilastokeskus-by-haku-oid-hakukohde-oid", hakuOid, hakukohdeOid))
    }

    private val cache = Caffeine.newBuilder()
        .expireAfterWrite(2L, TimeUnit.HOURS) // 2. asteen yhteishaulle kasvata
        .buildAsync { hakuJaHakukohde: Pair<String, String>, executor: Executor -> fetchHaunHakemuksetHakukohteella(hakuJaHakukohde.first, hakuJaHakukohde.second) }

    fun fetchHaunHakemuksetHakukohteellaCached(hakuOid: String, hakukohdeOid: String): CompletableFuture<List<Ataruhakemus>> {
        logger.info("Haetaan atarun hakemustiedot haun $hakuOid hakukohteelle $hakukohdeOid")
        val result = cache.get(Pair(hakuOid, hakukohdeOid))
        logger.info("Tulos haun $hakuOid hakukohteelle $hakukohdeOid cachessa: ${result.isDone}")
        return result
    }

}