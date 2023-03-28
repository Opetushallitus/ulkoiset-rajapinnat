package ulkoiset_rajapinnat.koodisto

import com.github.benmanes.caffeine.cache.Caffeine
import fi.vm.sade.properties.OphProperties
import org.apache.commons.lang3.concurrent.BasicThreadFactory
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.asynchttpclient.Dsl
import ulkoiset_rajapinnat.koodisto.dto.Koodisto
import ulkoiset_rajapinnat.util.BaseClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class KoodistoClient(properties: OphProperties) : BaseClient(
    properties, Dsl.asyncHttpClient(
        DefaultAsyncHttpClientConfig.Builder()
            .setThreadFactory(
                BasicThreadFactory.Builder()
                    .namingPattern("async-koodisto-client-thread-%d")
                    .daemon(true)
                    .priority(Thread.NORM_PRIORITY)
                    .build()
            )
            .build()
    )
) {

    private val cache = Caffeine.newBuilder()
        .expireAfterWrite(2L, TimeUnit.HOURS)
        .buildAsync { key: String, executor: Executor ->
             if ((key.split('%')[2].toBoolean())) {
                 fetchKoodistoWithRelationsForReal(key.split("%")[0], Integer.parseInt(key.split("%")[1]))
            } else {
                fetchKoodistoForReal(key.split("%")[0], Integer.parseInt(key.split("%")[1]))
            }
        }

    private fun fetchKoodistoForReal(koodisto: String, version: Int): CompletableFuture<Map<String, Koodisto>> =
        fetch<List<Koodisto>>(url("koodisto-service.codeelement-codes", koodisto, version))
            .thenApply { koodit -> koodit.map { it.koodiUri to it }.toMap() }

    private fun fetchKoodistoWithRelationsForReal(koodisto: String, version: Int): CompletableFuture<Map<String, Koodisto>> =
            fetch<List<Koodisto>>(url("koodisto-service.codeelement-codes-with-relations", koodisto, version))
                    .thenApply { koodit -> koodit.map { it.koodiUri to it }.toMap() }

    fun fetchKoodisto(koodisto: String, version: Int? = 1, withRelations: Boolean? = false): CompletableFuture<Map<String, Koodisto>> {
        return cache["$koodisto%$version%$withRelations"]
    }

}
