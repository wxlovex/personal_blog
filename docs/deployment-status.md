# 公网部署记录

## 2026-11-11：私人日记、广场与账号安全

- 入口：https://bounce-club.onrender.com
- GitHub 部署分支：`codex/render-blog`。
- 应用提交：`28d2638a670e74fa19f4701717236f1b82df228c`。
- Render 部署：`dep-dbpk3uui0phs73adurrg`，状态 `live`，北京时间 2026-11-11 00:13:49 完成。
- 继续复用现有 Singapore / Free / 单实例 Docker 服务，自动部署保持关闭。
- 本地 79 项测试、前端生产构建、GitHub Docker 与 TiDB 检查通过。
- 独立 TiDB Cloud 副本的 v4–v7 迁移演练通过；附加 26 项云数据库接口测试全部通过。
  本机到云端首次连接超时，独立测试提高连接验证等待时间后通过；生产连接配置未因此修改。
- 已暂停服务写入并保存生产库备份，再执行 v4–v7 升级。原账号、文章及历史关系数据内容校验一致，旧文章默认私密。
- `/api/health` 返回 HTTP 200 和 `{"status":"UP"}`；新版 `/api/user/security-options` 返回 `mailEnabled:false`。
- 公网浏览器 8 组验收通过：注册、错误密码与正常登录、私人日记编辑、补记分页、双账号隔离、旧社交接口停用、320–1440 像素布局、删除与退出后的内容清理。没有页面运行错误。
- 另 4 组公网 API 验收通过：邮件关闭与配额、找回密码接口拒绝邮件操作、分类修改和广场发布/撤回、修改密码及退出所有设备撤销会话。
- 专用测试账号、文章、分类和两个临时测试库已清理。清理后再次比对迁移前备份中的原始数据摘要，原账号、文章及历史关系数据一致。
- 新版启用数据库会话，旧 Token 需要重新登录。原有文章默认私密，只有作者手动发布后才出现在广场。
- 邮件验证与密码恢复继续关闭，广场发布无需验证邮箱；修改密码、退出所有设备与配额功能启用。
- 私有备份位于被 Git 忽略、受访问控制保护的 `target/render-private/release-production-backup.sql`；执行 Maven clean 前应另行妥善保存。
- 发布采用独立工作区 `target/release-source`，保留原开发目录及其未提交状态。后续发布应基于 GitHub 的部署分支继续。

### 上一版本记录

日期：2026-11-09。状态：已上线并完成公网验收。

- 博客入口：https://bounce-club.onrender.com
- Render 管理：https://dashboard.render.com/web/srv-dbacvm3rjlhs739pnft0
- 原 `bounce-club` 服务已切换为博客，保留原网址；Singapore、Free、单实例。
- 源码：https://github.com/wxlovex/personal_blog/tree/codex/render-blog
- 实际部署提交：`561d198bff25c54b52d7896b4e49f1030e5b1fa6`。
- 部署 ID：`dep-dboi9r49v7es73fc5rb0`，Render 状态为 `live`。
- Runtime 为 Docker，健康检查为 `/api/health`，自动部署关闭。

## 已完成的验收

- 45 项本地测试、41 项独立 TiDB 测试、512 MiB Docker + MySQL 验证通过。
- 公网浏览器验证通过：注册、错误密码提示、登录、发布、摘要、文章详情刷新、北京时间显示、
  评论发布及 11 条评论翻页、删除权限、401/403、API/静态资源 404、文章删除；无页面运行错误。
- 在实际云数据库上验证两位用户的 20 次并发点赞/取消，以及重复点赞、重复取消，计数一致。
- 重启 Render 服务后，原测试文章仍可读取，确认数据由 TiDB Cloud 持久保存。
- 测试文章、两个验收账户、独立兼容性测试库均已清理。
- 本机原数据库未操作。当前公网版本使用新建云数据库，首次访问需注册账号。

## 配置与后续维护

前后端同域。跨域来源限定为服务自己的 HTTPS 地址；数据库使用 TLS 证书和主机名校验。
数据库连接池上限 5，连接等待 30 秒。JWT 密钥及数据库凭证只保存在被忽略的本地
`.env.render` 和 Render 环境变量中，没有提交到 Git。

通过 Render 官方 CLI 浏览器授权完成接入，并通过官方 API 切换运行环境及配置环境变量。
本次 CLI 位于 `target/render-cli-2.36.0/render.exe`；登录配置目录为受访问控制保护的
`target/render-private/`，通过 `RENDER_CLI_CONFIG_DIR` 指定。该目录被 Git 忽略；
执行 Maven clean 会移除，届时可以重新授权。

前端日常目录为 `D:\project\personal_blog_vite`，修改后运行 `scripts/prepare-render.ps1`
同步部署源码，再更新部署分支并手动部署。详见 `render-deployment.md`。
免费 Web Service 空闲后会休眠，再次访问需要等待启动；不要将数据库或上传内容存放在容器临时目录。
