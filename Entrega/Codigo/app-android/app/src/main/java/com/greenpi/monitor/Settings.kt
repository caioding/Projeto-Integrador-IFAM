package com.greenpi.monitor

import android.content.Context
import androidx.core.content.edit

/**
 * Configuracoes persistidas em SharedPreferences.
 *
 * Nenhuma credencial e gravada no codigo-fonte: o host, o token de acesso do
 * dispositivo e as credenciais do painel sao informados em tempo de execucao,
 * na tela de configuracoes. Ver "config.example.properties" para os valores de
 * referencia usados no ambiente de desenvolvimento.
 */
class Settings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("greenpi", Context.MODE_PRIVATE)

    var host: String
        get() = prefs.getString("host", "") ?: ""
        set(v) = prefs.edit { putString("host", v.trim()) }

    var mqttPort: Int
        get() = prefs.getInt("mqtt_port", 1883)
        set(v) = prefs.edit { putInt("mqtt_port", v) }

    var httpPort: Int
        get() = prefs.getInt("http_port", 8080)
        set(v) = prefs.edit { putInt("http_port", v) }

    /** Token de acesso do dispositivo no ThingsBoard (usado como usuario MQTT). */
    var accessToken: String
        get() = prefs.getString("token", "") ?: ""
        set(v) = prefs.edit { putString("token", v.trim()) }

    /** Intervalo entre publicacoes, em segundos. */
    var intervalSeconds: Int
        get() = prefs.getInt("interval", 10)
        set(v) = prefs.edit { putInt("interval", v.coerceIn(2, 3600)) }

    /**
     * Fator k da compensacao termica. Quanto maior, menor a correcao aplicada.
     * Deve ser calibrado comparando a leitura com um termometro de referencia.
     */
    var thermalFactor: Double
        // Arredondado na leitura: guardado como Float, a conversao para Double
        // produziria valores como 1.600000023841858 na tela de configuracoes.
        get() = Math.round(prefs.getFloat("k", 1.6f) * 100.0) / 100.0
        set(v) = prefs.edit { putFloat("k", v.toFloat()) }

    /** Nome do dispositivo no ThingsBoard, usado pela tela de historico. */
    var deviceName: String
        get() = prefs.getString("device_name", "GreenPi-RPi5") ?: "GreenPi-RPi5"
        set(v) = prefs.edit { putString("device_name", v.trim()) }

    var tenantUser: String
        get() = prefs.getString("tb_user", "tenant@thingsboard.org") ?: ""
        set(v) = prefs.edit { putString("tb_user", v.trim()) }

    var tenantPassword: String
        get() = prefs.getString("tb_pass", "") ?: ""
        set(v) = prefs.edit { putString("tb_pass", v) }

    val isConfigured: Boolean
        get() = host.isNotEmpty() && accessToken.isNotEmpty()
}
