package ulkoiset_rajapinnat.ohjausparametrit

import fi.vm.sade.javautils.nio.cas.CasClientBuilder
import fi.vm.sade.javautils.nio.cas.CasConfig
import fi.vm.sade.properties.OphProperties
import ulkoiset_rajapinnat.config.Headers
import ulkoiset_rajapinnat.ohjausparametrit.dto.Ohjausparametrit
import ulkoiset_rajapinnat.util.BaseCasClient
import java.util.concurrent.CompletableFuture

class OhjausparametritClient(username: String,
                             password: String,
                             properties: OphProperties
) : BaseCasClient(
    properties, CasClientBuilder.build(
        CasConfig.CasConfigBuilder(
            username,
            password,
            "${properties.getProperty("host-virkailija")}/cas",
            "${properties.getProperty("host-virkailija")}/ohjausparametrit-service",
            Headers.CSRF,
            Headers.CALLER_ID,
            "/j_spring_cas_security_check"
        ).setJsessionName("JSESSIONID")
            .build()
    )
) {


    fun fetchOhjausparametrit(hakuOids: Set<String>): CompletableFuture<Map<String, Ohjausparametrit>> =
        fetch(url("ohjausparametrit-service.find-by-oids"), hakuOids)

}