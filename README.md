# EasyAuthReset

面向 **Fabric 1.21 / 1.21.1 服务端**、基于 [EasyAuth](https://github.com/NikitaCartes/EasyAuth) 的**密码邮箱自助重置模组**。

玩家忘记密码、无法登录时，**无需联系管理员**，在游戏内自助完成重置：

```
/resetpassword bind <你的邮箱>          → 绑定账号邮箱（验证邮箱所有权）
/resetpassword bind confirm <验证码>    → 完成绑定（或点击邮件里的激活链接）
/resetpassword                         → 向绑定邮箱发送重置验证码
/resetpassword confirm <验证码>        → 密码重置，临时密码发送到邮箱
```

**特性一览**

- 🔐 未登录玩家可直接执行（利用 EasyAuth 未登录状态下玩家实体已建立的特性）
- 📧 验证码一次性 / 5 分钟过期 / 错误 5 次作废 / 冷却 5 分钟（防滥用、防暴力）
- 👤 邮箱绑定（bind）验证所有权；换邮箱全程自助，无需找管理
- 🛡️ 服主预登记邮箱（`mailmap`）优先，玩家无法篡改
- 🔗 可选"点击邮件链接激活绑定"（内置轻量 HTTP 服务，零依赖）
- 🚨 可选 IP 一致性校验 + **三方告警**（游戏内 OP、管理员邮件、服务器日志）
- 💾 验证码 / 冷却 / 绑定关系磁盘持久化，**服务器重启不丢**
- 🌐 中英双语消息 + 可自定义前缀；SMTP 密码支持环境变量，避免明文
- 🧩 兼容 EasyAuth **3.3.5 – 3.4.4**（对发布 jar 逐项 `javap` 验证）

---

## 快速部署

1. 服务器（MC 1.21 或 1.21.1）`mods/` 放入：
   - `fabric-api`（1.21 版，如 `0.100.1+1.21`）
   - `easyauth` ≥ **3.3.5**（Modrinth/CurseForge 有 MC 1.21 版，推荐 3.4.4）
   - 本模组 `easyauthreset-1.0.0.jar`
2. 启动服务器，日志出现 `EasyAuthReset 初始化完成` 即加载成功（自动生成配置文件）
3. 编辑 `config/easyauthreset.json`：
   - `emailSender` + `emailPassword`：发件邮箱与应用专用密码（Gmail 需先开两步验证；也可以用 `emailPasswordEnvVar` 环境变量）
   - `alertEmail`：安全告警收件邮箱（建议填写）
   - 重启服务器生效

> 📄 完整配置示例见仓库根目录 [easyauthreset.example.json](easyauthreset.example.json)。

## 支持的命令

| 命令 | 说明 |
|---|---|
| `/resetpassword` | 无参数：已绑定/已登记 → 直接向绑定邮箱申请重置；否则显示用法 |
| `/resetpassword <邮箱>` | 申请重置（仅当账号<b>未绑定</b>且 `requireEmailBind: false` 时才接受输入邮箱；默认会拒绝并提示先绑定） |
| `/resetpassword confirm <验证码>` | 确认重置，生成临时密码（邮件 + 私聊消息） |
| `/resetpassword bind <邮箱>` | 绑定/更换邮箱：向该邮箱发验证码（验证所有权；已有绑定则提示"更换"） |
| `/resetpassword bind confirm <验证码>` | 完成绑定/更换 |

## 配置项（config/easyauthreset.json）

| 键 | 默认 | 说明 |
|---|---|---|
| `smtpHost` / `smtpPort` | `smtp.gmail.com` / `587` | SMTP 服务器（支持任意 STARTTLS/SSL 服务商） |
| `smtpTls` | `true` | STARTTLS（587 端口） |
| `smtpSsl` | `false` | SSL（465 端口），开启则忽略 `smtpTls` |
| `emailSender` / `emailPassword` | — | 发件邮箱与**应用专用密码**（非登录密码） |
| `emailPasswordEnvVar` | `""` | 从环境变量读取 SMTP 密码（优先于 `emailPassword`），如 `EASTYAUTHRESET_SMTP_PASSWORD` |
| `smtpTimeoutMillis` | `15000` | SMTP 连接/读写超时 |
| `smtpRetries` | `1` | 发送失败额外重试次数（间隔 3 秒，0–3） |
| `codeExpirySeconds` | `300` | 验证码有效期（30–86400） |
| `codeLength` | `6` | 验证码位数（4–10） |
| `maxCodeAttempts` | `5` | 错误尝试上限，超限作废 |
| `tempPasswordLength` | `12` | 临时密码长度（8–64） |
| `showTempPasswordInChat` | `true` | 私聊显示临时密码（SMTP 故障时的自救途径；`false` 仅靠邮件） |
| `cooldownSeconds` | `300` | 同一 UUID 两次申请间隔（10–86400） |
| `requireEmailBind` | `true` | 强制邮箱登记/绑定：未绑定账号不能重置（默认开，杜绝"验证码发到误填邮箱"） |
| `enableClickActivation` | `false` | 绑定邮件附带**一次性激活链接**（点击即完成绑定，无需输码） |
| `activationHttpPort` | `8123` | 激活 HTTP 服务端口（开启才需要放行防火墙） |
| `activationHttpBind` | `0.0.0.0` | 监听地址（`0.0.0.0`=对外可达；仅内网填 `127.0.0.1`） |
| `activationPublicUrl` | `""` | 玩家浏览器访问的公开地址（如 `http://域名:8123`）；反代 HTTPS 场景填 `https://域名` 并把 `/easyauthreset/` 代理到该端口 |
| `language` | `"zh"` | 消息语言：`zh` / `en` |
| `messagePrefix` | `""` | 玩家消息统一前缀，如 `§e[密码重置]§r ` |
| `ipCheckMode` | `"off"` | IP 一致性校验：`off` 关；`warn` 差异仅告警放行；`strict` 差异拦截+告警 |
| `alertEmail` | `""` | 安全事件告警邮箱（为空则仅游戏内 OP + 日志） |
| `verificationSubject` / `newPasswordSubject` | — | 邮件主题 |

配置缺失/非法字段会自动回填默认值并重写文件，不会导致启动失败。

**安全向推荐配置**（开箱即用偏安全，保持默认即可，按需微调）：

```json
{
  "requireEmailBind": true,
  "ipCheckMode": "warn",
  "alertEmail": "管理员邮箱",
  "emailPasswordEnvVar": "EASTYAUTHRESET_SMTP_PASSWORD",
  "enableClickActivation": false
}
```

## 玩家使用流程

### 忘记密码（全程自助，约 1 分钟）

1. 进服（未登录，被 EasyAuth 拦在出生点）
2. 有**服主登记邮箱**：直接 `/resetpassword` → 收验证码，跳第 4 步
   无登记邮箱：先自助绑定：
   - `/resetpassword bind 你的邮箱` → 收到绑定验证邮件（含 6 位验证码；开启点击激活后还有一次性链接，**点链接或输码二选一**）
   - `/resetpassword bind confirm 验证码` → 绑定成功（跳过第 3 步，直接 `/resetpassword`）
3. `/resetpassword`（无参数）→ 收到重置验证码
4. 若等待期间被 EasyAuth 超时踢出：**重新进服**即可，验证码不变（已持久化，重启也不丢）
5. `/resetpassword confirm 验证码` → 收到临时密码（邮件 + 私聊消息）
6. `/login 临时密码` 登录
7. `/account changePassword 临时密码 新密码` 立即修改

### 更换邮箱

`/resetpassword bind 新邮箱` → 验证 → 完成更换。**无需联系管理员**（仅"服主登记"的账号由管理在 mailmap 中修改）。

## 服主预登记邮箱（可选）

`config/easyauthreset_mailmap.json`（首次运行自动生成 `{}` 模板），由服主维护：

```json
{
  "steve": "steve@example.com",
  "c1f4a3d2-1111-2222-3333-444455556666": "alex@example.com"
}
```

- 键：**玩家名小写**（优先）或 **UUID**；值：对应邮箱
- 登记后：玩家 `/resetpassword`（无参数）→ 验证码**只发登记邮箱**，输入任何邮箱都被忽略，**无需 bind**；执行 bind 会被拒绝
- **运行时从不写回该文件**，玩家无法篡改；修改后重启生效

## 点击激活链接（可选，默认关闭）

- 开启条件（4 项）：`enableClickActivation: true`、`activationPublicUrl`（如 `http://你的域名或公网IP:8123`）、`activationHttpPort`、`activationHttpBind`
- 防火墙/云安全组**放行该 TCP 端口**；或 Nginx/Caddy 把 `/easyauthreset/` 反代到 `http://127.0.0.1:8123` 并填 `https://域名`（推荐，额外获得 HTTPS）
- 日志出现 `点击激活服务已启动` 即成功
- 安全设计：token 为 40 位随机十六进制、一次性、与验证码同生命周期、只做"绑定"（无重置能力）、页面不回显任何输入
- **省心选择**：保持关闭（默认），玩家用 6 位验证码，无需开放任何端口

## IP 一致性校验与安全告警（可选）

`ipCheckMode`：`warn`（默认建议）| `strict` | `off`。触发时机：一切"向邮箱发验证码"的入口（申请重置 / 绑定）。

当**当前连接 IP ≠ 账号历史登录 IP**（EasyAuth 记录的 `lastIp`）时，**三方告警**：

| 渠道 | 内容 |
|---|---|
| 🎮 游戏内（所有在线 OP） | `⚠️ EasyAuthReset 安全告警：玩家 xxx (uuid=xxx) 当前IP=… 历史IP=… 操作=…` |
| 📧 邮件（`alertEmail`） | `[EasyAuthReset] 安全告警: IP 差异 - …`（含玩家/UUID/操作/邮箱/前后 IP/绑定邮箱） |
| 📝 服务器日志 | `IP 差异告警: 玩家=…` |

- 同一 UUID 60 秒内同类告警只发一次（防刷屏）
- `strict`：差异**拦截**操作并提示玩家；`warn`：放行但保留全部告警
- 无历史 IP / 取不到当前 IP / IP 一致 → 一律放行（不误杀新玩家）

## 安全机制一览

- **UUID 绑定**：验证码/冷却/邮箱映射以连接实体 UUID 为键，跨重连稳定
- **冷却**：同 UUID 默认 5 分钟一次申请（发送失败也计入冷却）
- **验证码**：一次性、过期、错误上限（默认 5 次）、用途区分（重置/绑定）
- **密码哈希**：沿用 EasyAuth 的 BCrypt（`AuthHelper.hashPassword`），库中无明文
- **主线程零阻塞**：BCrypt 哈希与 SMTP 均在异步线程
- **配置容错**：缺字段回填 + 范围校验，删改配置不会导致启动失败
- **审计日志**：申请/绑定/重置/告警均记录玩家名 + UUID + 邮箱

### 已知局限：离线服身份伪造

非正版（cracked）服务器无法证明"连接者 = 账号主人"（离线 UUID 由昵称派生），任何人可用他人昵称进服执行重置流程 —— 这是**所有**邮箱自助重置方案在纯离线服上的固有风险。缓解手段（请尽量组合使用）：

1. 开启 EasyAuth 正版校验（`/account online` + Mojang API 校验）—— 正版服则无此问题；
2. `mailmap` 服主预登记（登记邮箱优先级最高，玩家不可改变）；
3. `ipCheckMode: warn/strict` 三方告警；
4. 日志审计 + 定期检查 `easyauthreset_emails.json`。

## 兼容性

| EasyAuth 版本 | 结论 |
|---|---|
| **3.3.5 / 3.3.6**（MC 1.21） | ✅ 支持（`depends: easyauth >=3.3.5`） |
| 3.4.1 / 3.4.2 / 3.4.3 / 3.4.4 | ✅ 支持 |
| < 3.3.3 | 未验证，不建议 |

兼容性说明：3.3.x 与 3.4.x 差异在于 Mixin 接口 `PlayerAuth` 的包名（`utils` ↔ `interfaces`），
本模组通过**反射调用** `easyAuth$getPlayerEntryV1()` / `easyAuth$getIpAddress()`（两代方法名一致）
兼容两个版本；`EasyAuth.DB`、`DbApi.getUserData`、`PlayerEntryV1`、`AuthHelper.hashPassword` 均逐项核对一致（3.3.5 / 3.4.4 发布 jar `javap` 验证）。

> `libs/easyauth-mc1.21-3.4.4.jar` 仅为**编译期 API 参照**（gitignore，不打入产物）；
> 运行时使用服务器 `mods/` 中的 EasyAuth（3.3.5 完全可用）。

## 构建与 CI

```bash
# 依赖：JDK 21（wrapper 自动下载 Gradle 9.5.1）
# 首次：下载 EasyAuth 编译期参考 jar 到 libs/（见 build.gradle 注释）
./gradlew build        # Windows: gradlew.bat build
```

产物：`build/libs/easyauthreset-1.0.0.jar`（javax.mail 以嵌套 jar 打入 `META-INF/jars/`）。

- **CI**：[`.github/workflows/build.yml`](.github/workflows/build.yml) —— push/PR 自动下载 EasyAuth API jar 并构建，产物上传为 artifact。
- **构建排坑**：EasyAuth 3.4.4 jar 由 Loom 1.14.10 构建（更高版本 Loom 才能作为依赖），且其模块元数据与 `plugins {}` 标记机制冲突，故使用 **buildscript classpath 方式**应用 Loom 1.17.19（见 `build.gradle` 注释）。

### 开发模式运行

```bash
./gradlew runServer    # 首次在 run/eula.txt 写入 eula=true
```

已内置 dev 用 `sqlite-jdbc`（`runtimeOnly`，不进产物），开发服务器可完整启动。
注意：开发模式验证的是"加载与初始化"，SMTP 收发与指令全链路请在真实服务器测试。

## 项目结构

```
src/main/java/xyz/nikitacartes/easyauthreset/
├── EasyAuthReset.java              # 模组主类（装配、生命周期）
├── config/EasyAuthResetConfig.java # 配置（缺省回填 + 范围校验）
├── command/ResetPasswordCommand.java
├── handler/PasswordResetHandler.java   # 核心业务（单例：冷却/绑定/IP校验/告警）
├── email/EmailService.java
├── email/GmailEmailService.java    # JavaMail SMTP（异步 + 重试 + 环境变量密码）
├── verification/VerificationCodeManager.java  # 验证码 + 激活 token（持久化）
├── storage/PlayerEmailStorage.java # 邮箱映射 v2 + 服主 mailmap
├── storage/StateStorage.java       # 验证码/冷却持久化
├── util/PasswordGenerator.java
├── util/Lang.java                  # zh/en 双语 + 前缀
└── web/HttpActivationServer.java   # 点击激活链接（可选，JDK 内置 HTTP 服务）
```

## 故障排查

| 现象 | 处理 |
|---|---|
| 启动报 `Mod was built with a newer version of Loom` | 升级 Loom（本工程已固定可用的组合） |
| 邮件发送失败 | 检查应用专用密码 / SMTP 端口 / `smtpTimeoutMillis`；查看日志 `邮件发送失败` |
| 日志出现 SMTP 未配置警告 | 填 `emailSender` + `emailPassword`（或设置 `emailPasswordEnvVar`） |
| 验证码无效 | 确认 5 分钟内、未被使用、未超错误次数；重启后仍有效（已持久化） |
| 提示"请先绑定" | 执行 `/resetpassword bind <邮箱>`，或由服主在 `mailmap` 登记 |
| 点击激活页面打不开 | 检查防火墙端口 / `activationPublicUrl` 是否与端口一致；反代方案确认路径 `/easyauthreset/` |
| 更新密码后无法登录 | 检查 EasyAuth 数据库连接；确认临时密码完整（含特殊字符后无多余空格） |
| 控制台出现中文乱码（类似 `宸茬敓鎴?`） | 这是 Windows 控制台编码问题：**服务器端日志已全部使用英文**（管理员向），玩家游戏内消息与邮件不受影响、正常显示中文。若仍出现乱码，可尝试在启动脚本中加 `-Dfile.encoding=UTF-8` 或控制台执行 `chcp 65001` |
