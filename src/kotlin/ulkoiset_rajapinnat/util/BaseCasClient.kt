package ulkoiset_rajapinnat.util

import com.google.gson.reflect.TypeToken
import fi.vm.sade.javautils.nio.cas.CasClient
import fi.vm.sade.properties.OphProperties
import ulkoiset_rajapinnat.config.Headers
import ulkoiset_rajapinnat.util.Json.gson
import java.util.concurrent.CompletableFuture

abstract class BaseCasClient(
    val properties: OphProperties,
    val client: CasClient
) {

    protected fun url(s: String, vararg params: Any): String {
        return properties.getProperty(s, *params)
    }

    inline fun <reified T> fetch(url: String, vararg acceptStatusCodes: Int): CompletableFuture<T> {
        val req = Headers.requestBuilderWithHeaders()
            .setUrl(url)
            .build()
        val t = object: TypeToken<T>() {}.type
        return this.client.execute(req).thenApply<T> {
            if(it.statusCode != 200 && !acceptStatusCodes.contains(it.statusCode)) {
                throw RuntimeException("Calling $url failed with status ${it.statusCode}")
            }
            gson.fromJson(it.responseBody, t)
        }.handle { u, t ->
            if(t != null) {
                throw RuntimeException("Failed to fetch $url",t)
            }
            u
        }
    }
    inline fun <reified T, A> fetch(url: String, body: A, vararg acceptStatusCodes: Int): CompletableFuture<T> {
        val req = Headers.requestBuilderWithHeaders()
            .setUrl(url)
            .setMethod("POST")
            .setHeader("Content-Type", "application/json")
            .setBody(Json.gson.toJson(body))
            .build()
        val t = object: TypeToken<T>() {}.type
        return this.client.execute(req).toCompletableFuture().thenApply<T> {
            if(it.statusCode != 200 && !acceptStatusCodes.contains(it.statusCode)) {
                throw RuntimeException("Calling $url failed with status ${it.statusCode}")
            }
            Json.gson.fromJson(it.responseBody, t)
        }.handle { u, t ->
            if(t != null) {
                throw RuntimeException("Failed to fetch $url",t)
            }
            u
        }
    }
}