"""Browser regressions for the DeepSeek review; API fixtures stay entirely local.

Start Vite/preview on 127.0.0.1:15173, then run with Python Playwright installed.
Set BROWSER_TEST_CHROME to use a local browser instead of Playwright Chromium.
"""
import json
import os
from pathlib import Path
from urllib.parse import parse_qs, urlparse

from playwright.sync_api import expect, sync_playwright

BASE = os.environ.get('BLOG_TEST_BASE_URL', 'http://127.0.0.1:15173').rstrip('/')
assert urlparse(BASE).hostname in ('127.0.0.1', 'localhost'), 'Only local test servers are supported'
OUT = Path(__file__).resolve().parents[1] / 'target' / 'review-54589' / 'browser'
OUT.mkdir(parents=True, exist_ok=True)
checks = []


class ApiFixture:
    def __init__(self, count=10, profile_status=200):
        self.comments = [self.note(i) for i in range(1, count + 1)]
        self.next_id = count + 1
        self.profile_status = profile_status
        self.fail_delete = False
        self.list_requests = []

    @staticmethod
    def note(i, content=None):
        return {'id': i, 'articleId': 1, 'userId': 1, 'content': content or f'补记 {i:02}',
                'createTime': '2026-11-11T00:00:00'}

    def handle(self, route):
        request = route.request
        parsed = urlparse(request.url)
        if not parsed.path.startswith('/api/'):
            route.continue_()
            return
        path = parsed.path.removeprefix('/api')
        method = request.method
        status = 200
        data = None
        if path == '/user/me':
            if self.profile_status == 0:
                route.abort('failed')
                return
            status = self.profile_status
            data = {'id': 1, 'username': 'review_user'}
        elif path == '/user/security-options':
            data = {'mailEnabled': False}
        elif path == '/category/list':
            data = []
        elif path == '/article/detail/1':
            data = {'id': 1, 'userId': 1, 'title': '审查回归日记', 'content': '正文', 'published': False,
                    'createTime': '2026-11-11T00:00:00', 'updateTime': '2026-11-11T00:00:00'}
        elif path == '/comment/1' and method == 'GET':
            params = parse_qs(parsed.query)
            page = int(params.get('page', ['1'])[0])
            size = int(params.get('size', ['10'])[0])
            self.list_requests.append(page)
            data = {'records': self.comments[(page - 1) * size:page * size], 'total': len(self.comments)}
        elif path == '/comment/publish':
            data = self.note(self.next_id, request.post_data_json['content'])
            self.next_id += 1
            self.comments.append(data)
        elif path.startswith('/comment/') and method == 'DELETE':
            if self.fail_delete:
                status = 503
            else:
                id_to_delete = int(path.rsplit('/', 1)[1])
                self.comments = [note for note in self.comments if note['id'] != id_to_delete]
        else:
            raise AssertionError(f'Unexpected API request: {method} {path}')
        route.fulfill(status=status, content_type='application/json', body=json.dumps({
            'code': status, 'message': 'success' if status == 200 else '测试服务暂不可用', 'data': data}, ensure_ascii=False))


def open_diary(browser, fixture):
    context = browser.new_context(reduced_motion='reduce')
    context.add_init_script("if (!sessionStorage.getItem('fixture-seeded')) { localStorage.setItem('blog_token', 'local-fixture-token'); sessionStorage.setItem('fixture-seeded', '1') }")
    context.route('**/api/**', fixture.handle)
    page = context.new_page()
    page.goto(BASE + '/article/1')
    page.wait_for_load_state('networkidle')
    return context, page


def submit_note(page, text):
    page.get_by_label('写下补记', exact=True).fill(text)
    page.get_by_role('button', name='存下补记').click()
    expect(page.locator('.comment-content').filter(has_text=text)).to_have_count(1)


with sync_playwright() as playwright:
    options = {'headless': True}
    if os.environ.get('BROWSER_TEST_CHROME'):
        options['executable_path'] = os.environ['BROWSER_TEST_CHROME']
    browser = playwright.chromium.launch(**options)
    runtime_errors = []
    try:
        fixture = ApiFixture(10)
        context, page = open_diary(browser, fixture)
        page.on('pageerror', lambda error: runtime_errors.append(str(error)))
        submit_note(page, '新写的第十一条')
        expect(page.locator('.comment-item')).to_have_count(11)
        assert fixture.list_requests == [1], 'Saving must preserve the loaded prefix'
        expect(page.locator('.comment-count')).to_have_text('11')
        expect(page.get_by_role('button', name='翻开更多补记')).to_have_count(0)
        checks.append('The eleventh note appears immediately without resetting the list')
        page.get_by_label('写下补记', exact=True).fill('\u200b\u200d\u00a0')
        expect(page.get_by_role('button', name='存下补记')).to_be_disabled()
        checks.append('Invisible-only notes cannot be submitted from the UI')
        context.close()

        fixture = ApiFixture(21)
        context, page = open_diary(browser, fixture)
        page.on('pageerror', lambda error: runtime_errors.append(str(error)))
        submit_note(page, '最新的第二十二条')
        page.get_by_role('button', name='翻开更多补记').click()
        expect(page.locator('.comment-item')).to_have_count(21)
        fixture.fail_delete = True
        page.get_by_role('button', name='删除这条补记').first.click()
        dialog = page.get_by_role('dialog').filter(has=page.get_by_role('heading', name='删除这条补记？'))
        dialog.get_by_role('button', name='确认删除', exact=True).click()
        expect(dialog.locator('.error-msg')).to_be_visible()
        expect(page.locator('.comment-item')).to_have_count(21)
        fixture.fail_delete = False
        dialog.get_by_role('button', name='确认删除', exact=True).click()
        expect(dialog).not_to_be_visible()
        expect(page.locator('.comment-item')).to_have_count(20)
        page.get_by_role('button', name='翻开更多补记').click()
        expect(page.locator('.comment-item')).to_have_count(21)
        contents = page.locator('.comment-content').all_text_contents()
        assert contents == [note['content'] for note in fixture.comments], 'No skipped, duplicated or reordered notes'
        expect(page.locator('.comment-count')).to_have_text('21')
        expect(page.get_by_role('button', name='翻开更多补记')).to_have_count(0)
        page.set_viewport_size({'width': 390, 'height': 844})
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
        page.screenshot(path=str(OUT / 'notes-mobile.png'), full_page=True)
        checks.append('Save, load more, failed deletion and retry preserve pagination without gaps or duplicates')
        checks.append('The note deletion controls fit a mobile viewport')
        context.close()

        for failure in (500, 429, 0):
            fixture = ApiFixture(0, profile_status=failure)
            context, page = open_diary(browser, fixture)
            page.on('pageerror', lambda error: runtime_errors.append(str(error)))
            expect(page).to_have_url(BASE + '/article/1')
            expect(page.get_by_role('button', name='重试账号信息')).to_be_visible()
            page.get_by_label('写下补记', exact=True).fill('保留的草稿')
            fixture.profile_status = 200
            page.get_by_role('button', name='重试账号信息').click()
            expect(page.get_by_role('button', name='重试账号信息')).to_have_count(0)
            expect(page.get_by_label('写下补记', exact=True)).to_have_value('保留的草稿')
            expect(page.get_by_role('button', name='修改分类', exact=True)).to_be_visible()
            assert page.evaluate("localStorage.getItem('blog_token')") == 'local-fixture-token'
            checks.append(f'Profile failure {failure or "network"} keeps the route, credential and draft; retry recovers')
            context.close()

        fixture = ApiFixture(0, profile_status=401)
        context, page = open_diary(browser, fixture)
        expect(page).to_have_url(BASE + '/login?redirect=%2Farticle%2F1')
        assert page.evaluate("localStorage.getItem('blog_token')") is None
        checks.append('A genuine 401 still clears the expired session and opens login')
        context.close()
        assert not runtime_errors, runtime_errors
        checks.append('No browser runtime errors during the regression scenarios')
    finally:
        browser.close()

(OUT / 'result.json').write_text(json.dumps({'count': len(checks), 'checks': checks}, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({'count': len(checks), 'checks': checks}, ensure_ascii=True, indent=2))
