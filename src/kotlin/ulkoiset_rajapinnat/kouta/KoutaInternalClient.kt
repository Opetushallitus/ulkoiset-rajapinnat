package ulkoiset_rajapinnat.kouta

import fi.vm.sade.javautils.nio.cas.CasClientBuilder
import fi.vm.sade.javautils.nio.cas.CasConfig
import fi.vm.sade.properties.OphProperties
import ulkoiset_rajapinnat.config.Headers
import ulkoiset_rajapinnat.kouta.dto.HakuInternal
import ulkoiset_rajapinnat.kouta.dto.HakukohdeInternal
import ulkoiset_rajapinnat.kouta.dto.KoulutusInternal
import ulkoiset_rajapinnat.kouta.dto.ToteutusInternal
import ulkoiset_rajapinnat.util.BaseCasClient
import java.util.concurrent.CompletableFuture

class KoutaInternalClient(username: String,
                          password: String,
                          properties: OphProperties
) : BaseCasClient(
    properties, CasClientBuilder.build(
        CasConfig.CasConfigBuilder(
            username,
            password,
            "${properties.getProperty("host-virkailija")}/cas",
            "${properties.getProperty("host-virkailija")}/kouta-internal",
            Headers.CSRF,
            Headers.CALLER_ID,
            "/auth/login"
        ).setJsessionName("session")
            .build()
    )
) {

    fun findByHakuOid(hakuOid: String): CompletableFuture<HakuInternal> = fetch(url("kouta-internal.haku-find-by-haku-oid", hakuOid))

    fun findHakukohteetByHakuOid(hakuOid: String): CompletableFuture<List<HakukohdeInternal>> = fetch(url("kouta-internal.hakukohteet-find-by-haku-oid", hakuOid))

    fun findKoulutuksetByHakuOid(hakuOid: String): CompletableFuture<List<KoulutusInternal>> = fetch(url("kouta-internal.koulutukset-find-by-haku-oid", hakuOid))
    fun findToteutuksetByHakuOid(hakuOid: String): CompletableFuture<List<ToteutusInternal>> = fetch(url("kouta-internal.toteutukset-find-by-haku-oid", hakuOid))

    fun findByHakuvuosi(year: Int): CompletableFuture<List<HakuInternal>> = fetch(url("kouta-internal.haku-find-by-hakuvuosi", year))
}