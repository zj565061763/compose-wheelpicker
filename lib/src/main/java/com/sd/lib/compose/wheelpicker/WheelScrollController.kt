package com.sd.lib.compose.wheelpicker

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal interface WheelScrollController {
  val firstVisibleItemIndex: Int
  val firstVisibleItemScrollOffset: Int
  val isScrollInProgress: Boolean
  val hasVisibleItems: Boolean
  val isPositioned: Boolean
  val totalItemsCount: Int
  val interactionSource: InteractionSource?

  fun onPositioned() {}
  fun resetPositioned() {}
  fun requestScrollToItem(index: Int, scrollOffset: Int = 0)
  suspend fun scrollToItem(index: Int)
  suspend fun animateScrollToItem(index: Int)
}

internal class LazyListWheelScrollController(
  private val lazyListState: LazyListState,
) : WheelScrollController {
  override var isPositioned by mutableStateOf(false)
    private set

  override val firstVisibleItemIndex: Int
    get() {
      return lazyListState.firstVisibleItemIndex
    }

  override val firstVisibleItemScrollOffset: Int
    get() {
      return lazyListState.firstVisibleItemScrollOffset
    }

  override val isScrollInProgress: Boolean
    get() {
      return lazyListState.isScrollInProgress
    }

  override val hasVisibleItems: Boolean
    get() {
      return lazyListState.layoutInfo.visibleItemsInfo.isNotEmpty()
    }

  override val totalItemsCount: Int
    get() {
      return lazyListState.layoutInfo.totalItemsCount
    }

  override val interactionSource: InteractionSource
    get() {
      return lazyListState.interactionSource
    }

  override fun onPositioned() {
    if (!isPositioned) {
      isPositioned = true
    }
  }

  override fun resetPositioned() {
    if (isPositioned) {
      isPositioned = false
    }
  }

  override fun requestScrollToItem(index: Int, scrollOffset: Int) {
    lazyListState.requestScrollToItem(index, scrollOffset)
  }

  override suspend fun scrollToItem(index: Int) {
    lazyListState.scrollToItem(index)
  }

  override suspend fun animateScrollToItem(index: Int) {
    lazyListState.animateScrollToItem(index)
  }
}
