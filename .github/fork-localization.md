# Fork-owned localization archive

The `fork-localization` branch preserves the complete localized repository state at the time this policy was introduced.

## Fork-owned configuration
- `README.md`, `README_EN.md`
- `.github/workflows/*.yml`
- `.github/upstream-versions.json`
- signing configuration supplied through GitHub Secrets

## Localization review areas
- `app/src/main/res/values*/strings.xml`
- `app/src/main/java/**/*.kt`
- `workspace/src/main/res/values*/strings.xml`
- `workspace/src/main/java/**/*.kt`

After an upstream merge, compare and reapply localized strings. Never restore complete Kotlin/XML source files blindly, because that can discard upstream logic and security fixes.

## 汉化方式与合并后检查

1. 对比 XML `values/strings.xml` 与 `values-zh-rCN/strings.xml` 键。
2. 检查 Compose `stringResource(...)` 的简体中文资源。
3. 扫描 Kotlin 硬编码 `Text`、标题、说明、状态、选项和单位。
4. Termux 页面混用资源与硬编码，每次合并必须专项复查。
5. 权限名、属性键、命令、路径、包名和 API 标识不翻译。
6. 新功能有未汉化文本时阻止 Release；翻译并复核后补充存档，再更新 `fork-localization`。
