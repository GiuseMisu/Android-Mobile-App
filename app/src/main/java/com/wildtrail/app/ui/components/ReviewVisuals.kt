package com.wildtrail.app.ui.components

import android.graphics.LinearGradient
import android.graphics.Shader
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

@Composable
fun AuroraHeader(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    colors: List<Color> = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.primary,
    ),
    content: @Composable BoxScope.() -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "phase",
    )
    val angle by transition.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "angle",
    )

    Box(modifier = modifier.clip(shape)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val shift = (phase - 0.5f) * w * 0.8f
            withTransform({
                rotate(degrees = angle, pivot = center)
                scale(scaleX = 1.35f, scaleY = 1.35f, pivot = center)
                translate(left = shift, top = 0f)
            }) {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = colors,
                        start = Offset(0f, 0f),
                        end = Offset(w * 0.55f, h),
                        tileMode = TileMode.Mirror,
                    ),
                    size = Size(w, h),
                )
            }
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.10f + 0.16f * phase),
                        Color.Transparent,
                    ),
                    center = Offset(w * (0.25f + 0.5f * phase), h * 0.28f),
                    radius = w * 0.75f,
                ),
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)),
                    startY = h * 0.45f,
                    endY = h,
                ),
            )
        }
        content()
    }
}

@Composable
fun RatingGauge(
    rating: Float,
    modifier: Modifier = Modifier,
    max: Int = 5,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    arcColors: List<Color> = listOf(
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
    ),
    numberColors: List<Color> = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
    ),
) {
    val animated by animateFloatAsState(
        targetValue = rating.coerceIn(0f, max.toFloat()),
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "gaugeFill",
    )
    val startAngle = 135f
    val totalSweep = 270f
    val numberArgbTop = numberColors.first().toArgb()
    val numberArgbBottom = numberColors.last().toArgb()
    val label = "%.1f".format(animated)

    Canvas(modifier = modifier) {
        val strokeW = size.minDimension * 0.13f
        val inset = strokeW / 2f
        val arcSize = Size(size.width - strokeW, size.height - strokeW)
        val topLeft = Offset(inset, inset)

        drawArc(
            color = trackColor,
            startAngle = startAngle,
            sweepAngle = totalSweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
        val frac = (animated / max.toFloat()).coerceIn(0f, 1f)
        if (frac > 0f) {
            drawArc(
                brush = Brush.sweepGradient(colors = arcColors, center = center),
                startAngle = startAngle,
                sweepAngle = totalSweep * frac,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }

        drawIntoCanvas { canvas ->
            val numberPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = size.minDimension * 0.34f
                isFakeBoldText = true
                shader = LinearGradient(
                    0f, center.y - size.minDimension * 0.18f,
                    0f, center.y + size.minDimension * 0.18f,
                    numberArgbTop, numberArgbBottom,
                    Shader.TileMode.CLAMP,
                )
            }
            val fm = numberPaint.fontMetrics
            val baseline = center.y - (fm.ascent + fm.descent) / 2f
            canvas.nativeCanvas.drawText(label, center.x, baseline, numberPaint)
        }
    }
}

@Composable
fun FullScreenPhotoViewer(
    imageUrls: List<String>,
    startIndex: Int,
    onDismiss: () -> Unit,
) {
    if (imageUrls.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, imageUrls.lastIndex),
        pageCount = { imageUrls.size },
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f)),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                AsyncImage(
                    model = imageUrls[page],
                    contentDescription = "Review photo ${page + 1}",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 72.dp),
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${imageUrls.size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.16f)),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}
