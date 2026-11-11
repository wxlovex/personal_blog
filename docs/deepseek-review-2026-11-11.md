# DeepSeek 原始审查报告

以下为 DeepSeek 只读审查的原始结论；核实、修复及验证结果另见 `review-fixes-2026-11-11.md`。条件性风险及建议不代表已证实漏洞。

## 审查摘要

**范围**：后端 `src/main/java` 全部 73 个 Java 文件、18 个测试类、`application.yaml`、`schema.sql`、v2–v7 迁移、`pom.xml`、Dockerfile、CI 配置；前端 `personal_blog_vite/src` 全部 26 个源文件及 Vite/package 配置。`frontend/` 快照与 `personal_blog_vite` 的 `src/public/package.json/package-lock.json/vite.config.js/index.html` 已逐文件 SHA-256 比对，**完全一致**，未发现部署快照漂移。

**结论**：未确认存在 IDOR/越权读写、SQL 注入、存储型 XSS、密码明文/弱哈希、会话伪造或跨账号数据泄露。鉴权（JWT + 数据库会话）、私有资源 404 统一、每写操作账号行锁、配额、请求体字节限制、公开接口收敛、SQL 参数化均有代码与测试支撑。主要问题集中在**部署来源识别（P1，需实测确认）**、补记分页的前端呈现、异常处理误判登录状态，以及若干 Unicode/不可见字符边界和停用功能残留。

---

## 问题列表

### P1-1 反向代理下按来源限流可能退化为全站共享额度

- **位置**：`D:\project\personal_blog\src\main\java\com\example\personal_blog\security\RequestRateGuard.java:26`、`:38-39`、`:43`、`:48`、`:57`；`D:\project\personal_blog\src\main\resources\application.yaml:5`、`:60-64`。
- **触发条件**：应用部署在会改写 TCP 对端的反向代理之后（README 记录生产为 Render Docker 服务；`docs/security.md:59-60` 已把可信代理配置列为“上线前必须核实”）。
- **实际影响**：`bench` 只取 `request.getRemoteAddr()`，而 `forward-headers-strategy: NONE` 明确禁用 Forwarded 头解析，Tomcat 不会还原真实客户端地址。若代理不保留原始对端，全站会共用同一个来源键：登录 20 次/分钟、注册 5 次/小时、写入 60 次/分钟、读取 240 次/分钟、敏感操作 5 次/分钟全部变成**全站总额度**。正常访问或多用户使用时会出现大面积 429；注册在 5 次/小时后完全不可用。现有账号级限流（登录/写入）仍然有效，不能缓解公开读取和注册。
- **代码依据**：`peer = request.getRemoteAddr()` 是唯一来源键；配置显式设置 `NONE`；测试 `SecurityProtectionTests:111-123` 还专门验证了 X-Forwarded-For 不能绕过，说明这是有意设计，但未解决代理场景的合并问题。
- **最小修复**：在确认 Render 代理网段后，配置 `server.forward-headers-strategy=NATIVE` 加 `server.tomcat.remoteip.internal-proxies=<可信代理 CIDR>`，只信任代理覆盖的 Forwarded 信息；或在边缘做来源限流并下调应用内按 IP 限额，保留账号级限额。不要直接信任任意 `X-Forwarded-For`。
- **验证方式**：在预发/生产临时记录一次请求的 `getRemoteAddr()` 与 `X-Forwarded-For`（不返回给客户端）；若所有请求的 `remoteAddr` 相同且不等于客户端地址，即可确认。再从两个不同网络同时发起登录/读取，观察是否共享同一 429 计数。

### P2-2 新增补记后当前页看不到，且列表被重置到第一页

- **位置**：`D:\project\personal_blog_vite\src\components\CommentSection.vue:18-31`、`:38`、`:59`；`D:\project\personal_blog\src\main\java\com\example\personal_blog\mapper\CommentMapper.java:10-15`；`D:\project\personal_blog\docs\api.md:117`。
- **触发条件**：一篇日记已有 10 条以上补记，用户提交新补记。
- **实际影响**：后端按 `create_time ASC, id ASC` 正序分页，新补记落在最后一页；前端提交成功后调用 `fetchComments()`（无参数→第 1 页），用第 1 页覆盖当前列表。用户刚保存的补记在界面上不出现，已加载的多页补记也被重置；用户可能误以为保存失败而重复提交，重复内容会继续占用配额。这不是后端分页错误（文档明确正序），是前端提交后的刷新策略问题。
- **代码依据**：`commentPublish` 的返回值被忽略（`:38`）；`fetchComments` 在非追加模式下直接 `comments.value = data.data.records`（`:27`）。
- **最小修复**：使用 `commentPublish` 返回的 `Comment` 追加到列表并 `total++`，或提交后按 `Math.ceil(total/size)` 请求最后一页；同时滚动到新补记。补一个前端用例（11 条补记时提交后可见）。
- **验证方式**：准备 10 条补记，提交第 11 条，观察列表是否仍只有前 10 条、需点“翻开更多补记”才能看到第 11 条；修复后应立即可见且总数一致。

### P2-3 `/user/me` 的非 401 失败会把已登录用户送回登录页

- **位置**：`D:\project\personal_blog_vite\src\router\index.js:42-53`；`D:\project\personal_blog_vite\src\api\index.js:27-36`。
- **触发条件**：首次进入 `requiresAuth` 页面时，`userMe()` 因网络超时、断网或后端 5xx 失败（不是 401）。
- **实际影响**：`catch` 对所有异常都执行 `return { path: '/login', ... }`，用户被强制离开当前私人页面；但本地 Token 并未清除，属于“仍登录却被当成未登录”。编辑中的内容、分页和筛选状态丢失，用户需要重新输入密码。真正的 401 已经由 axios 拦截器处理（`:27-36`），路由守卫无需把网络错误也当作认证失败。
- **代码依据**：守卫的 `try/catch` 未检查 `error.response?.status`；`currentUser` 在失败时保持 `null`。
- **最小修复**：只在 `error.response?.status === 401` 时跳登录；其余错误保留当前路由并展示可重试状态（或让页面自身的错误面板处理）。同时避免与 axios 的 `window.location.replace` 重复跳转。
- **验证方式**：用代理/离线模拟 `/api/user/me` 返回 500 或网络错误，直接打开 `/`，观察是否被送到 `/login`；修复后应停留在 `/` 并显示错误/重试。

### P3-4 邮箱验证接口可探测邮箱归属，且 409 分支绕过用户级邮件额度

- **位置**：`D:\project\personal_blog\src\main\java\com\example\personal_blog\security\AccountSecurityService.java:74-81`；`D:\project\personal_blog\src\main\java\com\example\personal_blog\controller\AccountSecurityController.java:42-46`。
- **触发条件**：已登录用户（注册开放，可自行注册）调用 `POST /user/email/verification`，提交任意邮箱和当前密码。
- **实际影响**：邮箱已被其他账号验证时返回 409“该邮箱无法绑定”，未绑定/属于本人时返回 200 并发信，形成邮箱归属探测。更关键的是 409 分支在 `limiter.acquire("verify-mail-user:" + user.getId(), ...)` **之前**返回，因此不消耗该账号每小时 3 次的邮件额度；攻击者只受来源 `sensitive-per-minute`（默认 5 次/分钟）约束，单 IP 约 300 次/小时。会泄露“某邮箱是否已注册并验证”的隐私信息，但不修改数据。`AccountSecurityIntegrationTests:190-195` 明确测试了 409，说明差异响应是产品取舍，额度顺序是缺陷。
- **最小修复**：把 `verify-mail-user` 的获取移到归属检查之前（或先统一返回 200，再异步向邮箱所有者发“有人尝试绑定”的通知）；保留 409 时也必须消耗账号级探测额度。
- **验证方式**：账号 A 在 1 小时内用账号 B 的已验证邮箱调用 4 次，当前应持续返回 409 而不触发账号级 429；修复后第 4 次应进入 429。

### P3-5 Unicode 边界处理不一致：不可见分类/补记，以及注册可建但无法登录的用户名

- **位置**：
  - `D:\project\personal_blog\src\main\java\com\example\personal_blog\service\impl\CategoryServiceImpl.java:40-41`、`:59-60`。
  - `D:\project\personal_blog\src\main\java\com\example\personal_blog\service\impl\CommentServiceImpl.java:38`。
  - `D:\project\personal_blog\src\main\java\com\example\personal_blog\dto\CategoryDTO.java:6`、`D:\project\personal_blog\src\main\java\com\example\personal_blog\dto\CommentDto.java:15-17`。
  - 对比：`D:\project\personal_blog\src\main\java\com\example\personal_blog\service\impl\ArticleServiceImpl.java:184-198`（标题自定义 trim 包含 `Character.isSpaceChar`）。
  - 前端：`D:\project\personal_blog_vite\src\components\CategoryManager.vue:20`、`D:\project\personal_blog_vite\src\components\CommentSection.vue:34`。
  - 用户名：`D:\project\personal_blog\src\main\java\com\example\personal_blog\dto\RegisterDTO.java:9` 与 `D:\project\personal_blog\src\main\java\com\example\personal_blog\dto\LoginDTO.java:6`。
- **触发条件/影响**：
  1. `@NotBlank` 与 `String.trim()` 都不去除 U+00A0、U+200B、U+3000 等字符；分类名或补记内容只含 U+200B 时可通过校验并入库，界面显示为空白条目。JS `trim()` 会去掉 U+00A0/U+3000，但不处理 U+200B，因此**从网页粘贴 U+200B 即可复现**。空白分类/补记会占用分类、补记和存储配额，且当前没有补记删除 UI（见 P3-6），清理困难。标题逻辑专门处理了 NBSP，分类/补记没有，属于实现不一致。
  2. 注册用户名的 `@Pattern` 按码点限 3–32，登录的 `@Size(max=32)` 按 UTF-16 `length()` 计数。17–32 个补充平面字母（如 U+10400）可通过注册（数据库 `VARCHAR(32)` 按字符计），但登录时 `@Size` 计算为 34–64 而返回 400，账号可创建却无法登录。前端注册框 `maxlength=32` 按 UTF-16 计数会挡住大部分 UI 输入，但 API 客户端仍可触发。
- **最小修复**：增加统一的文本规范化/校验工具，按码点处理并拒绝仅含 `Character.isWhitespace`、`isSpaceChar`、`FORMAT`（Cf）或控制字符的输入；分类/补记使用该工具替代 `trim()`。用户名在注册处增加与登录一致的 `@Size(max=32)`，或两边都改为按码点计数。
- **验证方式**：`POST /category {"name":"\u200B"}` 后查看分类列表是否出现空白项；注册 32 个 U+10400 后尝试登录，观察是否 400。修复后两处应分别在创建/注册阶段被 400 拒绝。

### P3-6 补记 DELETE 接口存在，但前端没有任何删除入口

- **位置**：`D:\project\personal_blog\src\main\java\com\example\personal_blog\controller\CommentController.java:33-37`；`D:\project\personal_blog\docs\api.md:115`；`D:\project\personal_blog_vite\src\api\index.js:109-117`；`D:\project\personal_blog_vite\src\components\CommentSection.vue:44-60`。
- **触发条件/影响**：用户写错补记、需要删除，或补记数量达到 `MAX_COMMENTS`（默认 10000，见 `application.yaml:71`）。后端支持删除并计入配额，但前端既没有 `commentDelete` API 封装，也没有按钮/确认框；用户只能删除整篇日记来清理补记（`ArticleServiceImpl.java:146` 会连带删除全部补记），或者手工调用接口。与 P2-2 的重复提交叠加时影响更明显。
- **最小修复**：在 `api/index.js` 增加 `commentDelete(id)`，在 `CommentSection` 每条补记旁加删除按钮 + `ConfirmDialog`；删除成功后从列表移除并递减 `total`。若产品决定不开放删除，应同时移除后端接口和文档，避免“文档承诺但 UI 不可用”。
- **验证方式**：浏览器中打开一条补记，当前无删除控件；直接 `DELETE /api/comment/{id}` 返回 200。修复后应可删除，且删除他人补记仍返回 404。

---

## 规范/冗余建议

1. **停用社交功能残留**：`UserSubscriptionMapper.java` 在整个 `src/main` 中无任何引用（只有自身定义）；`UserSubscription.java` 仅被该 Mapper 引用；`SubscriptionStatus`/`SubscribedAuthor` 仅服务于 410 墓碑接口。`ArticleLikeMapper` 的显式删除与 `schema.sql:96` 的 `ON DELETE CASCADE` 重复。建议保留 `SubscriptionController` 的 410 兼容层，删除未使用的 Mapper/Entity；或至少标注 `@Deprecated` 与保留原因。
2. **未使用/冗余 API 字段**：`Result.java:39-55` 的 `fail()`、`fail(String)`、`fail(ResultCode)` 无调用；`ArticleController.java:52` 的 `authorId` 在只查本人的 SQL 中永远只能等于 viewer，属于无效参数；`CommentMapper.java:11` 查询的 `authorName` 前端未展示（`CommentSection.vue:56`）；`ArticleMapper.java:12-16` 的 `view_count/like_count/liked/subscribed` 为兼容字段，但列表仍传输。建议用 `PageResponse<T>`（records/total/size/current/pages）封装返回，避免 MyBatis-Plus `Page` 的 `maxLimit/searchCount/orders` 等内部属性直接暴露（与 `docs/api.md:64` 的字段说明不一致）。
3. **前端死资源/样式**：`BlogIcon.vue:11-12` 的 `eye`、`heart`、`message` 图标未使用；`public/icons.svg` 未被任何页面引用；`ArticleCard.vue` 的 `.article-author/.meta-stat`、`DetailView.vue` 的 `.article-author/.article-stats`、`CommentSection.vue` 的 `.comment-login/.comment-avatar/.comment-author` 均为旧社交 UI 残留。清理可减少误导。
4. **测试与质量门禁**：`personal_blog_vite/package.json:6-9` 只有 dev/build/preview，没有 test/lint；仓库也没有 ESLint/Prettier/Vitest 配置。后端没有 Checkstyle/Spotless。`scripts/verify-*.py` 是手工验收脚本，CI 未运行。建议至少为 `useAuth`、`time.js`、`importText.js` 和 `CommentSection`/`PlazaView` 的请求竞态加 Vitest，并在 CI 跑一个 Playwright smoke。
5. **快照与 CI**：`scripts/check-frontend-snapshot.ps1` 已存在，但 `.github/workflows/render-build.yml:31-50` 直接 `docker build`，没有执行快照一致性检查；若有人改了 `personal_blog_vite` 却忘记 `prepare-render.ps1`，会静默部署旧前端。建议在 CI 用哈希清单检查（不依赖 Windows 脚本）。另外该脚本在当前主机被默认 PowerShell 执行策略阻止（实测 `PSSecurityException`），建议提供 `cmd` 包装或文档注明 `-ExecutionPolicy Bypass`。
6. **行尾与文档**：`.editorconfig:5` 与 `.gitattributes:1` 要求 LF，但仍有 26 个 Java 文件是 CRLF（`git diff --check` 已出现 “CRLF will be replaced” 警告），提交时会产生无意义 diff。`docs/verification.md:3,10-13` 仍是 2026-11-08 的旧验证记录，README `:84` 却称其为“本轮实际检查结果”，其中“浏览量/点赞/排行榜”已停用，建议归档或更新。
7. **配置防御性**：`AccountMailProperties.java:31-37` 始终允许 localhost 作为 `MAIL_PUBLIC_BASE_URL`，即使 `MAIL_ENABLED=true`；若生产启用邮件却忘记覆盖该值，验证/重置链接会指向 `http://localhost:8080`。建议 `enabled && local` 时启动失败。`application.yaml:57` 默认 CORS 含 `http://localhost:3000`，而 `.env.example:8` 未列出，建议对齐。
8. **防御性一致性**：`CategoryServiceImpl.java:77` 删除前的文章计数未加 `user_id`，而 `CategoryMapper.java:13` 的列表计数加了；正常 API/迁移不会产生跨账号引用，但异常历史数据会出现界面显示 0 篇、删除却返回 409。建议顺手加 `.eq("user_id", UserContext.requireUserId())`，并依赖外键处理真正的跨账号异常。安全事件（登录失败、改密、邮箱变更）目前无审计日志，只有限流/邮件脱敏日志，可作为后续项。

---

## 检查与未覆盖内容

- **已核查的关键防护**：JWT 签名/发行者/`jti` + 数据库会话双重校验；退出、改密、重置、邮箱变更均撤销会话；所有写路径先 `SELECT ... FOR UPDATE` 锁账号再检查配额；私有日记/分类/补记的跨账号访问统一 404；广场仅返回 `published=TRUE` 的公开投影；SQL 全部参数化；前端无 `v-html`/`innerHTML`/`eval`，内容均走 Vue 文本插值；CSP、CORS allowlist、HSTS、`nosniff`、`DENY` 均存在；邮件/重置 token 使用 256 位随机数、SHA-256 存储、用途隔离、单次消费、过期时间。
- **未执行**：未运行 `mvn verify`、前端构建或 Playwright（只读审查，避免写入 `target/`）；未连接任何数据库、Render 或 SMTP；未读取 `.env.render`/`.env` 等凭据文件；未审计依赖 CVE 或镜像扫描（文档也将其列为后续项）；未在真实 MySQL/TiDB 上重放迁移。
- **测试覆盖现状**：后端 18 个测试类，覆盖注册/登录/会话失效/限流容量/配额并发/双账号隔离/广场发布撤回/补记隔离/邮件凭证迁移；但**没有前端自动化测试**，也没有覆盖本次 P2-2、P3-4、P3-5、P3-6 的用例；代理来源识别只能在部署环境实测。
- **条件性说明**：P1-1 依赖 Render 是否改写 TCP 对端；本仓库无法从代码确认代理拓扑，未访问公网，故标注为“需实测确认的 P1”。其余问题均基于当前代码的直接可复现路径，未把推测写成事实。
