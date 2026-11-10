"""Verify per-article category changes against an isolated local app."""
import json
import os
import time
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('BLOG_TEST_BASE_URL', 'http://127.0.0.1:18080').rstrip('/')
assert urlparse(BASE).hostname in ('localhost', '127.0.0.1')
OUTPUT = Path(__file__).resolve().parents[1] / 'target' / 'article-category'
OUTPUT.mkdir(parents=True, exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=os.environ['BROWSER_TEST_CHROME'], headless=True)
    context = browser.new_context(reduced_motion='reduce')
    page = context.new_page()
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    payload = {'username': 'category_' + str(time.time_ns()), 'password': 'local-category-test-password'}
    assert context.request.post(BASE + '/api/user/register', data=payload).ok
    token = context.request.post(BASE + '/api/user/login', data=payload).json()['data']
    headers = {'Authorization': 'Bearer ' + token}
    checks = []

    def api(method, path, data=None):
        response = context.request.fetch(BASE + '/api' + path, method=method, headers=headers, data=data)
        assert response.ok, (path, response.status, response.text())
        return response.json()['data']

    def dialog():
        return page.get_by_role('dialog', name='修改分类', exact=True)

    def open_category(title):
        page.get_by_role('button', name='修改分类：' + title, exact=True).click()
        expect(dialog().get_by_label('文章分类', exact=True)).to_be_enabled()

    def save_category(category):
        dialog().get_by_label('文章分类', exact=True).select_option(label=category)
        dialog().get_by_role('button', name='保存分类', exact=True).click()
        expect(dialog()).not_to_be_visible()
        page.wait_for_load_state('networkidle')

    try:
        first = api('POST', '/category', {'name': '日常'})['id']
        second = api('POST', '/category', {'name': '读书'})['id']
        original = api('POST', '/article/publish', {'title': '待整理的日记', 'content': '第一行\n\n不能被分类操作覆盖的正文 🌱'})
        article_id = original['id']
        page.goto(BASE + '/login')
        page.evaluate('(token) => localStorage.setItem("blog_token", token)', token)
        page.goto(BASE + '/')
        page.wait_for_load_state('networkidle')
        expect(page.locator('.article-category')).to_have_text('未分类')
        open_category(original['title'])
        save_category('日常')
        expect(page.locator('.article-category')).to_have_text('日常')
        open_category(original['title'])
        expect(dialog().get_by_label('文章分类', exact=True)).to_have_value(str(first))
        dialog().get_by_label('文章分类', exact=True).select_option(str(second))
        dialog().get_by_role('button', name='取消', exact=True).click()
        assert api('GET', '/article/detail/' + str(article_id))['categoryId'] == first
        open_category(original['title'])
        save_category('读书')
        expect(page.locator('.article-category')).to_have_text('读书')
        checks.append('assign, move and cancel directly from each article in the list')

        open_category(original['title'])
        dialog().get_by_role('button', name='＋ 新建分类', exact=True).click()
        dialog().get_by_label('新分类名称', exact=True).fill('读书')
        dialog().get_by_role('button', name='创建并选用', exact=True).click()
        expect(dialog().get_by_role('alert')).to_contain_text('分类名称已存在')
        dialog().get_by_label('新分类名称', exact=True).fill('灵感')
        dialog().get_by_role('button', name='创建并选用', exact=True).click()
        expect(dialog().get_by_label('文章分类', exact=True).locator('option:checked')).to_have_text('灵感')
        save_category('灵感')
        expect(page.locator('.article-category')).to_have_text('灵感')
        saved = api('GET', '/article/detail/' + str(article_id))
        for field in ('title', 'content', 'createTime'):
            assert saved[field] == original[field]
        checks.append('create and select a new category while preserving title, body and creation time')

        page.goto(BASE + '/article/' + str(article_id))
        page.wait_for_load_state('networkidle')
        page.get_by_role('button', name='修改分类', exact=True).click()
        expect(dialog().get_by_label('文章分类', exact=True)).to_be_enabled()
        save_category('未分类')
        expect(page.locator('.article-header .eyebrow')).to_have_text('未分类')
        assert api('GET', '/article/detail/' + str(article_id))['categoryId'] is None
        checks.append('clear classification from the visible button under the detail title')

        page.goto(BASE + '/')
        page.wait_for_load_state('networkidle')
        def fail_list(route):
            route.fulfill(status=503, content_type='application/json', body=json.dumps({'message': '分类暂时无法加载'}))
        page.route('**/api/category/list', fail_list)
        page.get_by_role('button', name='修改分类：' + original['title'], exact=True).click()
        expect(dialog().get_by_role('alert')).to_contain_text('分类暂时无法加载')
        expect(dialog().get_by_role('button', name='保存分类', exact=True)).to_be_disabled()
        page.unroute('**/api/category/list', fail_list)
        dialog().get_by_role('button', name='重新加载分类', exact=True).click()
        expect(dialog().get_by_label('文章分类', exact=True)).to_be_enabled()
        def fail_save(route):
            route.fulfill(status=503, content_type='application/json', body=json.dumps({'message': '暂时无法保存'}))
        page.route('**/api/article/*/category', fail_save)
        dialog().get_by_label('文章分类', exact=True).select_option(str(first))
        dialog().get_by_role('button', name='保存分类', exact=True).click()
        expect(dialog().get_by_role('alert')).to_contain_text('暂时无法保存')
        assert api('GET', '/article/detail/' + str(article_id))['categoryId'] is None
        page.unroute('**/api/article/*/category', fail_save)
        # An edit made after the list loaded must not be overwritten by a category-only save.
        api('PUT', '/article/' + str(article_id), {'title': '另一处的新标题', 'content': '另一处刚刚保存的新正文'})
        save_category('日常')
        expect(page.locator('.article-title')).to_contain_text('另一处的新标题')
        assert api('GET', '/article/detail/' + str(article_id))['content'] == '另一处刚刚保存的新正文'
        api('PATCH', '/article/' + str(article_id) + '/category', {'categoryId': None})
        checks.append('load/save failure recovery and category-only updates preserve concurrent text edits')

        for i in range(11):
            api('POST', '/article/publish', {'title': f'分页日记 {i:02}', 'content': '分页测试正文', 'categoryId': first})
        page.reload()
        page.wait_for_load_state('networkidle')
        page.get_by_label('按分类筛选').select_option(str(first))
        expect(page.get_by_text('找到 11 篇记录 · 最新在前', exact=True)).to_be_visible()
        page.get_by_role('button', name='下一页', exact=True).click()
        expect(page.locator('.article-card')).to_have_count(1)
        open_category('分页日记 00')
        save_category('读书')
        expect(page.get_by_text('找到 10 篇记录 · 最新在前', exact=True)).to_be_visible()
        expect(page.locator('.article-card')).to_have_count(10)
        expect(page.get_by_role('navigation', name='日记分页')).not_to_be_visible()
        expect(page.locator('.article-list')).not_to_contain_text('分页日记 00')
        checks.append('filtered list and category counts refresh; moving the last item returns to a valid page')

        page.set_viewport_size({'width': 375, 'height': 812})
        page.screenshot(path=str(OUTPUT / 'list-mobile.png'), full_page=True)
        open_category('分页日记 10')
        page.screenshot(path=str(OUTPUT / 'dialog-mobile.png'), full_page=True)
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
        assert dialog().evaluate('(element) => element.scrollWidth <= element.clientWidth')
        page.set_viewport_size({'width': 320, 'height': 740})
        assert dialog().evaluate('(element) => element.scrollWidth <= element.clientWidth')
        dialog().get_by_role('button', name='取消', exact=True).click()
        page.set_viewport_size({'width': 1280, 'height': 900})
        page.screenshot(path=str(OUTPUT / 'list-desktop.png'), full_page=True)
        assert not errors, errors
        checks.append('mobile/desktop layout and no JavaScript page errors')
        result = {'passed': len(checks), 'checks': checks, 'pageErrors': errors}
        (OUTPUT / 'result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps(result, ensure_ascii=False))
    finally:
        context.close()
        browser.close()
