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
  private var _unfocusedCount by mutableIntStateOf(0)
  private var _reverseVisualDirection by mutableStateOf(false)

  var lastKnownIndex = initialIndex.coerceAtLeast(0)

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
  ) {
    val previousAttachment = attachment
    if (previousAttachment?.owner !== owner) {
      _unfocusedCount = unfocusedCount
    }
    _reverseVisualDirection = reverseVisualDirection

    if (count <= 0) {
      updateLastKnownIndexFromLive(previousAttachment)
      attachment = WheelAttachment(owner = owner, count = 0, itemSizePx = itemSizePx)
      snappedIndex = -1
      return
    }

    val wasInactiveOrSwitched = previousAttachment == null ||
      previousAttachment.count <= 0 ||
      previousAttachment.owner !== owner

    var restoredTarget: Int? = null
    if (wasInactiveOrSwitched) {
      val restoreTarget = lastKnownIndex.coerceIn(0, count - 1)
      lastKnownIndex = restoreTarget
      restoredTarget = restoreTarget
      lazyListState.requestScrollToItem(index = restoreTarget, scrollOffset = 0)
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

  /** 卸载[owner]绑定的滚轮，保存最近有效索引并重置挂载与吸附状态 */
  fun detach(owner: Any) {
    val current = attachment
    if (current?.owner !== owner) {
      return
    }
    updateLastKnownIndexFromLive(current)
    attachment = null
    snappedIndex = -1
  }

  fun clearSnappedIndex() {
    snappedIndex = -1
  }

  /** 布局已同步时将实时中心索引记录为最近已知位置 */
  fun updateLastKnownIndexFromLive(target: WheelAttachment? = attachment) {
    if (hasSyncedLivePosition(target)) {
      val liveIndex = currentIndex
      if (liveIndex >= 0) {
        lastKnownIndex = liveIndex
      }
    }
  }

  /** 滚轮静止、布局已同步且首项零偏移对齐时结算吸附索引 */
  fun settleCurrentIndex(owner: Any? = attachment?.owner) {
    val current = attachment
    if (current == null || current.owner !== owner) {
      return
    }
    if (current.count <= 0) {
      snappedIndex = -1
      return
    }
    if (!isIdleAndLayoutSynced(owner = owner)) {
      return
    }
    if (lazyListState.layoutInfo.visibleItemsInfo.isNotEmpty() && lazyListState.firstVisibleItemScrollOffset != 0) {
      return
    }
    val settled = currentIndex
    snappedIndex = settled
    if (settled >= 0) {
      lastKnownIndex = settled
    }
  }

  /** 优先返回已同步布局的实时中心索引，否则返回最近已知索引 */
  fun saveIndex(): Int {
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
}
