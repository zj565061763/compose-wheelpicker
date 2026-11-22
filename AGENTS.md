# AGENTS.md

## 项目概览

- `compose-wheelpicker` 是基于 Jetpack Compose 的垂直与水平滚轮选择器库，Maven 坐标为 `io.github.zj565061763.android:compose-wheelpicker`。
- 仓库包含发布库 `:lib`（`com.sd.lib.compose.wheelpicker`）与演示应用 `:app`（`com.sd.demo.compose.wheelpicker`）两个模块。
- `:lib` 仅依赖 `androidx.compose.foundation`，不引入 `material3` 等上层设计系统依赖。

| 项目 | 配置 / 路径 | 说明 |
|---|---|---|
| 构建工具链 | Gradle `8.11.1`、AGP `8.7.3`、Kotlin `1.9.25` | Java / Kotlin 编译目标均为 `1.8`，Compose BOM 为 `2025.07.00`（Compiler `1.5.15`） |
| SDK 版本 | `compileSdk = 35`、`minSdk = 21`、`targetSdk = 35`（`:app`） | 统一由 `gradle/libs.versions.toml` 与模块脚本管理 |
| `:lib` 源码 | `lib/src/main/java/com/sd/lib/compose/wheelpicker/` | 全部可复用公开 API 与内部实现 |
| `:lib` 发布 | `lib/build.gradle.kts`、`lib/gradle.properties` | `com.vanniktech.maven.publish` 单变体 `release` 发布（含源码与 Javadoc JAR） |
| `:app` 示例 | `app/src/main/java/com/sd/demo/compose/wheelpicker/` | 演示默认滚轮、自定义展示与焦点、日期联动、反向排列与跳转 |

## 常用命令

所有命令在仓库根目录执行：

```bash
# 构建发布 AAR 与示例 Debug APK
./gradlew :lib:assembleRelease :app:assembleDebug

# 运行库模块全部本地 JVM 单元测试
./gradlew :lib:testDebugUnitTest

# 运行单个 JVM 单元测试方法
./gradlew :lib:testDebugUnitTest \
  --tests "com.sd.lib.compose.wheelpicker.WheelPickerTest.testStateLifecycleShrinkClampAndSaver"

# 在指定模拟器上运行库模块仪器测试（同时连接真机时必须指定 ANDROID_SERIAL）
ANDROID_SERIAL=emulator-5554 ./gradlew :lib:connectedDebugAndroidTest

# 运行单个仪器测试方法
ANDROID_SERIAL=emulator-5554 ./gradlew :lib:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class="com.sd.lib.compose.wheelpicker.WheelPickerInstrumentedTest#testModalBottomSheetNestedScrollFlingAndSnap"

# 执行 Android Lint 静态检查
./gradlew lint
```

## 核心架构与行为不变量

| 文件 | 职责 |
|---|---|
| `WheelPicker.kt` | `VerticalWheelPicker` / `HorizontalWheelPicker` 入口、参数校验、状态生命周期挂载及 `LazyColumn` / `LazyRow` 组装 |
| `WheelViewport.kt` | 主轴视口尺寸计算、父容器受限时 `unfocusedCount` 自动缩小、固有尺寸测量、裁剪判定与偏移比例计算 |
| `WheelPickerState.kt` | 公开状态门面、编程式跳转调度、`rememberWheelPickerState`、`SnappedIndex` 及 `Saver` 保存恢复 |
| `WheelPositionStore.kt` | 挂载/卸载状态维护、中心与吸附索引同步计算、预跳转目标记录及缩容边界夹紧 |
| `WheelSnapObserver.kt` | 滚动快照观察、离手帧边界让出衔接与静止非零偏移自动归位 |
| `WheelPickerDisplay.kt` | `WheelPickerDisplayScope` 与默认缩放透明度展示效果 `DefaultWheelPickerDisplay` |
| `WheelPickerDefault.kt` | 默认中心焦点上下/左右分割线指示器 `WheelPickerFocusVertical` / `WheelPickerFocusHorizontal` |

修改 `:lib` 时必须保持以下不变量：

- 参数校验：`count() >= 0`、`itemSize` 为大于 `0.dp` 的有限值、`unfocusedCount >= 0`，非法参数立即抛 `IllegalArgumentException`。
- 主轴自适应与裁剪：主轴尺寸为 `itemSizePx * (safeUnfocusedCount * 2 + 1)`，父容器有限空间不足时自动缩小 `safeUnfocusedCount`，仍小于子项时启用裁剪。
- 交叉轴固有尺寸：只查询 `focus` 槽位，严禁查询底层 `LazyList` 节点（会触发 Compose 异常）。
- 单实例与空态语义：同一个 `WheelPickerState` 同时只能绑定一个滚轮，未挂载或 `count == 0` 时 `currentIndex` 与 `snappedIndex` 均为 `-1`、`scrollPercentageOf` 返回 `null`。
- 视觉方向符号：RTL 布局下的水平滚轮与 `reverseLayout = true` 需保持 `scrollPercentageOf` 视觉方向符号一致。
- 离手衔接与静止自动归位：禁用默认 `overscrollEffect`，滚动刚停止且处于非零像素偏移时先等待一帧边界并让出调度以衔接常规惯性滑动，仍停留在非零偏移时自动归位至最近条目并更新 `snappedIndex`。
- 预跳转：未挂载、空数据或首次测量完成前调用 `scrollToIndex` / `animateScrollToIndex` 不挂起，通过 `requestScrollToItem` 记录目标并在非空挂载测量后限制在 `0..<count` 内生效；常规并发滚动抢占直接委托给底层 `LazyListState` 的 `MutatorMutex`。
- 缩容边界收敛：列表缩容导致 `snappedIndex >= count` 时先将上一轮吸附值立即夹紧至 `count - 1`，避免覆盖底层存活稳定 `key` 锚点，待新列表完成测量且滚动静止后再按实际中心项更新 `snappedIndex`。

## 设计取舍与代码审查约定

- 以官方组件为标准：状态管理、滚动、嵌套滚动、分割线绘制与无障碍语义统一对齐官方 `LazyColumn`、`LazyListState`、`Pager`、`PagerState` 及 `HorizontalDivider` / `VerticalDivider` 的常规边界。
- 不为极端情况复杂化架构：实现与修复时保持整体设计简洁，不要为了处理极端或人工构造的边界情况改变整体设计、引入复杂机制或编写极端交错测试。
- 代码审查与问题核查对齐官方边界：自主审查或核查审查意见时，必须按“以官方组件为标准、对齐官方边界、有意设计不得作为缺陷或功能缺失报告”的约束过滤，官方组件同样未兜底或未在 KDoc 说明的场景不认定为问题。

以下具体取舍均属有意设计，代码审查不得作为缺陷或功能缺失报告：

- `count` 惰性求值与 `key` 底层透传：`VerticalWheelPicker` 与 `HorizontalWheelPicker` 接收 `count: () -> Int` 在组合期、`LazyListScope` 与编程式滚动执行前读取最新条目数，`key` 参数直接透传给底层 `LazyList`，由 `LazyListState` 原生最近邻窗口（约 ±100 项）保持可见锚点，再由 `WheelSnapObserver` 在静止时同步 `snappedIndex`。
- 不在公开状态读取与内部观察流中主动求值 `count()`：与官方 `PagerState` 一致，`currentIndex`、`snappedIndex`、`scrollPercentageOf` 及 `WheelSnapObserver` 仅基于已同步的挂载计数计算，修改列表后未完成重组或跳转前读取公开状态仍为上一轮已同步值。
- 不兜底混用实时 `count` 与旧列表快照的越界：与官方 `Pager` 一致，同帧更新数据并在重组前立即跳转时，调用方需在 `count`、`key` 与 `content` 的 lambda 内读取同一列表状态，库层不为混用实时条目数与重组期捕获的不可变列表导致的越界提供兜底。
- 不兜底或额外说明非可观察的 `count` 数据源：与官方 `LazyColumn` 及 `rememberPagerState` 一致，动态数据使用 Compose 可观察状态属通用前提，既不为直接裸读 `StateFlow.value` 或普通可变集合等非可观察数据源增加兜底，也不在 `count` 的公开 KDoc 中额外说明该前提。
- 不支持单个 `WheelPickerState` 同时挂载多个滚轮实例：与官方 `LazyListState` 及 `PagerState` 一致，仅支持单实例挂载与常规 `if / else` 条件分支顺序切换，不为 `AnimatedContent`、`Crossfade` 等过渡期新旧实例并存共享同一 `state` 做多实例状态转存或协同。
- 编程式滚动并发与中断对齐 `LazyListState`：不维护独立的请求 ID、子协程包装或自定义取消吞吐，并发抢占、手势打断与组件切换中断统一遵循底层 `LazyListState` 原生行为。
- 不提供按 `key` 跳转 API：不提供 `scrollToKey` / `animateScrollToKey`，调用方需自行在数据源查找索引后调用 `scrollToIndex` / `animateScrollToIndex`。
- 不做跨窗口重排追踪：不在状态层维护独立 `key` 缓存或全表扫描，单次重排跨度超出 `LazyList` 原生最近邻窗口（约 ±100 项）时按原索引位置回退。
- 不支持逐项独立 `MutableState` 暗中突变：不支持在稳定 `key` 闭包内用每个条目独立的 `MutableState` 原地改值却不触发列表重组，数据变更需通过不可变列表重组或 `SnapshotStateList` 结构修改驱动。
- 不跨空列表或重挂载记忆旧 `key`：列表变空（`count() == 0`）或组件卸载重挂载后统一按最近已知索引恢复并限制在新 `0..<count` 范围内，重新挂载时重置底层残留锚点。
- 不维护额外的缩容末项像素偏移修正：列表主轴可用空间恒不小于单个条目尺寸，缩容后末项正偏移回拉归零交由 `LazyListMeasure` 原生滚动回退处理，不在状态层或观察流中额外调用 `requestScrollToItem` 干预。
- 不为 `LookaheadScope` 尺寸伸缩动画维护双轨内边距：滚轮视口内边距直接由实际测量阶段按安全 `unfocusedCount` 更新，不为对滚轮自身施加 `animateBounds` 且动画途中并发调用 `scrollToIndex` 的低频场景维护测量栈覆盖修饰符。
- 不兜底超出 Compose `Constraints` 上限的极端尺寸或数值：与官方 `Modifier.height`、`PageSize.Fixed`、`IntrinsicSize` 及 `PagerState` 一致，不为极端超大 `itemSize`、无界容器下 `Int.MAX_VALUE` 级 `unfocusedCount` 或 `Int.MIN_VALUE` / `Int.MAX_VALUE` 级偏移索引差值做截断与 `Long` 防溢出兜底。
- 不对默认焦点分割线做非常规 `thickness` 截断兜底：与官方 `HorizontalDivider` / `VerticalDivider` 一致，`WheelPickerFocusVertical` 与 `WheelPickerFocusHorizontal` 仅将 `Dp.Hairline` 绘制为 `1px` 细线并对负值不绘制，不为 `Dp.Unspecified`、`Dp.Infinity` 或超出焦点区域尺寸的 `thickness` 做额外截断、校验或公开 KDoc 说明。
- 不在动画飞行途中按 `key` 改道：`animateScrollToIndex` 执行期间若发生数据增删重排，不中途取消并追踪旧 `key`，始终滚向调用时锁定的目标索引。
- 不替外部无帧时钟协程代借帧时钟或额外补充帧时钟文档：`animateScrollToIndex` 的行为与 KDoc 均与官方 `LazyListState.animateScrollToItem`、`PagerState.animateScrollToPage` 一致，需在含 `MonotonicFrameClock` 的 Compose 协程作用域（如 `LaunchedEffect`、`rememberCoroutineScope`）中调用。
- 不在 `snappedIndex` 公开 KDoc 中罗列缩容夹紧等边界例外：与官方 `PagerState.settledPage` 的 KDoc 一致，公开注释仅说明滚动中保持上一次吸附值并在停稳后更新的常规语义，非空缩容越界时立即夹紧至 `count - 1` 属防越界安全不变量，不在单行公开 KDoc 中堆砌该细节。
- 不对多帧挂起或异常恢复的自定义父级嵌套滚动增加额外看门狗：嵌套滚动衔接对齐官方 `LazyColumn` / `Pager` 与常规 `ModalBottomSheet` 行为，不挂载空壳 `NestedScrollConnection` 或多帧超时看门狗防御父容器在 `onPreFling` 中跨多帧挂起。
- 不向内层自定义可点击节点下发选中语义：条目外层 `Box` 通过 `semantics(mergeDescendants = true)` 为常规非交互内容合并暴露 `Selected` 状态，调用方若在 `content` 内自行使用 `Modifier.clickable` 等自带语义合并的交互节点，需按 Compose 规则自行使用 `Modifier.selectable` 声明选中态。
- 不支持条目内嵌可滚动子容器的独立嵌套滚动隔离：滚轮条目面向常规展示内容设计，不考虑在 `content` 内嵌 `LazyRow`、`LazyColumn` 等子级滚动容器的场景。
- 保持 `compose-foundation` 为 `implementation` 依赖：常规接入项目均会引入 `material3`、`material` 或 `foundation`，`:lib` 保持 `implementation(composeBom)` 与 `implementation(libs.androidx.compose.foundation)` 以避免向消费方传递版本约束，不改为 `api` 声明。

## 测试与仓库规范

- 纯算法、参数校验、视口测量策略与基础状态生命周期放入本地 JVM 测试 `WheelPickerTest`；涉及真实 `LazyListState` 滚动、嵌套滚动手势、动画打断、动态增删改跳转与 RTL 布局的行为放入 `WheelPickerInstrumentedTest`。
- 未经用户明确要求，不主动修改 `README.md`。
- `app/template.jks` 仅用于示例签名，不要提交 `local.properties`、生产密钥或 `build/` 产物。

| 测试类 | 覆盖范围 |
|---|---|
| `WheelPickerTest` | 非法参数校验、有限空间下 `unfocusedCount` 自动缩小、`snappedIndex` 与视觉偏移比例计算（含 `reverseLayout` 与 RTL 组合）、挂载/卸载/空列表过渡/缩容夹紧与 `Saver`、未挂载与首次测量前预跳转、固有尺寸测量与紧约束裁剪及预测测量隔离 |
| `WheelPickerInstrumentedTest` | 垂直/水平固有尺寸与紧约束像素裁剪、`ModalBottomSheet` 嵌套滚动惯性滑动与吸附、手势打断动画与取消动画后自动归位、RTL 水平滚轮视觉偏移与焦点指示器、手势非零偏移缩容夹紧与首尾缩容稳定 `key` 保持、动态扩缩容立即跳转、条件渲染卸载与空态回填恢复、重挂载跳转、条目选中无障碍语义更新 |
