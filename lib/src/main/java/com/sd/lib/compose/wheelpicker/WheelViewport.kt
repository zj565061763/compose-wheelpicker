package com.sd.lib.compose.wheelpicker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal const val MAX_CONSTRAINT_AXIS_PX = 262_142
private const val MAX_UNBOUNDED_MAIN_AXIS_PX = 32_766

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
      unfocusedCount = calculateSafeUnfocusedCount(
        maxSizePx = Constraints.Infinity,
        itemSizePx = itemSizePx,
        unfocusedCount = initialUnfocusedCount,
      ),
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

  /** 记录主轴约束上限，收缩单侧未选中条目数并更新首尾内边距 */
  fun onMeasure(
    orientation: WheelOrientation,
    density: Density,
    maxMainAxisPx: Int,
    itemSizePx: Int,
    requestedUnfocusedCount: Int,
  ): Int {
    _lastMaxMainAxisPx = maxMainAxisPx
    val safeUnfocusedCount = calculateSafeUnfocusedCount(
      maxSizePx = maxMainAxisPx,
      itemSizePx = itemSizePx,
      unfocusedCount = requestedUnfocusedCount,
    )
    _paddingValues.update(
      orientation = orientation,
      paddingDp = calculatePaddingDp(
        density = density,
        itemSizePx = itemSizePx,
        unfocusedCount = safeUnfocusedCount,
      ),
    )
    return safeUnfocusedCount
  }

  fun updateClip(clip: Boolean) {
    if (shouldClip != clip) {
      shouldClip = clip
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
    if (_orientation != orientation) {
      _orientation = orientation
    }
    if (_paddingDp != paddingDp) {
      _paddingDp = paddingDp
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
      )
      onResolvedUnfocusedCount(safeUnfocusedCount)

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
      viewportState.updateClip(
        clip = layoutWidth < listPlaceable.width ||
          layoutHeight < listPlaceable.height ||
          layoutWidth < focusPlaceable.width ||
          layoutHeight < focusPlaceable.height,
      )

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
        WheelOrientation.Vertical -> clampIntrinsicWidthForHeight(
          rawWidthPx = measurables.getOrNull(1)?.minIntrinsicWidth(itemSizePx) ?: 0,
          maxHeightPx = height,
        )

        WheelOrientation.Horizontal -> calculateIntrinsicMainAxisPx(
          orientation = orientation,
          itemSizePx = itemSizePx,
          unfocusedCount = unfocusedCount,
          crossAxisMaxPx = height,
        )
      }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
      measurables: List<IntrinsicMeasurable>,
      height: Int,
    ): Int {
      return when (orientation) {
        WheelOrientation.Vertical -> clampIntrinsicWidthForHeight(
          rawWidthPx = measurables.getOrNull(1)?.maxIntrinsicWidth(itemSizePx) ?: 0,
          maxHeightPx = height,
        )

        WheelOrientation.Horizontal -> calculateIntrinsicMainAxisPx(
          orientation = orientation,
          itemSizePx = itemSizePx,
          unfocusedCount = unfocusedCount,
          crossAxisMaxPx = height,
        )
      }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
      measurables: List<IntrinsicMeasurable>,
      width: Int,
    ): Int {
      return when (orientation) {
        WheelOrientation.Vertical -> calculateIntrinsicMainAxisPx(
          orientation = orientation,
          itemSizePx = itemSizePx,
          unfocusedCount = unfocusedCount,
          crossAxisMaxPx = width,
        )

        WheelOrientation.Horizontal -> clampIntrinsicHeightForWidth(
          rawHeightPx = measurables.getOrNull(1)?.minIntrinsicHeight(itemSizePx) ?: 0,
          maxWidthPx = width,
        )
      }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
      measurables: List<IntrinsicMeasurable>,
      width: Int,
    ): Int {
      return when (orientation) {
        WheelOrientation.Vertical -> calculateIntrinsicMainAxisPx(
          orientation = orientation,
          itemSizePx = itemSizePx,
          unfocusedCount = unfocusedCount,
          crossAxisMaxPx = width,
        )

        WheelOrientation.Horizontal -> clampIntrinsicHeightForWidth(
          rawHeightPx = measurables.getOrNull(1)?.maxIntrinsicHeight(itemSizePx) ?: 0,
          maxWidthPx = width,
        )
      }
    }
  }
}

/** 将[itemSize]换算为限制在 Compose [Constraints] 可表示范围内的有效像素尺寸 */
internal fun resolveWheelItemSizePx(
  density: Density,
  itemSize: Dp,
): Int {
  val rawPx = with(density) {
    val px = itemSize.toPx()
    when {
      px.isNaN() -> 1
      px >= MAX_CONSTRAINT_AXIS_PX.toFloat() -> MAX_CONSTRAINT_AXIS_PX
      else -> itemSize.roundToPx()
    }
  }
  return rawPx.coerceIn(1, MAX_CONSTRAINT_AXIS_PX)
}

/** 将单个条目主轴尺寸固定为限制在父约束内的[sizePx] */
internal fun Modifier.itemMainAxisSize(
  orientation: WheelOrientation,
  sizePx: Int,
): Modifier {
  return layout { measurable, constraints ->
    val resolvedSizePx = when (orientation) {
      WheelOrientation.Vertical -> sizePx.coerceIn(constraints.minHeight, constraints.maxHeight)
      WheelOrientation.Horizontal -> sizePx.coerceIn(constraints.minWidth, constraints.maxWidth)
    }
    val childConstraints = when (orientation) {
      WheelOrientation.Vertical -> Constraints.fitPrioritizingHeight(
        minWidth = constraints.minWidth,
        maxWidth = constraints.maxWidth,
        minHeight = resolvedSizePx,
        maxHeight = resolvedSizePx,
      )

      WheelOrientation.Horizontal -> Constraints.fitPrioritizingWidth(
        minWidth = resolvedSizePx,
        maxWidth = resolvedSizePx,
        minHeight = constraints.minHeight,
        maxHeight = constraints.maxHeight,
      )
    }
    val placeable = measurable.measure(childConstraints)
    layout(placeable.width, placeable.height) {
      placeable.placeRelative(0, 0)
    }
  }
}

/** 根据主轴可用空间与单轴上限计算不发生溢出的单侧未选中条目数 */
internal fun calculateSafeUnfocusedCount(
  maxSizePx: Int,
  itemSizePx: Int,
  unfocusedCount: Int,
): Int {
  if (itemSizePx <= 0 || unfocusedCount <= 0) {
    return 0
  }
  val clampedItemPx = itemSizePx.coerceAtMost(MAX_CONSTRAINT_AXIS_PX)
  val effectiveMaxPx = if (maxSizePx == Constraints.Infinity) {
    MAX_UNBOUNDED_MAIN_AXIS_PX.coerceAtLeast(clampedItemPx)
  } else {
    maxSizePx.coerceAtMost(MAX_CONSTRAINT_AXIS_PX)
  }
  if (effectiveMaxPx <= clampedItemPx) {
    return 0
  }
  val maxBySpace = (effectiveMaxPx - clampedItemPx) / 2 / clampedItemPx
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
  val total = (unfocusedCount.toLong() + 1L) * itemSizePx.toLong()
  if (total <= 0L) {
    return null
  }

  val minDiffIndex = -(unfocusedCount.toLong() + 1L)
  val maxDiffIndex = unfocusedCount.toLong() + 2L
  val diffIndex = (index.toLong() - firstVisibleItemIndex.toLong()).coerceIn(minDiffIndex, maxDiffIndex)
  val diff = diffIndex * itemSizePx.toLong() - firstVisibleItemScrollOffset.toLong()
  val percentage = (diff.toFloat() / total.toFloat()).coerceIn(-1f, 1f)
  if (percentage == 0f) {
    return 0f
  }
  return if (reverseDirection) -percentage else percentage
}

private fun calculateIntrinsicMainAxisPx(
  orientation: WheelOrientation,
  itemSizePx: Int,
  unfocusedCount: Int,
  crossAxisMaxPx: Int,
): Int {
  val clampedItemPx = itemSizePx.coerceIn(1, MAX_CONSTRAINT_AXIS_PX)
  val safeCount = calculateSafeUnfocusedCount(
    maxSizePx = Constraints.Infinity,
    itemSizePx = clampedItemPx,
    unfocusedCount = unfocusedCount,
  )
  val mainAxisPx = clampedItemPx * (safeCount * 2 + 1)
  return when (orientation) {
    WheelOrientation.Vertical -> clampIntrinsicHeightForWidth(rawHeightPx = mainAxisPx, maxWidthPx = crossAxisMaxPx)
    WheelOrientation.Horizontal -> clampIntrinsicWidthForHeight(rawWidthPx = mainAxisPx, maxHeightPx = crossAxisMaxPx)
  }
}

private fun calculatePaddingDp(
  density: Density,
  itemSizePx: Int,
  unfocusedCount: Int,
): Dp {
  return with(density) { (itemSizePx * unfocusedCount).toDp() }
}

private fun clampIntrinsicWidthForHeight(
  rawWidthPx: Int,
  maxHeightPx: Int,
): Int {
  val clampedWidth = rawWidthPx.coerceIn(0, MAX_CONSTRAINT_AXIS_PX)
  if (maxHeightPx == Constraints.Infinity || maxHeightPx <= 0) {
    return clampedWidth
  }
  return Constraints.fitPrioritizingHeight(
    minWidth = clampedWidth,
    maxWidth = clampedWidth,
    minHeight = 0,
    maxHeight = maxHeightPx.coerceAtMost(MAX_CONSTRAINT_AXIS_PX),
  ).maxWidth
}

private fun clampIntrinsicHeightForWidth(
  rawHeightPx: Int,
  maxWidthPx: Int,
): Int {
  val clampedHeight = rawHeightPx.coerceIn(0, MAX_CONSTRAINT_AXIS_PX)
  if (maxWidthPx == Constraints.Infinity || maxWidthPx <= 0) {
    return clampedHeight
  }
  return Constraints.fitPrioritizingWidth(
    minWidth = 0,
    maxWidth = maxWidthPx.coerceAtMost(MAX_CONSTRAINT_AXIS_PX),
    minHeight = clampedHeight,
    maxHeight = clampedHeight,
  ).maxHeight
}
