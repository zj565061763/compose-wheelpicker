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
  private var _nextHandoffId = 0
  private var _unfocusedCount by mutableIntStateOf(0)
  private var _reverseVisualDirection by mutableStateOf(false)

  var lastKnownIndex = initialIndex.coerceAtLeast(0)

  var pendingTargetIndex: Int? = null

  var attachment by mutableStateOf<WheelAttachment?>(null)
    private set

  var isDragged by mutableStateOf(false)
    private set

  var awaitingHandoffId by mutableIntStateOf(0)
    private set

  val isAwaitingFlingHandoff: Boolean
    get() {
      return awaitingHandoffId != 0
    }

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

  /** 判断底层可见项测量总数是否已与[expectedCount]同步 */
  fun isLayoutSyncedWith(expectedCount: Int): Boolean {
    return !scrollController.hasVisibleItems || scrollController.totalItemsCount == expectedCount
  }

  /** 判断滚轮是否归属[owner]、处于静止无待决手势状态且布局已与当前条目数同步 */
  fun isIdleAndLayoutSynced(
    owner: Any? = attachment?.owner,
    isProgrammaticScrollInFlight: Boolean = false,
  ): Boolean {
    val current = attachment
    if (current == null || current.owner !== owner || current.count <= 0) {
      return false
    }
    return !scrollController.isScrollInProgress &&
      !isProgrammaticScrollInFlight &&
      !isDragged &&
      !isAwaitingFlingHandoff &&
      isLayoutSyncedWith(current.count)
  }

  /** 计算[index]相对中心焦点的视觉偏移比例，未挂载或空数据时返回null */
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

  /** 同步[owner]实际测量生效的单侧未选中条目数 */
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

  /** 解绑[owner]观察会话并清理残留拖拽与离手等待状态 */
  fun onObserverDetached(owner: Any) {
    if (_observerOwner === owner) {
      _observerOwner = null
      isDragged = false
      awaitingHandoffId = 0
    }
  }

  /** 更新[owner]拖拽状态，开始新拖拽时清除上一轮离手交接等待 */
  fun updateDragged(
    owner: Any,
    dragged: Boolean,
  ) {
    if (_observerOwner === owner) {
      if (dragged) {
        awaitingHandoffId = 0
      }
      if (isDragged != dragged) {
        isDragged = dragged
      }
    }
  }

  /** 拖拽结束且启用离手保护时分配非零递增序号进入交接等待 */
  fun onDragStoppedAwaitingFling(owner: Any) {
    if (_observerOwner === owner && _hasFlingGuard) {
      val nextId = _nextHandoffId + 1
      _nextHandoffId = if (nextId == 0) 1 else nextId
      awaitingHandoffId = _nextHandoffId
    }
  }

  fun clearAwaitingFlingHandoff() {
    awaitingHandoffId = 0
  }

  /** 嵌套滚动交接完成时解除[owner]的离手等待 */
  fun onFlingHandoffResolved(owner: Any) {
    if (attachment?.owner === owner || _observerOwner === owner) {
      awaitingHandoffId = 0
    }
  }

  /** 仅当当前等待序号仍匹配[handoffId]时解除离手超时保护 */
  fun onFlingHandoffTimeout(
    owner: Any,
    handoffId: Int,
  ) {
    if ((attachment?.owner === owner || _observerOwner === owner) && awaitingHandoffId == handoffId) {
      awaitingHandoffId = 0
    }
  }

  /** 同步组件挂载参数，处理空列表过渡、重挂载位置恢复与缩容越界夹紧 */
  fun updateAttachment(
    owner: Any,
    count: Int,
    unfocusedCount: Int,
    itemSizePx: Int,
    reverseVisualDirection: Boolean,
    flingGuardEnabled: Boolean,
  ) {
    val previousAttachment = attachment
    if (previousAttachment == null || previousAttachment.owner !== owner) {
      awaitingHandoffId = 0
      scrollController.resetPositioned()
    }
    _unfocusedCount = unfocusedCount
    _reverseVisualDirection = reverseVisualDirection
    _hasFlingGuard = flingGuardEnabled && count > 0
    if (!_hasFlingGuard) {
      awaitingHandoffId = 0
    }

    if (count <= 0) {
      if (
        previousAttachment != null &&
        previousAttachment.count > 0 &&
        pendingTargetIndex == null &&
        scrollController.totalItemsCount > 0 &&
        isLayoutSyncedWith(previousAttachment.count)
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
        scrollController.totalItemsCount > 0 &&
        isLayoutSyncedWith(previousAttachment.count)
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
      }
    }

    attachment = WheelAttachment(owner = owner, count = count, itemSizePx = itemSizePx)

    if (restoredTarget != null) {
      snappedIndex = restoredTarget
      return
    }

    // 缩容时仅将上一轮越界的吸附索引夹紧至新边界，待新列表完成测量且交接结束后再按实际位置结算
    if (snappedIndex >= count) {
      val clamped = count - 1
      snappedIndex = clamped
      lastKnownIndex = lastKnownIndex.coerceAtMost(clamped)
    }
  }

  /** 卸载[owner]绑定的滚轮，保存最近有效索引并重置挂载与吸附状态 */
  fun detach(owner: Any): Boolean {
    val current = attachment
    if (current?.owner !== owner) {
      return false
    }
    if (
      current.count > 0 &&
      pendingTargetIndex == null &&
      scrollController.totalItemsCount > 0 &&
      isLayoutSyncedWith(current.count)
    ) {
      val lastIndex = currentIndex
      if (lastIndex >= 0) {
        lastKnownIndex = lastIndex
      }
    }
    _hasFlingGuard = false
    awaitingHandoffId = 0
    scrollController.resetPositioned()
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

  /** 布局已同步时将实时中心索引记录为最近已知位置 */
  fun updateLastKnownIndexFromLive() {
    val current = attachment
    if (
      current != null &&
      current.count > 0 &&
      scrollController.totalItemsCount > 0 &&
      isLayoutSyncedWith(current.count)
    ) {
      val liveIndex = currentIndex
      if (liveIndex >= 0) {
        lastKnownIndex = liveIndex
      }
    }
  }

  /** 新列表完成测量后若实际末项仍残留正偏移，则在静止且无待交接手势时将末项偏移归零 */
  fun clampLastItemResidualOffsetIfMeasured(
    owner: Any,
    isProgrammaticScrollInFlight: Boolean,
  ) {
    val current = attachment ?: return
    if (
      isIdleAndLayoutSynced(
        owner = owner,
        isProgrammaticScrollInFlight = isProgrammaticScrollInFlight,
      ) &&
      scrollController.hasVisibleItems &&
      scrollController.firstVisibleItemIndex >= current.count - 1 &&
      scrollController.firstVisibleItemScrollOffset > 0
    ) {
      val lastIndex = current.count - 1
      scrollController.requestScrollToItem(index = lastIndex, scrollOffset = 0)
    }
  }

  /** 滚轮静止且布局同步后按当前中心条目结算吸附索引 */
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
    if (
      !isIdleAndLayoutSynced(
        owner = owner,
        isProgrammaticScrollInFlight = isProgrammaticScrollInFlight,
      )
    ) {
      return
    }
    val settled = currentIndex
    snappedIndex = settled
    if (settled >= 0) {
      lastKnownIndex = settled
    }
  }

  /** 滚轮静止且首项零像素偏移对齐时结算吸附索引 */
  fun settleCurrentIndexIfAligned(isProgrammaticScrollInFlight: Boolean = false) {
    if (!isIdleAndLayoutSynced(isProgrammaticScrollInFlight = isProgrammaticScrollInFlight)) {
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

  /** 按待生效目标、实时中心项或最近已知位置返回待保存索引 */
  fun saveIndex(): Int {
    val pending = pendingTargetIndex
    if (pending != null) {
      return pending.coerceAtLeast(0)
    }
    val current = attachment
    if (
      current != null &&
      current.count > 0 &&
      scrollController.totalItemsCount > 0 &&
      isLayoutSyncedWith(current.count)
    ) {
      val live = currentIndex
      if (live >= 0) {
        return live
      }
    }
    return lastKnownIndex.coerceAtLeast(0)
  }
}
