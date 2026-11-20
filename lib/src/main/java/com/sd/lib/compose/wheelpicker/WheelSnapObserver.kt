package com.sd.lib.compose.wheelpicker

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

private const val MAX_FLING_HANDOFF_GUARD_FRAMES = 60

internal class WheelSnapObserver(
  private val store: WheelPositionStore,
  private val scrollExecutor: WheelScrollExecutor,
  private val scrollController: WheelScrollController,
) {
  /** 绑定[owner]观察会话，监听拖拽交互、离手交接超时与静止自动吸附 */
  suspend fun observe(owner: Any) {
    store.onObserverAttached(owner)
    try {
      coroutineScope {
        val interactionSource = scrollController.interactionSource
        if (interactionSource != null) {
          launch(start = CoroutineStart.UNDISPATCHED) {
            val activeDrags = HashSet<DragInteraction.Start>()
            interactionSource.interactions.collect { interaction ->
              var stoppedLastDrag = false
              when (interaction) {
                is DragInteraction.Start -> activeDrags.add(interaction)
                is DragInteraction.Stop -> {
                  val removed = activeDrags.remove(interaction.start)
                  stoppedLastDrag = removed && activeDrags.isEmpty()
                }
                is DragInteraction.Cancel -> {
                  // 触摸取消后 Compose 仍会发起零速度 fling 交接，滚动仍启用时同样进入离手保护
                  val removed = activeDrags.remove(interaction.start)
                  stoppedLastDrag = removed && activeDrags.isEmpty()
                }
              }
              if (stoppedLastDrag) {
                store.onDragStoppedAwaitingFling(owner)
              }
              store.updateDragged(owner = owner, dragged = activeDrags.isNotEmpty())
            }
          }
        }

        launch(start = CoroutineStart.UNDISPATCHED) {
          observeFlingHandoffTimeout(owner)
        }

        observeScrollAndSnap(owner = owner)
      }
    } finally {
      store.onObserverDetached(owner)
    }
  }

  /** 监听离手交接序号，超过最大保护帧数仍未回调时兜底解除等待 */
  private suspend fun observeFlingHandoffTimeout(owner: Any) {
    snapshotFlow { store.awaitingHandoffId }.collectLatest { handoffId ->
      if (handoffId == 0) {
        return@collectLatest
      }
      val frameClock = currentCoroutineContext()[MonotonicFrameClock]
      if (frameClock != null) {
        repeat(MAX_FLING_HANDOFF_GUARD_FRAMES) {
          frameClock.withFrameNanos { }
        }
      } else {
        yield()
      }
      store.onFlingHandoffTimeout(owner = owner, handoffId = handoffId)
    }
  }

  /** 判断滚轮是否处于滚动、拖拽、离手交接等待或编程式跳转中 */
  private fun isBusy(): Boolean {
    return scrollController.isScrollInProgress ||
      store.isDragged ||
      store.isAwaitingFlingHandoff ||
      scrollExecutor.isInFlight
  }

  /** 观察滚轮快照变化，静止且布局同步后处理末项残余偏移、自动吸附并结算吸附索引 */
  private suspend fun observeScrollAndSnap(owner: Any) {
    var wasActiveGestureOrScroll = false
    var lastAutoSnapState: AutoSnapState? = null
    snapshotFlow {
      ObserveSnapshot(
        attachment = store.attachment,
        liveCount = store.readLiveCount(owner),
        isScrollInProgress = scrollController.isScrollInProgress,
        isDragged = store.isDragged,
        awaitingHandoffId = store.awaitingHandoffId,
        isProgrammaticScrollInFlight = scrollExecutor.isInFlight,
        firstVisibleItemIndex = scrollController.firstVisibleItemIndex,
        firstVisibleItemScrollOffset = scrollController.firstVisibleItemScrollOffset,
        hasVisibleItems = scrollController.hasVisibleItems,
        isPositioned = scrollController.isPositioned,
        totalItemsCount = scrollController.totalItemsCount,
      )
    }.collect { snapshot ->
      if (
        store.attachment?.owner === owner &&
        snapshot.liveCount != null &&
        snapshot.liveCount != store.attachment?.count
      ) {
        val syncedCount = store.syncLiveCount()
        if (syncedCount != null && syncedCount <= 0) {
          scrollExecutor.cancelActiveScroll()
        }
      }
      val initialAttachment = store.attachment
      if (initialAttachment?.owner !== owner) {
        wasActiveGestureOrScroll = false
        lastAutoSnapState = null
        return@collect
      }
      if (initialAttachment.count <= 0) {
        wasActiveGestureOrScroll = false
        lastAutoSnapState = null
        store.clearSnappedIndex()
        return@collect
      }
      if (isBusy()) {
        wasActiveGestureOrScroll = true
        lastAutoSnapState = null
        store.updateLastKnownIndexFromLive()
        return@collect
      }
      if (!store.isLayoutSyncedWith(initialAttachment.count)) {
        lastAutoSnapState = null
        return@collect
      }

      val frameClock = currentCoroutineContext()[MonotonicFrameClock]
      while (true) {
        if (wasActiveGestureOrScroll) {
          wasActiveGestureOrScroll = false
          if (
            scrollController.hasVisibleItems &&
            scrollController.firstVisibleItemScrollOffset != 0 &&
            frameClock != null
          ) {
            // 非零偏移进入自动吸附前等待一帧边界并让出调度，避免嵌套滚动容器挂起执行 onPreFling 间隙过早截停离手 fling
            frameClock.withFrameNanos { }
            yield()
          } else {
            // 偏移已归零或无帧时钟时仅让出一次调度，等待同步触发的惯性滑动衔接
            yield()
          }
        }

        if (store.attachment?.owner !== owner) {
          lastAutoSnapState = null
          return@collect
        }

        if (isBusy()) {
          lastAutoSnapState = null
          val stoppedWhileOwned = awaitIdle(owner)
          if (!stoppedWhileOwned) {
            lastAutoSnapState = null
            if (store.attachment?.owner === owner && (store.attachment?.count ?: 0) <= 0) {
              store.clearSnappedIndex()
            }
            return@collect
          }
          wasActiveGestureOrScroll = true
          continue
        }

        val currentAttachment = store.attachment
        if (currentAttachment?.owner !== owner) {
          lastAutoSnapState = null
          return@collect
        }
        if (currentAttachment.count <= 0) {
          lastAutoSnapState = null
          store.clearSnappedIndex()
          return@collect
        }
        if (!store.isLayoutSyncedWith(currentAttachment.count)) {
          lastAutoSnapState = null
          return@collect
        }

        store.clampLastItemResidualOffsetIfMeasured(
          owner = owner,
          isProgrammaticScrollInFlight = scrollExecutor.isInFlight,
        )

        val currentOffset = scrollController.firstVisibleItemScrollOffset
        if (!scrollController.hasVisibleItems || currentOffset == 0) {
          lastAutoSnapState = null
          break
        }
        if (!scrollController.isPositioned) {
          lastAutoSnapState = null
          return@collect
        }

        val snapState = AutoSnapState(
          firstVisibleItemIndex = scrollController.firstVisibleItemIndex,
          scrollOffset = currentOffset,
          count = currentAttachment.count,
          itemSizePx = currentAttachment.itemSizePx,
        )
        if (snapState == lastAutoSnapState) {
          break
        }

        val completedNormally = scrollExecutor.performAutoSnap(owner)
        if (!completedNormally) {
          lastAutoSnapState = null
          wasActiveGestureOrScroll = true
          continue
        }

        lastAutoSnapState = snapState
      }

      store.onIdleSettled(
        owner = owner,
        isProgrammaticScrollInFlight = scrollExecutor.isInFlight,
      )
    }
  }

  /** 挂起等待滚轮恢复空闲或[owner]失效，返回恢复后[owner]是否仍有效非空 */
  private suspend fun awaitIdle(owner: Any): Boolean {
    snapshotFlow {
      val currentAttachment = store.attachment
      currentAttachment?.owner !== owner || currentAttachment.count <= 0 || !isBusy()
    }.first { it }
    val afterAttachment = store.attachment
    return afterAttachment?.owner === owner && afterAttachment.count > 0
  }
}

private data class ObserveSnapshot(
  val attachment: WheelAttachment?,
  val liveCount: Int?,
  val isScrollInProgress: Boolean,
  val isDragged: Boolean,
  val awaitingHandoffId: Int,
  val isProgrammaticScrollInFlight: Boolean,
  val firstVisibleItemIndex: Int,
  val firstVisibleItemScrollOffset: Int,
  val hasVisibleItems: Boolean,
  val isPositioned: Boolean,
  val totalItemsCount: Int,
)

private data class AutoSnapState(
  val firstVisibleItemIndex: Int,
  val scrollOffset: Int,
  val count: Int,
  val itemSizePx: Int,
)
