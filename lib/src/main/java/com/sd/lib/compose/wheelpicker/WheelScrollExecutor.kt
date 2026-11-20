package com.sd.lib.compose.wheelpicker

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal class WheelScrollExecutor(
  private val store: WheelPositionStore,
  private val scrollController: WheelScrollController,
) {
  private var _scrollRequestId = 0L
  private var _activeScrollJob: Job? = null
  private var _activeRequestId by mutableLongStateOf(0L)

  val isInFlight: Boolean
    get() {
      return _activeRequestId != 0L
    }

  /** 取消当前进行中的编程式滚动或自动吸附任务 */
  fun cancelActiveScroll() {
    _activeRequestId = 0L
    val activeJob = _activeScrollJob
    _activeScrollJob = null
    activeJob?.cancel()
  }

  /** 执行跳转到[index]，未挂载、空数据或首次摆放前直接记录目标而不挂起，运行中支持并发抢占 */
  suspend fun scrollToIndex(
    index: Int,
    animate: Boolean,
  ) {
    store.clearAwaitingFlingHandoff()
    store.syncLiveCount()
    val requestId = ++_scrollRequestId
    cancelActiveScroll()
    if (_scrollRequestId != requestId) {
      currentCoroutineContext().ensureActive()
      throwIfInterruptedWhileActive()
      return
    }

    val currentAttachment = store.attachment
    if (currentAttachment == null || currentAttachment.count <= 0) {
      val requestedIndex = index.coerceAtLeast(0)
      store.pendingTargetIndex = requestedIndex
      store.lastKnownIndex = requestedIndex
      scrollController.requestScrollToItem(index = requestedIndex, scrollOffset = 0)
      return
    }

    val targetIndex = index.coerceIn(0, currentAttachment.count - 1)
    store.lastKnownIndex = targetIndex

    if (!scrollController.isPositioned || scrollController.totalItemsCount <= 0) {
      store.pendingTargetIndex = null
      scrollController.requestScrollToItem(index = targetIndex, scrollOffset = 0)
      store.updateSnappedIndex(targetIndex)
      return
    }

    _activeRequestId = requestId
    store.pendingTargetIndex = targetIndex
    var succeeded = false
    try {
      runScrollStep(
        requestId = requestId,
        targetIndex = targetIndex,
        animate = animate,
      )
      succeeded = true
    } finally {
      if (_activeRequestId == requestId) {
        _activeRequestId = 0L
        val latestAttachment = store.attachment
        if (latestAttachment != null && latestAttachment.count > 0) {
          store.pendingTargetIndex = null
          if (succeeded && scrollController.totalItemsCount > 0) {
            store.settleCurrentIndexIfAligned(isProgrammaticScrollInFlight = isInFlight)
          } else {
            store.updateLastKnownIndexFromLive()
          }
        }
      }
    }
  }

  /** 静止且停留在非零像素偏移时归位至最近条目，返回是否正常完成 */
  suspend fun performAutoSnap(owner: Any): Boolean {
    val currentAttachment = store.attachment
    if (currentAttachment?.owner !== owner || currentAttachment.count <= 0) {
      return false
    }
    if (
      !scrollController.isPositioned ||
      !store.isIdleAndLayoutSynced(
        owner = owner,
        isProgrammaticScrollInFlight = isInFlight,
      )
    ) {
      return false
    }
    val currentOffset = scrollController.firstVisibleItemScrollOffset
    if (!scrollController.hasVisibleItems || currentOffset == 0) {
      return true
    }

    val targetIndex = store.currentIndex.coerceIn(0, currentAttachment.count - 1)
    val distanceToNearest = minOf(currentOffset, (currentAttachment.itemSizePx - currentOffset).coerceAtLeast(0))
    val animate = distanceToNearest > 1
    val requestId = ++_scrollRequestId
    _activeRequestId = requestId
    var completed = false
    return try {
      runScrollStep(
        requestId = requestId,
        targetIndex = targetIndex,
        animate = animate,
      )
      completed = _activeRequestId == requestId
      completed
    } catch (_: CancellationException) {
      currentCoroutineContext().ensureActive()
      false
    } finally {
      if (_activeRequestId == requestId) {
        _activeRequestId = 0L
        if (completed) {
          store.settleCurrentIndexIfAligned(isProgrammaticScrollInFlight = isInFlight)
        } else {
          store.updateLastKnownIndexFromLive()
        }
      }
    }
  }

  /** 启动可取消的底层滚动步骤，组件仍有效但步骤被抢占中断时抛出[CancellationException] */
  private suspend fun runScrollStep(
    requestId: Long,
    targetIndex: Int,
    animate: Boolean,
  ) {
    var completedNormally = false
    coroutineScope {
      val stepJob = launch(start = CoroutineStart.UNDISPATCHED) {
        if (animate) {
          scrollController.animateScrollToItem(targetIndex)
        } else {
          scrollController.scrollToItem(targetIndex)
        }
        completedNormally = true
      }
      if (_activeRequestId == requestId) {
        if (stepJob.isActive) {
          _activeScrollJob = stepJob
        }
      } else {
        stepJob.cancel()
      }
      try {
        stepJob.join()
      } finally {
        if (_activeScrollJob === stepJob) {
          _activeScrollJob = null
        }
      }
    }

    currentCoroutineContext().ensureActive()
    if (!completedNormally) {
      throwIfInterruptedWhileActive()
    }
  }

  /** 组件仍挂载且非空时为被抢占中断的跳转抛出[CancellationException] */
  private fun throwIfInterruptedWhileActive() {
    val latestAttachment = store.attachment
    if (latestAttachment != null && latestAttachment.count > 0) {
      throw CancellationException("Wheel programmatic scroll interrupted")
    }
  }
}
