"""Exercise article search, private categories and local text imports against a local test app."""
import json
import os
import time
from pathlib import Path
from urllib.parse import urlparse

from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('BLOG_TEST_BASE_URL', 'http://127.0.0.1:18080').rstrip('/')
assert urlparse(BASE).hostname in ('localhost', '127.0.0.1'), 'Run only against the isolated local test app.'
OUTPUT = Path(__file__).resolve().parents[1] / 'target' / 'article-features'
OUTPUT.mkdir(parents=True, exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=os.environ['BROWSER_TEST_CHROME'], headless=True)
    context = browser.new_context(reduced_motion='reduce', timezone_id='Asia/Shanghai')
    page = context.new_page()
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    checks = []
    stamp = str(time.time_ns())

    def account(suffix):
        payload = {'username': 'feature_' + stamp + suffix, 'password': 'local-feature-test-password'}
        assert context.request.post(BASE + '/api/user/register', data=payload).ok
        token = context.request.post(BASE + '/api/user/login', data=payload).json()['data']
        return {'Authorization': 'Bearer ' + token}, token

    headers, token = account('a')
    other_headers, _ = account('b')

    def api(method, path, data=None, auth=headers):
        response = context.request.fetch(BASE + '/api' + path, method=method, headers=auth, data=data)
        assert response.ok, (path, response.status, response.text())
        return response.json()['data']

    def upload(name, content):
        page.get_by_label('选择 txt 或 md 文件').set_input_files({
            'name': name, 'mimeType': 'text/plain', 'buffer': content,
        })

    try:
        page.goto(BASE + '/login')
        page.evaluate('(token) => localStorage.setItem("blog_token", token)', token)
        page.goto(BASE + '/')
        page.wait_for_load_state('networkidle')
        expect(page.get_by_role('heading', name='我的日记', exact=True)).to_be_visible()
        page.get_by_role('button', name='管理分类', exact=True).click()
        page.get_by_label('分类名称', exact=True).fill('生活')
        page.get_by_role('button', name='新建分类', exact=True).click()
        expect(page.locator('.category-items')).to_contain_text('生活')
        page.get_by_label('分类名称', exact=True).fill('生活')
        page.get_by_role('button', name='新建分类', exact=True).click()
        expect(page.get_by_role('alert')).to_contain_text('分类名称已存在')
        page.get_by_role('button', name='重命名分类：生活').click()
        page.get_by_label('分类名称', exact=True).fill('日常')
        page.get_by_role('button', name='保存名称', exact=True).click()
        expect(page.locator('.category-items')).to_contain_text('日常')
        daily = api('GET', '/category/list')[0]['id']
        checks.append('create, duplicate-name error and rename categories in the UI')

        page.goto(BASE + '/publish')
        page.wait_for_load_state('networkidle')
        txt = '第一段，中文和 emoji 🌱\n\n第二段原样保留。'
        upload('日常.TXT', txt.encode('utf-8-sig'))
        expect(page.get_by_label('正文', exact=True)).to_have_value(txt)
        expect(page.get_by_role('status')).to_contain_text('日常.TXT')
        expect(page.locator('#publish-title')).to_have_value('')
        assert page.locator('#publish-title').evaluate('(input) => input.required && !input.checkValidity()')
        page.locator('#publish-title').fill('   ')
        page.get_by_role('button', name='存下这一页').click()
        expect(page.get_by_role('alert')).to_contain_text('填写标题')
        page.locator('#publish-title').fill('清晨 TXT 导入')
        page.get_by_label('分类选填').select_option(str(daily))
        page.get_by_role('button', name='存下这一页').click()
        expect(page.get_by_role('heading', name='清晨 TXT 导入', exact=True)).to_be_visible()
        txt_id = int(page.url.rsplit('/', 1)[-1])
        assert api('GET', '/article/detail/' + str(txt_id))['content'] == txt
        checks.append('UTF-8 txt import requires a manual title and saves the original body with a category')

        page.goto(BASE + '/publish')
        page.wait_for_load_state('networkidle')
        page.get_by_role('button', name='新建分类', exact=True).click()
        page.get_by_label('新分类名称', exact=True).fill('随笔')
        page.get_by_role('button', name='创建并选用').click()
        expect(page.locator('#publish-category option:checked')).to_have_text('随笔')
        essays = next(item['id'] for item in api('GET', '/category/list') if item['name'] == '随笔')
        md = '# 旅途笔记\n\n- 清晨出发\n<script>window.importExecuted = true</script>'
        upload('notes.md', md.encode())
        expect(page.get_by_label('正文', exact=True)).to_have_value(md)
        for name, data, message in [
            ('wrong.csv', b'data', '请选择 .txt 或 .md'),
            ('empty.txt', b' \n ', '文件内容为空'),
            ('long.md', b'x' * 100001, '超过 100,000'),
            ('large.txt', b'x' * (1024 * 1024 + 1), '不能超过 1 MB'),
            ('binary.txt', b'\x00\x01', '非文本内容'),
        ]:
            upload(name, data)
            expect(page.get_by_role('alert')).to_contain_text(message)
            expect(page.get_by_label('正文', exact=True)).to_have_value(md)
        upload('replace.txt', b'replacement')
        expect(page.get_by_role('dialog')).to_be_visible()
        page.get_by_role('button', name='再想一想').click()
        expect(page.get_by_label('正文', exact=True)).to_have_value(md)
        upload('replace.txt', b'replacement')
        page.get_by_role('button', name='替换正文', exact=True).click()
        expect(page.get_by_label('正文', exact=True)).to_have_value('replacement')
        for name, data, expected in [
            ('中文.txt', '旧编码中文'.encode('gbk'), '旧编码中文'),
            ('unicode.txt', 'Unicode 中文 🌱'.encode('utf-16'), 'Unicode 中文 🌱'),
            ('windows.txt', b'line one\r\nline two', 'line one\nline two'),
            ('notes.md', md.encode(), md),
        ]:
            upload(name, data)
            page.get_by_role('button', name='替换正文', exact=True).click()
            expect(page.get_by_label('正文', exact=True)).to_have_value(expected)
        page.locator('#publish-title').fill('旅途 MD 导入')
        page.get_by_role('button', name='存下这一页').click()
        expect(page.get_by_role('heading', name='旅途 MD 导入', exact=True)).to_be_visible()
        md_id = int(page.url.rsplit('/', 1)[-1])
        assert api('GET', '/article/detail/' + str(md_id))['content'] == md
        assert page.evaluate('window.importExecuted') is None
        checks.append('Markdown stays plain text; invalid files preserve drafts; replacement requires confirmation; GBK and UTF-16 work')

        for i in range(12):
            api('POST', '/article/publish', {'title': f'旅途 {i:02}', 'content': 'seeded diary', 'categoryId': essays})
        api('POST', '/article/publish', {'title': '无关标题', 'content': '旅途 only in the body'})
        api('POST', '/article/publish', {'title': '旅途 私密', 'content': 'another account'}, other_headers)
        page.goto(BASE + '/')
        page.wait_for_load_state('networkidle')
        expect(page.locator('.article-card').first).to_contain_text('无关标题')
        expect(page.get_by_text('共 15 篇记录 · 最新在前', exact=True)).to_be_visible()
        page.get_by_label('按标题搜索我的日记').fill('旅途')
        page.get_by_role('button', name='搜索', exact=True).click()
        page.get_by_label('按分类筛选').select_option(str(essays))
        expect(page.get_by_text('找到 13 篇记录 · 最新在前', exact=True)).to_be_visible()
        page.get_by_role('button', name='下一页', exact=True).click()
        expect(page.get_by_role('button', name='第 2 页')).to_have_attribute('aria-current', 'page')
        expect(page.locator('.article-card')).to_have_count(3)
        expect(page.locator('.article-list')).not_to_contain_text('私密')
        page.get_by_label('按标题搜索我的日记').fill('不存在的标题')
        page.get_by_role('button', name='搜索', exact=True).click()
        expect(page.get_by_role('heading', name='没有找到符合条件的日记')).to_be_visible()
        page.get_by_role('button', name='查看全部日记', exact=True).click()
        expect(page.get_by_text('共 15 篇记录 · 最新在前', exact=True)).to_be_visible()
        checks.append('newest first, title-only private search, combined category filters, pagination and clearing empty results')

        page.get_by_role('button', name='管理分类', exact=True).click()
        page.get_by_role('button', name='删除分类：日常').click()
        page.get_by_role('button', name='确认删除', exact=True).click()
        expect(page.get_by_role('alert')).to_contain_text('分类下仍有文章')
        page.get_by_role('button', name='再想一想').click()
        page.goto(BASE + f'/article/{txt_id}/edit')
        page.wait_for_load_state('networkidle')
        expect(page.locator('#publish-category')).to_have_value(str(daily))
        page.locator('#publish-category').select_option(label='未分类')
        page.get_by_role('button', name='保存修改', exact=True).click()
        expect(page.get_by_role('heading', name='清晨 TXT 导入', exact=True)).to_be_visible()
        assert api('GET', '/article/detail/' + str(txt_id))['categoryId'] is None
        page.goto(BASE + '/')
        page.wait_for_load_state('networkidle')
        page.get_by_role('button', name='管理分类', exact=True).click()
        page.get_by_role('button', name='删除分类：日常').click()
        page.get_by_role('button', name='确认删除', exact=True).click()
        expect(page.get_by_role('dialog')).not_to_be_visible()
        expect(page.locator('.category-items')).not_to_contain_text('日常')
        checks.append('used categories cannot be deleted; editing preserves or clears classification; empty categories can be deleted')

        page.screenshot(path=str(OUTPUT / 'desktop.png'), full_page=True)
        page.set_viewport_size({'width': 375, 'height': 812})
        page.screenshot(path=str(OUTPUT / 'mobile.png'), full_page=True)
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), 'Home overflows on mobile'
        assert page.get_by_role('button', name='搜索', exact=True).bounding_box()['height'] < 55, 'Search button wraps on mobile'
        page.set_viewport_size({'width': 320, 'height': 740})
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), 'Home overflows at 320px'
        page.set_viewport_size({'width': 375, 'height': 812})
        page.goto(BASE + '/publish')
        page.wait_for_load_state('networkidle')
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), 'Editor overflows on mobile'
        page.screenshot(path=str(OUTPUT / 'editor-mobile.png'), full_page=True)
        page.emulate_media(color_scheme='dark')
        page.screenshot(path=str(OUTPUT / 'editor-dark.png'), full_page=True)
        assert not errors, errors
        checks.append('mobile layout, dark theme and no JavaScript page errors')
        (OUTPUT / 'result.json').write_text(json.dumps({'checks': checks, 'pageErrors': errors}, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps({'passed': len(checks), 'checks': checks}, ensure_ascii=False))
    finally:
        context.close()
        browser.close()
