# 行间 · 私人日记与广场

Java 17 / Spring Boot 3 / MyBatis-Plus / MySQL 8 的博客。日常前端工作目录为
`D:\project\personal_blog_vite`；`frontend/` 保存用于 Render 构建的前端源码快照。

源码仓库：[后端 personal_blog](https://github.com/wxlovex/personal_blog) · [前端 personal_bolg_vite](https://github.com/wxlovex/personal_bolg_vite)。

公网入口：[访问博客](https://bounce-club.onrender.com)。当前使用新建云数据库，首次访问请注册账号。
已复用原 bounce-club 免费服务，部署和公网验收记录见 [docs/deployment-status.md](docs/deployment-status.md)。

## 已实现

- 注册、登录、当前用户查询；BCrypt 密码哈希、JWT 与数据库会话校验、服务端退出撤销。
- 登录/注册/读写限流、请求体总大小限制及浏览器安全响应头；配置与本地验收见 [docs/security.md](docs/security.md)。
- 账号安全：修改密码、退出所有设备和账号存储配额。邮箱验证与邮件找回密码已实现但暂不启用，待域名准备好后接续；当前广场发布不要求验证邮箱。第二批配置见 [docs/account-security.md](docs/account-security.md)。
- 每个账号独立的私人日记：列表、标题搜索、分页、详情、修改和删除都按登录身份隔离。
- 标题选填，省略时自动采用正文第一行；记录支持多段正文与只有本人可见的补记。
- 私有分类：名称在本人账号内唯一，不同账号可以使用同名分类。
- 首页默认按创建时间倒序，可组合标题搜索和分类筛选；支持新建、重命名、删除空分类。每篇日记旁和详情页标题下可直接修改分类，支持新建并选用或设为未分类，写作页也保留分类选择。
- 写作页支持本地导入 `.txt`、`.md`，原文填入正文；导入后标题必填，确认保存后入库。支持 UTF-8、带 BOM 的 UTF-16 和 GB18030/GBK 中文文本，文件最多 1 MB、正文最多 100000 字。
- 日记默认私密，只有作者能手动发布到广场或撤回；广场分技术、日常、读书、娱乐，支持标题搜索、分类筛选和分页。
- 私人内容接口要求登录；广场仅公开作者主动发布的标题、正文与作者名，私人分类和补记始终私密。越权记录与不存在记录统一返回 404；内容响应禁止缓存。
- 排行、点赞、作者订阅及订阅文章流已停用，旧接口认证后返回 410。
- 历史记录和关系数据保留；旧共享分类通过 v4 脚本复制到记录所属账号并重新关联。
- 米白与墨绿的书房界面，适配手机与深色模式；我的日记只展示本人内容，广场展示全部用户主动公开的文章。

## 本地启动

1. 准备 JDK 17、Maven 3.8+、MySQL 8.0.16+。
2. 新库：创建 UTF-8 数据库，再导入 `src/main/resources/db/schema.sql`。
   ```sql
   CREATE DATABASE personal_blog CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
   在 MySQL 客户端中连接该库后执行：
   ```text
   source D:/project/personal_blog/src/main/resources/db/schema.sql
   ```
3. **已有数据库先备份，并核对原表结构**：
   - 原始三表版本：先执行 `database/upgrade-v2.sql`，再执行 `database/upgrade-v3-subscriptions.sql`。
   - 已完成上一轮分类/点赞升级的数据库：只需执行 `database/upgrade-v3-subscriptions.sql`，新增订阅表。
   - 从博客版本切换到私人日记：再执行 `database/upgrade-v4-private-journal.sql`。
     迁移前备份并停止写入；会保留原记录，将旧共享分类复制并关联到各自账号。
     新库直接使用当前 schema，不需要重复执行升级脚本。
   - 给现有私人日记库增加广场：完成 v4 后再执行 `database/upgrade-v5-plaza.sql`。会给所有旧文章设置默认私密，不会自动发布内容。
   - 启用安全会话：执行 `database/upgrade-v6-auth-sessions.sql`，仅新增会话表。先迁移再启动新版，已有用户需要重新登录。
   - 升级第二批账号安全：完成 v6 后执行 `database/upgrade-v7-account-security.sql`；即使暂缓邮件，也需创建邮箱归属与凭证表，供账号安全状态、改密和会话撤销使用。旧邮箱保持未验证。邮件默认关闭，后续完成发信接入后再启用；已验证邮箱才能用于密码恢复。
   新建库脚本中的 `CREATE TABLE IF NOT EXISTS` 不会升级已有表，不能代替迁移。
   项目不会在启动时自动执行 SQL，也没有操作现有数据库。
   原代码使用服务器本地时间写入 DATETIME；升级脚本保留历史时间原值。
   请先确认旧部署的时区，再决定是否将历史时间转换为 UTC，避免与新写入的 UTC 时间混用。
4. 在 PowerShell 中设置运行环境：
   ```powershell
   $env:DB_URL = 'jdbc:mysql://localhost:3306/personal_blog?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8'
   $env:DB_USERNAME = 'blog_app'
   $env:DB_PASSWORD = '<数据库密码>'
   $bytes = New-Object byte[] 48
   $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
   $rng.GetBytes($bytes)
   $rng.Dispose()
   $env:JWT_SECRET = [Convert]::ToBase64String($bytes)
   $env:CORS_ALLOWED_ORIGINS = 'http://localhost:5173,http://127.0.0.1:5173'
   mvn spring-boot:run
   ```
   密钥至少 32 字节；生产环境生成一次后持久化到部署环境，所有实例使用同一密钥。
   每次改变密钥会使旧 Token 失效。默认 Token 有效期 24 小时，可用 `JWT_TTL_SECONDS` 调整。
   原项目 Token 不含新签发者信息，升级后需要重新登录。
5. 默认接口地址：`http://localhost:8080`。可通过 `PORT` 修改端口。

所有时间字段采用 UTC；前端展示时转换到用户时区。私人日记不再记录浏览量，旧计数只保留在数据库中。

## 接口与前端对接

完整参数、权限、响应示例及旧接口变更见 [docs/api.md](docs/api.md)。
现有接口路径保留，成功响应统一为 `{code:200,message:"success",data:...}`；
原先直接读取详情对象、评论数组或字符串的前端代码需要同步调整。

## 自动验证

```powershell
mvn verify
```

默认使用独立的内存 H2（MySQL 模式），执行实际 SQL、MockMvc 接口测试和多线程事务测试，
不连接开发数据库。覆盖注册重复、密码哈希、过期 Token、双账号数据隔离、越权读写、
私有分类、搜索分页、补记、旧社交入口停用、分类迁移和删除关联数据。
最新 DeepSeek 审查、修复及验证结果见 [审查修复记录](docs/review-fixes-2026-11-11.md)；早期版本记录见 [docs/verification.md](docs/verification.md)。

也可以在**专用空 MySQL 测试库**上执行同一套测试：

```powershell
$env:TEST_DB_URL = 'jdbc:mysql://127.0.0.1:3307/blog_verification?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
$env:TEST_DB_USERNAME = 'root'
$env:TEST_DB_PASSWORD = '<测试库密码>'
mvn verify
Remove-Item Env:TEST_DB_URL,Env:TEST_DB_USERNAME,Env:TEST_DB_PASSWORD
```

测试会删除该测试库业务表中的全部数据，必须使用独立测试库。

## 容器运行

```powershell
mvn verify
docker build -t personal-blog .
docker run --rm -p 8080:8080 --env-file .env personal-blog
```

参照 `.env.example` 填写部署环境；Spring Boot 本地启动不会自动加载 `.env` 文件。
数据库应由独立实例提供，容器内 `localhost` 指向容器自身。
Dockerfile 自动构建 Vue 前端、执行后端测试并生成 Jar，最终镜像使用非 root 用户运行。
前端修改后先运行 `./scripts/prepare-render.ps1` 更新 `frontend/`，再提交源码及构建镜像。

## Render 公网部署

部署拓扑为一个免费 Docker Web Service（页面和 API 同域）加独立持久化数据库。
复用原 bounce-club 服务的步骤、免费数据库准备、环境变量和验收见
[docs/render-deployment.md](docs/render-deployment.md)。
所有业务 API 同时支持原路径和 `/api` 前缀；前端开发代理现在直接转发 `/api`，不再剥离前缀。
`GET /api/health` 检查数据库连接，连接异常返回 503，不公开连接配置。

## 结构

- `controller`：HTTP 接口、参数校验与公开访问声明。
- `service`：业务逻辑、权限判断及事务边界。
- `mapper`：参数绑定查询、分页、原子计数和行锁。
- `entity / dto`：持久化对象、请求对象及用户信息视图。
- `common`：统一响应、业务异常、全局异常处理。
- `config / utils`：分页、密码编码、JWT、CORS 和请求用户上下文。

## 私人日记的访问规则

私人内容接口的所属账号只从 Token 读取，客户端传入作者 ID 或猜测记录 ID 都不能扩大访问范围。
未登录返回 401；访问其他账号的记录、分类或补记返回 404。
广场通过独立的公开接口仅展示作者手动发布的文章。私人分类与补记不包含在公开响应中；撤回后公共详情返回 404。
已公开文章的标题和正文修改后会同步到广场，编辑页有明确提示。原有排行、点赞、订阅和订阅文章流继续停用。

旧评论中由其他账号写下的内容不会显示为本人的补记。升级不删除这些历史数据。
旧共享分类需要完成 v4 迁移后再恢复写入，详见 [私人日记改版说明](docs/private-journal.md)。

## 后续演进建议

- 写作体验：按账号保存草稿、自动保存、日期回看、全文搜索、心情与标签。
- 个人数据：导出、备份恢复、账号注销、记录回收站。
- 账户安全：邮箱验证、找回密码、登录限流、Token 轮换与刷新。
- 部署运维：HTTPS、数据库备份恢复演练、监控告警和 CI。

富文本或 Markdown 必须在前端使用受信任的渲染器并进行 HTML 清理；后端保存的是原始正文。
分类与补记均只属于当前账号，不提供公共内容入口。

广场实现、升级与验证说明见 [广场功能说明](docs/plaza.md)。当前私人日记、广场及账号安全版本已于 2026-11-11 部署到公网，云数据库已完成 v4–v7 升级，详见 [部署记录](docs/deployment-status.md)。
