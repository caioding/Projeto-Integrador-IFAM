package com.greenpi.monitor

import android.util.Log
import java.io.File

/**
 * Leitura dos sensores ambientais do Raspberry Pi Sense HAT (rev. 2) atraves do
 * barramento I2C, usando a biblioteca nativa [sensehat] (ver src/main/cpp).
 *
 * Chips utilizados (enderecos confirmados com "i2cdetect -y 1" no kit):
 *   0x5F  HTS221   - umidade relativa e temperatura   (WHO_AM_I = 0xBC)
 *   0x5C  LPS25H   - pressao barometrica e temperatura (WHO_AM_I = 0xBD)
 *   0x39  TCS3400  - luminosidade e cor (RGBC)         (ID       = 0x90)
 *
 * Nos sensores da ST o bit 7 do endereco de sub-registrador habilita o
 * auto-incremento, permitindo ler varios registradores em uma unica transacao.
 */
class SenseHat {

    companion object {
        private const val TAG = "SenseHat"
        const val I2C_BUS = "/dev/i2c-1"

        // Enderecos I2C
        private const val ADDR_HTS221 = 0x5F
        private const val ADDR_LPS25H = 0x5C
        private const val ADDR_TCS3400 = 0x39

        // Identificacao dos chips
        private const val WHOAMI_HTS221 = 0xBC
        private const val WHOAMI_LPS25H = 0xBD
        private const val ID_TCS3400 = 0x90

        init {
            System.loadLibrary("sensehat")
        }

        /** Indica se o hardware esta presente (a Pi 5 tem o barramento; o celular nao). */
        fun isHardwareAvailable(): Boolean = File(I2C_BUS).exists()
    }

    private external fun nativeOpen(path: String): Int
    private external fun nativeClose(fd: Int)
    private external fun nativeReadBlock(fd: Int, addr: Int, reg: Int, len: Int): ByteArray?
    private external fun nativeWriteReg(fd: Int, addr: Int, reg: Int, value: Int): Int

    private var fd: Int = -1

    /** Coeficientes de calibracao de fabrica do HTS221, lidos uma unica vez. */
    private var h0Rh = 0.0
    private var h1Rh = 0.0
    private var t0DegC = 0.0
    private var t1DegC = 0.0
    private var h0T0Out = 0
    private var h1T0Out = 0
    private var t0Out = 0
    private var t1Out = 0
    private var calibrated = false

    /**
     * Abre o barramento e inicializa os tres sensores.
     * @return true se o barramento foi aberto e ao menos um sensor respondeu.
     */
    fun open(): Boolean {
        if (fd >= 0) return true
        if (!isHardwareAvailable()) {
            Log.i(TAG, "$I2C_BUS nao existe - executando sem Sense HAT")
            return false
        }
        fd = nativeOpen(I2C_BUS)
        if (fd < 0) {
            Log.e(TAG, "Nao foi possivel abrir $I2C_BUS")
            return false
        }
        val ok = initHts221() and initLps25h() and initTcs3400()
        if (!ok) Log.w(TAG, "Um ou mais sensores nao responderam ao WHO_AM_I")
        return true
    }

    fun close() {
        if (fd >= 0) {
            nativeClose(fd)
            fd = -1
            calibrated = false
        }
    }

    // ------------------------------------------------------------------ HTS221

    private fun initHts221(): Boolean {
        val who = readReg(ADDR_HTS221, 0x0F) ?: return false
        if (who != WHOAMI_HTS221) {
            Log.e(TAG, "HTS221 WHO_AM_I inesperado: 0x%02X".format(who))
            return false
        }
        // AV_CONF: media de 32 amostras de umidade e 16 de temperatura (reduz ruido).
        nativeWriteReg(fd, ADDR_HTS221, 0x10, 0x1B)
        // CTRL_REG1: PD=1 (ligado), BDU=1 (leitura consistente), ODR=1 Hz.
        nativeWriteReg(fd, ADDR_HTS221, 0x20, 0x85)
        readHts221Calibration()
        return true
    }

    /**
     * Le os 16 bytes de calibracao (0x30..0x3F) gravados de fabrica no chip.
     * O HTS221 nao entrega valores em unidades de engenharia: e preciso
     * interpolar linearmente entre dois pontos de referencia.
     */
    private fun readHts221Calibration() {
        val c = nativeReadBlock(fd, ADDR_HTS221, 0x80 or 0x30, 16) ?: return
        fun u(i: Int) = c[i].toInt() and 0xFF
        fun s16(lo: Int, hi: Int): Int {
            val v = (u(hi) shl 8) or u(lo)
            return if (v > 32767) v - 65536 else v
        }
        h0Rh = u(0) / 2.0
        h1Rh = u(1) / 2.0
        val t1t0msb = u(5)
        t0DegC = (((t1t0msb and 0x03) shl 8) or u(2)) / 8.0
        t1DegC = (((t1t0msb and 0x0C) shl 6) or u(3)) / 8.0
        h0T0Out = s16(6, 7)
        h1T0Out = s16(10, 11)
        t0Out = s16(12, 13)
        t1Out = s16(14, 15)
        calibrated = h1T0Out != h0T0Out && t1Out != t0Out
        Log.i(TAG, "Calibracao HTS221: H0=$h0Rh H1=$h1Rh T0=$t0DegC T1=$t1DegC")
    }

    // ------------------------------------------------------------------ LPS25H

    private fun initLps25h(): Boolean {
        val who = readReg(ADDR_LPS25H, 0x0F) ?: return false
        if (who != WHOAMI_LPS25H) {
            Log.e(TAG, "LPS25H WHO_AM_I inesperado: 0x%02X".format(who))
            return false
        }
        // CTRL_REG1: PD=1 (ligado), ODR=1 Hz, BDU=1.
        nativeWriteReg(fd, ADDR_LPS25H, 0x20, 0x94)
        return true
    }

    // ----------------------------------------------------------------- TCS3400

    private fun initTcs3400(): Boolean {
        // No TCS3400 todo acesso exige o bit 7 (COMMAND) ligado no endereco.
        val id = readReg(ADDR_TCS3400, 0x80 or 0x12) ?: return false
        if (id != ID_TCS3400) {
            Log.e(TAG, "TCS3400 ID inesperado: 0x%02X".format(id))
            return false
        }
        nativeWriteReg(fd, ADDR_TCS3400, 0x81, 0xC0)       // ATIME: ~178 ms de integracao
        nativeWriteReg(fd, ADDR_TCS3400, 0x8F, 0x02)       // CONTROL: ganho 16x (ambiente interno)
        nativeWriteReg(fd, ADDR_TCS3400, 0x80, 0x03)       // ENABLE: PON + AEN
        return true
    }

    // -------------------------------------------------------------- Leitura

    /** Le todos os sensores. Retorna null se o barramento nao estiver aberto. */
    fun read(): SensorReading? {
        if (fd < 0) return null

        // HTS221: HUMIDITY_OUT (0x28..0x29) e TEMP_OUT (0x2A..0x2B).
        var humidity = Double.NaN
        var tempHts = Double.NaN
        nativeReadBlock(fd, ADDR_HTS221, 0x80 or 0x28, 4)?.let { d ->
            if (calibrated) {
                val hOut = le16(d, 0)
                val tOut = le16(d, 2)
                humidity = h0Rh + (h1Rh - h0Rh) * (hOut - h0T0Out) / (h1T0Out - h0T0Out).toDouble()
                tempHts = t0DegC + (t1DegC - t0DegC) * (tOut - t0Out) / (t1Out - t0Out).toDouble()
                humidity = humidity.coerceIn(0.0, 100.0)
            }
        }

        // LPS25H: PRESS_OUT (0x28..0x2A, 24 bits) e TEMP_OUT (0x2B..0x2C).
        var pressure = Double.NaN
        var tempLps = Double.NaN
        nativeReadBlock(fd, ADDR_LPS25H, 0x80 or 0x28, 5)?.let { d ->
            fun u(i: Int) = d[i].toInt() and 0xFF
            var raw = (u(2) shl 16) or (u(1) shl 8) or u(0)
            if (raw > 0x7FFFFF) raw -= 0x1000000
            pressure = raw / 4096.0                     // hPa
            tempLps = 42.5 + le16(d, 3) / 480.0         // graus Celsius
        }

        // TCS3400: CDATA, RDATA, GDATA, BDATA (0x14..0x1B, auto-incremento).
        var clear = 0
        var red = 0
        var green = 0
        var blue = 0
        nativeReadBlock(fd, ADDR_TCS3400, 0x80 or 0x14, 8)?.let { d ->
            clear = le16u(d, 0); red = le16u(d, 2); green = le16u(d, 4); blue = le16u(d, 6)
        }

        return SensorReading(
            humidity = humidity,
            temperatureHts = tempHts,
            temperatureLps = tempLps,
            pressure = pressure,
            light = clear,
            red = red, green = green, blue = blue
        )
    }

    // -------------------------------------------------------------- Utilitarios

    private fun readReg(addr: Int, reg: Int): Int? =
        nativeReadBlock(fd, addr, reg, 1)?.let { it[0].toInt() and 0xFF }

    private fun le16(d: ByteArray, i: Int): Int {
        val v = ((d[i + 1].toInt() and 0xFF) shl 8) or (d[i].toInt() and 0xFF)
        return if (v > 32767) v - 65536 else v
    }

    private fun le16u(d: ByteArray, i: Int): Int =
        ((d[i + 1].toInt() and 0xFF) shl 8) or (d[i].toInt() and 0xFF)
}

/** Valores brutos lidos do Sense HAT, ainda sem compensacao termica. */
data class SensorReading(
    val humidity: Double,
    val temperatureHts: Double,
    val temperatureLps: Double,
    val pressure: Double,
    val light: Int,
    val red: Int,
    val green: Int,
    val blue: Int
)
