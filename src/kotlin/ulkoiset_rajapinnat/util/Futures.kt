package ulkoiset_rajapinnat.util

import kotlinx.coroutines.future.await
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.CompletionStage
import java.util.function.BiFunction
import java.util.function.Function

suspend operator fun <T> CompletableFuture<T>.invoke(): T {
    return await()
}

fun <CHUNKS, RESULTS> sequentialBatches(f: (List<CHUNKS>) -> CompletableFuture<List<RESULTS>>,
                                        p: CompletableFuture<Pair<List<List<CHUNKS>>, List<RESULTS>>>): CompletableFuture<List<RESULTS>> {
    return p.thenCompose {
            (chunks, results) ->
        val head = chunks.firstOrNull()
        if(head != null) {
            val jj = f(head).thenApply {
                val tail = chunks.drop(1)
                val vv = it + results
                Pair(tail, vv)
            }
            sequentialBatches(f, jj)
        } else {
            completedFuture(results)
        }
    }
}
