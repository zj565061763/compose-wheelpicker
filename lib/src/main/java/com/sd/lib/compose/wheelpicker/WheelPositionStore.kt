package com.sd.lib.compose.wheelpicker

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal data class WheelAttachment(
  val owner: Any,
  val count: Int,
  val itemSizePx: Int,
)

internal class WheelPositionStore(
  initialIndex: Int,
  private val scrollController: WheelScrollController,
) {
  private var _observerOwner: Any? = null
  private var _hasFlingGuard = false
  private var _cancelAwaitingFling = false
  private var _unfocusedCount by mutableIntStateOf(0)
  private var _reverseVisualDirection by mutableStateOf(false)

  var lastKnownIndex = initialIndex.coerceAtLeast(0)

  var pendingTargetIndex: Int? = null

  var attachment by mutableStateOf<WheelAttachment?>(null)
    private set

  var isDragged by mutableStateOf(false)
    private set

  var isAwaitingFlingHandoff by mutableStateOf(false)
    private set

  var snappedIndex by mutableIntStateOf(-1)
    private set

  val currentIndex: Int by derivedStateOf {
    val current = attachment
    if (current == null || current.count <= 0) {
      -1
    } else {
      calculateSnappedIndex(
        firstVisibleItemIndex = scrollController.firstVisibleItemIndex,
        firstVisibleItemScrollOffset = scrollController.firstVisibleItemScrollOffset,
        count = current.count,
        itemSizePx = current.itemSizePx,
      )
    }
  }

  fun scrollPercentageOf(index: Int): Float? {
    val current = attachment
    if (current == null || current.count <= 0) {
      return null
    }
    return calculateScrollPercentage(
      index = index,
      firstVisibleItemIndex = scrollController.firstVisibleItemIndex,
      firstVisibleItemScrollOffset = scrollController.firstVisibleItemScrollOffset,
      unfocusedCount = _unfocusedCount,
      itemSizePx = current.itemSizePx,
      reverseDirection = _reverseVisualDirection,
    )
  }

  fun updateViewportUnfocusedCount(
    owner: Any,
    unfocusedCount: Int,
  ) {
    if (attachment?.owner === owner && _unfocusedCount != unfocusedCount) {
      _unfocusedCount = unfocusedCount
    }
  }

  fun onObserverAttached(owner: Any) {
    _observerOwner = owner
  }

  fun onObserverDetached(owner: Any) {
    if (_observerOwner === owner) {
      _observerOwner = null
      isDragged = false
      isAwaitingFlingHandoff = false
      _cancelAwaitingFling = false
    }
  }

  fun updateDragged(
    owner: Any,
    dragged: Boolean,
  ) {
    if (_observerOwner === owner) {
      if (dragged) {
        isAwaitingFlingHandoff = false
        _cancelAwaitingFling = false
      }
      if (isDragged != dragged) {
        isDragged = dragged
      }
    }
  }

  fun onDragStoppedAwaitingFling(owner: Any) {
    if (_observerOwner === owner && _hasFlingGuard) {
      isAwaitingFlingHandoff = true
      _cancelAwaitingFling = false
    }
  }

  fun invalidateAwaitingFlingHandoff() {
    if (isAwaitingFlingHandoff) {
      isAwaitingFlingHandoff = false
      _cancelAwaitingFling = true
    }
  }

  fun onPreFlingHandoff(owner: Any): Boolean {
    if (attachment?.owner !== owner && _observerOwner !== owner) {
      return false
    }
    isAwaitingFlingHandoff = false
    if (_cancelAwaitingFling) {
      _cancelAwaitingFling = false
      return true
    }
    return false
  }

  fun onFlingHandoffResolved(owner: Any) {
    if (attachment?.owner === owner || _observerOwner === owner) {
      isAwaitingFlingHandoff = false
      _cancelAwaitingFling = false
    }
  }

  fun updateAttachment(
    owner: Any,
    count: Int,
    unfocusedCount: Int,
    itemSizePx: Int,
    reverseVisualDirection: Boolean,
    flingGuardEnabled: Boolean,
    isProgrammaticScrollInFlight: Boolean,
  ) {
    val previousAttachment = attachment
    _unfocusedCount = unfocusedCount
    _reverseVisualDirection = reverseVisualDirection
    _hasFlingGuard = flingGuardEnabled && count > 0
    if (!_hasFlingGuard) {
      isAwaitingFlingHandoff = false
      _cancelAwaitingFling = false
    }

    if (count <= 0) {
      if (
        previousAttachment != null &&
        previousAttachment.count > 0 &&
        pendingTargetIndex == null &&
        scrollController.totalItemsCount > 0
      ) {
        val lastIndex = currentIndex
        if (lastIndex >= 0) {
          lastKnownIndex = lastIndex
        }
      }
      attachment = WheelAttachment(owner = owner, count = 0, itemSizePx = itemSizePx)
      snappedIndex = -1
      return
    }

    val wasInactiveOrSwitched = previousAttachment == null ||
      previousAttachment.count <= 0 ||
      previousAttachment.owner !== owner

    var restoredTarget: Int? = null
    if (wasInactiveOrSwitched) {
      if (
        previousAttachment != null &&
        previousAttachment.owner !== owner &&
        previousAttachment.count > 0 &&
        pendingTargetIndex == null &&
        scrollController.totalItemsCount > 0
      ) {
        val liveIndex = currentIndex
        if (liveIndex >= 0) {
          lastKnownIndex = liveIndex
        }
      }
      val rawTarget = pendingTargetIndex ?: lastKnownIndex
      val restoreTarget = rawTarget.coerceIn(0, count - 1)
      pendingTargetIndex = null
      lastKnownIndex = restoreTarget
      restoredTarget = restoreTarget
      scrollController.requestScrollToItem(index = restoreTarget, scrollOffset = 0)
    } else {
      val pending = pendingTargetIndex
      if (pending != null) {
        val clampedTarget = pending.coerceIn(0, count - 1)
        pendingTargetIndex = clampedTarget
        lastKnownIndex = clampedTarget
      } else if (
        !scrollController.isScrollInProgress &&
        !isProgrammaticScrollInFlight &&
        scrollController.hasVisibleItems &&
        scrollController.firstVisibleItemIndex >= count - 1 &&
        scrollController.firstVisibleItemScrollOffset > 0
      ) {
        // 缩容后原中间项变为新末项且残留正偏移时，将末项偏移归零
        val lastIndex = count - 1
        scrollController.requestScrollToItem(index = lastIndex, scrollOffset = 0)
      }
    }

    attachment = WheelAttachment(owner = owner, count = count, itemSizePx = itemSizePx)

    if (restoredTarget != null) {
      snappedIndex = restoredTarget
      return
    }

    if (!scrollController.isScrollInProgress && !isProgrammaticScrollInFlight) {
      if (snappedIndex !in 0..<count) {
        settleCurrentIndexIfAligned()
      }
    }
    if (snappedIndex >= count) {
      val clamped = count - 1
      snappedIndex = clamped
      lastKnownIndex = lastKnownIndex.coerceAtMost(clamped)
    }
  }

  fun detach(owner: Any): Boolean {
    val current = attachment
    if (current?.owner !== owner) {
      return false
    }
    if (current.count > 0 && pendingTargetIndex == null && scrollController.totalItemsCount > 0) {
      val lastIndex = currentIndex
      if (lastIndex >= 0) {
        lastKnownIndex = lastIndex
      }
    }
    _hasFlingGuard = false
    isAwaitingFlingHandoff = false
    _cancelAwaitingFling = false
    attachment = null
    snappedIndex = -1
    return true
  }

  fun clearSnappedIndex() {
    snappedIndex = -1
  }

  fun updateSnappedIndex(index: Int) {
    snappedIndex = index
  }

  fun updateLastKnownIndexFromLive() {
    if (scrollController.totalItemsCount > 0) {
      val liveIndex = currentIndex
      if (liveIndex >= 0) {
        lastKnownIndex = liveIndex
      }
    }
  }

  fun onIdleSettled(
    owner: Any,
    isProgrammaticScrollInFlight: Boolean,
  ) {
    val finalAttachment = attachment
    if (finalAttachment?.owner !== owner) {
      return
    }
    if (finalAttachment.count <= 0) {
      snappedIndex = -1
      return
    }
    if (isProgrammaticScrollInFlight || scrollController.isScrollInProgress) {
      return
    }
    val settled = currentIndex
    snappedIndex = settled
    if (settled >= 0) {
      lastKnownIndex = settled
    }
  }

  fun settleCurrentIndexIfAligned() {
    if (scrollController.isScrollInProgress) {
      return
    }
    if (scrollController.hasVisibleItems && scrollController.firstVisibleItemScrollOffset != 0) {
      return
    }
    val settled = currentIndex
    snappedIndex = settled
    if (settled >= 0) {
      lastKnownIndex = settled
    }
  }

  fun saveIndex(): Int {
    val pending = pendingTargetIndex
    if (pending != null) {
      return pending.coerceAtLeast(0)
    }
    if (scrollController.totalItemsCount > 0) {
      val live = currentIndex
      if (live >= 0) {
        return live
      }
    }
    return lastKnownIndex.coerceAtLeast(0)
  }
}
