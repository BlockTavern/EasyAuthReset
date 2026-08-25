# 更新日志 / Changelog

> 本文件由人工维护（中英双语）。发布时 GitHub Release 流程会自动读取**当前版本**的条目作为发布说明；
> 若某版本缺条目，则回退使用 git 提交记录。格式：`## vX.Y.Z` 开头，直到下一个 `##` 结束。

---

## v1.0.8

- 中文：统一游戏内消息配色方案——成功=绿、进行中=黄、错误=红、邮箱/临时密码=青色高亮、警告=金、次要说明=灰。
- EN: Unified chat message color scheme — green=success, yellow=info, red=error, aqua=emails/temp passwords, gold=warnings, gray=secondary hints.

## v1.0.7

- 中文：SMTP 启动探测升级：DNS 解析诊断、IPv6 检查、自动探测备用端口（465/587），并给出明确配置建议。
- EN: Upgraded startup SMTP probe: DNS/IPv6 diagnostics and automatic alternate-port tests (465/587) with config hints.

## v1.0.6

- 中文：版本号修正（修复 v1.0.5 误发产物文件名的问题）。
- EN: Version numbering fix (v1.0.5 misnamed artifact was removed).

## v1.0.5（已合并）

- 中文：SMTP 启动可达性探测；新增管理命令 `/easyauthreset test <邮箱>`（OP/控制台发送测试邮件）；冷却改为仅内存（重启即清空，运维重启不惩罚玩家）。
- EN: Startup SMTP reachability probe; new OP command `/easyauthreset test <email>`; cooldown now memory-only (cleared on restart).

## v1.0.4

- 中文：完善全部操作路径的玩家提示（成功/错误/警告一一对应），并明确"已绑定邮箱时使用绑定邮箱"的提示。
- EN: Complete prompt coverage for every command path; clear hint when a bound/registered email is used.

## v1.0.3

- 中文：新增配置 `emailSenderName`——自定义发件人显示名（可中文，如 "BlockTavern（方块酒馆）"）。
- EN: New option `emailSenderName` — custom sender display name.

## v1.0.2

- 中文：新增配置 `emailReplyTo`——玩家回复邮件转到指定地址（如支持@你的域名）；修复字段重复的构建问题。
- EN: New option `emailReplyTo` (custom reply-to address); fixed duplicated field build issue.

## v1.0.1

- 中文：SMTP 配置不匹配诊断（发件域名 vs smtp.gmail.com 自动警告）；国内 SMTP 服务商配置指南；邮件失败日志含主机/端口/发件人提示。
- EN: SMTP mismatch diagnostics (sender domain vs smtp.gmail.com) + domestic SMTP provider guide; failure logs include host/port/sender hints.

## v1.0.0

- 中文：首个发布版。未登录玩家通过邮箱验证码自助重置 EasyAuth 密码；邮箱绑定/换绑、服主预登记（mailmap）、验证码/激活链接/冷却持久化、点击激活链接（可选）、IP 一致性校验与三方告警（可选）、SMTP 环境变量密码、zh/en 双语、CI 自动构建与发布。
- EN: Initial release. Email-based self-service password reset for unlogged players; email binding/rebind, admin mailmap, code/link/cooldown persistence, optional click-activation links, optional IP-consistency alerts, env-var SMTP password, zh/en messages, CI build & release.
