package com.greenpi.monitor

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.greenpi.monitor.databinding.ActivityHistoryBinding
import kotlin.concurrent.thread

/**
 * Historico do ambiente, consultado na API REST do ThingsBoard.
 *
 * Demonstra o caminho de volta dos dados: o que subiu por MQTT e foi persistido
 * pela plataforma retorna ao aplicativo por HTTP, autenticado com JWT.
 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var settings: Settings

    /** Janela de consulta: ultimas 3 horas. */
    private val windowMillis = 3 * 60 * 60 * 1000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = Settings(this)

        binding.btnReload.setOnClickListener { load() }
        load()
    }

    private fun load() {
        if (settings.host.isEmpty()) {
            binding.tvHistoryStatus.text = "Configure o host do ThingsBoard antes de consultar o historico."
            return
        }
        binding.tvHistoryStatus.text = "Consultando a plataforma..."
        binding.btnReload.isEnabled = false

        thread {
            val result = runCatching {
                val api = ThingsBoardRest(settings.host, settings.httpPort)
                api.login(settings.tenantUser, settings.tenantPassword)
                val deviceId = api.findDeviceId(settings.deviceName)
                val now = System.currentTimeMillis()
                api.timeseries(
                    deviceId,
                    listOf("temperature", "humidity", "light", "pressure"),
                    now - windowMillis,
                    now
                )
            }
            runOnUiThread {
                binding.btnReload.isEnabled = true
                result.fold(
                    onSuccess = { series -> renderCharts(series) },
                    onFailure = { e ->
                        binding.tvHistoryStatus.text =
                            "Falha ao consultar: ${e.javaClass.simpleName} - ${e.message}"
                    }
                )
            }
        }
    }

    private fun renderCharts(series: Map<String, List<Pair<Long, Double>>>) {
        val total = series.values.sumOf { it.size }
        binding.tvHistoryStatus.text = if (total == 0) {
            "Nenhum dado nas ultimas 3 horas. Inicie a coleta na Raspberry Pi."
        } else {
            "Ultimas 3 horas - $total pontos recebidos de ${settings.deviceName}"
        }

        fun color(res: Int) = ContextCompat.getColor(this, res)
        binding.chartTemp.setData(series["temperature"].orEmpty(), color(R.color.chart_temp))
        binding.chartHum.setData(series["humidity"].orEmpty(), color(R.color.chart_hum))
        binding.chartLight.setData(series["light"].orEmpty(), color(R.color.chart_light))
        binding.chartPress.setData(series["pressure"].orEmpty(), color(R.color.chart_press))

        // O valor mais recente vai no titulo da secao, e nao sobre a curva.
        title(binding.tvTitleTemp, "Temperatura", binding.chartTemp.lastValue(), "graus C", 1)
        title(binding.tvTitleHum, "Umidade", binding.chartHum.lastValue(), "%rH", 0)
        title(binding.tvTitleLight, "Luminosidade", binding.chartLight.lastValue(), "contagens", 0)
        title(binding.tvTitlePress, "Pressao", binding.chartPress.lastValue(), "hPa", 1)
    }

    /**
     * Monta o titulo da secao com o valor mais recente.
     *
     * O numero e formatado em separado e so depois concatenado: unidades como
     * "%rH" contem o caractere '%' e quebrariam String.format se fizessem parte
     * da string de formato.
     */
    private fun title(view: android.widget.TextView, name: String, value: Double?, unit: String, decimals: Int) {
        view.text = if (value == null) {
            "$name ($unit)"
        } else {
            val formatted = "%.${decimals}f".format(value)
            "$name ($unit)  -  atual: $formatted"
        }
    }
}
