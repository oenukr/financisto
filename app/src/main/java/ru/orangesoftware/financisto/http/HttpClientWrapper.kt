package ru.orangesoftware.financisto.http

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

open class HttpClientWrapper(private val client: OkHttpClient?) {

    @Throws(Exception::class)
    fun getAsJson(url: String): JSONObject = JSONObject(getAsString(url).orEmpty())

    @Throws(Exception::class)
    open fun getAsString(url: String): String? = get(url)?.body?.string()

    @Throws(Exception::class)
    fun getAsStringIfOk(url: String): String? {
        val response: Response? = get(url)
        val s: String? = response?.body?.string()
        if (response?.isSuccessful == true) {
            return s
        } else {
            throw RuntimeException(s)
        }
    }

    @Throws(IOException::class)
    protected fun get(url: String): Response? {
        val request: Request = Request.Builder()
                .url(url)
                .build()
        return client?.newCall(request)?.execute()
    }

}
