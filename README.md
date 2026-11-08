# Personal Blog

Java 17 / Spring Boot 3 / MyBatis-Plus / MySQL 8 的博客。日常前端工作目录为
`D:\project\personal_blog_vite`；`frontend/` 保存用于 Render 构建的前端源码快照。

## 已实现

- 注册、登录、当前用户查询；BCrypt 密码哈希、JWT 鉴权。
- 游客阅读文章、浏览量原子递增；登录用户发布、编辑、删除自己的文章。
- 文章标题搜索、作者筛选、分类筛选、分页与摘要。
- 点赞、取消点赞、当前用户点赞状态；请求幂等，数据库唯一约束和事务保证计数一致。
- 浏览量榜、点赞榜；支持分类筛选及 Top N，使用累计数据。
- 作者订阅和取消订阅、订阅状态及订阅者数量、我的订阅列表；禁止订阅自己，重复请求保持幂等。
- 登录用户的文章列表优先展示已订阅作者；另有只看订阅作者的文章流，支持原有分页与筛选。
- 共享分类：登录用户创建，创建者编辑和删除；使用中的分类禁止删除。
- 评论分页和作者名；评论作者、文章作者可以删除评论。
- 统一响应、HTTP 错误状态、参数校验、可配置 CORS。

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
   $env:CORS_ALLOWED_ORIGINS = 'http://localhost:5173'
   mvn spring-boot:run
   ```
   密钥至少 32 字节；生产环境生成一次后持久化到部署环境，所有实例使用同一密钥。
   每次改变密钥会使旧 Token 失效。默认 Token 有效期 24 小时，可用 `JWT_TTL_SECONDS` 调整。
   原项目 Token 不含新签发者信息，升级后需要重新登录。
5. 默认接口地址：`http://localhost:8080`。可通过 `PORT` 修改端口。

所有时间字段采用 UTC；前端展示时转换到用户时区。浏览量按详情请求计数，重复刷新仍计数。

## 接口与前端对接

完整参数、权限、响应示例及旧接口变更见 [docs/api.md](docs/api.md)。
现有接口路径保留，成功响应统一为 `{code:200,message:"success",data:...}`；
原先直接读取详情对象、评论数组或字符串的前端代码需要同步调整。

## 自动验证

```powershell
mvn verify
```

默认使用独立的内存 H2（MySQL 模式），执行实际 SQL、MockMvc 接口测试和多线程事务测试，
不连接开发数据库。覆盖注册重复、密码哈希、过期 Token、越权操作、分类维护、
筛选分页、排行、点赞重试、混合点赞/取消、并发浏览量和删除关联数据。
本轮实际检查结果见 [docs/verification.md](docs/verification.md)。

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

## 作者订阅的展示规则

登录后请求 `GET /article/list`，必须携带 Token 才能得到个性化排序：
已订阅作者的文章排在前面，两组各按发布时间和 ID 倒序，在数据库排序后再分页。
游客和没有订阅的用户仍按发布时间浏览全部文章。排行榜保持原有累计计数排序。

`GET /article/subscriptions` 需要登录，只返回已订阅作者文章；没有订阅时为空列表。
作者后续发布的文章会自动进入订阅流；取消订阅后，新的查询立即反映变化。
前端切换账号、订阅或取消订阅后，应清理文章列表缓存并从第一页重新加载。
订阅仅影响阅读排序，不包含邮件、站内通知或付费订阅。

## 后续演进建议

- 用户运营：邮箱验证、找回密码、登录限流、Token 刷新与撤销。
- 内容运营：草稿/发布状态、Markdown 编辑器、图片上传、标签及内容审核。
- 数据产品：记录浏览和点赞事件后，再实现真正的周榜/月榜与去重访客统计。
- 权限运营：管理员角色、分类审核、举报处理和审计日志。
- 部署运维：HTTPS、数据库备份恢复演练、监控告警和 CI。

富文本或 Markdown 必须在前端使用受信任的渲染器并进行 HTML 清理；后端保存的是原始正文。
当前分类采用创建者权限，没有隐含管理员或公开提权入口。
