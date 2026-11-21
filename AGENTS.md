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
  --tests "com.sd.lib.compose.wheelpicker.WheelPickerTest.testNativeKeyTrackingSyncsSnappedIndexOnInsertDeleteAndReorder"

# 在指定模拟器上运行库模块仪器测试（同时连接真机时必须指定 ANDROID_SERIAL）
ANDROID_SERIAL=emulator-5554 ./gradlew :lib:connectedDebugAndroidTest

# 运行单个仪器测试方法
ANDROID_SERIAL=emulator-5554 ./gradlew :lib:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class="com.sd.lib.compose.wheelpicker.WheelPickerInstrumentedTest#testModalBottomSheetAndMultiFrameOnPreFlingNestedScrollSafety"

# 执行 Android Lint 静态检查
./gradlew lint
```

## 核心架构与行为不变量

| 文件 | 职责 |
|---|---|
| `WheelPicker.kt` | `VerticalWheelPicker` / `HorizontalWheelPicker` 入口、参数校验、`NestedScrollConnection` 离手衔接及 `LazyColumn` / `LazyRow` 组装 |
| `WheelViewport.kt` | 主轴视口尺寸计算、父容器受限时 `unfocusedCount` 自动缩小、固有尺寸测量、裁剪判定与偏移比例计算 |
| `WheelPickerState.kt` | 公开状态门面、`rememberWheelPickerState`、`SnappedIndex` 及 `Saver` 保存恢复 |
| `WheelPositionStore.kt` | 挂载/卸载会话、手势与离手标记、中心与吸附索引同步计算及缩容边界夹紧 |
| `WheelScrollExecutor.kt` | 按索引编程式滚动、并发抢占取消、异常兜底复位及自动吸附动作执行 |
| `WheelSnapObserver.kt` | `DragInteraction` 跟踪、离手 fling 保护窗口与静止非零偏移自动吸附观察 |
| `WheelPickerDisplay.kt` | `WheelPickerDisplayScope` 与默认缩放透明度展示效果 `DefaultWheelPickerDisplay` |
| `WheelPickerDefault.kt` | 默认中心焦点上下/左右分割线指示器 `WheelPickerFocusVertical` / `WheelPickerFocusHorizontal` |
| `WheelScrollController.kt` | `LazyListState` 滚动控制抽象，供本地 JVM 测试替身接入 |

修改 `:lib` 时必须保持以下不变量：

- 参数校验：`count() >= 0`、`itemSize` 为大于 `0.dp` 的有限值、`unfocusedCount >= 0`，非法参数立即抛 `IllegalArgumentException`。
- 主轴自适应与裁剪：`itemSizePx` 限制在 `1..262_142`（Compose `Constraints` 单轴上限），主轴尺寸为 `itemSizePx * (safeUnfocusedCount * 2 + 1)`，父容器空间不足时自动缩小 `safeUnfocusedCount`，仍小于子项时启用裁剪。
- 交叉轴固有尺寸：只查询 `focus` 槽位，严禁查询底层 `LazyList` 节点（会触发 Compose 异常）。
- 单实例与空态语义：同一个 `WheelPickerState` 同时只能绑定一个滚轮，未挂载或 `count == 0` 时 `currentIndex` 与 `snappedIndex` 均为 `-1`、`scrollPercentageOf` 返回 `null`。
- 视觉方向符号：RTL 布局下的水平滚轮与 `reverseLayout = true` 需保持 `scrollPercentageOf` 视觉方向符号一致。
- 离手衔接保护：禁用默认 `overscrollEffect`，松手或触摸取消时分配递增交接序号 `awaitingHandoffId` 配合 `NestedScrollConnection`（含按序号匹配的 60 帧超时兜底）等待嵌套滚动父容器完成常规 `onPreFling`，不得提前启动自动吸附截停 fling。
- 静止自动吸附：停止在非零像素偏移时自动归位至最近条目并更新 `snappedIndex`。
- 预跳转与无死锁：未挂载、空数据或首次摆放（`onGloballyPositioned`）完成前调用 `scrollToIndex` / `animateScrollToIndex` 不得挂起死锁，通过 `requestScrollToItem` 记录目标并在非空挂载摆放后限制在 `0..<count` 内生效。
- 缩容边界收敛：列表缩容导致 `snappedIndex >= count` 时仅将上一轮吸附值立即夹紧至 `count - 1`，待新列表完成测量且手势与离手交接全部结束后再更新 `snappedIndex` 并将实际末项残留正偏移归零，避免交接完成前发布未测量的旧索引或覆盖底层存活稳定 `key` 锚点。

## 设计取舍与代码审查约定

- 以官方组件为标准：状态管理、滚动、嵌套滚动与无障碍语义统一对齐官方 `LazyColumn`、`LazyListState` 与 `Pager` 的常规边界。
- 不为极端情况复杂化架构：实现与修复时保持整体设计简洁，不要为了处理极端或人工构造的边界情况改变整体设计、引入复杂机制。
- 代码审查与问题核查对齐官方边界：自主审查或核查审查意见时，必须按“以官方组件为标准、对齐官方边界、有意设计不得作为缺陷或功能缺失报告”的约束过滤，官方组件同样未兜底或未在 KDoc 说明的场景不认定为问题。

以下具体取舍均属有意设计，代码审查不得作为缺陷或功能缺失报告：

- `count` 惰性求值与 `key` 底层透传：`VerticalWheelPicker` 与 `HorizontalWheelPicker` 接收 `count: () -> Int` 在 `LazyListScope` 与编程式滚动执行前惰性读取最新条目数，`key` 参数直接透传给底层 `LazyList`，由 `LazyListState` 原生最近邻窗口（约 ±100 项）保持可见锚点，再由 `WheelSnapObserver` 在静止时同步 `snappedIndex`。
- 不在公开状态读取与内部观察流中主动求值 `count()`：与官方 `PagerState` 一致，`currentIndex`、`snappedIndex`、`scrollPercentageOf` 及 `WheelSnapObserver` 仅基于已同步的挂载计数计算，仅在组合期、测量期与显式调用 `scrollToIndex` / `animateScrollToIndex` 时求值 `count()`，修改列表后未完成重组或跳转前读取公开状态仍为上一轮已同步值，避免 `snapshotFlow` 在条件卸载前抢先求值 `count()`、无关条目语义与图层随增删失效以及空态同帧回填在重组前短暂暴露假索引 `0`。
- 不兜底混用实时 `count` 与旧列表快照的越界：与官方 `Pager` 一致，同帧更新数据并在重组前立即跳转时，调用方需在 `count`、`key` 与 `content` 的 lambda 内读取同一列表状态，库层不为混用实时条目数与重组期捕获的不可变列表导致的越界提供兜底。
- 不兜底或额外说明非可观察的 `count` 数据源：与官方 `LazyColumn` 及 `rememberPagerState` 一致，动态数据使用 Compose 可观察状态属通用前提，既不为直接裸读 `StateFlow.value` 或普通可变集合等非可观察数据源增加兜底，也不在 `count` 的公开 KDoc 中额外说明该前提。
- 不提供按 `key` 跳转 API：不提供 `scrollToKey` / `animateScrollToKey`，调用方需自行在数据源查找索引后调用 `scrollToIndex` / `animateScrollToIndex`。
- 不做跨窗口重排追踪：不在状态层维护独立 `key` 缓存、全表扫描或快照探测（如 `keyProbeIndex`），单次重排跨度超出 `LazyList` 原生最近邻窗口（约 ±100 项）时按原索引位置回退。
- 不支持逐项独立 `MutableState` 暗中突变：不支持在稳定 `key` 闭包内用每个条目独立的 `MutableState` 原地改值却不触发列表重组，数据变更需通过不可变列表重组或 `SnapshotStateList` 结构修改驱动。
- 不跨空列表或重挂载记忆旧 `key`：列表变空（`count() == 0`）或组件卸载重挂载后统一按最近已知索引恢复并限制在新 `0..<count` 范围内，重新挂载时重置底层残留锚点。
- 不在清空列表同步重测的重组前窗口冻结 `currentIndex`：与官方 `LazyListState.firstVisibleItemIndex` 及 `PagerState.currentPage` 一致，清空列表并在重组前触发同步重测时实时滚动索引返回 `0`（稳定选中态 `snappedIndex` 仍保持原值并在回填后恢复），不在测量数与挂载数未同步时将 `currentIndex` 冻结为旧索引。
- 不兜底同帧清空、同步空测量再同帧回填的位置重置：与官方 `LazyListState` 及 `PagerState` 一致，在同一帧内清空列表、触发同步空测量再立即回填同等数量条目属人工构造的极端交错，位置归零属预期上游行为，不在状态或观察流中额外维护瞬态空测量标志。
- 不在动画飞行途中按 `key` 改道：`animateScrollToIndex` 执行期间若发生数据增删重排，不中途取消并追踪旧 `key`，始终滚向调用时锁定的目标索引。
- 不替外部无帧时钟协程代借帧时钟或额外补充帧时钟文档：`animateScrollToIndex` 的行为与 KDoc 均与官方 `LazyListState.animateScrollToItem`、`PagerState.animateScrollToPage` 一致，需在含 `MonotonicFrameClock` 的 Compose 协程作用域（如 `LaunchedEffect`、`rememberCoroutineScope`）中调用，不为缺少帧时钟的外部协程跨协程借用或等待帧时钟，也不在公开 KDoc 中额外说明该通用要求。
- 不在 `snappedIndex` 公开 KDoc 中罗列缩容夹紧等边界例外：与官方 `PagerState.settledPage` 的 KDoc 一致，公开注释仅说明滚动中保持上一次吸附值并在停稳后更新的常规语义，非空缩容越界时立即夹紧至 `count - 1` 属防越界安全不变量，不在单行公开 KDoc 中堆砌该细节。
- 不做同帧抢跑越界等待：修改数据状态后未等重组就在同一帧调用越界 `scrollToIndex` 时，按调用当下 `count()` 返回值直接限制边界，不做跨帧挂起等待。
- 不做半格偏移删首项补偿：手势停留在非零像素偏移期间并发删除当前首项时，交由 `LazyList` 原生布局与松手后的自动吸附处理。
- 不对超大条目跨项动画做像素瞬移裁剪：`animateScrollToIndex` 直接复用官方 `LazyListState.animateScrollToItem`（仅在跨度超过 100 项时瞬移），极端超大 `itemSize` 下跨项像素距离过大导致的动画耗时增长属上游原生行为，不拆分为预跳转加短动画。
- 不对异常挂起或乱序恢复的父级嵌套滚动做跨协程拦截：嵌套滚动行为与官方 `LazyColumn` / `Pager` 对齐，仅针对 `ModalBottomSheet` 等遵守常规协作契约的父容器在 `onPreFling` 期间延迟自动吸附，不通过 `NestedScrollDispatcher` 追踪或取消外层作用域的离手协程 `Job`，不处理自定义父容器在 `onPreFling` / `onPostFling` 中超长挂死、乱序恢复或挂起期间并发编程式跳转等极端构造场景。
- 不向内层自定义可点击节点下发选中语义：条目外层 `Box` 通过 `semantics(mergeDescendants = true)` 为常规非交互内容合并暴露 `Selected` 状态，调用方若在 `content` 内自行使用 `Modifier.clickable` 等自带语义合并的交互节点，需按 Compose 规则自行使用 `Modifier.selectable` 声明选中态，库层不新增点击回调或作用域 API。
- 不支持条目内嵌可滚动子容器的独立嵌套滚动隔离：滚轮条目面向常规展示内容设计，不考虑在 `content` 内嵌 `LazyRow`、`LazyColumn` 等子级滚动容器的场景。
- 保持 `compose-foundation` 为 `implementation` 依赖：常规接入项目均会引入 `material3`、`material` 或 `foundation`，`:lib` 保持 `implementation(composeBom)` 与 `implementation(libs.androidx.compose.foundation)` 以避免向消费方传递版本约束，不改为 `api` 声明。

## 测试与仓库规范

- 纯算法、状态生命周期、并发抢占与自动吸附优先放入本地 JVM 测试 `WheelPickerTest`（使用轻量 Fake，不引入 Mockito）；涉及真实 Compose 测量、嵌套滚动手势、触摸打断与 RTL 布局的行为放入 `WheelPickerInstrumentedTest`。
- 未经用户明确要求，不主动修改 `README.md`。
- `app/template.jks` 仅用于示例签名，不要提交 `local.properties`、生产密钥或 `build/` 产物。

| 测试类 | 覆盖范围 |
|---|---|
| `WheelPickerTest` | `unfocusedCount` 防溢出收缩、`snappedIndex` 与视觉偏移比例计算、挂载/卸载/空列表过渡与 `Saver`、未挂载预跳转、并发抢占与异常后状态自愈、重复取消后自动吸附自愈、`onPreFling` 挂起保护、缩容夹紧与末项残余偏移归零、重组前改表跳转及恢复原条目数同步、条件渲染卸载不抢跑求值 `count`、常规 `key` 增删重排同步 |
| `WheelPickerInstrumentedTest` | 垂直/水平固有尺寸与紧约束裁剪、`ModalBottomSheet` 多帧 `onPreFling` 嵌套滚动及触摸取消安全、手势中途打断动画及后续头插保持、RTL 水平滚轮视觉偏移与焦点指示器、手势非零偏移及 `onPreFling` 挂起期间缩容夹紧与稳定 `key` 保持、重组前缩容或扩容跳转及恢复原条目数不越界、条件渲染卸载安全与空态回填恢复、重挂载跳转、条目选中无障碍语义更新、已测量未首次摆放时跳转无死锁 |
