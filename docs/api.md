# API 对接说明

## 通用约定

- 基础地址默认 `http://localhost:8080`。
- 写接口和 `GET /user/me` 需要 `Authorization: Bearer <token>`。
- 公开查询接口可不传 Token；携带 Token 时仍会校验其有效性，用于返回 `liked`。
  登录过期后，前端应清理 Token 再匿名重试公开查询。
- 登录、注册忽略 Authorization 头，客户端携带过期 Token 时也能重新登录。
- 成功 HTTP 200：`{"code":200,"message":"success","data":...}`。
- 错误 HTTP 状态与 `code` 一致：400 参数错误、401 未登录/凭证错误、
  403 无权限、404 不存在、409 冲突、500 系统异常。
- 页码从 1 开始，默认 `page=1&size=10`，单页上限 100；超出总页数返回空列表。
- 时间字段是 UTC 的 `yyyy-MM-dd'T'HH:mm:ss`（可能附带小数秒），前端解析时按 UTC 处理。
  旧库历史 DATETIME 保留原值，升级时需核对原部署时区并按需转换，详见 README。
- 每篇文章可属于一个分类，`categoryId=null` 表示未分类；没有指定分类筛选时返回全部分类文章。
- Long ID 当前按 JSON 数值返回。超大历史 ID 若超过 JS 安全整数范围，需在迁移时单独评估。

## 用户

| 方法 | 路径 | 请求 | data |
|---|---|---|---|
| POST | /user/register | username、password、可选 email | id、username、email、createTime |
| POST | /user/login | username、password | Token 字符串 |
| GET | /user/me | 需要登录 | 当前用户资料，不含密码 |

注册用户名为 3–32 位 Unicode 字母、数字、下划线或短横线；密码至少 8 个字符，
最多 72 个 UTF-8 字节；邮箱可不填。重复用户名返回 409。

## 作者订阅

| 方法 | 路径 | 参数 | 权限 |
|---|---|---|---|
| PUT | /user/{authorId}/subscription | 无请求体 | 登录 |
| DELETE | /user/{authorId}/subscription | 无请求体 | 登录 |
| GET | /user/{authorId}/subscription | 作者 ID | 公开，可带 Token |
| GET | /user/subscriptions | 可选 page、size | 登录 |

订阅、取消和查询状态统一返回：

```json
{"code":200,"message":"success","data":{"authorId":2,"subscribed":true,"subscriberCount":3}}
```

subscribed 是当前用户是否订阅该作者；游客查询为 false。subscriberCount 是该作者订阅者总数。
登录身份只从 Token 读取，客户端无法指定订阅者 ID。不能订阅自己（400），
作者不存在返回 404；重复订阅、重复取消均成功。重复订阅保留首次订阅时间。
允许双向订阅，不包含邮件提醒或付费业务。

我的订阅列表返回分页对象，records 每项只包含 id、username、subscribedAt、articleCount，
不包含作者邮箱或密码；按订阅时间倒序、作者 ID 倒序。

文章列表、详情和排行榜增加 subscribed 布尔字段，前端可据此显示“已订阅”。
订阅/取消成功后请重置文章分页缓存；切换账号时也应清理个性化列表缓存。

## 文章

| 方法 | 路径 | 参数或请求体 | 权限 |
|---|---|---|---|
| POST | /article/publish | title、content、可选 categoryId | 登录 |
| PUT | /article/{id} | title、content、可选 categoryId | 作者 |
| DELETE | /article/{id} | 无 | 作者 |
| GET | /article/list | page、size、categoryId、authorId、keyword 均可选 | 公开 |
| GET | /article/subscriptions | page、size、categoryId、authorId、keyword 均可选 | 登录 |
| GET | /article/detail/{id} | 文章 ID | 公开 |
| GET | /article/ranking | sort、limit、categoryId 均可选 | 公开 |
| PUT | /article/{id}/like | 无请求体 | 登录 |
| DELETE | /article/{id}/like | 无请求体 | 登录 |

发布/编辑示例：

```json
{"title":"理解数据库事务","content":"文章正文","categoryId":1}
```

标题非空，最多 50 字；正文非空，最多 100000 字。编辑为完整替换：
省略 categoryId 或显式传 null 都会取消分类。创建和编辑返回完整文章，不增加浏览量。

文章列表的 `data` 为分页对象：

```json
{
  "records": [
    {
      "id": 1,
      "userId": 2,
      "categoryId": 3,
      "title": "理解数据库事务",
      "summary": "正文前200个字符",
      "content": null,
      "authorName": "alice",
      "categoryName": "后端开发",
      "viewCount": 12,
      "likeCount": 3,
      "liked": false,
      "subscribed": true,
      "createTime": "2026-11-08T01:00:00",
      "updateTime": "2026-11-08T01:10:00"
    }
  ],
  "total": 1,
  "size": 10,
  "current": 1,
  "pages": 1
}
```

`keyword` 对标题执行字面子串匹配，不会作为 SQL 通配表达式；搜索大小写由数据库排序规则决定。
列表和排行榜只返回摘要，详情返回完整 content；访问详情原子增加浏览量一次。

登录后的 `/article/list` 默认先展示已订阅作者文章，再展示其他作者文章；
每组内部按 createTime 倒序、id 倒序。优先级在数据库分页之前应用，total 仍为筛选后的全部文章数。
游客或没有订阅时，结果等同于按时间倒序浏览。

`/article/subscriptions` 只返回当前用户已订阅作者的文章，并支持列表的全部筛选参数；
没有订阅或筛选未命中时返回空分页，不混入其他作者。该列表不增加浏览量。
新发布文章会自动加入订阅流；取消订阅后新请求立即生效。

排行榜示例：`GET /article/ranking?sort=likes&limit=10&categoryId=3`。
sort 仅允许 `views`（默认）或 `likes`，limit 为 1–100；`data` 是文章数组，
顺序即排名，前端用数组位置显示名次。同分按发布时间倒序、ID 倒序排序。
榜单依据累计数据，不代表“最近一周发生的浏览或点赞”。
订阅关系只影响普通文章列表的排序，不影响排行榜的名次。

点赞与取消点赞都返回：

```json
{"code":200,"message":"success","data":{"liked":true,"likeCount":3}}
```

取消后 liked 为 false。重复点赞、重复取消均成功，计数只在状态发生变化时调整。
每个用户最多对同一文章点赞一次，作者也可以点赞自己的文章。

## 分类

| 方法 | 路径 | 请求 | 权限 |
|---|---|---|---|
| GET | /category/list | 无 | 公开 |
| POST | /category | name、可选 description | 登录 |
| PUT | /category/{id} | name、可选 description | 创建者 |
| DELETE | /category/{id} | 无 | 创建者 |

分类名称去除首尾空白后全站唯一，最多 40 字，描述最多 200 字。
所有作者可以使用已存在的分类。列表返回 id、userId（创建者）、name、description、
createTime、articleCount。仍有文章使用的分类删除时返回 409，应先移动文章。

## 评论

| 方法 | 路径 | 参数或请求体 | 权限 |
|---|---|---|---|
| POST | /comment/publish | articleId、content | 登录 |
| GET | /comment/{articleId} | 可选 page、size | 公开 |
| DELETE | /comment/{id} | 评论 ID | 评论作者或文章作者 |

评论最多 2000 字，列表的 data 为分页对象，records 包含 authorName，按时间和 ID 正序。
删除文章会清理其评论与点赞关系。

## 原前端必须同步的地方

| 接口 | 旧响应 | 新响应读取方式 |
|---|---|---|
| 注册 | 成功字符串 | response.data 中的用户资料 |
| 发布文章 | 成功字符串 | response.data 中的新文章 |
| 文章详情 | Article 对象 | response.data |
| 删除文章 | 成功字符串 | 检查 HTTP 状态和 code |
| 评论列表 | Comment 数组 | response.data.records |
| 文章列表 | 含完整正文的分页 | response.data.records 中使用 summary 展示摘要 |

此表中的 response 指后端 JSON 对象；使用 Axios 时还需先取 Axios 的 response.data。
登录、文章列表和发表评论原有的外层 Result 结构保留。
前端必须同时处理非 2xx 的 HTTP 响应，尤其是 401 清理登录状态、403 提示无权限、
409 显示冲突信息。服务重启或请求重试不会重复增加点赞数。
