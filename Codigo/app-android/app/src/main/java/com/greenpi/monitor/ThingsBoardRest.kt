package com.greenpi.monitor

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cliente da API REST do ThingsBoard, usado pela tela de historico.
 *
 * Enquanto a telemetria sobe por MQTT, a consulta do historico usa HTTP/REST com
 * autenticacao JWT - os dois protocolos de rede estudados no modulo de Redes de
 * Computadores, cada um no papel em que se sai melhor: MQTT para o fluxo
 * continuo e leve de dados, REST para consultas pontuais.
 *
 * Todas as chamadas sao bloqueantes e devem ser feitas fora da thread principal.
 */
/** Valor de telemetria acompanhado do instante em que a plataforma o registrou. */
data class TsValue(val ts: Long, val value: String) {
    fun toDoubleOrNull(): Double? = value.toDoubleOrNull()
}

class ThingsBoardRest(host: String, port: Int) {

    private val base = "http://$host:$port"
    private var jwt: String? = null

    /** Autentica e guarda o token JWT da sessao. */
    fun login(username: String, password: String) {
        val body = JSONObject().put("username", username).put("password", password)
        val response = request("POST", "/api/auth/login", body.toString(), auth = false)
        jwt = JSONObject(response).getString("token")
    }

    /** Resolve o UUID do dispositivo a partir do nome cadastrado no ThingsBoard. */
    fun findDeviceId(deviceName: String): String {
        val encoded = URLEncoder.encode(deviceName, "UTF-8")
        val response = request("GET", "/api/tenant/devices?deviceName=$encoded")
        return JSONObject(response).getJSONObject("id").getString("id")
    }

    /**
     * Busca as series temporais das chaves informadas.
     * @return mapa chave -> lista de pares (timestamp, valor), em ordem crescente.
     */
    fun timeseries(
        deviceId: String,
        keys: List<String>,
        fromMillis: Long,
        toMillis: Long,
        limit: Int = 500
    ): Map<String, List<Pair<Long, Double>>> {
        val path = "/api/plugins/telemetry/DEVICE/$deviceId/values/timeseries" +
                "?keys=${keys.joinToString(",")}" +
                "&startTs=$fromMillis&endTs=$toMillis&limit=$limit&orderBy=ASC"
        val json = JSONObject(request("GET", path))

        val result = mutableMapOf<String, List<Pair<Long, Double>>>()
        for (key in keys) {
            val arr: JSONArray = json.optJSONArray(key) ?: continue
            val points = ArrayList<Pair<Long, Double>>(arr.length())
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val value = item.getString("value").toDoubleOrNull() ?: continue
                points.add(item.getLong("ts") to value)
            }
            result[key] = points.sortedBy { it.first }
        }
        return result
    }

    /**
     * Le o valor mais recente de cada chave informada.
     * Sem intervalo de tempo, o ThingsBoard devolve apenas a ultima amostra.
     *
     * O timestamp acompanha o valor porque o painel precisa saber a IDADE do
     * dado: se a Raspberry Pi for desligada, a plataforma continua devolvendo a
     * ultima leitura gravada, indefinidamente. Sem o "ts" nao ha como distinguir
     * uma leitura de agora de uma leitura de ontem.
     */
    fun latestValues(deviceId: String, keys: List<String>): Map<String, TsValue> {
        val path = "/api/plugins/telemetry/DEVICE/$deviceId/values/timeseries" +
                "?keys=${keys.joinToString(",")}"
        val json = JSONObject(request("GET", path))

        val result = mutableMapOf<String, TsValue>()
        for (key in keys) {
            val arr = json.optJSONArray(key) ?: continue
            if (arr.length() > 0) {
                val item = arr.getJSONObject(0)
                result[key] = TsValue(item.getLong("ts"), item.getString("value"))
            }
        }
        return result
    }

    // ---------------------------------------------------------------- HTTP

    private fun request(method: String, path: String, body: String? = null, auth: Boolean = true): String {
        val conn = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            if (auth) jwt?.let { setRequestProperty("X-Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            body?.let { conn.outputStream.use { os -> os.write(it.toByteArray()) } }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (code !in 200..299) {
                throw RuntimeException("HTTP $code: ${text.take(200)}")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }
}
