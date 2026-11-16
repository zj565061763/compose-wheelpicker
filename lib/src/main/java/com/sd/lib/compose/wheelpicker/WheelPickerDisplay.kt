package com.sd.lib.compose.wheelpicker

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs

/** 滚轮条目展示作用域 */
@Stable
interface WheelPickerDisplayScope {
  /** 当前滚轮状态 */
  val state: WheelPickerState

  /** 渲染[index]对应的条目内容 */
  @Composable
  fun Content(index: Int)
}

/** 默认条目展示效果，随偏离中心程度缩小并降低透明度 */
@Composable
fun WheelPickerDisplayScope.DefaultWheelPickerDisplay(
  index: Int,
) {
  Box(
    modifier = Modifier.graphicsLayer {
      val percentage = state.scrollPercentageOf(index)
      val factor = if (percentage != null) {
        (1f - abs(percentage)).coerceIn(0.5f, 1f)
      } else {
        0f
      }
      alpha = factor
      scaleX = factor
      scaleY = factor
    },
    contentAlignment = Alignment.Center,
  ) {
    Content(index)
  }
}

@Composable
internal fun rememberWheelPickerDisplayHolder(
  state: WheelPickerState,
  display: @Composable WheelPickerDisplayScope.(index: Int) -> Unit,
  content: @Composable (index: Int) -> Unit,
): WheelPickerDisplayHolder {
  val displayState = rememberUpdatedState(display)
  val contentState = rememberUpdatedState(content)
  return remember(state, displayState, contentState) {
    WheelPickerDisplayHolder(
      state = state,
      displayState = displayState,
      contentState = contentState,
    )
  }
}

@Stable
internal class WheelPickerDisplayHolder(
  override val state: WheelPickerState,
  private val displayState: State<@Composable WheelPickerDisplayScope.(index: Int) -> Unit>,
  private val contentState: State<@Composable (index: Int) -> Unit>,
) : WheelPickerDisplayScope {
  @Composable
  fun Display(index: Int) {
    displayState.value.invoke(this, index)
  }

  @Composable
  override fun Content(index: Int) {
    contentState.value.invoke(index)
  }
}
