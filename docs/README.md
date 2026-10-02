# 文档索引（README）

更新：2026-10-02。本目录是项目唯一权威文档源。**接手的 AI 或人从这里开始。**

## 阅读顺序（按此顺序读，不要跳）

| 顺序 | 文档 | 内容 | 读者 |
|---|---|---|---|
| 1 | [HANDOVER.md](HANDOVER.md) | 交接总纲：现状快照、环境准备、必读规则、下一步任务索引 | **接手 AI 必读** |
| 2 | [CONVENTIONS.md](CONVENTIONS.md) | 约束规范：硬约束、混编边界、TDD、门禁、提交规范 | 写代码前必读 |
| 3 | [STATUS.md](STATUS.md) | 进度台账：已完成（含提交对照）、未完成、已知问题 | 了解现状 |
| 4 | [ROADMAP.md](ROADMAP.md) | 待办任务书：每个任务的目标/验收/授权文件 | 领取任务 |
| 5 | [ARCHITECTURE.md](ARCHITECTURE.md) | 架构：分层、数据流、语言边界、主题系统 | 改动前理解系统 |
| 6 | [DEVELOPMENT.md](DEVELOPMENT.md) | 操作手册：命令、测试、排障速查 | 日常操作 |

## 目录结构

```
docs/
  README.md           本文件
  HANDOVER.md         交接总纲
  CONVENTIONS.md      约束规范（唯一约束源，取代旧 dev-contract）
  STATUS.md           进度台账
  ROADMAP.md          待办任务书
  ARCHITECTURE.md     架构说明
  DEVELOPMENT.md      操作手册
  archive/            历史文档归档（只读参考，内容已被上文文档吸收或过时）
  superpowers/specs/  历史设计定稿（设计依据，仍然有效）
  superpowers/plans/  历史实施计划（已执行完毕，留档）
  ui/ ui-redesign/    可点击原型与设计包（视觉参考）
```

## 单一事实源约定

- **约束**：只有 CONVENTIONS.md。与其他文档冲突时以它为准。
- **进度**：只有 STATUS.md。完成任何任务必须更新它。
- **待办**：只有 ROADMAP.md。新任务先入它再开工。
- `archive/` 与 `superpowers/` 下的历史文档不再更新；引用其结论时须与上文三源核对。
