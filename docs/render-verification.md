# Render 部署适配验证

日期：2026-11-08 至 2026-11-09。

- Vue 生产构建通过（Vite 8.1.0），页面及脚本均打入 Spring Boot Jar。
- Maven verify：45 项测试通过，0 失败、0 跳过；使用隔离 H2 MySQL 模式。
- 新增覆盖 `/api` 与旧路径兼容、真实登录/发文/评论调用、页面路由转发、API/资源 404、
  未登录写入 401、健康检查和数据库故障返回 503。
- 在 127.0.0.1:18080 启动真实 Jar，用独立内存 H2 和无界面 Chrome 验证：
  注册、错误密码提示、登录、发布、摘要、详情刷新、评论、11 条评论翻页、
  其他用户隐藏删除按钮、服务端 403、未登录 401、删除文章，全部通过，无页面运行错误。
- 页面验证脚本：`scripts/smoke-browser.py`。临时日志、截图、测试依赖均在 Git 忽略的 target/。
- 最终页面回归增加 UTC 到北京时间的显示检查，同样通过。
- Linux 云端实际 Docker 构建通过；镜像在 512 MiB 容器中连接独立 MySQL 8.0.46，
  健康检查、登录页面和文章列表接口验证通过。
- 独立 TiDB 8.5.1 SQL 引擎的 41 项业务测试通过，包含并发点赞/取消、订阅、外键、
  自订阅检查和级联删除。该测试使用临时单节点 unistore，不等同于 TiDB Cloud 线上集群验收。
- TiDB Cloud Starter 已通过校验证书的 TLS 连接创建六张业务表，并在临时库验证了生成列
  自订阅约束与级联外键能同时生效。之后本机直连数据库出现 TCP 超时；线上网络仍需在 Render 验收。
- 修复：订阅表使用生成列配合 CHECK 兼容级联外键；点赞事务使用 READ COMMITTED，
  避免 TiDB 等待行锁前的旧快照。数据库连接池等待时间改为可配置的 30 秒。
- 已验证的代码提交：`561d198bff25c54b52d7896b4e49f1030e5b1fa6`。
  云端记录：https://github.com/wxlovex/personal_blog/actions/runs/42450628946 。
- 部署分支：https://github.com/wxlovex/personal_blog/tree/codex/render-blog 。
- 2026-11-09 已通过官方 CLI 授权和官方 API 完成原服务替换，公网入口为
  https://bounce-club.onrender.com 。部署 ID 为 `dep-dboi9r49v7es73fc5rb0`，状态为 live。
- 公网真实浏览器回归再次通过，结果位于 `target/browser-test/result.json`；
  20 次并发点赞/取消、幂等重试、重启后文章仍可读取均通过，记录位于 `target/render-persistence.json`。
- 测试文章、两个公网验收账户、专用云端兼容性测试库均已清理。本地原数据库未操作。
- 本地凭证位于 Git 忽略的 `.env.render`，CLI 登录配置保存在受访问控制保护且被忽略的
  `target/render-private/`；没有上传到 GitHub。
