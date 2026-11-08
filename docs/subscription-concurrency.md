# 订阅并发修复说明

## 触发与影响

DeepSeek 的真实 MySQL 测试发现，同一用户对同一作者并发执行订阅和取消时，
`INSERT ... ON DUPLICATE KEY UPDATE` 与 `DELETE` 可能发生死锁，最终返回 HTTP 500。
原测试 `mixedConcurrentSubscribeAndUnsubscribeConverges` 保留了失败，未跳过或降低断言。
H2 同一套测试通过，说明 H2 的成功不能替代真实 MySQL 的并发验证。

## 修复

`SubscriptionServiceImpl.change` 在事务内按双方用户 ID 的升序，
分别调用 `UserMapper.lockId` 执行 `SELECT id ... FOR UPDATE`，再写入或删除订阅关系。
行锁在事务结束后释放；不存在的订阅关系也有稳定的锁定对象。

所有订阅与取消都遵循同一顺序，A 订阅 B 和 B 订阅 A 也采用相同顺序。
只锁当前订阅者会让双向订阅中的外键锁检查产生新的锁顺序问题，因此双方都先锁定。
事务仍使用 READ_COMMITTED，唯一键、自订阅约束和外键保留，人数由实际关系行计算。
没有通过捕获死锁后直接返回成功来掩盖写入失败。

此做法采用了 [MySQL 官方死锁处理说明](https://dev.mysql.com/doc/refman/8.0/en/innodb-deadlocks-handling.html)
中的固定顺序访问和保持事务简短原则。它针对当前订阅写入路径减少锁冲突，
不构成对所有未来数据库操作都不会死锁的保证。

共享同一用户的订阅写入会串行化，热门作者的写入吞吐量会受其事务耗时约束。
未来增加用户删除、批量订阅等写入路径时，也需重新评估锁顺序和整笔事务重试。

## 前端兼容性

按项目 AGENTS.md 检查了 `D:/project/personal_blog_vite/src/api/index.js`：
本次仅修复事务内部行为，HTTP 路径、参数、返回字段和前端的 Token 传递约定均不变。
订阅功能页面接入属于独立工作，本修复不包含页面开发。

## 验证职责

主助手修改业务代码并审阅报告，所有编译、测试、SQL 与真实 HTTP 执行由 DeepSeek 完成。
复测要求为 H2 和真实 MySQL 全量验证、至少五轮独立混合并发验证，以及修复后 Jar 的 HTTP 验证。
最终结果以 DeepSeek 的原始日志和报告为准，不能仅凭本说明判断修复已经通过。
