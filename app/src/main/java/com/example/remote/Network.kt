package com.example.remote

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.ws
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.logging.HttpLoggingInterceptor
import okio.ByteString
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession

object Network {

    val ktorClient = HttpClient(OkHttp) {
        engine {
            addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
        }
        install(WebSockets) {
            pingInterval = 500
        }
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }
    }

    private var wsSession: WebSocketSession? = null
    private var webSocket: WebSocket? = null

    suspend fun addPhone(
        code: String,
        la: String,
        system_width: String,
        system_height: String,
        is_show: String,
        is_clock: String,
        power: String
    ) {
        ktorClient.submitForm("http://control.y99j.com/api/phone/addPhone", parameters {
            append("code", code)
            append("pai", android.os.Build.MANUFACTURER)
            append("xinghao", android.os.Build.MODEL)
            append("name", android.os.Build.BRAND)
            append("system_version", android.os.Build.VERSION.RELEASE)
            append("la", la)
            append("system_width", system_width)
            append("system_height", system_height)
            append("is_online", "1")
            append("is_show", is_show)
            append("is_clock", is_clock)
            append("power", power)
        }, encodeInQuery = false)
    }

    suspend fun changeStatus(
        code: String, is_online: String, is_show: String, is_clock: String, power: String
    ) {
        ktorClient.submitForm("http://control.y99j.com/api/phone/changeStatus", parameters {
            append("code", code)
            append("is_online", is_online)
            append("is_show", is_show)
            append("is_clock", is_clock)
            append("power", power)
        })
    }

    suspend fun addLog(
        code: String,
        kongjian: String,
        do_time: String,
        content: String,
    ) {
        ktorClient.submitForm("http://control.y99j.com/api/phone/addLog", parameters {
            append("code", code)
            append("kongjian", kongjian)
            append("do_time", do_time)
            append("content", content)
        })
    }

    suspend fun setPassword(
        code: String,
        password_type: String,
        password: String
    ) {
        ktorClient.submitForm("http://control.y99j.com/api/phone/editPassword", parameters {
            append("code", code)
            append("password_type", password_type)
            append("password", password)
        })
    }

    suspend fun remoteControl()  {
        ktorClient.webSocket("ws://control.y99j.com/ws/") {
            for (message in incoming){
                Log.e("TAG", "remoteControl: ${message}")
            }
            wsSession = this
            while (true) {
                Log.e("TAG", "remoteControl: ${incoming.receive()}")
                when (val message = incoming.receive()) {
                    is Frame.Binary -> {}
                    is Frame.Close -> {
                        close()
                    }

                    is Frame.Ping -> {
                        Log.e("TAG", "remoteControl: ping}")
                    }

                    is Frame.Pong -> {
                        Log.e("TAG", "remoteControl: pong}")
                    }

                    is Frame.Text -> {
                        Log.e("TAG", "remoteControl: ${incoming.receive()}}")
                    }
                }
            }
        }
    }

    suspend fun sendMessage(message: String) {
        wsSession?.send(message)
        webSocket?.send(message)
    }

    suspend fun close() {
        wsSession?.close()
    }
}

sealed interface ControlType {
    data object History : ControlType
    data object Home : ControlType
    data object Back : ControlType
    data object Lock : ControlType
    data object Unlock : ControlType
    data object Black : ControlType
    data object Light : ControlType
    data class Click(val x: Int, val y: Int) : ControlType
    data class Touch(val startX: Int, val startY: Int, val endX: Int, val endY: Int) : ControlType
    data class LongClick(val x: Int, val y: Int) : ControlType
}