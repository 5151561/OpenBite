# OpenBite 黑客松落地计划

Nov 11, 2026 · @Changle

## 定位与叙事

主攻 **Best Frankenstein**：把视觉大模型、地图、PWA 和 MCP 这几样本不相干的技术缝成一个“点一次菜，就留下一段记忆”的个人生活外脑。

- **一句话：** 拍一张看不懂的菜单，点完菜就自动生成一段可被 AI 回忆的用餐记忆。
- **缝合逻辑：** 点单（视觉模型）→ 记忆（本地数据库）→ 空间（地图）→ 回忆（MCP + Agent）。每一块的输出都是下一块的输入，不是四个功能的堆砌。
- **诚实的隐私表述：** 数据全部存在本地；识别时把图片发给用户自选的模型（BYOK），上传前剥离 EXIF。不再说“零隐私泄漏”。
- **砍掉的叙事：** 连锁店小程序截图从“点单”降级为“导入历史记录”，放进 Roadmap，不进 Demo 主线。

## MVP 范围

只做一条能在 3 分钟内演完的主线，其余全部进 Roadmap。

| 功能 | 决定 | 理由 |
| --- | --- | --- |
| 纸质 / 手写菜单拍照识别 + 翻译 | 做 | Demo 的“视觉奇迹”，痛点最直观 |
| 外卖式点单界面（分类、加减份数、备注） | 做 | 识别结果的承载界面 |
| “给店员看”全屏小票（原文 + 忌口） | 做 | 点单闭环的终点，带“仅供参考”提示 |
| 点单后自动生成用餐记录草稿 | 做 | 解决录入成本，连接点单与记忆 |
| 打分、短评、照片、纪念日标签 | 做 | 记忆的最小内容 |
| 地图打点（EXIF 或手动） | 做 | 空间维度，Demo 镜头好看 |
| 纪念日实体 + 到期提醒 | 做 | Agent 环节的数据来源 |
| MCP Server（5 个工具） | 做 | Agent 联动高潮 |
| 小程序长截图导入 | 不做 | 价值在聚合记录，非点单；进 Roadmap |
| 菜名 TTS 发音 | 不做 | 锦上添花，浏览器 API 可后补 |
| 连锁店 Deep Link | 不做 | 与主线无关 |
| 年度回顾 Year in Bites | 不做 | 需要大量历史数据，黑客松演不出来 |
| 逆地理编码 | 不做 | 手动填地址即可 |
| 本地视觉模型 | 不做 | 只在 README 里说明可替换 |

## 核心闭环

用户只主动做两件事：拍照和点菜。记录、打点、提醒都由系统顺势生成。

1. **拍照：** 上传菜单照片，前端剥离 EXIF 但保留经纬度作为店铺位置候选。
2. **识别：** 视觉模型返回结构化菜单（分类、原文名、译名、价格、可能的过敏原），流式逐条渲染。
3. **点单：** 用户勾选菜品、填写忌口，生成“给店员看”的全屏小票。
4. **自动记录：** 点“已下单”即创建一条用餐记录草稿，已点菜品、价格、时间、位置全部预填。
5. **补记忆：** 饭后推送一次提醒，用户补打分、短评、照片，可选纪念日标签与同行人。
6. **地图：** 店铺以图钉出现在地图上，点开看历次记录。
7. **回忆：** 本地服务每天检查纪念日，提前 7 天发出提醒；用户在 Claude Desktop 里提问，Agent 通过 MCP 拉取去年的记录并给出建议。

## 技术架构

一个本地服务同时持有 SQLite、对 PWA 提供 REST、对 Agent 提供 MCP，解决了原方案里“浏览器存储 MCP 读不到”的矛盾。

&#91;embedded content: 系统架构 · 4 个本地组件，1 个外部服务\]

唯一离开电脑的数据是去掉 EXIF 的菜单图片；纪念日提醒由本地服务定时检查并发系统通知，不依赖 MCP 主动推送。技术栈：Vite + React + Tailwind、MapLibre GL、Bun + 内置 `bun:sqlite`、官方 MCP SDK。

**为什么选 Bun：** 原生跑 TypeScript、自带 SQLite，不用编译 better-sqlite3 这类原生依赖，一个人搭环境更省事。Bun 2026 年确实从 Zig 改写成了 Rust，但执行 JS 的仍是 JavaScriptCore；对这个项目来说，Node 和 Bun 的内存、性能差距都可以忽略，选它主要是因为省依赖。

**视觉模型：** 写一个很薄的适配层（输入图片，输出固定 JSON 结构），默认接 Claude：开发和测试用 Haiku 5.5 控制成本，效果不够再切 Sonnet 5.5。11/14–11/15 用测试集各跑一遍，按识别准确率定默认值。

**评委不需要 API Key：** 种子数据里放好预先识别过的菜单，不填 Key 也能体验点单、记录、地图和 MCP 全流程；只有“现场拍新菜单”需要用户自带 Key。

## 数据模型

在原方案基础上补齐 `Dish`、`Milestone`、`Person` 三个实体，并给时间加上时区，所有表都在同一个本地 SQLite 文件里。

```typescript
interface Place {
  id: string;
  name: string;
  lat: number;
  lon: number;
  address?: string;
  tags: string[];            // ["深夜食堂", "适合约会"]
  created_at: string;
}

interface Dish {
  id: string;
  place_id: string;
  category: string;
  name_original: string;     // 菜单原文
  name_translated: string;
  price?: number;
  currency?: string;
  allergens?: string[];      // 模型推测，界面标注“仅供参考”
  source_photo_id?: string;
}

interface DiningLog {
  id: string;
  place_id: string;
  visited_at: string;        // ISO 时间
  timezone: string;          // "Asia/Tokyo"，纪念日按当地日期计算
  status: "draft" | "done";  // 点单后自动生成 draft
  milestone_id?: string;
  companion_ids: string[];
  items: {
    dish_id: string;
    name_snapshot: string;   // 快照，菜单变了也不丢
    price_snapshot?: number;
    qty: number;
    rating?: number;         // 1-5
    comment?: string;
  }[];
  note?: string;
  photo_ids: string[];
}

interface Milestone {
  id: string;
  title: string;             // "恋爱纪念日"
  type: "anniversary" | "birthday" | "celebration" | "other";
  date: string;              // 首次发生的日期 "2024-05-20"
  recurrence: "yearly" | "none";
  remind_days_before: number; // 默认 7
  person_ids: string[];
}

interface Person {
  id: string;
  display_name: string;      // "老婆"、"大学室友"
}
```

照片文件存在本地目录，数据库只存路径。

## MCP 工具

保留 5 个工具：4 个只读，1 个只追加用餐记录，不能修改或删除已有数据。

| 工具 | 参数 | 返回 | Demo 中的用途 |
| --- | --- | --- | --- |
| `upcoming_milestones` | `days_ahead` | 即将到来的纪念日 + 当年对应的用餐记录 | Agent 开场：“下周五是恋爱两周年” |
| `query_dining_logs` | `milestone_id` / `person_id` / `date_range` | 用餐记录，含菜品、评分、短评 | 回忆“去年吃了什么、打了几分” |
| `search_places` | `lat`, `lon`, `radius`, `tags` | 打过标签的店铺 | 推荐“同价位、适合约会”的店 |
| `get_place_menu` | `place_id` | 结构化菜单 + 历史评分 | 为当晚预选菜单 |
| `create_dining_log` | `place_id`, `items`, `milestone_id?`, `note?` | 新记录 id | 演示 Agent 也能写入记忆 |

原方案的 `build_order_draft` 删除：点单小票由前端生成，Agent 用不上。日历预订交给用户自己连接的日历 MCP，不在本项目范围内。

## 开发排期

一个人、约 24 天：截止时间是 2026-12-05 06:00（GMT+8），按 12 月 4 日晚提交倒排，留出最后一天缓冲；11 月 30 日冻结功能。

| 日期 | 阶段 | 交付物 | 验收标准 |
| --- | --- | --- | --- |
| 11/11–11/13 | 规格与骨架 | Kiro specs、steering、建表、Bun 本地服务跑起来 | `.kiro/` 提交进仓库；PWA 能读写一条测试数据 |
| 11/14–11/19 | 菜单识别 + 点单 | 模型适配层、识别 Prompt、点单界面、给店员看小票 | 10 张测试菜单里至少 8 张可用；流式渲染 |
| 11/20–11/22 | 自动记录 | 下单即生成草稿、补打分 / 照片 / 纪念日标签 | 从拍照到草稿零手动输入 |
| 11/23–11/25 | 地图 | MapLibre 图钉、店铺详情、历次记录 | 点图钉能看到时间胶囊卡片 |
| 11/26–11/29 | 纪念日 + MCP | Milestone 提醒任务、5 个 MCP 工具 | Claude Desktop 能调用全部工具并返回真实数据 |
| 11/30 | 冻结 + 种子数据 | 一年量演示数据，含预先识别好的菜单 | 演示脚本完整跑通 3 遍；不填 API Key 也能浏览全部功能 |
| 12/1–12/2 | 录 Demo | 3 分钟视频 | 符合下方分镜 |
| 12/3 | 提交材料 | README、架构图、Kiro 使用说明 | 陌生人照 README 10 分钟内能跑起来 |
| 12/4 | 缓冲 + 提交 | 修最后的问题，提交 | 12/5 06:00 前完成 |

## Demo 分镜

视频按闭环顺序走一遍，每段只证明一件事；Agent 段演示真实调用，不做模拟弹窗。

| 时间 | 画面 | 证明什么 |
| --- | --- | --- |
| 0:00–0:20 | 海外小店手写菜单，看不懂、不敢问 | 痛点 |
| 0:20–1:05 | 拍照 → 菜品流式出现 → 勾选、填忌口 → 全屏小票给店员 | 视觉模型缝进点单流程（最长的一段） |
| 1:05–1:30 | 点“已下单”，记录草稿自动生成；饭后补一个 5 星和合影 | 点单自动变成记忆，零录入成本 |
| 1:30–1:50 | 地图上的图钉，点开看到去年纪念日的时间胶囊 | 空间 + 时间维度 |
| 1:50–2:35 | 系统通知“下周五恋爱两周年” → 在 Claude Desktop 提问 → 画面并排显示 MCP 调用日志 | 本地数据通过 MCP 被 Agent 真实调用 |
| 2:35–2:50 | Kiro specs、steering、hooks 文件快速切换 | Kiro 如何驱动开发 |
| 2:50–3:00 | 架构图 + 开源地址 | 收尾 |

## 风险与应对

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| 手写菜单识别不稳 | Demo 翻车 | D3 先收集 10 张真实菜单做测试集；录制时选识别稳定的那张，现场演示备一段录屏 |
| 识别耗时长 | 观感拖沓 | 流式输出 JSON，菜品逐条渲染；视频里可剪掉等待 |
| 过敏原翻译出错 | 真实安全风险 | 小票上并列原文，过敏原标“仅供参考，请向店员确认” |
| 浏览器提醒不可靠 | 纪念日提醒演示失败 | 提醒由本地服务发系统通知，不依赖浏览器后台 |
| MCP 客户端配置复杂 | 评委跑不起来 | README 提供一键配置片段和种子数据 |
| 范围回涨 | 延期 | D12 冻结，之后只修 bug；新想法一律写进 Roadmap |

## 待确认问题

- [ ] 今年 Kiroween 的提交截止日期和赛道规则（排期按 14 天假设）
- [ ] 团队人数（排期按 1–2 人假设）
- [ ] 视觉模型选哪家，评委运行时是否需要自带 API Key
- [ ] Demo 用的真实菜单照片从哪来（需要 10 张左右）
- [ ] 本地服务用 Node 还是 Bun

## 已确认的决定

| 问题 | 决定 |
| --- | --- |
| 截止时间 | 2026-12-05 06:00（GMT+8），按 12/4 晚提交排期 |
| 团队 | 一个人 |
| 视觉模型 | 适配层 + 默认 Claude（Haiku 5.5 起步，必要时 Sonnet 5.5）；评委用种子数据，无需 Key |
| 菜单照片 | 优先自己去小店拍；不够再用 Wikimedia Commons 等明确 CC 授权的图，并在 README 署名 |
| 本地服务运行时 | Bun |

网上随手找的菜单照片多半有版权，放进公开仓库和 Demo 视频有风险，所以只用自己拍的或明确授权的。

仍待确认：今年赛道的具体评审规则，需要你对照官网再看一遍。
