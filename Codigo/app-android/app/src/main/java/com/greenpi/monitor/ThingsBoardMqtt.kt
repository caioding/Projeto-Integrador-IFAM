package com.greenpi.monitor

import android.util.Log
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

/**
 * Publicacao de telemetria no ThingsBoard via MQTT.
 *
 * O ThingsBoard identifica o dispositivo pelo token de acesso, enviado no campo
 * "username" da conexao MQTT; nao ha senha. A telemetria vai para o topico
 * reservado "v1/devices/me/telemetry".
 *
 * A implementacao segue o mesmo padrao do exemplo SimpleMQTT visto em aula
 * (Eclipse Paho), com reconexao automatica e sessao limpa.
 */
class ThingsBoardMqtt(
    private val host: String,
    private val port: Int,
    private val accessToken: String
) {
    companion object {
        private const val TAG = "ThingsBoardMqtt"
        const val TELEMETRY_TOPIC = "v1/devices/me/telemetry"
    }

    private var client: MqttClient? = null

    val isConnected: Boolean get() = client?.isConnected == true

    /** Abre a conexao com o broker. Lanca excecao em caso de falha. */
    fun connect() {
        if (isConnected) return
        val url = "tcp://$host:$port"
        val clientId = "greenpi-" + System.currentTimeMillis()
        val c = MqttClient(url, clientId, MemoryPersistence())
        val opts = MqttConnectOptions().apply {
            userName = accessToken          // o token do dispositivo faz o papel de usuario
            isCleanSession = true
            connectionTimeout = 15
            keepAliveInterval = 30
            isAutomaticReconnect = true
        }
        c.connect(opts)
        client = c
        Log.i(TAG, "Conectado a $url")
    }

    /** Publica um payload JSON de telemetria com QoS 1. */
    fun publish(json: String) {
        val c = client ?: throw IllegalStateException("Cliente MQTT nao conectado")
        c.publish(TELEMETRY_TOPIC, MqttMessage(json.toByteArray()).apply { qos = 1 })
    }

    fun disconnect() {
        try {
            client?.let { if (it.isConnected) it.disconnect(); it.close() }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao desconectar: ${e.message}")
        }
        client = null
    }
}
