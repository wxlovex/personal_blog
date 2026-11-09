# Render 小范围部署

目标：复用 bounce-arena 项目对应的 `bounce-club` Web Service，Vue 页面和 Spring Boot
API 共用一个 HTTPS 入口，数据库独立持久化。Web Service 使用 Free，接受空闲休眠。

## 代码准备

在 `D:\project\personal_blog` 运行：

```powershell
./scripts/prepare-render.ps1
mvn verify
docker build -t personal-blog .
```

脚本只将前端工作目录的 src、public、锁文件和构建配置同步到仓库的 frontend/，
不包含 node_modules、dist、编辑器配置或本地凭证。后续前端更新也需运行此脚本。
Docker 内按顺序执行 `npm ci`、`npm run build`、`mvn verify`，再将包含页面的 Jar 放入运行镜像。
本机没有 Docker 时，Docker 构建仍需由 Render 验证。

## 免费数据库

优先候选为 **TiDB Cloud Starter（MySQL-compatible）**。
它使用 MySQL 协议，继续使用当前 MySQL JDBC 驱动；是否满足所有事务和约束要求，
需在实际实例上验证后再切换线上服务。

- 创建 Starter 免费实例，优先选择与 Render 相近的新加坡区域（以实际可选区域为准）。
- 保持消费上限为 0，不设置付费额度；免费额度用尽会限制服务，不能视为无限资源。
- 在 SQL Editor 创建 `personal_blog` 空库，使用 utf8mb4 编码。
- 在该空库执行 `src/main/resources/db/schema.sql`，检查六张业务表及所有约束。
- TiDB 的 CHECK 约束需要检查 `tidb_enable_check_constraint` 是否开启。
  如果关闭，先在允许的实例配置中开启，再建表；无法开启时先停下核查，不能假定约束已生效。
- 订阅表通过生成列计算是否为自订阅，再由 CHECK 强制约束，避免直接对级联外键列使用 CHECK
  导致 MySQL/TiDB 错误 3823；唯一约束、外键及级联删除均保留。
- JDBC 使用 TLS 主机名和证书校验，不使用 `useSSL=false` 或关闭证书验证。
- 限制数据库网络访问到实际部署与运维所需来源；不要为了连接方便放开全部来源。

```sql
CREATE DATABASE personal_blog CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE personal_blog;
-- 在此执行 schema.sql，随后核对：
SHOW TABLES;
SHOW CREATE TABLE user_subscription;
SHOW VARIABLES LIKE 'tidb_enable_check_constraint';
```

应用默认不自动执行建表或升级 SQL。不要把完整测试套件指向已有业务库：
集成测试会清空业务表；云端兼容性测试必须使用另建的专用空测试库。
如果保留本地已有数据，先单独备份并核对原始库版本，执行合适的 v2/v3 升级，
再迁移到云端；本次部署适配不操作现有本地数据库。

2026-11-08 核查：Aiven 已于 2026-11-06 停止新建免费 MySQL，故不作为新建零费用方案。

## Render 配置

在现有 `bounce-club` 服务中修改 Source 为包含当前源码的博客仓库和部署分支，
Runtime 选择 Docker，Dockerfile 为 `./Dockerfile`，构建上下文为仓库根目录。
保持 Free 方案，并将 Health Check Path 改为 `/api/health`。
旧的 Node Build/Start Command 和 Docker Command 覆盖值应清空，使用 Dockerfile 的入口。

`render.yaml` 描述这套配置。若原服务由 Blueprint 管理，先核对现有 Blueprint 归属，
避免旧 Blueprint 后续同步覆盖新设置；不要未经核对创建同名的第二个服务。

在 Render 的环境变量中填写（不提交真实值到 Git）：

| 变量 | 值 |
| --- | --- |
| `DB_URL` | `jdbc:mysql://<host>:4000/personal_blog?sslMode=VERIFY_IDENTITY&serverTimezone=UTC&characterEncoding=UTF-8` |
| `DB_USERNAME` | TiDB Connect 页面给出的完整用户名 |
| `DB_PASSWORD` | 数据库密码 |
| `JWT_SECRET` | 一次生成、长期保存的随机密钥，至少 32 字节 |
| `DB_POOL_SIZE` | `5` |
| `CORS_ALLOWED_ORIGINS` | `https://bounce-club.onrender.com`；限定为本服务的 HTTPS 来源 |
| `DB_CONNECTION_TIMEOUT_MS` | `30000` |

`PORT` 使用 Render 提供的值。运行镜像设置了适合小内存环境的 JVM 参数；
实际峰值内存和启动时间仍需以 Render 部署日志为准。

## 验收与切换

1. 先完成数据库实际兼容性测试和云端构建，再将原服务切换到博客。
2. 打开 `/api/health`，预期 HTTP 200 和 `{"status":"UP"}`。
3. 验证 `/login`、`/register`、`/publish` 及文章详情直接打开、刷新正常。
4. 验证注册、错误密码提示、登录、文章发布、摘要、详情、评论分页与删除权限。
5. 验证未登录写接口返回 401、其他用户删除文章返回 403。
6. 重启服务后再次查看原文章，确认数据来自持久化数据库。

免费服务空闲后会休眠，容器文件系统不会持久保存上传或数据库数据。
应用目前保存文本，不包含图片上传。数据库备份应定期导出并保存在服务之外。

## 官方资料

- https://render.com/docs/native-runtimes
- https://render.com/docs/free
- https://docs.pingcap.com/tidbcloud/serverless-limitations/
- https://aiven.io/changelog
