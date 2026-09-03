package com.greenpi.monitor

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.greenpi.monitor.databinding.ActivitySettingsBinding
import kotlin.concurrent.thread

/** Tela de configuracao do servidor, das credenciais e dos parametros de coleta. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = Settings(this)

        binding.etHost.setText(settings.host)
        binding.etMqttPort.setText(settings.mqttPort.toString())
        binding.etHttpPort.setText(settings.httpPort.toString())
        binding.etToken.setText(settings.accessToken)
        binding.etDeviceName.setText(settings.deviceName)
        binding.etInterval.setText(settings.intervalSeconds.toString())
        binding.etFactor.setText(settings.thermalFactor.toString())
        binding.etTbUser.setText(settings.tenantUser)
        binding.etTbPass.setText(settings.tenantPassword)

        binding.btnSave.setOnClickListener {
            save()
            finish()
        }
        binding.btnTest.setOnClickListener { testConnection() }
    }

    private fun save() {
        settings.host = binding.etHost.text.toString()
        settings.mqttPort = binding.etMqttPort.text.toString().toIntOrNull() ?: 1883
        settings.httpPort = binding.etHttpPort.text.toString().toIntOrNull() ?: 8080
        settings.accessToken = binding.etToken.text.toString()
        settings.deviceName = binding.etDeviceName.text.toString()
        settings.intervalSeconds = binding.etInterval.text.toString().toIntOrNull() ?: 10
        settings.thermalFactor = binding.etFactor.text.toString().toDoubleOrNull() ?: 1.6
        settings.tenantUser = binding.etTbUser.text.toString()
        settings.tenantPassword = binding.etTbPass.text.toString()
    }

    /** Faz uma conexao MQTT de teste e publica uma amostra, sem bloquear a interface. */
    private fun testConnection() {
        save()
        binding.btnTest.isEnabled = false
        show("Testando...", R.color.text_secondary)

        thread {
            val result = runCatching {
                val mqtt = ThingsBoardMqtt(settings.host, settings.mqttPort, settings.accessToken)
                mqtt.connect()
                mqtt.publish("{\"connection_test\":1}")
                mqtt.disconnect()
            }
            runOnUiThread {
                binding.btnTest.isEnabled = true
                result.fold(
                    onSuccess = { show("Conexao bem-sucedida: telemetria de teste publicada.", R.color.status_ok) },
                    onFailure = { e -> show("Falha: ${e.javaClass.simpleName} - ${e.message}", R.color.status_alerta) }
                )
            }
        }
    }

    private fun show(text: String, colorRes: Int) {
        binding.tvTestResult.visibility = View.VISIBLE
        binding.tvTestResult.text = text
        binding.tvTestResult.setTextColor(ContextCompat.getColor(this, colorRes))
    }
}
