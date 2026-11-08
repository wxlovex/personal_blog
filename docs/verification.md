# 本轮验证记录

日期：2026-11-08。

| 检查 | 结果 |
|---|---|
| JDK 17 编译、Maven verify、Spring Boot Jar 打包 | 通过 |
| H2 MySQL 模式的 23 项接口与数据库测试 | 全部通过，0 失败、0 跳过 |
| 独立 MySQL 8.0.25 的同一套 23 项测试 | 全部通过，0 失败、0 跳过 |
| 并发浏览详情 40 次 | 浏览量准确增加 40 |
| 3 个用户并发重试点赞、取消 | 关系数与计数一致，每人每篇最多一条关系 |
| 同一用户交错点赞、取消 40 次 | 最终计数与关系表一致，保持在 0–1 |
| 打包 Jar 的真实 HTTP 流程 | 注册、登录、分类、发文、点赞、排行榜、游客详情通过 |
| 模拟原三表数据库升级 | 原文章、评论、浏览量 42 保留，新增分类与点赞表成功 |
| 重复用户名的迁移拦截 | 在业务表变更前失败，未创建 category 表 |
| git diff --check | 通过 |

测试源码：`src/test/java/com/example/personal_blog/PersonalBlogApplicationTests.java`。
默认运行 `mvn verify` 使用内存测试库；连接专用 MySQL 测试库的方法见 README。

本轮在受限工作区运行，使用了 `target/maven-settings.xml` 和工作区内的 Maven 缓存，
只读复用已有依赖、从 Maven Central 补充缺失依赖，没有修改全局 Maven 配置。
该临时配置和日志均位于 Git 忽略的 target 目录。

实际执行命令：

```text
mvn -B -s target/maven-settings.xml -gs target/maven-settings.xml verify
```

MySQL 测试使用单独初始化的数据目录 `target/mysql-verification/data` 和 3307 端口，
与现有博客数据库隔离；现有数据库没有执行迁移。

## 验证边界

- 前端代码不在当前仓库，未验证页面渲染和前端对新响应结构的适配。
- 当前环境没有 Docker 命令；提供 Dockerfile，但没有构建或运行容器。
- 迁移验证基于与原 Java 实体一致的模拟旧表，实际旧库仍需核对字段类型、
  自增主键和已有约束，备份后在维护窗口执行。
- 排行榜验证的是累计数据排序；没有宣称周/月事件榜或独立访客去重。
