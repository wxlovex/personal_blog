# DeepSeek 复核原始结果

以下为限定关键文件的只读复核；最终核实与处置见 `review-fixes-2026-11-11.md`。

## 已解决项

- **新增补记立即可见**：`CommentSection.vue` 用 `loadedComments + recentComments` 合并渲染，提交成功后直接把服务端返回的补记加入 `recentComments`、`total++`，不重新拉第一页；部分分页已加载时也能立即看到，并滚动到列表末尾。
- **加载更多不重复**：`mergeComments` 按 `id` 去重；只有请求成功才推进 `page`；失败重试沿用原 `page`；新补记后来出现在服务端分页中时，会从 `recentComments` 移除，不会重复。
- **本组件删除后的分页位移**：删除后同时从 `loadedComments`/`recentComments` 移除并递减 `total`，再用 `floor(loadedComments.length / PAGE_SIZE)` 回退到部分边界页，下一轮会重读该页，避免跳过一条；删除失败保留对话框和 `deleteError`，可重试。
- **`/me` 非 401 失败保留路由与 token**：`useProfile` 只对非 401 设置 `profileError` 并重新抛出；router 的 `catch` 不再跳登录，导航继续；`App.vue` 显示提示和“重试账号信息”按钮，重试会先清空错误。`currentUser` 仅在 token 未变时写入，`pending/pendingToken` 防止同 token 重复请求，旧账号响应不会覆盖新账号。
- **401 处理**：router 在 `401 && !isLoggedIn` 时返回 `false` 中止进入私有页；用 `token` 比较和 `App.vue` 的 `route.fullPath + token` key 隔离旧账号页面状态。真正的登出/跳转仍由 axios 拦截器负责（见条件项）。
- **Unicode/ZWJ**：前端 `hasVisibleText` 只做可见性判断，不修改原文；后端 `TextContent.requireVisible` 只裁剪首尾空白，`isVisible` 只把 White_Space、Cf、Cc 视为不可见，ZWJ（U+200D）等格式字符在文本内部被保留，不会拆散 ZWJ 表情；纯空白/纯不可见字符会被拒绝。
- **409 仍消耗账号额度**：`AccountSecurityService.prepareVerification` 在 `emailOwner` 归属检查之前先获取 `verify-mail-user` 与 `verify-mail-target` 令牌；返回 409 的探测会消耗账号额度，账号额度耗尽时直接 429，不会绕过。
- **代理默认安全**：`forward-headers-strategy` 默认 `NONE`；`internal-proxies` 默认 `(?!)`，在 NATIVE 下也不匹配任何代理，默认不信任任何来源。

## 新增/残余的具体错误

1. **`application.yaml:9` 多了一个 `}`**：`${TRUSTED_PROXY_PATTERN:(?!)}}` 行末存在多余右花括号。由于 `(?!)` 本身恒不匹配，默认“谁都不信任”的安全语义不受影响，但建议清理为 `${TRUSTED_PROXY_PATTERN:(?!)}`，避免环境变量/占位符解析产生歧义。
2. **409 同时消耗目标邮箱额度（低）**：`prepareVerification` 在归属检查前也获取 `verify-mail-target:<email>`。任意已登录用户对同一邮箱发起 3 次探测（其中 409 也算），即可让该邮箱在 1 小时内无法发起验证邮件。若设计只要求 409 消耗**账号**额度，建议把 target 限流放回归属确认之后，或改成“请求者+目标”维度。
3. **跨标签页/其他设备的删除仍可能造成 offset 跳项（低）**：本地删除已按 `loadedComments` 回退分页；但若另一个标签页删除已加载区间之前的补记，下一次 `page+1` 仍会按旧 offset 取数，`mergeComments` 只能去重、不能补漏。单标签页验收不受影响；若要求多端一致，需要游标分页或重新拉取已加载区间。

## 条件性部署风险

- **默认 NONE 不等于解决来源限流合并**：代码默认不信任任何来源，这是安全的；但在 Render 代理后，若不显式设置 `FORWARD_HEADERS_STRATEGY=NATIVE` 并把 `TRUSTED_PROXY_PATTERN` 配成代理的真实地址正则，`getRemoteAddr()` 仍是代理地址，来源限流会继续合并。**不要使用 `FRAMEWORK`**——它会绕过 `internal-proxies` 并信任客户端转发头；也不要使用过宽的正则。
- **401 跳转依赖 axios 拦截器**：本次只看到 router 在 401 且已登出时中止导航；实际 `logout + location.replace('/login...')` 在 `api/index.js`，不在 8 文件范围内。回归需确认过期 401 仍完成跳转，否则会停在当前页只剩空壳。
- **集成与构建依赖**：`CommentSection.vue` 已 import `commentDelete`，但 `api/index.js` 是否已导出该函数未在范围内；`TextContent` 的调用方（分类/补记服务）也未读取，helper 本身正确但需回归确认已接入。
