package com.greenpi.monitor

import android.content.Context
import android.net.wifi.WifiManager
import java.io.File

/**
 * Metricas do proprio dispositivo embarcado, obtidas do sistema operacional.
 *
 * Ilustra o pilar de Sistemas Operacionais do curso: a temperatura do SoC vem do
 * sysfs (interface do kernel Linux exposta pelo Android) e o RSSI vem do
 * framework Android, via WifiManager.
 */
object SystemStats {

    private const val THERMAL_ZONE = "/sys/class/thermal/thermal_zone0/temp"

    /**
     * Temperatura do SoC em graus Celsius, lida de /sys/class/thermal.
     * O kernel expoe o valor em milesimos de grau. Retorna NaN se indisponivel
     * (por exemplo, ao rodar num celular comum).
     */
    fun cpuTemperature(): Double = try {
        val raw = File(THERMAL_ZONE).readText().trim().toDouble()
        if (raw > 1000) raw / 1000.0 else raw
    } catch (e: Exception) {
        Double.NaN
    }

    /** Potencia do sinal Wi-Fi em dBm, ou null se nao houver conexao Wi-Fi. */
    @Suppress("DEPRECATION")
    fun wifiRssi(context: Context): Int? = try {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val rssi = wm.connectionInfo.rssi
        if (rssi == -127 || rssi == 0) null else rssi
    } catch (e: Exception) {
        null
    }

    /** Nome da rede Wi-Fi conectada, quando disponivel. */
    @Suppress("DEPRECATION")
    fun wifiSsid(context: Context): String? = try {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wm.connectionInfo.ssid?.trim('"')?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
    } catch (e: Exception) {
        null
    }
}
