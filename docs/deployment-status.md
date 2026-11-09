# 公网部署记录

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
