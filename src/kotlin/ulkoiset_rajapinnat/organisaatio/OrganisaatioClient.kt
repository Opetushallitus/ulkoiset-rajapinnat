package ulkoiset_rajapinnat.organisaatio

import com.google.common.collect.Sets
import fi.vm.sade.properties.OphProperties
import org.apache.commons.lang3.concurrent.BasicThreadFactory
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.asynchttpclient.Dsl
import ulkoiset_rajapinnat.organisaatio.dto.Organisaatio
import ulkoiset_rajapinnat.util.BaseClient
import ulkoiset_rajapinnat.util.sequentialBatches
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture

class OrganisaatioClient(properties: OphProperties) : BaseClient(
    properties, Dsl.asyncHttpClient(
        DefaultAsyncHttpClientConfig.Builder()
            .setThreadFactory(
                BasicThreadFactory.Builder()
                    .namingPattern("async-organisaatio-client-thread-%d")
                    .daemon(true)
                    .priority(Thread.NORM_PRIORITY)
                    .build()
            )
            .build()
    )
) {

    private fun fetchOrganisaatiotForReal(organisaatioOids: List<String>): CompletableFuture<List<Organisaatio>> =
        fetch(url("organisaatio-service.find-by-oids-v4"), organisaatioOids)

    fun fetchOrganisaatiot(organisaatioOids: Set<String>): CompletableFuture<List<Organisaatio>> {
        val MAX_BATCH_SIZE = 500
        return sequentialBatches(
            this::fetchOrganisaatiotForReal,
            completedFuture(Pair(organisaatioOids.chunked(MAX_BATCH_SIZE), listOf()))
        )
    }

    fun fetchOrganisaatiotAndParentOrganisaatiot(organisaatioOids: Set<String>): CompletableFuture<List<Organisaatio>> {
        return fetchOrganisaatiot(organisaatioOids).thenCompose { orgs ->
            val parentOids = orgs.flatMap { it.parentOids() }.toSet()
            val difference =
                Sets.difference(parentOids, organisaatioOids)
            fetchOrganisaatiot(difference).thenApply {
                it + orgs
            }
        }
    }

}