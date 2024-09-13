package ulkoiset_rajapinnat.util

import com.google.gson.reflect.TypeToken
import fi.vm.sade.javautils.nio.cas.CasClient
import fi.vm.sade.properties.OphProperties
import org.slf4j.LoggerFactory
import ulkoiset_rajapinnat.config.Headers
import ulkoiset_rajapinnat.util.Json.gson
import java.util.concurrent.CompletableFuture

abstract class BaseCasClient(
    val properties: OphProperties,
    val client: CasClient,
    val timeoutMillis: Int = 1000 * 60 * 30
) {

    val logger = LoggerFactory.getLogger("BaseCasClient")

    protected fun url(s: String, vararg params: Any): String {
        return properties.getProperty(s, *params)
    }

    inline fun <reified T> fetch(url: String, vararg acceptStatusCodes: Int): CompletableFuture<T> {
        val req = Headers.requestBuilderWithHeaders()
            .setUrl(url)
            .setRequestTimeout(timeoutMillis)
            .setReadTimeout(timeoutMillis)
            .build()
        val t = object: TypeToken<T>() {}.type
        val startTimeMillis = System.currentTimeMillis()
        logger.info("GET Calling url: $url")
        return this.client.executeAndRetryWithCleanSessionOnStatusCodes(req, setOf(302, 401)).thenApply<T> {
            if(it.statusCode != 200 && !acceptStatusCodes.contains(it.statusCode)) {
                throw RuntimeException("Calling $url failed with status ${it.statusCode}")
            }
            gson.fromJson(it.responseBody, t)
        }.handle { u, t ->
            if(t != null) {
                logger.error("Failed to fetch from url $url: $t")
                throw RuntimeException("Failed to fetch $url",t)
            } else {
                logger.info("(${System.currentTimeMillis() - startTimeMillis }ms) Got response from $url" )
            }
            u
        }
    }
    inline fun <reified T, A> fetch(url: String, body: A, vararg acceptStatusCodes: Int): CompletableFuture<T> {
        val req = Headers.requestBuilderWithHeaders()
            .setUrl(url)
            .setRequestTimeout(timeoutMillis)
            .setReadTimeout(timeoutMillis)
            .setMethod("POST")
            .setHeader("Content-Type", "application/json")
            .setBody(Json.gson.toJson(body))
            .build()
        val t = object: TypeToken<T>() {}.type
        val startTimeMillis = System.currentTimeMillis()
        logger.info("POST Calling url: $url")
        return this.client.executeAndRetryWithCleanSessionOnStatusCodes(req, setOf(302, 401)).toCompletableFuture().thenApply<T> {
            if(it.statusCode != 200 && !acceptStatusCodes.contains(it.statusCode)) {
                throw RuntimeException("Calling $url failed with status ${it.statusCode}")
            }
            Json.gson.fromJson(it.responseBody, t)
        }.handle { u, t ->
            if(t != null) {
                logger.error("Failed to fetch from url $url: $t")
                throw RuntimeException("Failed to fetch $url",t)
            } else {
                logger.info("(${System.currentTimeMillis() - startTimeMillis }ms) Got response from $url" )
            }
            u
        }
    }
}