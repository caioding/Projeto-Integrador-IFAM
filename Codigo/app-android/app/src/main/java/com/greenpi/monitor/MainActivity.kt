package com.greenpi.monitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.greenpi.monitor.databinding.ActivityMainBinding
import kotlin.concurrent.thread

/**
 * Tela principal: mostra a leitura corrente do ambiente e controla o servico de coleta.
 *
 * O mesmo APK atende os dois cenarios da demonstracao. Na Raspberry Pi 5, onde o
 * barramento /dev/i2c-1 existe, o app opera como coletor e como painel. Num
 * celular, onde nao ha Sense HAT, ele funciona apenas como painel, consultando o
 * historico armazenado no ThingsBoard.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: Settings
    private var hasSensors = false

    /** Ultima amostra obtida da plataforma quando o app roda sem sensores. */
    @Volatile private var remoteSample: EnvironmentSample? = null
    private var pollingThread: Thread? = null
    @Volatile private var polling = false

    private val observer: (Repository.State) -> Unit = { render(it) }

    /**
     * Redesenha a tela periodicamente mesmo sem dado novo.
     *
     * A deteccao de leitura obsoleta depende da passagem do tempo, nao da
     * chegada de uma amostra. Sem este ciclo, uma Pi desligada congelaria a
     * interface no ultimo render bem-sucedido - exatamente o que se quer evitar.
     */
    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            render(Repository.state)
            ticker.postDelayed(this, 5_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = Settings(this)
        hasSensors = SenseHat.isHardwareAvailable()

        binding.tvMode.text = if (hasSensors) {
            "Modo coletor + painel - Sense HAT detectado em ${SenseHat.I2C_BUS}"
        } else {
            "Modo painel - sem Sense HAT neste dispositivo"
        }

        binding.btnToggle.isEnabled = hasSensors

        binding.btnToggle.setOnClickListener { toggleCollection() }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        requestNotificationPermissionIfNeeded()
    }

    override fun onStart() {
        super.onStart()
        Repository.observe(observer)
        if (!hasSensors) startRemotePolling()
        ticker.post(tick)
    }

    override fun onStop() {
        ticker.removeCallbacks(tick)
        Repository.removeObserver(observer)
        stopRemotePolling()
        super.onStop()
    }

    /**
     * Sem Sense HAT, o app assume o papel de painel: consulta periodicamente os
     * ultimos valores publicados na plataforma pela Raspberry Pi.
     */
    private fun startRemotePolling() {
        if (polling || settings.host.isEmpty()) return
        polling = true
        pollingThread = thread(name = "greenpi-painel") {
            val api = ThingsBoardRest(settings.host, settings.httpPort)
            var deviceId: String? = null
            while (polling) {
                try {
                    if (deviceId == null) {
                        api.login(settings.tenantUser, settings.tenantPassword)
                        deviceId = api.findDeviceId(settings.deviceName)
                    }
                    val values = api.latestValues(
                        deviceId,
                        listOf("temperature", "temperature_raw", "humidity",
                               "pressure", "light", "cpu_temp", "wifi_rssi")
                    )
                    fun num(k: String) = values[k]?.toDoubleOrNull() ?: Double.NaN
                    // O timestamp vem da plataforma, nunca do relogio local: e ele
                    // que revela uma leitura parada no tempo quando a Pi desliga.
                    val sampledAt = values.values.maxOfOrNull { it.ts }
                    remoteSample = if (sampledAt == null) null else EnvironmentSample(
                        temperature = num("temperature"),
                        temperatureRaw = num("temperature_raw"),
                        humidity = num("humidity"),
                        pressure = num("pressure"),
                        light = values["light"]?.toDoubleOrNull()?.toInt() ?: 0,
                        cpuTemperature = num("cpu_temp"),
                        wifiRssi = values["wifi_rssi"]?.toDoubleOrNull()?.toInt(),
                        timestamp = sampledAt
                    )
                    runOnUiThread {
                        binding.tvError.visibility = android.view.View.GONE
                        render(Repository.state)
                    }
                } catch (e: Exception) {
                    deviceId = null
                    runOnUiThread {
                        binding.tvError.visibility = android.view.View.VISIBLE
                        binding.tvError.text =
                            "Falha ao consultar a plataforma: ${e.message ?: e.javaClass.simpleName}"
                    }
                }
                try { Thread.sleep(10_000) } catch (e: InterruptedException) { break }
            }
        }
    }

    private fun stopRemotePolling() {
        polling = false
        pollingThread?.interrupt()
        pollingThread = null
    }

    private fun toggleCollection() {
        if (Repository.state.running) {
            CollectorService.stop(this)
            return
        }
        if (!settings.isConfigured) {
            Snackbar.make(binding.root, "Configure o host e o token antes de iniciar", Snackbar.LENGTH_LONG)
                .setAction("Configurar") {
                    startActivity(Intent(this, SettingsActivity::class.java))
                }.show()
            return
        }
        CollectorService.start(this)
    }

    /** Reflete o estado do repositorio na interface. */
    private fun render(state: Repository.State) {
        binding.btnToggle.text = when {
            !hasSensors -> "Coleta indisponivel (sem sensores)"
            state.running -> "Parar coleta"
            else -> "Iniciar coleta"
        }

        val sample = state.sample ?: remoteSample
        if (sample != null) {
            // Uma leitura antiga nao descreve mais o ambiente. Ela continua na
            // tela como referencia, mas esmaecida e sob um aviso explicito -
            // caso contrario a Pi desligada passa por Pi funcionando.
            val stale = sample.isStale(settings.staleAfterMillis)

            binding.tvTemp.text = fmt(sample.temperature, 1)
            binding.tvTempSub.text = "graus C  (bruto ${fmt(sample.temperatureRaw, 1)})"
            binding.tvHum.text = fmt(sample.humidity, 0)
            binding.tvLight.text = sample.light.toString()
            binding.tvPress.text = fmt(sample.pressure, 0)
            binding.tvCpu.text = fmt(sample.cpuTemperature, 1)
            binding.tvRssi.text = sample.wifiRssi?.toString() ?: "--"

            if (stale) {
                tint(binding.tvTemp, EnvStatus.DESCONHECIDO)
                tint(binding.tvHum, EnvStatus.DESCONHECIDO)
                tint(binding.tvLight, EnvStatus.DESCONHECIDO)
            } else {
                tint(binding.tvTemp, EnvStatus.forTemperature(sample.temperature))
                tint(binding.tvHum, EnvStatus.forHumidity(sample.humidity))
                tint(binding.tvLight, EnvStatus.forLight(sample.light))
            }
            dim(stale)

            val status = if (stale) EnvStatus.DESCONHECIDO else sample.status()
            binding.tvStatus.text = if (stale) "SEM CONTATO" else "AMBIENTE ${status.label}"
            binding.tvStatusDetail.text = if (stale) {
                "Ultima leitura ha ${formatAge(sample.ageMillis())} - a Raspberry Pi " +
                        "pode estar desligada ou fora da rede"
            } else {
                describe(sample, status)
            }
            binding.cardStatus.setCardBackgroundColor(ContextCompat.getColor(this, colorOf(status)))
        }

        binding.tvMqtt.text = when {
            state.mqttConnected -> "Conectado a ${settings.host}:${settings.mqttPort} - ${state.publishCount} envios"
            state.running -> "Conectando a ${settings.host}:${settings.mqttPort}..."
            !hasSensors && remoteSample != null -> "Lendo de ${settings.host}:${settings.httpPort} (painel)"
            !hasSensors -> "Consultando ${settings.host}:${settings.httpPort}..."
            else -> "Desconectado"
        }

        if (state.lastError != null) {
            binding.tvError.visibility = android.view.View.VISIBLE
            binding.tvError.text = state.lastError
        } else if (hasSensors) {
            binding.tvError.visibility = android.view.View.GONE
        }
    }

    /** Explica, em texto, qual grandeza determinou o status geral. */
    private fun describe(s: EnvironmentSample, status: EnvStatus): String {
        if (status == EnvStatus.OK) return "Temperatura, umidade e luminosidade dentro da faixa ideal"
        val causes = buildList {
            if (EnvStatus.forTemperature(s.temperature).severity >= status.severity) add("temperatura")
            if (EnvStatus.forHumidity(s.humidity).severity >= status.severity) add("umidade")
            if (EnvStatus.forLight(s.light).severity >= status.severity) add("luminosidade")
        }
        return if (causes.isEmpty()) "Sem leitura valida" else "Fora da faixa ideal: " + causes.joinToString(", ")
    }

    private fun colorOf(status: EnvStatus) = when (status) {
        EnvStatus.OK -> R.color.status_ok
        EnvStatus.ATENCAO -> R.color.status_atencao
        EnvStatus.ALERTA -> R.color.status_alerta
        EnvStatus.DESCONHECIDO -> R.color.status_desconhecido
    }

    private fun tint(view: android.widget.TextView, status: EnvStatus) {
        view.setTextColor(ContextCompat.getColor(this, colorOf(status)))
    }

    /** Esmaece os numeros quando eles deixam de representar o estado atual. */
    private fun dim(stale: Boolean) {
        val alpha = if (stale) 0.45f else 1f
        listOf(
            binding.tvTemp, binding.tvTempSub, binding.tvHum, binding.tvLight,
            binding.tvPress, binding.tvCpu, binding.tvRssi
        ).forEach { it.alpha = alpha }
    }

    private fun fmt(v: Double, decimals: Int) =
        if (v.isNaN()) "--" else "%.${decimals}f".format(v)

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }
}
