package com.greenpi.monitor

/**
 * Amostra completa do ambiente, ja com a compensacao termica aplicada,
 * pronta para ser publicada e exibida.
 */
data class EnvironmentSample(
    val temperature: Double,      // graus Celsius, compensada
    val temperatureRaw: Double,   // graus Celsius, direto do HTS221
    val humidity: Double,         // %rH
    val pressure: Double,         // hPa
    val light: Int,               // contagens do canal "clear" do TCS3400
    val cpuTemperature: Double,   // graus Celsius do SoC
    val wifiRssi: Int?,           // dBm
    val timestamp: Long = System.currentTimeMillis()
) {
    /** Serializa no formato esperado pelo topico v1/devices/me/telemetry. */
    fun toJson(): String {
        val sb = StringBuilder("{")
        fun num(k: String, v: Double, dec: Int) {
            if (!v.isNaN()) sb.append("\"$k\":${"%.${dec}f".format(java.util.Locale.US, v)},")
        }
        num("temperature", temperature, 2)
        num("temperature_raw", temperatureRaw, 2)
        num("humidity", humidity, 1)
        num("pressure", pressure, 2)
        sb.append("\"light\":$light,")
        num("cpu_temp", cpuTemperature, 1)
        wifiRssi?.let { sb.append("\"wifi_rssi\":$it,") }
        sb.append("\"status\":\"${status().label}\"")
        sb.append("}")
        return sb.toString()
    }

    /** Status geral do ambiente: o pior entre temperatura, umidade e luminosidade. */
    fun status(): EnvStatus = listOf(
        EnvStatus.forTemperature(temperature),
        EnvStatus.forHumidity(humidity),
        EnvStatus.forLight(light)
    ).maxByOrNull { it.severity } ?: EnvStatus.OK

    /** Ha quanto tempo esta amostra foi registrada, em milissegundos. */
    fun ageMillis(now: Long = System.currentTimeMillis()): Long = now - timestamp

    /**
     * Uma amostra e considerada obsoleta quando passa do prazo em que a proxima
     * ja deveria ter chegado. Nesse caso os valores continuam na tela, mas nao
     * podem ser apresentados como a condicao atual do ambiente.
     */
    fun isStale(maxAgeMillis: Long, now: Long = System.currentTimeMillis()): Boolean =
        ageMillis(now) > maxAgeMillis
}

/**
 * Formata uma duracao como texto curto para a interface ("12 s", "3 min", "2 h").
 */
fun formatAge(millis: Long): String {
    val s = millis / 1000
    return when {
        s < 60 -> "$s s"
        s < 3600 -> "${s / 60} min"
        s < 86400 -> "${s / 3600} h"
        else -> "${s / 86400} d"
    }
}

/**
 * Faixas de referencia para cultivo de plantas de interior. O status e usado
 * para colorir os cartoes do app (verde / amarelo / vermelho).
 */
enum class EnvStatus(val severity: Int, val label: String) {
    OK(0, "OK"),
    ATENCAO(1, "ATENCAO"),
    ALERTA(2, "ALERTA"),
    DESCONHECIDO(-1, "SEM DADOS");

    companion object {
        fun forTemperature(t: Double) = when {
            t.isNaN() -> DESCONHECIDO
            t in 18.0..28.0 -> OK
            t in 15.0..32.0 -> ATENCAO
            else -> ALERTA
        }

        fun forHumidity(h: Double) = when {
            h.isNaN() -> DESCONHECIDO
            h in 40.0..70.0 -> OK
            h in 30.0..80.0 -> ATENCAO
            else -> ALERTA
        }

        fun forLight(l: Int) = when {
            l > 100 -> OK
            l >= 30 -> ATENCAO
            else -> ALERTA
        }

        fun forPressure(p: Double) = if (p.isNaN()) DESCONHECIDO else OK
    }
}

/**
 * Compensacao do auto-aquecimento do Sense HAT.
 *
 * O Sense HAT fica montado a poucos milimetros do SoC da Raspberry Pi 5, que
 * opera perto de 57 graus Celsius. Esse calor eleva a leitura do HTS221 em
 * cerca de 12 a 15 graus. A correcao subtrai uma fracao da diferenca entre a
 * temperatura da CPU e a do sensor:
 *
 *     T_real = T_sensor - (T_cpu - T_sensor) / k
 *
 * O fator k depende da montagem (gabinete, ventilacao) e por isso e ajustavel
 * na tela de configuracoes, sendo calibrado com um termometro de referencia.
 */
object ThermalCompensation {
    fun apply(sensorTemp: Double, cpuTemp: Double, k: Double): Double {
        if (sensorTemp.isNaN()) return Double.NaN
        if (cpuTemp.isNaN() || k <= 0.0) return sensorTemp
        return sensorTemp - (cpuTemp - sensorTemp) / k
    }
}
