package ir.cheshmgoya.app.net

import ir.cheshmgoya.core.ai.HttpTransport
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** [HttpTransport] on OkHttp, with a hard per-call timeout. */
class OkHttpTransport(val client: OkHttpClient = OkHttpClient()) : HttpTransport {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    override suspend fun postJson(url: String, headers: Map<String, String>, body: String, timeoutMs: Long): HttpTransport.Response =
        execute(request(url, headers).post(body.toRequestBody(jsonType)).build(), timeoutMs)

    override suspend fun get(url: String, headers: Map<String, String>, timeoutMs: Long): HttpTransport.Response =
        execute(request(url, headers).get().build(), timeoutMs)

    suspend fun postBody(url: String, headers: Map<String, String>, body: RequestBody, timeoutMs: Long): Pair<Int, ByteArray> {
        val call = client.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
            .newCall(request(url, headers).post(body).build())
        return await(call) { r -> r.code to (r.body?.bytes() ?: ByteArray(0)) }
    }

    private fun request(url: String, headers: Map<String, String>) =
        Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }

    private suspend fun execute(req: Request, timeoutMs: Long): HttpTransport.Response {
        val call = client.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build().newCall(req)
        return await(call) { r -> HttpTransport.Response(r.code, r.body?.string().orEmpty()) }
    }

    private suspend fun <T> await(call: Call, read: (Response) -> T): T = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val v = try {
                    response.use(read)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resumeWithException(e)
                    return
                }
                if (cont.isActive) cont.resume(v)
            }
        })
    }
}
