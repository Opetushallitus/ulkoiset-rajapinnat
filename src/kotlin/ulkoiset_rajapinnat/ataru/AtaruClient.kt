package ulkoiset_rajapinnat.ataru

import fi.vm.sade.javautils.nio.cas.CasClientBuilder
import fi.vm.sade.javautils.nio.cas.CasConfig
import fi.vm.sade.properties.OphProperties
import ulkoiset_rajapinnat.ataru.dto.Ataruhakemus
import ulkoiset_rajapinnat.config.Headers
import ulkoiset_rajapinnat.util.BaseCasClient
import ulkoiset_rajapinnat.util.sequentialBatches
import ulkoiset_rajapinnat.valintaperusteet.dto.HakukohteenValintaperusteResponse
import ulkoiset_rajapinnat.valintapisteet.dto.HakemuksenValintapisteet
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture

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
    ), 1000 * 60 * 60 * 4
) {

    fun fetchHaunHakemukset(hakuOid: String): CompletableFuture<List<Ataruhakemus>> {
        return fetch(url("lomake-editori.tilastokeskus-by-haku-oid", hakuOid))
    }

    fun fetchHaunHakemuksetHakukohteella(hakuOid: String, hakukohdeOid: String): CompletableFuture<List<Ataruhakemus>> {
        return fetch(url("lomake-editori.tilastokeskus-by-haku-oid-hakukohde-oid", hakuOid, hakukohdeOid))
    }

}