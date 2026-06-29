package com.wildtrail.app.ui.components

import android.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.wildtrail.app.domain.model.GeoPoint
import com.wildtrail.app.util.HikeMath

@Composable
fun ElevationChart(points: List<GeoPoint>) {
    val series = remember(points) { buildElevationSeries(points) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        if (series.size < 2) {
            Text(
                "No elevation data recorded for this hike.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            return@Card
        }
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            factory = { ctx ->
                LineChart(ctx).apply {
                    description.isEnabled = false
                    setNoDataText("")
                    legend.isEnabled = false
                    axisRight.isEnabled = false
                    setTouchEnabled(true)
                    isDragEnabled = true
                    setScaleEnabled(true)
                    setPinchZoom(true)
                    xAxis.position = XAxis.XAxisPosition.BOTTOM
                    xAxis.setDrawGridLines(false)
                    xAxis.valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String =
                            "%.1f km".format(value)
                    }
                    axisLeft.valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String =
                            "${value.toInt()} m"
                    }
                }
            },
            update = { chart ->
                val entries = series.map { Entry(it.first, it.second) }
                val ds = LineDataSet(entries, "Elevation").apply {
                    color = Color.parseColor("#2E5D3A")
                    fillColor = Color.parseColor("#7DA66D")
                    setDrawCircles(false)
                    setDrawValues(false)
                    setDrawFilled(true)
                    lineWidth = 2f
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                }
                chart.data = LineData(ds)
                chart.invalidate()
            },
        )
    }
}

private fun buildElevationSeries(points: List<GeoPoint>): List<Pair<Float, Float>> {
    if (points.size < 2) return emptyList()
    val out = ArrayList<Pair<Float, Float>>(points.size)
    var cumDistKm = 0.0
    for (i in points.indices) {
        if (i > 0) {
            cumDistKm += HikeMath.haversineMeters(points[i - 1], points[i]) / 1000.0
        }
        val altM = points[i].altitudeM
        if (altM != null) {
            out += cumDistKm.toFloat() to altM.toFloat()
        }
    }
    return out
}
