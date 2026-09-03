package com.greenpi.monitor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * Grafico de linha minimalista, desenhado diretamente no Canvas.
 *
 * Evita uma dependencia externa de graficos e mantem o APK enxuto, mostrando o
 * uso de View customizada - conteudo do modulo de Android basico.
 */
class LineChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var points: List<Pair<Long, Double>> = emptyList()
    private var lineColor = Color.parseColor("#2E7D32")

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 1f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#616161")
        textSize = 26f
    }

    /**
     * Define a serie exibida. O valor mais recente nao e desenhado sobre o
     * grafico: ele aparece no titulo da secao, para nao encobrir a curva.
     */
    fun setData(data: List<Pair<Long, Double>>, color: Int) {
        points = data
        lineColor = color
        invalidate()
    }

    /** Ultimo valor da serie, ou null quando nao ha dados. */
    fun lastValue(): Double? = points.lastOrNull()?.second

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = 90f
        val right = width - 16f
        val top = 16f
        val bottom = height - 36f

        // Linhas de grade horizontais
        for (i in 0..4) {
            val y = top + (bottom - top) * i / 4f
            canvas.drawLine(left, y, right, y, gridPaint)
        }

        if (points.size < 2) {
            canvas.drawText("Sem dados no periodo", left, (top + bottom) / 2f, textPaint)
            return
        }

        val values = points.map { it.second }
        var minV = values.min()
        var maxV = values.max()
        if (maxV - minV < 1e-6) { minV -= 1.0; maxV += 1.0 }
        val range = maxV - minV

        // Rotulos do eixo vertical
        for (i in 0..4) {
            val y = top + (bottom - top) * i / 4f
            val v = maxV - range * i / 4.0
            canvas.drawText("%.1f".format(v), 4f, y + 9f, textPaint)
        }

        val tMin = points.first().first.toDouble()
        val tMax = points.last().first.toDouble()
        val tRange = (tMax - tMin).coerceAtLeast(1.0)

        fun px(t: Long) = left + ((t - tMin) / tRange * (right - left)).toFloat()
        fun py(v: Double) = bottom - ((v - minV) / range * (bottom - top)).toFloat()

        // Area sob a curva
        val area = Path().apply {
            moveTo(px(points.first().first), bottom)
            points.forEach { lineTo(px(it.first), py(it.second)) }
            lineTo(px(points.last().first), bottom)
            close()
        }
        fillPaint.color = (lineColor and 0x00FFFFFF) or 0x22000000
        canvas.drawPath(area, fillPaint)

        // Curva
        val path = Path()
        points.forEachIndexed { i, p ->
            if (i == 0) path.moveTo(px(p.first), py(p.second)) else path.lineTo(px(p.first), py(p.second))
        }
        linePaint.color = lineColor
        canvas.drawPath(path, linePaint)

    }
}
