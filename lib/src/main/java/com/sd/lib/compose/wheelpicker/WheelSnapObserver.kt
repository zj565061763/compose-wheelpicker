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

  private suspend fun observeFlingHandoffTimeout(owner: Any) {
    snapshotFlow { store.isAwaitingFlingHandoff }.collectLatest { awaiting ->
      if (!awaiting) {
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
      store.onFlingHandoffResolved(owner)
    }
  }

  private fun isBusy(): Boolean {
    return scrollController.isScrollInProgress ||
      store.isDragged ||
      store.isAwaitingFlingHandoff ||
      scrollExecutor.isInFlight
  }

  private suspend fun observeScrollAndSnap(owner: Any) {
    var wasActiveGestureOrScroll = false
    var lastAutoSnapState: AutoSnapState? = null
    snapshotFlow {
      ObserveSnapshot(
        attachment = store.attachment,
        isScrollInProgress = scrollController.isScrollInProgress,
        isDragged = store.isDragged,
        isAwaitingFlingHandoff = store.isAwaitingFlingHandoff,
        isProgrammaticScrollInFlight = scrollExecutor.isInFlight,
        firstVisibleItemIndex = scrollController.firstVisibleItemIndex,
        firstVisibleItemScrollOffset = scrollController.firstVisibleItemScrollOffset,
        hasVisibleItems = scrollController.hasVisibleItems,
        isPositioned = scrollController.isPositioned,
      )
    }.collect { _ ->
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

        val currentOffset = scrollController.firstVisibleItemScrollOffset
        if (!scrollController.hasVisibleItems || currentOffset == 0) {
          lastAutoSnapState = null
          break
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

  private suspend fun awaitIdle(owner: Any): Boolean {
    snapshotFlow {
      val currentAttachment = store.attachment
      when {
        currentAttachment?.owner !== owner || currentAttachment.count <= 0 -> true
        isBusy() -> {
          store.updateLastKnownIndexFromLive()
          false
        }
        else -> true
      }
    }.first { it }
    val afterAttachment = store.attachment
    return afterAttachment?.owner === owner && afterAttachment.count > 0
  }
}

private data class ObserveSnapshot(
  val attachment: WheelAttachment?,
  val isScrollInProgress: Boolean,
  val isDragged: Boolean,
  val isAwaitingFlingHandoff: Boolean,
  val isProgrammaticScrollInFlight: Boolean,
  val firstVisibleItemIndex: Int,
  val firstVisibleItemScrollOffset: Int,
  val hasVisibleItems: Boolean,
  val isPositioned: Boolean,
)

private data class AutoSnapState(
  val firstVisibleItemIndex: Int,
  val scrollOffset: Int,
  val count: Int,
  val itemSizePx: Int,
)
