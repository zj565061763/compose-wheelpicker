package com.sd.lib.compose.wheelpicker

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield

internal class WheelSnapObserver(
  private val store: WheelPositionStore,
  private val lazyListState: LazyListState,
) {
  /** 观察滚轮快照，静止且布局同步后执行自动归位并结算吸附索引 */
  suspend fun observe() {
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
      if (attachment == null || attachment.count <= 0) {
        wasScrolling = false
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
          withFrameNanos { }
        }
        yield()
        if (lazyListState.isScrollInProgress) {
          wasScrolling = true
          return@collect
        }
      }

      val currentAttachment = store.attachment
      if (currentAttachment == null || currentAttachment.count <= 0) {
        return@collect
      }
      if (!store.isLayoutSyncedWith(currentAttachment.count)) {
        return@collect
      }

      if (lazyListState.layoutInfo.visibleItemsInfo.isNotEmpty() && lazyListState.firstVisibleItemScrollOffset != 0) {
        if (!performAutoSnap(currentAttachment.count)) {
          wasScrolling = true
          return@collect
        }
      }

      store.settleCurrentIndex()
    }
  }

  /** 静止停留在非零像素偏移时归位至最近条目，返回是否正常完成 */
  private suspend fun performAutoSnap(count: Int): Boolean {
    val targetIndex = store.currentIndex.coerceIn(0, count - 1)
    return try {
      lazyListState.animateScrollToItem(targetIndex)
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
