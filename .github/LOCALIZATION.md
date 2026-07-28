# 本地化追踪 · Localization Tracking

> 用于追踪汉化内容变更，辅助 `merge-upstream` 合并后的新增字符串定位。

## 基准信息

| 字段 | 值 |
|------|-----|
| 基准 commit | `5044796` |
| 基准日期 | 2026-07-28 |
| 英文 key 总数 | 1879 |
| 简中 (zh-CN) 覆盖 | 1879 / 1879 (100%) |
| 繁中 (zh-TW) 覆盖 | 1879 / 1879 (100%) |
| 上次批量翻译 | 2026-07-28，补全 104 + 5 条 |

## 各语言覆盖情况

| 语言 | app 模块 | search 模块 | web-ui | 差距 (vs en) |
|------|---------|------------|--------|-------------|
| zh-CN | 1879 / 1879 | 5 / 5 | 20 / 20 | ✅ 完整 |
| zh-TW | 1879 / 1879 | 5 / 5 | — | ✅ 完整 |
| ja | 1770 / 1879 | 5 / 5 | — | 缺 109 |
| ko | 1770 / 1879 | 5 / 5 | — | 缺 109 |
| ru | 1770 / 1879 | 5 / 5 | — | 缺 109 |
| ar | 1490 / 1879 | — | — | 缺 389 |

## 如何检测新增字符串

### 方法 1：diff 对比（推荐）

```bash
# 拉取最新代码后：
git diff 5044796 HEAD -- app/src/main/res/values/strings.xml | grep '^+.*<string name='
# 输出即为新增或变更的英文字符串
```

### 方法 2：精确 key 对比

```bash
python3 -c "
import xml.etree.ElementTree as ET
en = {s.get('name') for s in ET.parse('app/src/main/res/values/strings.xml').getroot().findall('string')}
zh = {s.get('name') for s in ET.parse('app/src/main/res/values-zh/strings.xml').getroot().findall('string')}
missing = en - zh
if missing:
    print(f'缺 {len(missing)} 条:')
    for k in sorted(missing): print(f'  {k}')
else:
    print('中英对齐，无缺失')
"
```

### 方法 3：CI 自动检测

`merge-upstream.yml` 合并后如有冲突将发微信告警，冲突文件列表中包含 `strings.xml` 即表示上游新增了字符串。

## 翻译工作流

1. 上游更新 → 自动合并或收到冲突告警
2. 用方法 1 定位新增英文 key
3. 参考现有 `values-zh/strings.xml` 风格进行翻译
4. 同步更新 `values-zh-rTW/strings.xml`（繁体）
5. 更新此文件中的基准 commit SHA

## 可用工具

- `locale-tui/` — Python TUI 翻译管理工具，支持 AI 批量翻译、dead entry 检测
  ```bash
  cd locale-tui && uv run locale-tui
  ```

## 本次补全记录 (2026-07-28)

批量补齐 104 条简中翻译（commit `5044796`），覆盖范围：

- **Termux 设置** (27): 应用状态、权限、验证、超时、工作目录
- **xAI Grok** (19): OAuth 登录、认证、刷新、限额
- **新增设备工具** (21): 键盘控制、NFC、外部存储、密钥库、压缩、短信、壁纸、网页抓取
- **浏览器超时** (4): 单工具超时、任务超时
- **本地 LLM** (3): 性能遥测、投机解码、视觉降级提示
- **语言选项** (3): 阿拉伯语、波斯语、乌尔都语

繁体补齐 5 条旧漏翻译（TTS 温度/语速、主题名称）。