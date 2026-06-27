# AI / Agent 说明 — CleanSweep

本文件包含在本仓库中工作的 AI 编程助手（Grok、Claude、Cursor 等）应遵循的规则与上下文。

**若本文件存在，在开始任何非平凡任务时，务必先阅读本文件。**

## 关键规则：Git 提交与 Conventional Commits

> **若用户要求你创建 git 提交**（例如："commit the changes"、"use git to commit"、"make a commit"、"git commit this"，或类似要求生成提交信息的指令），你**必须**严格遵循 [Conventional Commits](https://www.conventionalcommits.org/) 规范。

### 要求
- 使用标准格式：
  ```
  <type>(<optional scope>): <description>

  [optional body]

  [optional footer(s)]
  ```
- 常用 type：`feat`、`fix`、`docs`、`style`、`refactor`、`perf`、`test`、`chore`、`build`、`ci`
- 主题行使用清晰、小写、现在时描述（句末不加句号）。
- 在有助于理解时添加 scope（例如 `feat(swiper)`、`fix(ui)`、`docs(contributing)`）。
- 破坏性变更使用 `!` 或 `BREAKING CHANGE:` 页脚。

### 良好的提交信息示例
- `feat(swiper): move next/clear labels inline with icons`
- `fix(layout): reduce padding around image card to match tighter reference`
- `docs: add Conventional Commits requirement for AI agents`
- `refactor(cardstack): simplify maxCardHeight calculation`

### 为何重要
- 支持自动生成变更日志。
- 支持语义化版本管理。
- 保持 git 历史一致且机器可读。

**此规则必须在文档中可见。** 因此，相同说明会以注释或章节形式标注于：
- CONTRIBUTING.md
- README.md
- docs/*.md

这样，无论通过工具还是其他方式阅读这些文件时，都能遇到并记住该要求，贯穿多次对话。

---

## 其他说明
- 尽可能优先编辑现有文件，而非新建文件。
- 对于 Android/Kotlin 变更，在**提交前**运行 `./gradlew :app:compileDebugKotlin`（或完整 assemble）进行验证——**不要跳过此步骤**。若 CLI 使用的 JDK 与 Android Studio 不一致，应调整环境（例如项目 JDK/工具链），而不是省略验证。参见 `docs/cleaning-app-architecture.md` 第 2.3 节。
- 遵循 CONTRIBUTING.md 中记录的架构与编码规范。
- 进行 UI 变更时，需同时考虑手机布局（OrganizePhoneLayout）与展开式布局。

若不确定提交信息格式，请询问用户，或默认使用 `chore` 类型并附上清晰描述。