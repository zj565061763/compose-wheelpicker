package com.sd.lib.compose.wheelpicker

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot

internal data class WheelAttachment(
  val owner: Any,
  val count: Int,
  val itemSizePx: Int,
)

internal class WheelPositionStore(
  initialIndex: Int,
  private val lazyListState: LazyListState,
) {
  private var _countProvider: (() -> Int)? = null
  private var _unfocusedCount by mutableIntStateOf(0)
  private var _reverseVisualDirection by mutableStateOf(false)

  var lastKnownIndex = initialIndex.coerceAtLeast(0)

  var pendingTargetIndex: Int? = null

  var attachment by mutableStateOf<WheelAttachment?>(null)
    private set

  var snappedIndex by mutableIntStateOf(-1)
    private set

  val currentIndex: Int by derivedStateOf {
    val current = attachment
    if (current == null || current.count <= 0) {
      -1
    } else {
      calculateSnappedIndex(
        firstVisibleItemIndex = lazyListState.firstVisibleItemIndex,
        firstVisibleItemScrollOffset = lazyListState.firstVisibleItemScrollOffset,
        count = current.count,
        itemSizePx = current.itemSizePx,
      )
    }
  }

  /** 判断底层测量条目总数是否已与[expectedCount]同步 */
  fun isLayoutSyncedWith(expectedCount: Int): Boolean {
    val layoutInfo = lazyListState.layoutInfo
    return layoutInfo.visibleItemsInfo.isEmpty() || layoutInfo.totalItemsCount == expectedCount
  }

  /** 判断滚轮是否归属[owner]、处于静止状态且布局已与当前条目数同步 */
  fun isIdleAndLayoutSynced(owner: Any? = attachment?.owner): Boolean {
    val current = attachment
    if (current == null || current.owner !== owner || current.count <= 0) {
      return false
    }
    return !lazyListState.isScrollInProgress && isLayoutSyncedWith(current.count)
  }

  /** 计算[index]相对中心焦点的视觉偏移比例，未挂载或空数据时返回null */
  fun scrollPercentageOf(index: Int): Float? {
    val current = attachment
    if (current == null || current.count <= 0) {
      return null
    }
    return calculateScrollPercentage(
      index = index,
      firstVisibleItemIndex = lazyListState.firstVisibleItemIndex,
      firstVisibleItemScrollOffset = lazyListState.firstVisibleItemScrollOffset,
      unfocusedCount = _unfocusedCount,
      itemSizePx = current.itemSizePx,
      reverseDirection = _reverseVisualDirection,
    )
  }

  /** 同步[owner]实际测量生效的单侧未选中条目数 */
  fun updateViewportUnfocusedCount(
    owner: Any,
    unfocusedCount: Int,
  ) {
    Snapshot.withoutReadObservation {
      if (attachment?.owner === owner && _unfocusedCount != unfocusedCount) {
        _unfocusedCount = unfocusedCount
      }
    }
  }

  /** 同步组件挂载参数，处理空列表过渡、重挂载位置恢复与缩容越界夹紧 */
  fun updateAttachment(
    owner: Any,
    count: Int,
    unfocusedCount: Int,
    itemSizePx: Int,
    reverseVisualDirection: Boolean = false,
    countProvider: (() -> Int)? = null,
  ) {
    val previousAttachment = attachment
    _unfocusedCount = unfocusedCount
    _reverseVisualDirection = reverseVisualDirection
    _countProvider = countProvider

    if (count <= 0) {
      recordLiveIndexIfNoPending(previousAttachment)
      attachment = WheelAttachment(owner = owner, count = 0, itemSizePx = itemSizePx)
      snappedIndex = -1
      return
    }

    val wasInactiveOrSwitched = previousAttachment == null ||
      previousAttachment.count <= 0 ||
      previousAttachment.owner !== owner

    var restoredTarget: Int? = null
    if (wasInactiveOrSwitched) {
      val rawTarget = pendingTargetIndex ?: lastKnownIndex
      val restoreTarget = rawTarget.coerceIn(0, count - 1)
      pendingTargetIndex = null
      lastKnownIndex = restoreTarget
      restoredTarget = restoreTarget
      lazyListState.requestScrollToItem(index = restoreTarget, scrollOffset = 0)
    } else {
      val pending = pendingTargetIndex
      if (pending != null) {
        val clampedTarget = pending.coerceIn(0, count - 1)
        pendingTargetIndex = clampedTarget
        lastKnownIndex = clampedTarget
      }
    }

    attachment = WheelAttachment(owner = owner, count = count, itemSizePx = itemSizePx)

    if (restoredTarget != null) {
      snappedIndex = restoredTarget
      return
    }

    // 缩容时仅将上一轮越界的吸附索引夹紧至新边界，待新列表完成测量且滚动静止后再按实际位置结算
    if (snappedIndex >= count) {
      val clamped = count - 1
      snappedIndex = clamped
      lastKnownIndex = lastKnownIndex.coerceAtMost(clamped)
    }
  }

  /** 从挂载的条目数提供者同步最新条目总数 */
  fun syncLiveCount() {
    val current = attachment ?: return
    val provider = _countProvider ?: return
    val liveCount = provider()
    require(liveCount >= 0) { "require count >= 0, but was $liveCount" }
    if (liveCount != current.count) {
      updateAttachment(
        owner = current.owner,
        count = liveCount,
        unfocusedCount = _unfocusedCount,
        itemSizePx = current.itemSizePx,
        reverseVisualDirection = _reverseVisualDirection,
        countProvider = provider,
      )
    }
  }

  /** 卸载[owner]绑定的滚轮，保存最近有效索引并重置挂载与吸附状态 */
  fun detach(owner: Any) {
    val current = attachment
    if (current?.owner !== owner) {
      return
    }
    recordLiveIndexIfNoPending(current)
    _countProvider = null
    attachment = null
    snappedIndex = -1
  }

  fun clearSnappedIndex() {
    snappedIndex = -1
  }

  fun updateSnappedIndex(index: Int) {
    snappedIndex = index
  }

  /** 布局已同步时将实时中心索引记录为最近已知位置 */
  fun updateLastKnownIndexFromLive() {
    if (hasSyncedLivePosition()) {
      val liveIndex = currentIndex
      if (liveIndex >= 0) {
        lastKnownIndex = liveIndex
      }
    }
  }

  /** 滚轮静止且布局同步后按当前中心条目结算吸附索引 */
  fun onIdleSettled(owner: Any) {
    val finalAttachment = attachment
    if (finalAttachment?.owner !== owner) {
      return
    }
    if (finalAttachment.count <= 0) {
      snappedIndex = -1
      return
    }
    if (!isIdleAndLayoutSynced(owner = owner)) {
      return
    }
    val settled = currentIndex
    snappedIndex = settled
    if (settled >= 0) {
      lastKnownIndex = settled
    }
  }

  /** 滚轮静止且首项零像素偏移对齐时结算吸附索引 */
  fun settleCurrentIndexIfAligned() {
    if (!isIdleAndLayoutSynced()) {
      return
    }
    val layoutInfo = lazyListState.layoutInfo
    if (layoutInfo.visibleItemsInfo.isNotEmpty() && lazyListState.firstVisibleItemScrollOffset != 0) {
      return
    }
    val settled = currentIndex
    snappedIndex = settled
    if (settled >= 0) {
      lastKnownIndex = settled
    }
  }

  /** 按待生效目标、实时中心项或最近已知位置返回待保存索引 */
  fun saveIndex(): Int {
    val pending = pendingTargetIndex
    if (pending != null) {
      return pending.coerceAtLeast(0)
    }
    if (hasSyncedLivePosition()) {
      val live = currentIndex
      if (live >= 0) {
        return live
      }
    }
    return lastKnownIndex.coerceAtLeast(0)
  }

  private fun hasSyncedLivePosition(target: WheelAttachment? = attachment): Boolean {
    return target != null &&
      target.count > 0 &&
      lazyListState.layoutInfo.totalItemsCount > 0 &&
      isLayoutSyncedWith(target.count)
  }

  private fun recordLiveIndexIfNoPending(target: WheelAttachment?) {
    if (pendingTargetIndex == null && hasSyncedLivePosition(target)) {
      val liveIndex = currentIndex
      if (liveIndex >= 0) {
        lastKnownIndex = liveIndex
      }
    }
  }
}
