package com.greenpi.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import kotlin.concurrent.thread

/**
 * Servico de primeiro plano responsavel pelo ciclo de coleta e publicacao.
 *
 * Roda numa thread propria, separada da interface, e mantem um WakeLock parcial
 * para que a coleta continue mesmo com a tela apagada - um exemplo direto dos
 * conceitos de processos, threads e gerenciamento de recursos vistos no modulo
 * de Sistemas Operacionais.
 */
class CollectorService : Service() {

    companion object {
        private const val TAG = "CollectorService"
        private const val CHANNEL_ID = "greenpi_collector"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val i = Intent(context, CollectorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CollectorService::class.java))
        }
    }

    @Volatile private var running = false
    private var worker: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running) return START_STICKY
        running = true
        startForeground(NOTIFICATION_ID, buildNotification("Iniciando coleta..."))

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GreenPi::Collector").apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L)
        }

        Repository.update { it.copy(running = true, lastError = null) }
        worker = thread(name = "greenpi-collector") { loop() }
        return START_STICKY
    }

    /** Laco principal: le os sensores, aplica a compensacao e publica. */
    private fun loop() {
        val settings = Settings(this)
        val senseHat = SenseHat()
        val hasSensors = senseHat.open()
        if (!hasSensors) {
            Repository.update { it.copy(lastError = "Sense HAT nao encontrado neste dispositivo") }
        }

        val mqtt = ThingsBoardMqtt(settings.host, settings.mqttPort, settings.accessToken)

        while (running) {
            val startedAt = System.currentTimeMillis()
            try {
                val cpuTemp = SystemStats.cpuTemperature()
                val rssi = SystemStats.wifiRssi(this)
                val raw = senseHat.read()

                if (raw != null) {
                    val compensated = ThermalCompensation.apply(
                        raw.temperatureHts, cpuTemp, settings.thermalFactor
                    )
                    val sample = EnvironmentSample(
                        temperature = compensated,
                        temperatureRaw = raw.temperatureHts,
                        humidity = raw.humidity,
                        pressure = raw.pressure,
                        light = raw.light,
                        cpuTemperature = cpuTemp,
                        wifiRssi = rssi
                    )
                    Repository.update { it.copy(sample = sample) }

                    if (!mqtt.isConnected) mqtt.connect()
                    mqtt.publish(sample.toJson())

                    Repository.update {
                        it.copy(
                            mqttConnected = true,
                            publishCount = it.publishCount + 1,
                            lastError = null
                        )
                    }
                    updateNotification(sample)
                } else {
                    Repository.update { it.copy(lastError = "Falha ao ler os sensores") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro no ciclo de coleta", e)
                Repository.update {
                    it.copy(
                        mqttConnected = false,
                        lastError = "${e.javaClass.simpleName}: ${e.message ?: "erro desconhecido"}"
                    )
                }
                try { mqtt.disconnect() } catch (_: Exception) {}
            }

            // Mantem o periodo estavel, descontando o tempo gasto no ciclo.
            val elapsed = System.currentTimeMillis() - startedAt
            val sleepMs = settings.intervalSeconds * 1000L - elapsed
            if (sleepMs > 0) {
                try { Thread.sleep(sleepMs) } catch (e: InterruptedException) { break }
            }
        }

        mqtt.disconnect()
        senseHat.close()
    }

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        Repository.update { it.copy(running = false, mqttConnected = false) }
        super.onDestroy()
    }

    // ------------------------------------------------------------ Notificacao

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Coleta GreenPi",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Coleta e envio da telemetria ambiental" }
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("GreenPi Monitor")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(intent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(s: EnvironmentSample) {
        val text = "%.1f C  %.0f%%rH  luz %d  [%s]".format(
            s.temperature, s.humidity, s.light, s.status().label
        )
        (getSystemService(NotificationManager::class.java))
            .notify(NOTIFICATION_ID, buildNotification(text))
    }
}
