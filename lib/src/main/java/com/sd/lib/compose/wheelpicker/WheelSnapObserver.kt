package com.sd.lib.compose.wheelpicker

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.snapshotFlow
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield

internal class WheelSnapObserver(
  private val store: WheelPositionStore,
  private val lazyListState: LazyListState,
) {
  /** 观察[owner]绑定的滚轮快照，静止且布局同步后执行自动归位并结算吸附索引 */
  suspend fun observe(owner: Any) {
    var wasScrolling = false
    snapshotFlow {
      val layoutInfo = lazyListState.layoutInfo
      ObserveSnapshot(
        attachment = store.attachment,
        isScrollInProgress = lazyListState.isScrollInProgress,
        firstVisibleItemIndex = lazyListState.firstVisibleItemIndex,
        firstVisibleItemScrollOffset = lazyListState.firstVisibleItemScrollOffset,
        hasVisibleItems = layoutInfo.visibleItemsInfo.isNotEmpty(),
        totalItemsCount = layoutInfo.totalItemsCount,
      )
    }.collect { snapshot ->
      val attachment = snapshot.attachment
      if (attachment?.owner !== owner) {
        wasScrolling = false
        return@collect
      }
      if (attachment.count <= 0) {
        wasScrolling = false
        store.clearSnappedIndex()
        return@collect
      }
      if (snapshot.isScrollInProgress) {
        wasScrolling = true
        store.updateLastKnownIndexFromLive()
        return@collect
      }
      if (!store.isLayoutSyncedWith(attachment.count)) {
        return@collect
      }

      if (wasScrolling) {
        wasScrolling = false
        if (snapshot.hasVisibleItems && snapshot.firstVisibleItemScrollOffset != 0) {
          // 非零偏移进入自动归位前等待一帧边界并让出调度，避免截停刚离手的惯性滚动衔接
          currentCoroutineContext()[MonotonicFrameClock]?.withFrameNanos { }
        }
        yield()
        if (lazyListState.isScrollInProgress) {
          wasScrolling = true
          return@collect
        }
      }

      val currentAttachment = store.attachment
      if (currentAttachment?.owner !== owner) {
        return@collect
      }
      if (currentAttachment.count <= 0) {
        store.clearSnappedIndex()
        return@collect
      }
      if (!store.isLayoutSyncedWith(currentAttachment.count)) {
        return@collect
      }

      val currentOffset = lazyListState.firstVisibleItemScrollOffset
      if (lazyListState.layoutInfo.visibleItemsInfo.isNotEmpty() && currentOffset != 0) {
        val snapped = performAutoSnap(
          attachment = currentAttachment,
          currentOffset = currentOffset,
        )
        if (!snapped) {
          wasScrolling = true
          return@collect
        }
      }

      store.onIdleSettled(owner)
    }
  }

  /** 静止停留在非零像素偏移时归位至最近条目，返回是否正常完成 */
  private suspend fun performAutoSnap(
    attachment: WheelAttachment,
    currentOffset: Int,
  ): Boolean {
    val targetIndex = store.currentIndex.coerceIn(0, attachment.count - 1)
    val distanceToNearest = minOf(currentOffset, (attachment.itemSizePx - currentOffset).coerceAtLeast(0))
    return try {
      if (distanceToNearest > 1) {
        lazyListState.animateScrollToItem(targetIndex)
      } else {
        lazyListState.scrollToItem(targetIndex)
      }
      true
    } catch (_: CancellationException) {
      currentCoroutineContext().ensureActive()
      false
    }
  }
}

private data class ObserveSnapshot(
  val attachment: WheelAttachment?,
  val isScrollInProgress: Boolean,
  val firstVisibleItemIndex: Int,
  val firstVisibleItemScrollOffset: Int,
  val hasVisibleItems: Boolean,
  val totalItemsCount: Int,
)
