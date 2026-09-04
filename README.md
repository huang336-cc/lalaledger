# 拉拉记账 / lalaledger

一款纯本地离线的安卓记账 App：简洁、极速、好看。
**无登录、无广告、无云端服务**，所有数据保存在手机本机。

> English documentation: [README_EN.md](README_EN.md)

- 技术栈：Kotlin 2.0 + Jetpack Compose (Material 3) + Room + DataStore + Navigation Compose
- 架构：MVVM，分层清晰（data / domain / ui / util）
- 最低支持：Android 10（API 29），目标 API 35
- 多语言：简体中文 / English，应用内一键切换（不跟随系统）
- 开源协议：[MIT License](LICENSE)

---

## 功能总览

| 模块 | 说明 |
| --- | --- |
| 多账本 | 创建 / 删除 / 重命名 / 切换账本，可选图标与颜色；账本间数据完全隔离（外键 CASCADE）；**旅行账本开关**（新建勾选或菜单切换，普通账本不显示成员功能） |
| 旅行定向记账 | 旅行账本专属：**同行成员管理**（添加/编辑/删除，10 色标签）；记账时分别选**归属成员（谁消费）**与**付款人（谁垫付）**，默认本人，支持记账页快捷加人；账单列表/详情/导出图显示**归属/垫付双胶囊标签**（深浅色自适应）；删除成员保留历史账单；首次进入显示新手引导卡 |
| 日期时间补记 | 记账可选日期时间（日期选择器 + 24 小时制时间选择器），默认当前时间，方便补记 |
| 快速记账 | 大号金额卡片**点击弹出数字键盘**（浮层式，不遮挡分类区），选完分类自动收起；**滑动主界面不收起键盘，仅点击空白处收起**（位移阈值判定，滤掉小幅抖动）；支出/收入胶囊切换 |
| 账单编辑 | 账单详情页顶栏"编辑"进入修改模式，金额/类型/分类/成员/垫付人/时间/地点/图片/备注全量回填，保存即更新（保留原创建时间） |
| 分类 | 预置 14 个支出分类 + 5 个收入分类，**全量平铺**（无新增入口）；长按任一分类可改名/换图标/换颜色 |
| 地点快捷录入 | "当前地点"一键定位填入，常用地点横滑胶囊一键复用，支持手动输入 |
| 小票图片 | 拍照 / 相册多选（Photo Picker），图片压缩后存应用私有目录；**账单列表行内缩略图 + 点击全屏预览**；详情页 Pager 放大预览 |
| 多选批量操作 | 多选模式：圆形勾选框、全选/取消全选、实时统计（条目/总支出/总收入/净花费）；**首次进入显示多选功能说明**；底部操作栏：生成汇总图片 / 批量删除（二次确认）/ 取消 |
| 汇总图片导出 | 一键生成 1080px 宽 PNG 长图保存到相册：汇总数据卡 + 账单清单（**归属/垫付双胶囊** / 金额 / 小票缩略图）；配色跟随深浅主题，最多 100 条 |
| 单条账单分享图 | 详情页顶栏导出：单条账单分享卡（金额 / 分类 / 成员 / 垫付 / 时间 / 地点 / 备注 / 小票图），深浅主题自适应 |
| 统计面板 | 总支出/总收入/净花费/日均消费；**切换统计账本**（不影响全局当前账本）；**分类多选筛选**（列出该账本出现过的分类，选中即联动图表与金额）；时段筛选：今日/本周/本月/全部/自定义；旅行账本支持**按归属 / 按垫付双维度成员筛选**；**成员明细卡**（各成员消费与垫付总额） |
| AA 结算 | 旅行账本内置 AA 结算卡：自动计算每人消费、垫付、**应收/应付净额**；贪心算法生成**简化转账方案**（谁转给谁、转多少）；**一键复制结算文本**到剪贴板并弹出成功提示 |
| 账单详情 | 分层卡片：金额/类型/分类/归属/垫付/地点/备注/时间/图片；顶栏导出分享图 / 编辑 / 删除二次确认 |
| 主题 | 浅色（米白底 + 薄荷绿 + 雾蓝）/ 深色（深蓝灰 + 柔和青绿）/ 跟随系统；胶囊标签等组件深浅色自适应 |
| 语言 | 设置页**简体中文 / English 单选切换**，选择后立即生效（无需重启）；语言名固定以各自语言显示 |
| 关于 | 关于弹窗（版本 + 简介 + **开源许可** + **免责声明**入口）；变更履历（中英文自动跟随界面语言） |
| 性能 | 适配高刷新率屏幕（自动取设备最高刷新率）；键盘动画为绘制层平移（不触发 measure/layout）；Release 包 R8 + 资源收缩 + 仅保留中英资源 |

## 权限策略（按需申请，最小化）

| 权限 | 用途 | 时机 |
| --- | --- | --- |
| `CAMERA` | 拍照记小票 | 点"拍照"时申请 |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | "当前地点"快捷填入 | 点"当前地点"时申请 |
| 存储权限 | —— 不需要 | 相册用系统 Photo Picker；导出图片用 MediaStore，Android 10+ 均免权限 |

## 工程结构

```
app/src/main/java/com/lightledger/app/
├── LightLedgerApp.kt          # Application：AppContainer 手动依赖注入 + 语言内存缓存 + 首启动预置数据
├── MainActivity.kt            # 唯一 Activity：attachBaseContext 注入语言 + 高刷新率适配
├── data/
│   ├── db/
│   │   ├── AppDatabase.kt     # Room 数据库（version 4）
│   │   ├── Converters.kt      # 图片路径列表 <-> JSON
│   │   ├── Migrations.kt      # 迁移链 v1→v2→v3→v4（新增字段/新表写法模板）
│   │   ├── dao/               # Room DAO（Flow 响应式查询）
│   │   └── entity/            # TransactionEntity / CategoryEntity / AccountBookEntity
│   │                          #   / PlaceEntity / MemberEntity（含 payerMemberId 外键）
│   ├── prefs/SettingsDataStore.kt  # 主题 / 当前账本 / 语言 / 引导已读标记
│   └── repository/            # 仓库层：内存聚合 + DAO 封装（SeedData 预置数据）
├── domain/
│   └── model/                 # TransactionType / StatPeriod / ThemeMode / IconLibrary
├── ui/
│   ├── app/AppViewModel.kt    # 全局状态：主题 / 账本 / 语言
│   ├── theme/                 # Material 3 主题 + 语义色 + 图表色板
│   ├── navigation/NavGraph.kt # 底部导航 + 路由（home/record/stats/me/books/detail/edit/members）
│   ├── home/                  # 首页：统计卡 + 最近账单 + 多选 + 旅行引导卡
│   ├── record/                # 记账页：金额键盘 / 成员双选择 / 日期时间 / 分类平铺
│   ├── stats/                 # 统计：饼图 / 排行 / 成员明细 / AA 结算
│   ├── detail/                # 账单详情
│   ├── books/  members/       # 账本管理 / 成员管理
│   ├── settings/              # 我的：主题/语言/清空/变更履历/关于
│   └── components/            # BillRow（双胶囊）/ MemberChip / 弹窗等通用组件
└── util/
    ├── MoneyFormat.kt  DateUtils.kt
    ├── SummaryImageExporter.kt # Canvas 手绘汇总长图 / 单条分享图
    ├── ImageStore.kt  LocationHelper.kt
    └── LocaleHelper.kt        # 多语言：createConfigurationContext 包装 + Activity 查找
```

## 数据模型

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `account_books` | `name / icon / color / isTrip` | 账本；`isTrip` 控制成员功能开关 |
| `transactions` | `bookId(FK CASCADE) / type / amount / categoryId(FK SET_NULL) / location / note / images(JSON) / memberId(FK SET_NULL) / payerMemberId(FK SET_NULL) / createdAt / updatedAt` | 账单；`memberId`=归属（谁消费），`payerMemberId`=垫付（谁付钱），null=本人 |
| `categories` | `name / icon / color / type / isDefault / sortOrder` | 分类全局共享（不按账本隔离） |
| `members` | `bookId(FK CASCADE) / name / color` | 旅行账本成员；删除成员保留账单（外键 SET_NULL） |
| `places` | `name / usedAt` | 常用地点（按使用时间排序） |

### 迁移链（新增迁移必须追加到 `Migrations.ALL`）

| 版本 | 变更 |
| --- | --- |
| v1→v2 | `transactions.updatedAt`、`places` 表 |
| v2→v3 | `members` 表、`account_books.isTrip`、`transactions.memberId` 外键 |
| v3→v4 | `transactions.payerMemberId` 外键（SET_NULL）+ 索引 |

> 注意：Room 会按 schema JSON 严格校验列定义。`ALTER TABLE ADD COLUMN` 带 `REFERENCES` 的写法需与实体注解完全一致（含 `ON DELETE` 行为），否则启动时崩溃。

## 关键设计决策与关键技术点

- **金额用"分"（Long）存储**：避免浮点误差，展示层统一 `MoneyFormat` 转换。
- **当前账本全局响应式**：`AppViewModel.currentBookId` 是唯一事实来源（DataStore 持久化），首页/记账/统计全部 `flatMapLatest` 跟随；统计页另支持独立切换统计账本（不改动全局）。
- **多语言（零 AppCompat 依赖）**：`LocaleHelper.wrap()` 用 `createConfigurationContext` 包装 Context；`MainActivity.attachBaseContext` 读 Application 内存缓存（DataStore collector 持续同步），切换语言 = 写 DataStore → 更新内存 → `activity.recreate()`，零阻塞 IO。
- **AA 结算算法**：每人净额 = 垫付 − 消费；净额>0 应收、<0 应付；贪心配对（最大债权人 ↔ 最大债务人）生成最少笔数简化转账方案。
- **键盘收起手势**：`awaitEachGesture` 自定义判定——按下后位移超过 `touchSlop×4` 或滚动已消费事件 → 视为滑动不收起；仅"点按主界面"收起。
- **日期时间选择陷阱**：M3 `DatePicker` 的 `selectedDateMillis` 是 **UTC 当天 0 点**，需 `atZone(UTC).toLocalDate()` 后再与 TimePicker 的时分在本地时区合成，否则相差 8 小时。
- **高刷新率**：`requestHighestRefreshRate()` 取 `display.supportedModes` 最高刷新率设 `preferredDisplayModeId`（API 30+ 用 `Activity.display`）。
- **汇总长图内存控制**：`android.graphics.Canvas` 手绘 1080px PNG（RGB_565 + 100 条上限），MediaStore 保存免存储权限。
- **图片生命周期**：拍照/相册图片统一压缩（最长边 1600px、JPEG 85）复制进 `files/Receipts/`；删除账单、批量删除、编辑移除图片时同步清理文件。

## 开发约束（修改代码前必读）

1. **所有用户可见文案必须走字符串资源**：中文写 `res/values/strings.xml`，英文写 `res/values-en/strings.xml`，两份必须同步增删；UI 用 `stringResource(R.string.xxx)`，非 Compose 上下文用 `context.getString(...)`；ViewModel 不返回文案字符串，返回资源 id（Int）由 UI 层解析。
2. **数据库任何结构变更必须**：`AppDatabase.version += 1` → 在 `Migrations.kt` 写 `MIGRATION_n_m` 并追加进 `ALL` 数组 → 编译后对照 `app/schemas` 生成的 JSON 校验列定义。
3. **分类不提供新增入口**（预置分类覆盖全场景，防止碎片化），长按编辑改名/图标/颜色即可。
4. **成员相关**：删除成员必须保留账单（外键 SET_NULL，不可改 CASCADE）；归属/垫付 null 语义 = "本人"，UI 层显示 `record_self` 词条。
5. **非旅行账本不得出现成员 UI**：所有成员功能以 `isTrip == true` 为前提。
6. **键盘/动画性能红线**：键盘浮层只用 `graphicsLayer` 绘制层平移，禁止用会触发 measure/layout 的动画（如 `animateDpAsState` 位移）。
7. **新页面必须双语言完整**，词条含参数用 `%1$s` / `%1$d` 占位；XML 里的 `&` 必须写成 `&amp;`。
8. **Release 构建必须保持** R8 minify + shrinkResources + 签名；`keystore/lalaledger.jks` 为演示密钥（密码在 `build.gradle.kts` 内），正式发布请更换为自己的密钥。

## 二次开发指引

```bash
# 环境：JDK 17 + Android SDK（compileSdk 35 / build-tools 35.0.0）+ Gradle 9.3
# Debug 构建
gradle assembleDebug
# Release 构建（R8 + 签名，产物 app/build/outputs/apk/release/lalaledger-v1.4.2.apk）
gradle assembleRelease
```

- 新增页面：`ui/<feature>/` 建 Screen + ViewModel → `NavGraph.kt` 加路由 → 文案进两份 strings.xml。
- 新增数据库字段：entity 加字段（带默认值）→ 迁移链 → repository/ViewModel 透传。
- 新增统计维度：在 `StatsViewModel.compute()` 内对 `byCategory`（时间+分类筛选后）做过滤聚合，即可自动联动汇总卡/饼图/排行。
- 新增词条：先在 `values/strings.xml` 写中文，再同步 `values-en/strings.xml`；同名 key 不得缺失，否则英文环境崩溃。

## 开源协议与免责声明

本项目基于 [MIT License](LICENSE) 开源。应用内「我的 → 关于 → 开源许可 / 免责声明」可查看完整文案（中英双语）。摘要：

- 可自由使用、复制、修改、合并、出版发行、散布、再授权及贩售。
- 软件按"原样"提供，不含任何担保；AA 结算与转账方案仅供参考，请自行核对后使用。
