package com.sd.lib.compose.wheelpicker

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter

/** 创建并记住[WheelPickerState]，[initialIndex]为首次创建时的初始索引，配置变更后恢复已保存的位置 */
@Composable
fun rememberWheelPickerState(initialIndex: Int = 0): WheelPickerState {
  return rememberSaveable(saver = WheelPickerState.Saver) {
    WheelPickerState(initialIndex = initialIndex)
  }
}

/** 监听吸附索引变化，仅在[WheelPickerState.snappedIndex]有效（>= 0）时回调[block] */
@Composable
fun WheelPickerState.SnappedIndex(block: (Int) -> Unit) {
  val blockState = rememberUpdatedState(block)
  LaunchedEffect(this) {
    snapshotFlow { snappedIndex }
      .filter { it >= 0 }
      .collect { blockState.value(it) }
  }
}

/**
 * 滚轮状态，同一个实例同时只能绑定一个滚轮组件
 *
 * @param initialIndex 首次创建时的初始索引
 */
@Stable
class WheelPickerState(
  initialIndex: Int = 0,
) {
  internal val lazyListState = LazyListState(firstVisibleItemIndex = initialIndex.coerceAtLeast(0))

  private val _store = WheelPositionStore(
    initialIndex = initialIndex,
    lazyListState = lazyListState,
  )
  private val _snapObserver = WheelSnapObserver(
    store = _store,
    lazyListState = lazyListState,
  )

  /** 当前位于中心焦点区域的条目索引，滚动过程中实时变化，未挂载或无数据时为-1 */
  val currentIndex: Int
    get() {
      return _store.currentIndex
    }

  /** 当前停稳吸附的条目索引，未挂载或无数据时为-1，滚动过程中保持上一次吸附值并在停止后更新 */
  val snappedIndex: Int
    get() {
      return _store.snappedIndex
    }

  /** 是否正在滚动 */
  val isScrollInProgress: Boolean
    get() {
      return lazyListState.isScrollInProgress
    }

  /** 交互事件源 */
  val interactionSource: InteractionSource
    get() {
      return lazyListState.interactionSource
    }

  /** 动画滚动到[index]，超出当前有效范围时自动限制在边界内；未挂载或空数据时记录目标并在后续数据就绪时生效 */
  suspend fun animateScrollToIndex(index: Int) {
    scrollToIndex(index = index, animate = true)
  }

  /** 立即跳转到[index]，超出当前有效范围时自动限制在边界内；未挂载或空数据时记录目标并在后续数据就绪时生效 */
  suspend fun scrollToIndex(index: Int) {
    scrollToIndex(index = index, animate = false)
  }

  /** 计算[index]相对中心焦点的视觉偏移比例，范围[-1f, 1f]，0f表示居中，负值偏向视口上侧或左侧，正值偏向视口下侧或右侧，未挂载或无数据时返回null */
  fun scrollPercentageOf(index: Int): Float? {
    return _store.scrollPercentageOf(index)
  }

  internal fun updateViewportUnfocusedCount(unfocusedCount: Int) {
    _store.updateViewportUnfocusedCount(unfocusedCount)
  }

  internal fun updateAttachment(
    count: Int,
    unfocusedCount: Int,
    itemSizePx: Int,
    reverseVisualDirection: Boolean = false,
  ) {
    _store.updateAttachment(
      count = count,
      unfocusedCount = unfocusedCount,
      itemSizePx = itemSizePx,
      reverseVisualDirection = reverseVisualDirection,
    )
  }

  internal fun detach() {
    _store.detach()
  }

  internal suspend fun observeSnappedIndex() {
    _snapObserver.observe()
  }

  /** 执行跳转到[index]，未挂载或空数据时直接记录目标而不挂起 */
  private suspend fun scrollToIndex(
    index: Int,
    animate: Boolean,
  ) {
    val currentAttachment = _store.attachment
    if (currentAttachment == null || currentAttachment.count <= 0) {
      val requestedIndex = index.coerceAtLeast(0)
      _store.lastKnownIndex = requestedIndex
      lazyListState.requestScrollToItem(index = requestedIndex, scrollOffset = 0)
      return
    }

    val targetIndex = index.coerceIn(0, currentAttachment.count - 1)
    _store.lastKnownIndex = targetIndex
    if (animate) {
      lazyListState.animateScrollToItem(targetIndex)
    } else {
      lazyListState.scrollToItem(targetIndex)
    }
    _store.settleCurrentIndex()
  }

  companion object {
    /** [WheelPickerState]状态保存与恢复器 */
    val Saver: Saver<WheelPickerState, Int> = Saver(
      save = { state -> state._store.saveIndex() },
      restore = { WheelPickerState(initialIndex = it) },
    )
  }
}
