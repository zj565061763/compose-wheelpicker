package com.sd.lib.compose.wheelpicker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal enum class WheelOrientation {
  Vertical,
  Horizontal,
  ;

  fun maxMainAxisSize(constraints: Constraints): Int {
    return when (this) {
      Vertical -> constraints.maxHeight
      Horizontal -> constraints.maxWidth
    }
  }

  fun isVisualDirectionReversed(
    reverseLayout: Boolean,
    layoutDirection: LayoutDirection,
  ): Boolean {
    return when (this) {
      Vertical -> reverseLayout
      Horizontal -> reverseLayout xor (layoutDirection == LayoutDirection.Rtl)
    }
  }
}

@Stable
internal class WheelViewportState(
  orientation: WheelOrientation,
  density: Density,
  itemSizePx: Int,
  initialUnfocusedCount: Int,
) {
  private var _lastMaxMainAxisPx = Constraints.Infinity

  private val _paddingValues = WheelPaddingValues(
    orientation = orientation,
    paddingDp = calculatePaddingDp(
      density = density,
      itemSizePx = itemSizePx,
      unfocusedCount = initialUnfocusedCount,
    ),
  )

  val contentPadding: PaddingValues
    get() {
      return _paddingValues
    }

  var shouldClip by mutableStateOf(false)
    private set

  /** 按最近一次主轴约束像素上限计算安全的单侧未选中条目数 */
  fun resolveUnfocusedCount(
    itemSizePx: Int,
    requestedUnfocusedCount: Int,
  ): Int {
    return calculateSafeUnfocusedCount(
      maxSizePx = _lastMaxMainAxisPx,
      itemSizePx = itemSizePx,
      unfocusedCount = requestedUnfocusedCount,
    )
  }

  /** 记录主轴约束上限，收缩单侧未选中条目数并在实际测量阶段更新首尾内边距 */
  fun onMeasure(
    orientation: WheelOrientation,
    density: Density,
    maxMainAxisPx: Int,
    itemSizePx: Int,
    requestedUnfocusedCount: Int,
    isLookingAhead: Boolean = false,
  ): Int {
    val safeUnfocusedCount = calculateSafeUnfocusedCount(
      maxSizePx = maxMainAxisPx,
      itemSizePx = itemSizePx,
      unfocusedCount = requestedUnfocusedCount,
    )
    if (!isLookingAhead) {
      _lastMaxMainAxisPx = maxMainAxisPx
      _paddingValues.update(
        orientation = orientation,
        paddingDp = calculatePaddingDp(
          density = density,
          itemSizePx = itemSizePx,
          unfocusedCount = safeUnfocusedCount,
        ),
      )
    }
    return safeUnfocusedCount
  }

  fun updateClip(clip: Boolean) {
    Snapshot.withoutReadObservation {
      if (shouldClip != clip) {
        shouldClip = clip
      }
    }
  }
}

@Stable
private class WheelPaddingValues(
  orientation: WheelOrientation,
  paddingDp: Dp,
) : PaddingValues {
  private var _orientation by mutableStateOf(orientation)
  private var _paddingDp by mutableStateOf(paddingDp)

  fun update(
    orientation: WheelOrientation,
    paddingDp: Dp,
  ) {
    Snapshot.withoutReadObservation {
      if (_orientation != orientation) {
        _orientation = orientation
      }
      if (_paddingDp != paddingDp) {
        _paddingDp = paddingDp
      }
    }
  }

  override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp {
    return if (_orientation == WheelOrientation.Horizontal) _paddingDp else 0.dp
  }

  override fun calculateTopPadding(): Dp {
    return if (_orientation == WheelOrientation.Vertical) _paddingDp else 0.dp
  }

  override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp {
    return if (_orientation == WheelOrientation.Horizontal) _paddingDp else 0.dp
  }

  override fun calculateBottomPadding(): Dp {
    return if (_orientation == WheelOrientation.Vertical) _paddingDp else 0.dp
  }
}

/** 承载滚轮列表与中心焦点槽位，空间不足时自动开启绘制裁剪 */
@Composable
internal fun WheelViewportBox(
  modifier: Modifier = Modifier,
  orientation: WheelOrientation,
  itemSizePx: Int,
  unfocusedCount: Int,
  viewportState: WheelViewportState,
  onResolvedUnfocusedCount: (Int) -> Unit,
  listContent: @Composable () -> Unit,
  focusContent: @Composable () -> Unit,
) {
  val density = LocalDensity.current
  val onResolvedState = rememberUpdatedState(onResolvedUnfocusedCount)
  val measurePolicy = remember(orientation, density, itemSizePx, unfocusedCount, viewportState) {
    createWheelViewportMeasurePolicy(
      orientation = orientation,
      itemSizePx = itemSizePx,
      unfocusedCount = unfocusedCount,
      viewportState = viewportState,
      onResolvedUnfocusedCount = { onResolvedState.value(it) },
    )
  }

  Layout(
    modifier = modifier.graphicsLayer {
      clip = viewportState.shouldClip
    },
    content = {
      listContent()
      Box(contentAlignment = Alignment.Center) {
        focusContent()
      }
    },
    measurePolicy = measurePolicy,
  )
}

/** 创建视口测量策略，自适应收缩主轴尺寸并仅通过焦点槽位查询交叉轴固有尺寸 */
internal fun createWheelViewportMeasurePolicy(
  orientation: WheelOrientation,
  itemSizePx: Int,
  unfocusedCount: Int,
  viewportState: WheelViewportState,
  onResolvedUnfocusedCount: (Int) -> Unit,
): MeasurePolicy {
  return object : MeasurePolicy {
    override fun MeasureScope.measure(
      measurables: List<Measurable>,
      constraints: Constraints,
    ): MeasureResult {
      val maxMainAxisPx = orientation.maxMainAxisSize(constraints)
      val safeUnfocusedCount = viewportState.onMeasure(
        orientation = orientation,
        density = this,
        maxMainAxisPx = maxMainAxisPx,
        itemSizePx = itemSizePx,
        requestedUnfocusedCount = unfocusedCount,
        isLookingAhead = isLookingAhead,
      )
      if (!isLookingAhead) {
        onResolvedUnfocusedCount(safeUnfocusedCount)
      }

      val viewportMainAxisPx = itemSizePx * (safeUnfocusedCount * 2 + 1)
      val listConstraints = when (orientation) {
        WheelOrientation.Vertical -> Constraints.fitPrioritizingHeight(
          minWidth = constraints.minWidth,
          maxWidth = constraints.maxWidth,
          minHeight = viewportMainAxisPx,
          maxHeight = viewportMainAxisPx,
        )

        WheelOrientation.Horizontal -> Constraints.fitPrioritizingWidth(
          minWidth = viewportMainAxisPx,
          maxWidth = viewportMainAxisPx,
          minHeight = constraints.minHeight,
          maxHeight = constraints.maxHeight,
        )
      }

      val listPlaceable = measurables[0].measure(listConstraints)
      val layoutWidth: Int
      val layoutHeight: Int
      val focusConstraints: Constraints
      when (orientation) {
        WheelOrientation.Vertical -> {
          layoutWidth = listPlaceable.width.coerceIn(constraints.minWidth, constraints.maxWidth)
          layoutHeight = viewportMainAxisPx.coerceIn(constraints.minHeight, constraints.maxHeight)
          focusConstraints = Constraints.fitPrioritizingHeight(
            minWidth = layoutWidth,
            maxWidth = layoutWidth,
            minHeight = itemSizePx,
            maxHeight = itemSizePx,
          )
        }

        WheelOrientation.Horizontal -> {
          layoutWidth = viewportMainAxisPx.coerceIn(constraints.minWidth, constraints.maxWidth)
          layoutHeight = listPlaceable.height.coerceIn(constraints.minHeight, constraints.maxHeight)
          focusConstraints = Constraints.fitPrioritizingWidth(
            minWidth = itemSizePx,
            maxWidth = itemSizePx,
            minHeight = layoutHeight,
            maxHeight = layoutHeight,
          )
        }
      }

      val focusPlaceable = measurables[1].measure(focusConstraints)
      if (!isLookingAhead) {
        viewportState.updateClip(
          clip = layoutWidth < listPlaceable.width ||
            layoutHeight < listPlaceable.height ||
            layoutWidth < focusPlaceable.width ||
            layoutHeight < focusPlaceable.height,
        )
      }

      val listX = (layoutWidth - listPlaceable.width) / 2
      val listY = (layoutHeight - listPlaceable.height) / 2
      val focusX = (layoutWidth - focusPlaceable.width) / 2
      val focusY = (layoutHeight - focusPlaceable.height) / 2

      return layout(layoutWidth, layoutHeight) {
        listPlaceable.placeRelative(x = listX, y = listY)
        focusPlaceable.placeRelative(x = focusX, y = focusY)
      }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
      measurables: List<IntrinsicMeasurable>,
      height: Int,
    ): Int {
      return when (orientation) {
        WheelOrientation.Vertical -> measurables.getOrNull(1)?.minIntrinsicWidth(itemSizePx) ?: 0
        WheelOrientation.Horizontal -> calculateIntrinsicMainAxisPx(itemSizePx = itemSizePx, unfocusedCount = unfocusedCount)
      }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
      measurables: List<IntrinsicMeasurable>,
      height: Int,
    ): Int {
      return when (orientation) {
        WheelOrientation.Vertical -> measurables.getOrNull(1)?.maxIntrinsicWidth(itemSizePx) ?: 0
        WheelOrientation.Horizontal -> calculateIntrinsicMainAxisPx(itemSizePx = itemSizePx, unfocusedCount = unfocusedCount)
      }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
      measurables: List<IntrinsicMeasurable>,
      width: Int,
    ): Int {
      return when (orientation) {
        WheelOrientation.Vertical -> calculateIntrinsicMainAxisPx(itemSizePx = itemSizePx, unfocusedCount = unfocusedCount)
        WheelOrientation.Horizontal -> measurables.getOrNull(1)?.minIntrinsicHeight(itemSizePx) ?: 0
      }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
      measurables: List<IntrinsicMeasurable>,
      width: Int,
    ): Int {
      return when (orientation) {
        WheelOrientation.Vertical -> calculateIntrinsicMainAxisPx(itemSizePx = itemSizePx, unfocusedCount = unfocusedCount)
        WheelOrientation.Horizontal -> measurables.getOrNull(1)?.maxIntrinsicHeight(itemSizePx) ?: 0
      }
    }
  }
}

/** 将单个条目主轴尺寸固定为[size] */
internal fun Modifier.itemMainAxisSize(
  orientation: WheelOrientation,
  size: Dp,
): Modifier {
  return when (orientation) {
    WheelOrientation.Vertical -> height(size)
    WheelOrientation.Horizontal -> width(size)
  }
}

/** 根据主轴可用空间计算单侧未选中条目数，空间不足时自动缩小 */
internal fun calculateSafeUnfocusedCount(
  maxSizePx: Int,
  itemSizePx: Int,
  unfocusedCount: Int,
): Int {
  if (itemSizePx <= 0 || unfocusedCount <= 0) {
    return 0
  }
  if (maxSizePx == Constraints.Infinity) {
    return unfocusedCount
  }
  if (maxSizePx <= itemSizePx) {
    return 0
  }
  val maxBySpace = (maxSizePx - itemSizePx) / 2 / itemSizePx
  return minOf(unfocusedCount, maxBySpace)
}

/** 按首项滚动偏移过半规则计算当前靠近中心的条目索引 */
internal fun calculateSnappedIndex(
  firstVisibleItemIndex: Int,
  firstVisibleItemScrollOffset: Int,
  count: Int,
  itemSizePx: Int,
): Int {
  if (count <= 0) {
    return -1
  }
  val rounded = if (firstVisibleItemScrollOffset > itemSizePx / 2) {
    firstVisibleItemIndex + 1
  } else {
    firstVisibleItemIndex
  }
  return rounded.coerceIn(0, count - 1)
}

/** 计算[index]相对中心焦点的归一化偏移比例，[reverseDirection]为true时翻转符号 */
internal fun calculateScrollPercentage(
  index: Int,
  firstVisibleItemIndex: Int,
  firstVisibleItemScrollOffset: Int,
  unfocusedCount: Int,
  itemSizePx: Int,
  reverseDirection: Boolean = false,
): Float? {
  if (itemSizePx <= 0 || unfocusedCount < 0) {
    return null
  }
  val total = (unfocusedCount + 1) * itemSizePx
  val diffIndex = (index - firstVisibleItemIndex).coerceIn(-(unfocusedCount + 1), unfocusedCount + 2)
  val diff = diffIndex * itemSizePx - firstVisibleItemScrollOffset
  val percentage = (diff.toFloat() / total.toFloat()).coerceIn(-1f, 1f)
  if (percentage == 0f) {
    return 0f
  }
  return if (reverseDirection) -percentage else percentage
}

private fun calculateIntrinsicMainAxisPx(
  itemSizePx: Int,
  unfocusedCount: Int,
): Int {
  return itemSizePx * (unfocusedCount * 2 + 1)
}

private fun calculatePaddingDp(
  density: Density,
  itemSizePx: Int,
  unfocusedCount: Int,
): Dp {
  return with(density) { (itemSizePx * unfocusedCount).toDp() }
}
