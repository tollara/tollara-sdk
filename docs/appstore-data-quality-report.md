# App Store 数据质量分析报告

> **数据源**：`appstore.app_info`（MySQL 5.7.44-log，497 万行）  
> **分析日期**：2026-03-28  
> **目的**：为 schema 设计、导入脚本、资产校验脚本提供数据分布与质量依据

---

## 1. 表结构与规模

| 指标 | 值 |
|------|-----|
| 总行数 | 4,971,889 |
| 字段数 | 47 |
| 主键 | `id`（无 NULL、无重复） |
| 业务键 | `bid`（无 NULL、无重复） |
| 包名 | `package_name`（100% 非空） |

**版本维度分布**：

| version | 行数 | 占比 |
|---------|------|------|
| v1 | 4,300,387 | 86.57% |
| v2 | 634,502 | 12.75% |
| v0 / v3 | 极少 | <0.1% |

**结论**：导入主战场是 **v1**（约 430 万行）；v2 约 63 万行，需单独评估是否纳入同一 schema。

---

## 2. 字段覆盖率（NULL 与空值）

基于 `SELECT COUNT(*) WHERE col IS NULL OR col = ''` 统计。

| 字段 | 非空率 | 备注 |
|------|--------|------|
| id, bid, package_name, version, version_code | 100% | 主键/版本标识 |
| c_time, m_time, u_time | ~100% | 时间戳 |
| version_status | 99.28% release / 0.72% null | |
| release_state | 84.01% 有值 / 15.99% null | |
| release_stage | 47.46% 有值 / 52.54% null | |
| release_type | 99.16% 有值 | |
| version_type | 100% | 见 §3 |
| channel_ids | 100% | 均为 `["all"]` |
| support_device | 100% | 见 §5.4 |
| tag_ids / tag_names | ~88% | JSON 数组 |
| icon_url | 99.96% | 4 条空字符串 |
| screenshot | 84.66% | JSON 变体多，见 §5.6 |
| desc / edesc | >99.99% | |
| download_url, size, resolution | ~99.96% | |

**低覆盖 / 高 NULL 字段**：`release_stage`（约一半为 null）、`tag_*`（约 12% 缺失）。

---

## 3. 枚举分布与业务含义

### 3.1 version_status

- `release`：4,935,266（99.28%）
- `null`：36,623

### 3.2 release_state

| 值 | 行数 | 占比 |
|----|------|------|
| released | 2,358,928 | 47.46% |
| internal_testing | 1,776,353 | 35.74% |
| null | 794,719 | 15.99% |
| deleted | 41,889 | 0.84% |

**含义**：仅约 **47%** 为 `released`；**36%** 为内测态。导入不能只筛 `released`。

### 3.3 release_stage（在 release_state = released 子集内）

| 值 | 行数 | 占 released 比例 |
|----|------|------------------|
| 100 | 1,756,853 | 74.48% |
| 200 | 602,075 | 25.52% |
| null | 43 | 异常，见 §5.3 |

### 3.4 version_type

| 值 | 行数 | 含义（业务侧） |
|----|------|----------------|
| 1 | 3,168,421 | 商店/正式版本风格 |
| 2 | 1,190,565 | 开发/versionName 风格版本 |
| 0 | 613,903 | 需与产品确认 |

**`version_type=2` 与 release 交叉**（非仅在 stage200）：

| release_state | release_stage | version_type=2 行数 |
|---------------|---------------|---------------------|
| released | 200 | 671,326 |
| internal_testing | 200 | 500,666 |
| internal_testing | 100 | 2,795 |
| released | 100 | 117 |

### 3.5 arch

- `arm64-v8a`：2,479,283
- `armeabi-v7a`：901,418
- x64/x86：极少

**结论**：优先兼容 **arm64-v8a**；v1 为主体；`version_type=2` 必须在版本选择逻辑中显式处理。

---

## 4. 全量字段质量摘要

- **主键/基础字段**：稳定，无重复。
- **时间字段**：格式以 `YYYY-MM-DD HH:MM:SS` 与 epoch ms 为主；负 epoch 28 条、超远未来 12 条。
- **release 组合异常**：43 条 `released` + `release_stage=null`（与业务规则不符，建议复核源库或 ETL）。
- **渠道**：`channel_ids` 均为 `["all"]`，**未见 `beta`**。
- **设备**：`support_device` 仅 7 种枚举，**未见 watch/tv**；表内无 `category` / `category_ids`。
- **标签 JSON**：`tag_ids` / `tag_names` 各约 1,056 条无效 JSON（~0.02%）。
- **icon_url**：4 条空串，2 条非标准 URL。
- **screenshot**：有效 JSON 84.66%；空数组 `[]` 13.69%；1,000+ 条非 JSON 文本。
- **desc/edesc/download**：整体质量高。

---

## 5. 关键字段专项问题

### 5.1 version_type=2 的适用边界

- **总量**：1,190,565 条
- **不是**只在 `released + stage200` 下才出现
- `released`：671,443；`internal_testing`：503,461

### 5.2 渠道语义

- `channel_ids` **未出现 `beta`**。
- 若业务要把 beta/公测当作独立渠道，当前主表**没有直接字段**，需从 `release_type`、`release_stage`、`version_type` 或别表推断。

### 5.3 release_state + release_stage 异常

- 43 条 `release_state=released` 但 `release_stage=null` — **按现有认知不应出现**，建议列入异常清单并复核。

### 5.4 设备语义

- `support_device` 枚举：`phone` / `pc` / `car` / `vr` / `glass` / `speaker` / `phone|pad|pc`
- **未见 `watch` / `tv`**
- 无独立 `form_factor` / `deviceType` 列；若 schema 要统一 `phone|pad|pc|watch|tv|car`，需做映射/归并

### 5.5 icon 字段

- 4 条空值；2 条非预期 URL 形态
- 整体可用性极高，但不能假设 100% 合法 URL

### 5.6 screenshot 字段

- 多种 JSON 变体并存：有效 JSON、空数组字符串、非 JSON 文本
- `validate-asset-urls-v2.py` 严格 JSON 解析会把空数组/非 JSON 一并计为异常 — **需区分**：
  - 空数组是否算「无截图资产」？
  - 非 JSON 是否一律视为坏数据？

### 5.7 多版本共存

- 按 `(package_name, version_code, COALESCE(release_state,''), COALESCE(release_stage,''))` 唯一组合：**4,917,617**
- 同 package 多 version_code / 多 release 状态/阶段是**常态**，不是少量脏数据
- 与 `version_type=1/2`、灰度/多版本业务假设一致

---

## 6. 对后续 schema / 脚本设计的直接影响

### 6.1 schema 必补字段

建议在 `app_listings` / `app_versions` / `version_artifacts` 中保留来源映射，不要只存抽象 status：

| 目标字段 | 来源 | 说明 |
|---------|------|------|
| `releaseState` / `releaseStateRaw` | `release_state` | released / internal_testing / deleted / null |
| `releaseStage` | `release_stage` | 100 / 200 / null |
| `releaseType` | `release_type` | 100 / 200 / 300 |
| `versionType` | `version_type` | 1=商店版本, 2=开发/versionName 风格 |
| `versionStatus` | `version_status` | release / null |
| `supportDeviceRaw` | `support_device` | 原始枚举，供映射到统一 schema |

还需补：

- `tagIds` / `tagNames` — 当前 schema 尚无 tags 概念
- `screenshot` → 独立 `screenshots[]`，并容忍 `[]`
- `iconUrl` 空值处理策略
- `releaseState + releaseStage` 联合状态，避免只存单一 `AppVersionStatus`

### 6.2 对 version_type=2 脚本约束

- 当前 `normalize-v1.js` / v2 仅按 `(packageName, versionCode max)` 取一条 → **不适配本表**
- 同一 `(package, versionCode)` 下可能并存：不同 `release_state`、`release_stage`、`version_type`、以及不同 `version_name` / `size` / `icon` / `screenshot`

**脚本应改为按维度筛选后择优**，例如：

```
released + stage100
  → released + stage200
  → internal_testing + stage200
  → internal_testing + stage100
  → released + null stage
  → 其余
```

### 6.3 设备映射建议

| 原值 | 建议映射 |
|------|---------|
| phone | phone |
| pc | pc |
| car | car |
| speaker | phone/pad 或保留 car-adjacent |
| vr / glass | 保留原值或 `unknown` |
| phone\|pad\|pc | 多设备展开，勿压成单一 formFactor |

Watch/TV：当前无来源；schema 可预留，导入脚本不能凭空生成。

### 6.4 渠道映射

- `channelIds=["all"]` → `AppChannel.PRODUCTION` 或 `DEFAULT`
- 未见 `beta` → 若 UI 需要 beta，只能由 `releaseType` / `releaseStage` / `versionType` 规则推断

---

## 7. 可直接引用的结论

1. **497 万行全部落在 v1/v2，且 99.28% 为 release 状态；导入主战场明确是 v1。**
2. **released 仅占约 47%，internal_testing 占 36%；不能只按 released 导入。**
3. **`version_type=2` 有 119 万条，与 stage100/stage200、released/internal_testing 强相关，必须在版本选择逻辑里显式处理。**
4. **表内没有 watch/tv/category 字段；support_device 与 tag_names 只能部分覆盖设备/分类语义。**
5. **screenshot/icon/desc/download 整体质量高，但 icon 空值、screenshot 空数组/非 JSON、released+null stage 需列入异常清单。**
6. **同 package 多 version_code + 多 release 状态/阶段是常态；schema 与导入脚本必须按 AppVersion 粒度建多行，不能一包一行。**

---

## 附录：推荐校验 SQL

```sql
-- 版本与 release 分布
SELECT version, COUNT(*) FROM app_info GROUP BY version;
SELECT release_state, COUNT(*) FROM app_info GROUP BY release_state;
SELECT release_stage, COUNT(*) FROM app_info WHERE release_state = 'released' GROUP BY release_stage;
SELECT version_type, COUNT(*) FROM app_info GROUP BY version_type;

-- version_type=2 交叉
SELECT release_state, release_stage, COUNT(*)
FROM app_info WHERE version_type = 2
GROUP BY release_state, release_stage;

-- 异常：released 但 stage 为空
SELECT COUNT(*) FROM app_info
WHERE release_state = 'released' AND release_stage IS NULL;

-- 渠道与设备
SELECT channel_ids, COUNT(*) FROM app_info GROUP BY channel_ids LIMIT 20;
SELECT support_device, COUNT(*) FROM app_info GROUP BY support_device;

-- 资产字段空值
SELECT
  SUM(icon_url IS NULL OR icon_url = '') AS icon_empty,
  SUM(screenshot IS NULL OR screenshot = '') AS screenshot_empty,
  SUM(download_url IS NULL OR download_url = '') AS download_empty
FROM app_info;

-- 多版本粒度（唯一组合数）
SELECT COUNT(DISTINCT CONCAT(package_name, '|', version_code, '|',
  COALESCE(release_state,''), '|', COALESCE(release_stage,'')))
FROM app_info;
```
