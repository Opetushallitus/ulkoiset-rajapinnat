package ulkoiset_rajapinnat.haku

import fi.vm.sade.properties.OphProperties
import org.apache.commons.lang3.concurrent.BasicThreadFactory
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.asynchttpclient.Dsl
import ulkoiset_rajapinnat.haku.dto.*
import ulkoiset_rajapinnat.util.BaseClient
import ulkoiset_rajapinnat.util.sequentialBatches
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.*



class HakuClient(properties: OphProperties) : BaseClient(
    properties, Dsl.asyncHttpClient(
        DefaultAsyncHttpClientConfig.Builder()
            .setThreadFactory(
                BasicThreadFactory.Builder()
                    .namingPattern("async-haku-client-thread-%d")
                    .daemon(true)
                    .priority(Thread.NORM_PRIORITY)
                    .build()
            )
            .build()
    )
) {

    fun findHakuByHakuvuosi(vuosi: Int): CompletableFuture<HakuResultByAlkamisvuosi> =
        fetch(url("tarjonta-service.haku-find-by-hakuvuosi", vuosi))



    fun fetchHaku(hakuOid: String): CompletableFuture<OldHaku> =
        fetch<HakuResult>(url("tarjonta-service.haku", hakuOid))
            .thenApply { it.result }

    fun fetchHakuHakukohdeTulos(hakuOid: String): CompletableFuture<List<OldHakukohdeTulos>> =
        fetch<HakukohdeTulosResult>(url("tarjonta-service.haku-hakukohde-tulos", hakuOid))
            .thenApply { it.tulokset }

    fun fetchHakukohdeSearchByHakuOid(hakuOid: String): CompletableFuture<List<OldSearchTulos>> =
        fetch<HakukohdeSearchResult>(url("tarjonta-service.hakukohde-search-by-haku-oid", hakuOid)).thenApply {
            it.result.tulokset
        }

    fun fetchKoulutusSearchByHakuOid(hakuOid: String): CompletableFuture<List<OldKoulutusSearchTulos>> =
        fetch<KoulutusSearchResult>(url("tarjonta-service.koulutus-search-by-haku-oid", hakuOid))
            .thenApply { it.result.tulokset }

    private fun fetchTilastokeskusForReal(l: List<String>): CompletableFuture<List<OldTilastokeskus>> {
        return fetch(url("tarjonta-service.tilastokeskus"), l)
    }

    fun fetchTilastokeskus(hakukohdeOids: Set<String>): CompletableFuture<List<OldTilastokeskus>> {
        val MAX_BATCH_SIZE = 500
        return sequentialBatches(
            this::fetchTilastokeskusForReal,
            completedFuture(Pair(hakukohdeOids.chunked(MAX_BATCH_SIZE), listOf()))
        )
    }


}