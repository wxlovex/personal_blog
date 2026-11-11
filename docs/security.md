# 本地安全加固（第一批）

本轮只修改本地代码并使用独立测试数据库验证，没有部署或修改现有业务库。

## 已实现

| 控制 | 默认值 | 行为 |
| --- | --- | --- |
| 登录来源限流 | 每来源 20 次/分钟 | 两套路由共用额度，包含非法参数请求 |
| 登录账号限流 | 每账号 5 次/分钟 | 数据库实际账号 ID 作为键，成功登录清除账号失败计数；超限最多等待当前 60 秒窗口结束 |
| 注册来源限流 | 每来源 5 次/小时 | 两套路由共用额度，可用 REGISTRATION_ENABLED=false 关闭注册 |
| 写入限流 | 每来源及每登录账号各 60 次/分钟 | POST/PUT/PATCH/DELETE；换来源无法绕过账号额度 |
| 读取限流 | 每来源 240 次/分钟 | REST 接口共用额度，页面与静态资源不计入 |
| 退出限流 | 独立每来源 240 次/分钟 | 写入额度用完仍可退出 |
| 请求体限制 | 1 MiB | 在 JSON 解析前计量实际字节；无 Content-Length 的分块请求同样受限，返回 413 |
| 限流缓存 | 最多 20000 个键 | 并发原子计数；过期清理；容量用完拒绝新键，不驱逐活跃限额 |
| 会话 | 24 小时、每账号最多 20 个 | JWT 含唯一 jti，数据库中必须存在未过期会话；退出删除对应会话 |

阈值都是初始值，可通过 `.env.example` 列出的环境变量调整。限流采用固定窗口，返回 429、统一 JSON 和 Retry-After；受信任跨域前端能读取 Retry-After。
限流器只在当前进程内共享，重启后计数重置；多实例部署前必须改用共享限流存储或边缘限流。
匿名请求体在鉴权前最多缓冲 MAX_REQUEST_BYTES，生产应配合边缘限流或下调上限；仅 POST、PUT、PATCH、DELETE 缓冲请求体。

登录不存在的账号时也执行 BCrypt 校验，减少通过处理时间判断用户名是否存在的差异。
安全日志只记录触发的策略，不输出密码、Token 或提交的用户名。

全站增加 X-Content-Type-Options、X-Frame-Options、Referrer-Policy、HSTS、Permissions-Policy 和 CSP。脚本仅允许同源，禁止对象嵌入与被其他页面嵌套；Vue 所需的内联样式保留。
保留原有按用户隔离数据、公开文章投影、参数化 SQL、输入校验和私有响应禁止缓存等机制。

## 会话与前端兼容

登录的返回值仍是 Token 字符串，原调用方式保留。新增 POST /user/logout 与 /api/user/logout。
前端等待服务端撤销成功后再清理本地身份；网络失败显示重试提示。跨标签页清理仍生效，旧账号请求迟到的 401 不会清除新账号的凭证。
会话记录保存在数据库，服务重启后已撤销的 Token 不会恢复；各设备单独撤销。
会话上限及过期清理在用户行锁下执行，防止并发登录突破存储上限。

## 数据库迁移

新库使用当前 schema.sql。已有库在备份并核对版本后执行 database/upgrade-v6-auth-sessions.sql。
该脚本幂等，只新建 auth_session，不改日记、分类或用户数据。生产环境仍不自动执行 SQL。
必须先迁移再启动新版；缺失表时不会回退到无会话校验模式。升级前的 JWT 全部需要重新登录。

## 本地验证

2026-11-10 本地结果：52 项后端测试全部通过；10 项真实浏览器/HTTP/服务重启验收通过；前端生产构建通过。没有部署到公网。

运行 mvn verify：原业务回归、安全接口回归、并发限流、过期恢复、限流容量、实际请求字节计量和会话迁移测试。
测试使用独立 H2。不要把 TEST_DB_URL 指向已有业务库，部分原集成测试会清空数据。

前端运行 npm run build，随后可运行 scripts/verify-security.py（需要 Python Playwright、JDK 和本地打包 Jar）。
本地 Jar 验收前需把前端 dist 的内容复制到 target/classes/static，再执行 Maven package；这样验收的是 Jar 内的前端，不依赖 Vite 开发服务器。
浏览器验收脚本只在 127.0.0.1:18081 创建服务，使用 target/security-browser/ 下独立 H2 文件库；不读取业务数据库配置。
它验证真实页面的注册/登录/写日记、跨账号隔离、退出失败重试、跨标签页退出、旧 Token 重放、413、429 和真实 JVM 重启后的会话持久性。
证据保存在 target/security-final-verify.log、target/security-browser/result.json 和 target/security-browser/logout.png。

Tomcat 从 10.1.55 升至 10.1.61，对应 Apache 官方已披露补丁：https://tomcat.apache.org/security-10.html 。这不等于已经完成全部依赖及基础镜像扫描。

## 后续上线前必须核实

来源默认取 TCP 连接对端，明确禁用自动信任 Forwarded/X-Forwarded-For。不能直接使用客户端提供的头来限流。
托管平台前有代理时，必须先验证代理覆盖规则与可信代理范围，正确配置来源识别或将来源限流放在边缘；否则可能把所有用户合并到一个额度。
可通过 `FORWARD_HEADERS_STRATEGY=NATIVE` 和 `TRUSTED_PROXY_PATTERN` 配置 Tomcat 原生代理解析。
两项默认分别为 `NONE` 和 `(?!)`（不匹配任何对端）；仅启用 NATIVE 仍不会信任任意请求头。
`TRUSTED_PROXY_PATTERN` 使用 Java 正则表达式而非 CIDR，只应匹配经过确认的代理地址。
例如专用测试代理 `192.0.2.10` 可配置为 `192\.0\.2\.10`；此地址仅作示例，不能直接用于 Render。
代理必须清理或正确追加客户端传入的转发头，且应用入口必须限制直接绕过代理的连接。
Tomcat 会从右向左遍历地址链，遇到首个不可信地址即确定客户端，不会直接使用最左侧的任意值，
详见 [Tomcat RemoteIpValve 文档](https://tomcat.apache.org/tomcat-10.1-doc/api/org/apache/catalina/valves/RemoteIpValve.html)。
本轮真实 HTTP 回归验证了可信代理下两个客户端使用独立预算、伪造最左侧地址不能绕过限制，
以及未配置可信代理时即使 NATIVE 开启也不能伪造来源。线上代理地址仍需在部署环境核实。
本地联调尚不能替代 MySQL/TiDB 实例上的迁移、并发和 TLS 验证；本轮未连接云端数据库。

第二批已补密码修改/恢复、邮箱归属验证、可选的广场发布邮箱门槛与账号配额；目前邮件验证和找回密码暂缓启用，发布门槛默认关闭，见 [account-security.md](account-security.md)。异常注册验证、共享限流、Spring Security 过滤器链迁移、完整依赖与镜像扫描、云端最小权限、备份恢复及告警仍属后续工作。
Token 目前继续保存在 localStorage；CSP 与文本转义降低脚本注入风险，不代表任何脚本注入都能被阻止。
