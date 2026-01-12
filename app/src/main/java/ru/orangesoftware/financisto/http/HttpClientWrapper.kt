package ru.orangesoftware.financisto.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.json.JSONObject

open class HttpClientWrapper(private val client: HttpClient) {

    constructor() : this(HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    })

    @Throws(Exception::class)
    fun getAsJson(url: String): JSONObject = JSONObject(getAsString(url).orEmpty())

    @Throws(Exception::class)
    open fun getAsString(url: String): String? = getAsStringIfOk(url)

    @Throws(Exception::class)
    fun getAsStringIfOk(url: String): String? {
        return runBlocking {
            val response = client.get { url(url) }
            if (response.status.isSuccess()) {
                response.bodyAsText()
            } else {
                throw RuntimeException("HTTP Error: ${response.status}")
            }
        }
    }
}
