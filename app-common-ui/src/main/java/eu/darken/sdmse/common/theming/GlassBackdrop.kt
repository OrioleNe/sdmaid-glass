package eu.darken.sdmse.common.theming

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize

/**
 * Mô phỏng hiệu ứng "kính" (Liquid Glass) bằng cách làm mờ chính nội dung nằm phía sau một bề mặt.
 *
 * Cách dùng:
 *  1. `val backdrop = rememberGlassBackdrop()`
 *  2. Nội dung nằm phía sau (ví dụ danh sách cuộn): `Modifier.glassSource(backdrop)`
 *  3. Bề mặt kính (ví dụ thanh dưới): `Modifier.glassRegion(backdrop, shape, tint)`
 *
 * Phần kính được vẽ NGAY TRONG lượt vẽ của nguồn, nên luôn đồng bộ khi cuộn (không bị trễ khung hình).
 * Làm mờ thật chỉ có từ Android 12 (API 31); máy cũ hơn dùng lớp màu gần đặc.
 */
@Stable
class GlassBackdrop internal constructor(
    internal val sourceLayer: GraphicsLayer,
    internal val blurLayer: GraphicsLayer,
) {
    internal var sourceOrigin by mutableStateOf(Offset.Zero)

    internal var regionOrigin by mutableStateOf(Offset.Zero)
    internal var regionSize by mutableStateOf(Size.Zero)
    internal var regionShape by mutableStateOf<Shape?>(null)
    internal var regionTint by mutableStateOf(Color.Transparent)
    internal var regionBlur by mutableStateOf(24.dp)
    internal var regionActive by mutableStateOf(false)
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val sourceLayer = rememberGraphicsLayer()
    val blurLayer = rememberGraphicsLayer()
    return remember(sourceLayer, blurLayer) { GlassBackdrop(sourceLayer, blurLayer) }
}

/** Đánh dấu nội dung phía sau mà kính sẽ làm mờ. */
fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.sourceOrigin = it.positionInRoot() }
    .drawWithContent {
        backdrop.sourceLayer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.sourceLayer)
        if (backdrop.regionActive) drawGlassRegion(backdrop)
    }

/** Đánh dấu vùng này là bề mặt kính. Chỉ nên có một vùng cho mỗi [GlassBackdrop]. */
@Composable
fun Modifier.glassRegion(
    backdrop: GlassBackdrop,
    shape: Shape,
    tint: Color,
    blurRadius: Dp = 24.dp,
): Modifier {
    SideEffect {
        backdrop.regionShape = shape
        backdrop.regionTint = tint
        backdrop.regionBlur = blurRadius
    }
    DisposableEffect(backdrop) {
        backdrop.regionActive = true
        onDispose { backdrop.regionActive = false }
    }
    return this.onGloballyPositioned {
        backdrop.regionOrigin = it.positionInRoot()
        backdrop.regionSize = it.size.toSize()
    }
}

private fun DrawScope.drawGlassRegion(backdrop: GlassBackdrop) {
    val shape = backdrop.regionShape ?: return
    val regionSize = backdrop.regionSize
    if (regionSize.width <= 0f || regionSize.height <= 0f) return

    // Vị trí vùng kính theo toạ độ của nguồn.
    val topLeft = backdrop.regionOrigin - backdrop.sourceOrigin
    val path = shape.createOutline(regionSize, layoutDirection, this).toGlassPath()
    val tint = backdrop.regionTint

    translate(topLeft.x, topLeft.y) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurPx = backdrop.regionBlur.toPx()
            backdrop.blurLayer.renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
            backdrop.blurLayer.record(size = IntSize(regionSize.width.toInt(), regionSize.height.toInt())) {
                translate(-topLeft.x, -topLeft.y) { drawLayer(backdrop.sourceLayer) }
            }
            clipPath(path) { drawLayer(backdrop.blurLayer) }
            drawPath(path, tint)
        } else {
            // Không có làm mờ thật: dùng màu gần đặc để chữ vẫn dễ đọc.
            drawPath(path, tint.copy(alpha = 0.96f))
        }

        // Viền sáng mảnh, tạo cảm giác cạnh kính.
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.50f),
                    Color.White.copy(alpha = 0.08f),
                    Color.White.copy(alpha = 0.20f),
                ),
                startY = 0f,
                endY = regionSize.height,
            ),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

private fun Outline.toGlassPath(): Path = when (this) {
    is Outline.Generic -> path
    is Outline.Rectangle -> Path().apply { addRect(rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
}
