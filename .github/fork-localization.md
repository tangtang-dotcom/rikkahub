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
