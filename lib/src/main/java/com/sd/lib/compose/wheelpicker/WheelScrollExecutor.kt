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

  fun cancelActiveScroll() {
    _activeRequestId = 0L
    _activeScrollJob?.cancel()
    _activeScrollJob = null
  }

  suspend fun scrollToIndex(
    index: Int,
    animate: Boolean,
  ) {
    val currentAttachment = store.attachment
    if (currentAttachment == null || currentAttachment.count <= 0) {
      val requestedIndex = index.coerceAtLeast(0)
      _scrollRequestId += 1L
      cancelActiveScroll()
      store.pendingTargetIndex = requestedIndex
      store.lastKnownIndex = requestedIndex
      scrollController.requestScrollToItem(index = requestedIndex, scrollOffset = 0)
      return
    }

    val targetIndex = index.coerceIn(0, currentAttachment.count - 1)
    val requestId = ++_scrollRequestId
    cancelActiveScroll()
    store.lastKnownIndex = targetIndex

    if (scrollController.totalItemsCount <= 0) {
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
            store.settleCurrentIndexIfAligned()
          } else {
            store.updateLastKnownIndexFromLive()
          }
        }
      }
    }
  }

  suspend fun performAutoSnap(owner: Any): Boolean {
    val currentAttachment = store.attachment
    if (currentAttachment?.owner !== owner || currentAttachment.count <= 0) {
      return false
    }
    if (
      scrollController.isScrollInProgress ||
      store.isDragged ||
      store.isAwaitingFlingHandoff ||
      isInFlight
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
          store.settleCurrentIndexIfAligned()
        } else {
          store.updateLastKnownIndexFromLive()
        }
      }
    }
  }

  private suspend fun runScrollStep(
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
      _activeScrollJob = stepJob
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
      val latestAttachment = store.attachment
      if (latestAttachment == null || latestAttachment.count <= 0) {
        return
      }
      throw CancellationException("Wheel programmatic scroll interrupted")
    }
  }
}
