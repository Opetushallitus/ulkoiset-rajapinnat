package ulkoiset_rajapinnat.valinta_tulos_service

import com.github.benmanes.caffeine.cache.Caffeine
import fi.vm.sade.javautils.nio.cas.CasClientBuilder
import fi.vm.sade.javautils.nio.cas.CasConfig
import fi.vm.sade.properties.OphProperties
import ulkoiset_rajapinnat.config.Headers
import ulkoiset_rajapinnat.util.BaseCasClient
import ulkoiset_rajapinnat.valinta_tulos_service.dto.HakemuksenValinnanTulos
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class ValintaTulosServiceClient(username: String,
                                password: String,
                                properties: OphProperties) : BaseCasClient(
    properties, CasClientBuilder.build(
        CasConfig.CasConfigBuilder(
            username,
            password,
            "${properties.getProperty("host-virkailija")}/cas",
            "${properties.getProperty("host-virkailija")}/valinta-tulos-service",
            Headers.CSRF,
            Headers.CALLER_ID,
            "/auth/login"
        ).setJsessionName("session")
            .build()
    )
) {

    private val cache = Caffeine.newBuilder()
        .expireAfterWrite(2L, TimeUnit.HOURS)
        .buildAsync { hakuOid: String, executor: Executor -> fetchHaunHakemustenTulokset(hakuOid) }

    fun fetchHaunHakemustenTuloksetCached(hakuOid: String): CompletableFuture<List<HakemuksenValinnanTulos>> {
        return cache.get(hakuOid)
    }

    fun fetchHaunHakemustenTulokset(hakuOid: String): CompletableFuture<List<HakemuksenValinnanTulos>> {
        return fetch<List<HakemuksenValinnanTulos>>(url("valinta-tulos-service.cas.hakemukset", hakuOid), 200)
    }

    fun fetchHakemustenTuloksetHakukohteille(hakuOid: String, hakukohdeOids: List<String>): CompletableFuture<List<HakemuksenValinnanTulos>> {
        return fetch<List<HakemuksenValinnanTulos>, List<String>>(url("valinta-tulos-service.cas.hakemukset", hakuOid), hakukohdeOids, 200)
    }

}