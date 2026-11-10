# 文章整理与文件导入

验证日期：2026-11-10。本轮完成本地实现与验证，未触发公网部署。

- 首页默认按创建时间倒序；时间相同时按 ID 倒序。编辑旧记录不改变其创建时间与排序。
- 搜索框按标题进行字面子串搜索，只查询当前账号，可与分类筛选、分页组合使用。
- 首页“管理分类”支持新建、重命名、删除空分类。使用中的分类需先移动其中的记录后再删除。
- 写作页可选择或取消分类，也可新建分类并立即选用。编辑已有记录时保留其原分类。
- 首页每篇日记旁、详情页标题下新增“修改分类”，可直接选择、新建或取消分类；仅更新分类与修改时间，不覆盖标题和正文。筛选列表与分类数量同步刷新，移出末页最后一篇时自动返回有效页。
- “导入文件”支持 `.txt`、`.md`，文件内容填入正文，标题手动填写；确认保存后才入库。
- 导入已有正文时先提示确认替换；取消、读取失败、文件格式错误时保留现有文字。
- 文件最多 1 MB，正文最多 100000 字；支持 UTF-8、带 BOM 的 UTF-16 和 GB18030/GBK。
- Markdown 按原始文本保存和显示。普通手写记录仍可省略标题，由正文第一行命名。

快捷分类使用新增 `PATCH /article/{id}/category` 接口，无数据库结构变更；前端源码在 `D:\project\personal_blog_vite`，
并通过 `scripts/prepare-render.ps1` 同步到本仓库的 `frontend/` 构建快照。

验证结果：

- 前端 `npm run build` 通过。
- 后端 `mvn -B -s target/maven-settings.xml -gs target/maven-settings.xml verify`：31 项测试通过，0 失败、0 跳过，包含快捷分类权限、字段保留及 PATCH 跨域预检。
- `scripts/verify-article-features.py`：6 组真实浏览器流程通过，覆盖分类 CRUD、导入与校验、搜索筛选分页、账号隔离、移动端及深色模式，页面 JavaScript 错误为 0。
- `scripts/verify-article-category.py`：6 组快捷分类流程通过，覆盖列表与详情入口、新建与取消分类、失败重试、并行正文修改不被覆盖、筛选末页回退及移动端，页面 JavaScript 错误为 0；记录保存在 `target/article-category/`。
- 浏览器使用本地服务和独立内存 H2 数据库，不连接已有日记库；验证图片和 JSON 记录保存在忽略目录 `target/article-features/`。

浏览器验证可在已启动的本地测试服务上运行：

```powershell
$env:BLOG_TEST_BASE_URL = 'http://127.0.0.1:18080'
$env:BROWSER_TEST_CHROME = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
python scripts/verify-article-features.py
python scripts/verify-article-category.py
```

需预先安装 Python Playwright。脚本会在本地测试服务中新建测试账号和记录，请使用独立测试库。
