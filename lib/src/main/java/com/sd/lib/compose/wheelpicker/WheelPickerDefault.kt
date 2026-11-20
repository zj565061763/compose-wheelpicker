package com.sd.lib.compose.wheelpicker

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 垂直滚轮默认焦点指示器，在焦点区域上下边缘绘制分割线 */
@Composable
fun WheelPickerFocusVertical(
  modifier: Modifier = Modifier,
  thickness: Dp = 1.dp,
  color: Color = defaultDividerColor(),
) {
  WheelPickerFocusIndicator(
    modifier = modifier,
    orientation = WheelOrientation.Vertical,
    thickness = thickness,
    color = color,
  )
}

/** 水平滚轮默认焦点指示器，在焦点区域左右边缘绘制分割线 */
@Composable
fun WheelPickerFocusHorizontal(
  modifier: Modifier = Modifier,
  thickness: Dp = 1.dp,
  color: Color = defaultDividerColor(),
) {
  WheelPickerFocusIndicator(
    modifier = modifier,
    orientation = WheelOrientation.Horizontal,
    thickness = thickness,
    color = color,
  )
}

/** 按滚轮方向在焦点区域两侧边缘绘制分割线 */
@Composable
private fun WheelPickerFocusIndicator(
  modifier: Modifier = Modifier,
  orientation: WheelOrientation,
  thickness: Dp,
  color: Color,
) {
  Spacer(
    modifier = modifier
      .fillMaxSize()
      .drawBehind {
        if (!thickness.value.isFinite() || thickness < 0.dp || color.alpha <= 0f) {
          return@drawBehind
        }
        val strokePx = if (thickness == Dp.Hairline) {
          1f
        } else {
          thickness.toPx()
        }
        if (strokePx <= 0f) {
          return@drawBehind
        }
        when (orientation) {
          WheelOrientation.Vertical -> {
            val lineHeight = strokePx.coerceAtMost(size.height)
            if (lineHeight > 0f && size.width > 0f) {
              val lineSize = Size(width = size.width, height = lineHeight)
              drawRect(
                color = color,
                topLeft = Offset.Zero,
                size = lineSize,
              )
              drawRect(
                color = color,
                topLeft = Offset(x = 0f, y = size.height - lineHeight),
                size = lineSize,
              )
            }
          }

          WheelOrientation.Horizontal -> {
            val lineWidth = strokePx.coerceAtMost(size.width)
            if (lineWidth > 0f && size.height > 0f) {
              val lineSize = Size(width = lineWidth, height = size.height)
              drawRect(
                color = color,
                topLeft = Offset.Zero,
                size = lineSize,
              )
              drawRect(
                color = color,
                topLeft = Offset(x = size.width - lineWidth, y = 0f),
                size = lineSize,
              )
            }
          }
        }
      },
  )
}

@Composable
private fun defaultDividerColor(): Color {
  val baseColor = if (isSystemInDarkTheme()) Color.White else Color.Black
  return baseColor.copy(alpha = 0.2f)
}
