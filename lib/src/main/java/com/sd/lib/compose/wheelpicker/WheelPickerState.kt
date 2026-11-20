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

/** 滚轮状态，同一个实例同时只能绑定一个滚轮组件 */
@Stable
class WheelPickerState internal constructor(
  initialIndex: Int,
  scrollController: WheelScrollController?,
) {
  /** 创建滚轮状态，[initialIndex]为首次创建时的初始索引 */
  constructor(initialIndex: Int = 0) : this(
    initialIndex = initialIndex,
    scrollController = null,
  )

  internal val lazyListState = LazyListState(firstVisibleItemIndex = initialIndex.coerceAtLeast(0))

  private val _scrollController: WheelScrollController = scrollController ?: LazyListWheelScrollController(
    lazyListState = lazyListState,
  )
  private val _store = WheelPositionStore(
    initialIndex = initialIndex,
    scrollController = _scrollController,
  )
  private val _scrollExecutor = WheelScrollExecutor(
    store = _store,
    scrollController = _scrollController,
  )
  private val _snapObserver = WheelSnapObserver(
    store = _store,
    scrollExecutor = _scrollExecutor,
    scrollController = _scrollController,
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
      return _scrollController.isScrollInProgress
    }

  /** 交互事件源 */
  val interactionSource: InteractionSource
    get() {
      return _scrollController.interactionSource ?: lazyListState.interactionSource
    }

  /** 动画滚动到[index]，超出当前有效范围时自动限制在边界内；未挂载或空数据时记录目标并在后续数据就绪时生效 */
  suspend fun animateScrollToIndex(index: Int) {
    _scrollExecutor.scrollToIndex(index = index, animate = true)
  }

  /** 立即跳转到[index]，超出当前有效范围时自动限制在边界内；未挂载或空数据时记录目标并在后续数据就绪时生效 */
  suspend fun scrollToIndex(index: Int) {
    _scrollExecutor.scrollToIndex(index = index, animate = false)
  }

  /** 计算[index]相对中心焦点的视觉偏移比例，范围[-1f, 1f]，0f表示居中，负值偏向视口上侧或左侧，正值偏向视口下侧或右侧，未挂载或无数据时返回null */
  fun scrollPercentageOf(index: Int): Float? {
    return _store.scrollPercentageOf(index)
  }

  /** 订阅帧间实时条目数同步失效通知 */
  internal fun attachLiveCountSync() {
    _store.attachLiveCountSync()
  }

  internal fun updateViewportUnfocusedCount(
    owner: Any,
    unfocusedCount: Int,
  ) {
    _store.updateViewportUnfocusedCount(
      owner = owner,
      unfocusedCount = unfocusedCount,
    )
  }

  internal fun onPositioned(owner: Any) {
    if (_store.attachment?.owner === owner) {
      _scrollController.onPositioned()
    }
  }

  internal fun onFlingHandoffResolved(owner: Any) {
    _store.onFlingHandoffResolved(owner)
  }

  /** 同步组件挂载参数，空列表或切换持有者时取消活跃滚动 */
  internal fun updateAttachment(
    owner: Any,
    count: Int,
    unfocusedCount: Int,
    itemSizePx: Int,
    reverseVisualDirection: Boolean = false,
    flingGuardEnabled: Boolean = false,
    countProvider: (() -> Int)? = null,
  ) {
    val previousOwner = _store.attachment?.owner
    if (count <= 0 || (previousOwner != null && previousOwner !== owner)) {
      _scrollExecutor.cancelActiveScroll()
    }
    _store.updateAttachment(
      owner = owner,
      count = count,
      unfocusedCount = unfocusedCount,
      itemSizePx = itemSizePx,
      reverseVisualDirection = reverseVisualDirection,
      flingGuardEnabled = flingGuardEnabled,
      countProvider = countProvider,
    )
  }

  /** 解绑[owner]并在成功卸载时取消活跃滚动 */
  internal fun detach(owner: Any) {
    if (_store.detach(owner)) {
      _scrollExecutor.cancelActiveScroll()
    }
  }

  internal suspend fun observeSnappedIndex(owner: Any) {
    _snapObserver.observe(owner)
  }

  companion object {
    /** [WheelPickerState]状态保存与恢复器 */
    val Saver: Saver<WheelPickerState, Int> = Saver(
      save = { state -> state._store.saveIndex() },
      restore = { WheelPickerState(initialIndex = it) },
    )
  }
}
