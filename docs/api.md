# 日记与广场 API

## 通用约定

- 本地地址默认 `http://localhost:8080`；业务路由同时支持原路径及 `/api` 前缀。
- 私人日记、个人分类、补记和发布状态操作要求 `Authorization: Bearer <token>`；注册、登录、健康检查及广场只读接口公开访问。
- 成功为 HTTP 200、`{code:200,message:"success",data:...}`；错误状态与 `code` 一致。
- 401 表示登录无效；访问其他账号的日记、分类或补记与资源不存在统一返回 404。
- 内容接口设置 `Cache-Control: no-store`。前端在退出、切换账号或收到 401 时清理页面状态。
- 身份只取自 Token，传入 `userId`、`authorId` 等字段不能扩大访问范围。
- 时间为 UTC 的 `yyyy-MM-dd'T'HH:mm:ss`，前端按 UTC 解析并转成本地时间。
- 页码从 1 开始，最大 100000，默认 `page=1&size=10`，最大 size 为 100；私人列表仅统计本人内容，广场列表仅统计已公开文章。
- 限流返回 HTTP 429 和 `Retry-After` 秒数；写入请求体默认最多 1 MiB，超过返回 HTTP 413。错误使用统一 JSON 结构；不支持的请求内容类型返回 415，无法满足 Accept 的响应类型返回 406，406 可无响应体。

## 用户

| 方法 | 路径 | 请求 | 返回 data |
|---|---|---|---|
| POST | /user/register | username、password、可选 email | 本人资料，不含密码 |
| POST | /user/login | username、password | Token 字符串 |
| GET | /user/me | 无 | 当前账号资料 |
| POST | /user/logout | 无，要求 Bearer Token | 撤销当前会话，data 为 null |
| GET | /user/security | 无，要求登录 | 验证邮箱、会话数、实际存储用量与配额 |
| GET | /user/security-options | 无，公开 | mailEnabled，不含发信配置 |
| POST | /user/password | currentPassword、newPassword，要求登录 | 改密并撤销全部会话及未使用邮件凭证 |
| POST | /user/logout-all | currentPassword，要求登录 | 撤销全部会话及未使用邮件凭证 |
| POST | /user/email/verification | currentPassword、email，要求登录 | 异步发送验证链接，不返回凭证 |
| POST | /user/email/confirm | token，公开 | 消费一次性验证链接；data 为受影响账号 ID |
| POST | /user/password/forgot | email，公开 | 已验证邮箱异步接收重置链接；统一返回成功，不透露邮箱是否存在 |
| POST | /user/password/reset | token、newPassword，公开 | 消费一次性重置链接；data 为受影响账号 ID，不自动登录 |

用户名为 3–32 位 Unicode 字母、数字、下划线或短横线。密码至少 8 字符，最多 72 个 UTF-8 字节。
重复用户名返回 409，非法参数返回 400。注册、登录忽略客户端携带的旧 Token。
退出成功后，当前 Token 在两套路径上均返回 401，其他设备的会话继续有效。
前端先调用退出接口，再清理本地身份；网络失败显示重试提示，不能将本地清理当作退出成功。
每次登录创建独立会话；每个账号最多保留 20 个会话，达到上限时撤销最早的会话。
升级前无会话记录的旧 Token 需要重新登录。具体限流阈值见 [security.md](security.md)。
资料新增 emailVerified 布尔字段，注册时始终为 false。此前收集的 email 不视为已验证。
敏感操作再次验证失败返回 403，保留当前登录；被撤销的会话返回 401。
验证邮箱或重置密码成功会撤销目标账号的全部会话，原链接重复、过期、用途不匹配均返回 400。
邮件未启用时，申请邮件、确认邮箱及消费重置链接均返回 503，不能通过日志或接口获取替代凭证。
邮箱链接使用配置的可信站点地址和 URL 片段，前端经 POST 交付 token；链接不会使用客户端 Host 头生成。
新密码沿用 8 字符及 72 UTF-8 字节限制。完整配置见 [account-security.md](account-security.md)。

## 日记

| 方法 | 路径 | 参数/请求体 | 行为 |
|---|---|---|---|
| POST | /article/publish | content、可选 title、categoryId | 存下本人日记 |
| PUT | /article/{id} | content、可选 title、categoryId | 完整替换本人日记 |
| PATCH | /article/{id}/category | categoryId，可传 null | 仅修改本人日记的分类，null 为未分类 |
| PUT | /article/{id}/publication | published 布尔值、category | 作者手动发布或撤回；发布时 category 必填 |
| DELETE | /article/{id} | 无 | 删除本人日记及其补记、历史点赞关系 |
| GET | /article/list | page、size、keyword、categoryId、authorId | 只查询本人日记 |
| GET | /article/detail/{id} | 日记 ID | 返回本人完整正文，不增加浏览计数 |

正文非空，最多 100000 字；标题最多 50 字，省略或空白时采用正文第一行并安全截断到上限。
显式标题与正文派生标题均去除首尾空白（含不换行空格 U+00A0），派生后仍为空时使用“随手记”。PUT 省略 title 同样会从新正文首行生成标题。
修改时省略或传 null 的 categoryId 会取消分类；前端编辑已有记录时应保留原 categoryId。
分类必须属于当前账号。指定其他账号的 authorId 只会得到空列表；keyword 对标题执行字面子串匹配。
快捷分类接口只更新 categoryId 与 updateTime，不提交或覆盖标题、正文、创建时间及历史计数。
首页每篇日记旁及详情页标题下均有“修改分类”入口，支持选择、新建或取消分类。

列表 data 为 `{records,total,size,current,pages}`，按 createTime、id 倒序排列。
records 含 id、userId、categoryId、title、summary、authorName、categoryName、createTime、updateTime。
列表不含完整正文，详情含 content；作者信息只可能是本人。
私人列表及详情增加 published、plazaCategory、publishedTime。保存新记录始终 published=false；普通编辑和快捷分类不能改变发布状态。
viewCount、likeCount 是兼容保留的历史字段，不再用于界面；liked、subscribed 固定为 false。

## 广场

| 方法 | 路径 | 参数 | 行为 |
|---|---|---|---|
| GET | /plaza/categories | 无 | 技术、日常、读书、娱乐 |
| GET | /plaza/articles | page、size、keyword、category | 只返回全部用户已公开文章，按 publishedTime、id 倒序 |
| GET | /plaza/articles/{id} | 文章 ID | 仅返回已公开正文；私密、撤回和不存在均为 404 |

广场只读接口允许未登录访问，并忽略携带的过期 Token。keyword 对标题执行字面子串匹配，可与 category 组合；category 必须是上述四种之一。
列表为 `{records,total,size,current,pages}`；记录包含 id、authorId、authorName、title、summary、category、publishedTime、updateTime，content 为空。
公共详情用 content 返回正文，不包含个人 categoryId/categoryName、补记、邮件、密码或历史社交关系。

`PUT /article/{id}/publication` 请求示例：`{"published":true,"category":"技术"}`。仅作者可调用，返回更新后的本人文章。
目前邮件功能暂缓，默认发布广场及编辑已公开正文不要求验证邮箱。显式设置 `REQUIRE_VERIFIED_EMAIL_FOR_PUBLICATION=true` 后，未验证邮箱的发布及公开正文编辑返回 403；撤回、删除及私人日记写入仍可用。
账号默认最多 20 MiB 文本、1000 篇日记、10000 条补记、200 个分类；增加内容超额返回 409，现有数据保持原样。
撤回请求为 `{"published":false}`，清除广场分类和发布时间，但保留原文、私人分类与补记；撤回时忽略 category 的取值（仍限制最多 20 字），发布时必须提供有效的广场分类。
已发布文章更改广场分类或重复发布不会重置发布时间；撤回后重新发布会使用新的发布时间。
个人分类与广场分类互不影响。已公开文章的普通编辑会同步标题与正文，编辑页会明确提示。
所有读取均禁止缓存，撤回后公共列表和新的详情请求立即不可见；原有私人接口仍只允许作者访问。

前端写作页支持 `.txt` / `.md` 文件导入：在浏览器中读取原文并填入正文，导入后要求手动填写标题，
点击保存时仍使用上述 JSON 发布/修改接口，没有新增文件上传接口。单文件最多 1 MB，正文上限不变。
支持 UTF-8、带 BOM 的 UTF-16 与 GB18030/GBK 文本；空文件、非文本与超长正文会被拒绝，已有正文替换前需确认。
Markdown 内容按原文保存和显示，不执行其中的 HTML 或脚本。普通手写日记仍保留标题选填行为。

## 私有分类

| 方法 | 路径 | 请求 | 行为 |
|---|---|---|---|
| GET | /category/list | 无 | 本人分类及本人记录数 |
| POST | /category | name、可选 description | 创建本人分类 |
| PUT | /category/{id} | name、可选 description | 修改本人分类 |
| DELETE | /category/{id} | 无 | 删除未使用的本人分类 |

名称去除首尾空白后在本人账号内唯一，最多 40 字；描述最多 200 字。
同一账号重名、删除使用中的分类返回 409。不同账号可以使用同名分类。

## 本人补记

为兼容原路径，补记仍使用 `/comment`，界面统一称“补记”。

| 方法 | 路径 | 参数/请求体 | 行为 |
|---|---|---|---|
| POST | /comment/publish | articleId、content | 向本人日记添加补记 |
| GET | /comment/{articleId} | page、size | 本人日记中由本人写下的补记 |
| DELETE | /comment/{id} | 无 | 删除本人补记 |

补记最多 2000 字，按时间与 ID 正序分页。旧公开评论中其他账号写下的内容不返回。
拥有历史评论或订阅关系不构成访问他人日记的权限。

## 已停用的社交接口

以下路径保留为明确的停用响应：未登录返回 401，认证后返回 410，data 为空。
它们不读取或修改历史社交数据。

- `GET /article/ranking`、`GET /article/subscriptions`
- `PUT/DELETE /article/{id}/like`
- `GET/PUT/DELETE /user/{authorId}/subscription`
- `GET /user/subscriptions`

## 升级与前端兼容

旧博客数据库必须在完成 v2/v3 升级后执行 `database/upgrade-v4-private-journal.sql`，再执行 `database/upgrade-v5-plaza.sql`。
新库直接使用当前 schema。迁移前备份并停止写入，详见 [私人日记改版说明](private-journal.md)。
前端首页、日记详情与编辑页均须登录；401 后返回登录页，不再尝试匿名读取。
原保存接口 `/article/publish` 路径保留，保存后仍为私密；只有独立的 publication 操作才可公开到广场。
