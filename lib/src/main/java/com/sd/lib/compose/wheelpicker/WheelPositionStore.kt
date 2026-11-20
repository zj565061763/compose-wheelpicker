package com.sd.lib.compose.wheelpicker

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
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
  private var _flingGuardRequested = false
  private var _hasFlingGuard = false
  private var _countProvider: (() -> Int)? = null
  private val _liveCountSyncInvalidator = mutableStateOf(Unit, neverEqualPolicy())
  private var _nextHandoffId = 0
  private var _unfocusedCount by mutableIntStateOf(0)
  private var _reverseVisualDirection by mutableStateOf(false)
  private var _snappedIndex by mutableIntStateOf(-1)

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

  val snappedIndex: Int
    get() {
      val raw = _snappedIndex
      if (raw < 0) {
        return -1
      }
      val count = effectiveCount()
      return if (count <= 0) -1 else raw.coerceAtMost(count - 1)
    }

  val currentIndex: Int by derivedStateOf {
    val current = attachment
    val count = effectiveCount(current)
    if (current == null || count <= 0) {
      -1
    } else {
      calculateSnappedIndex(
        firstVisibleItemIndex = scrollController.firstVisibleItemIndex,
        firstVisibleItemScrollOffset = scrollController.firstVisibleItemScrollOffset,
        count = count,
        itemSizePx = current.itemSizePx,
      )
    }
  }

  /** 读取当前挂载的有效条目数，优先采用已缓存的实时提供者返回值 */
  private fun effectiveCount(current: WheelAttachment? = attachment): Int {
    if (current == null) {
      return 0
    }
    val provider = _countProvider ?: return current.count
    val liveCount = provider()
    require(liveCount >= 0) { "require count >= 0, but was $liveCount" }
    return liveCount
  }

  /** 按指定挂载快照[target]与当前滚动位置计算中心条目索引 */
  private fun calculateAttachedCurrentIndex(target: WheelAttachment?): Int {
    if (target == null || target.count <= 0) {
      return -1
    }
    return calculateSnappedIndex(
      firstVisibleItemIndex = scrollController.firstVisibleItemIndex,
      firstVisibleItemScrollOffset = scrollController.firstVisibleItemScrollOffset,
      count = target.count,
      itemSizePx = target.itemSizePx,
    )
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
    if (current == null || effectiveCount(current) <= 0) {
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

  /** 绑定[owner]观察会话，切换持有者时清理残留拖拽与离手等待状态 */
  fun onObserverAttached(owner: Any) {
    if (_observerOwner !== owner) {
      _observerOwner = owner
      isDragged = false
      awaitingHandoffId = 0
    }
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
    countProvider: (() -> Int)? = null,
  ) {
    val previousAttachment = attachment
    if (previousAttachment == null || previousAttachment.owner !== owner) {
      isDragged = false
      awaitingHandoffId = 0
      scrollController.resetPositioned()
    }
    _unfocusedCount = unfocusedCount
    _reverseVisualDirection = reverseVisualDirection
    _flingGuardRequested = flingGuardEnabled
    _countProvider = countProvider
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
        val lastIndex = calculateAttachedCurrentIndex(previousAttachment)
        if (lastIndex >= 0) {
          lastKnownIndex = lastIndex
        }
      }
      attachment = WheelAttachment(owner = owner, count = 0, itemSizePx = itemSizePx)
      _snappedIndex = -1
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
        val liveIndex = calculateAttachedCurrentIndex(previousAttachment)
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
      _snappedIndex = restoredTarget
      return
    }

    // 缩容时仅将上一轮越界的吸附索引夹紧至新边界，待新列表完成测量且交接结束后再按实际位置结算
    if (_snappedIndex >= count) {
      val clamped = count - 1
      _snappedIndex = clamped
      lastKnownIndex = lastKnownIndex.coerceAtMost(clamped)
    }
  }

  /** 订阅实时条目数同步失效通知，确保帧间改写挂载计数后即使条目数恢复原值也能触发重组同步 */
  fun attachLiveCountSync() {
    return _liveCountSyncInvalidator.value
  }

  /** 读取[owner]当前挂载提供者的实时条目数，未挂载或无提供者时返回null */
  fun readLiveCount(owner: Any): Int? {
    if (attachment?.owner !== owner) {
      return null
    }
    return _countProvider?.invoke()
  }

  /** 从挂载的条目数提供者同步最新条目总数，返回同步后的有效条目数，未挂载时返回null */
  fun syncLiveCount(): Int? {
    val current = attachment ?: return null
    val provider = _countProvider ?: return current.count
    val liveCount = provider()
    require(liveCount >= 0) { "require count >= 0, but was $liveCount" }
    if (liveCount != current.count) {
      _liveCountSyncInvalidator.value = Unit
      updateAttachment(
        owner = current.owner,
        count = liveCount,
        unfocusedCount = _unfocusedCount,
        itemSizePx = current.itemSizePx,
        reverseVisualDirection = _reverseVisualDirection,
        flingGuardEnabled = _flingGuardRequested,
        countProvider = provider,
      )
    }
    return liveCount
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
      val lastIndex = calculateAttachedCurrentIndex(current)
      if (lastIndex >= 0) {
        lastKnownIndex = lastIndex
      }
    }
    _flingGuardRequested = false
    _countProvider = null
    _hasFlingGuard = false
    isDragged = false
    awaitingHandoffId = 0
    scrollController.resetPositioned()
    attachment = null
    _snappedIndex = -1
    return true
  }

  fun clearSnappedIndex() {
    _snappedIndex = -1
  }

  fun updateSnappedIndex(index: Int) {
    _snappedIndex = index
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
      val liveIndex = calculateAttachedCurrentIndex(current)
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
      _snappedIndex = -1
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
    val settled = calculateAttachedCurrentIndex(finalAttachment)
    _snappedIndex = settled
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
    val settled = calculateAttachedCurrentIndex(attachment)
    _snappedIndex = settled
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
      val live = calculateAttachedCurrentIndex(current)
      if (live >= 0) {
        return live
      }
    }
    return lastKnownIndex.coerceAtLeast(0)
  }
}
