"""Verify private diary flows through the real frontend and backend using two test accounts."""
import json
import os
import time
from datetime import datetime, timedelta
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('BLOG_TEST_BASE_URL', 'http://127.0.0.1:18080').rstrip('/')
OUTPUT = Path(__file__).resolve().parents[1] / 'target' / 'browser-test'
OUTPUT.mkdir(parents=True, exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=os.environ['BROWSER_TEST_CHROME'], headless=True)
    context = browser.new_context(timezone_id='Asia/Shanghai', reduced_motion='reduce')
    page = context.new_page()
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    username = 'browser_' + str(int(time.time()))
    password = 'local-browser-test-password'
    article_id = None
    token = None
    other = None
    checks = []
    try:
        page.goto(BASE + '/')
        expect(page).to_have_url(BASE + '/login?redirect=/')
        assert context.request.get(BASE + '/api/article/list').status == 401
        checks.append('guest pages and APIs require login')

        page.goto(BASE + '/register')
        page.wait_for_load_state('networkidle')
        page.get_by_label('用户名', exact=True).fill(username)
        page.get_by_label('邮箱').fill('browser@example.com')
        page.get_by_label('密码', exact=True).fill(password)
        page.get_by_role('button', name='开启第一章', exact=True).click()
        expect(page).to_have_url(BASE + '/login')
        page.get_by_label('用户名', exact=True).fill(username)
        page.get_by_label('密码', exact=True).fill('incorrect-password')
        page.get_by_role('button', name='回到书房', exact=True).click()
        expect(page.locator('.error-msg')).to_contain_text('用户名或密码错误')
        page.get_by_label('密码', exact=True).fill(password)
        page.get_by_role('button', name='回到书房', exact=True).click()
        expect(page).to_have_url(BASE + '/')
        expect(page.get_by_role('heading', name='书页正等着第一行字')).to_be_visible()
        checks.append('register, invalid login, valid login, independent empty journal')

        page.get_by_role('link', name='写一页', exact=True).click()
        page.get_by_label('正文', exact=True).fill('只写给自己的第一行\n\n不必写得完整，真实就很好。')
        page.get_by_role('button', name='存下这一页', exact=True).click()
        expect(page.get_by_role('heading', name='只写给自己的第一行', exact=True)).to_be_visible()
        article_id = int(page.url.rsplit('/', 1)[1])
        token = page.evaluate('localStorage.getItem("blog_token")')
        headers = {'Authorization': 'Bearer ' + token}
        response = context.request.get(BASE + '/api/article/detail/' + str(article_id), headers=headers)
        assert response.ok and response.headers.get('cache-control') == 'no-store'
        created_at = response.json()['data']['createTime']
        expected_time = (datetime.fromisoformat(created_at) + timedelta(hours=8)).strftime('%Y-%m-%d %H:%M')
        expect(page.locator('.article-detail-meta')).to_contain_text(expected_time)
        expect(page.locator('.article-stats')).to_have_count(0)
        checks.append('untitled writing auto title, UTC display, no-store and no social metrics')

        page.get_by_role('link', name='修改这一页', exact=True).click()
        expect(page.get_by_label('正文', exact=True)).to_have_value('只写给自己的第一行\n\n不必写得完整，真实就很好。')
        page.get_by_label('标题', exact=False).fill('后来重新读这一页')
        page.get_by_label('正文', exact=True).fill('今天留下的一点想法。\n\n再次修改，只是为了更贴近此刻的自己。')
        page.get_by_role('button', name='保存修改', exact=True).click()
        expect(page.get_by_role('heading', name='后来重新读这一页', exact=True)).to_be_visible()
        page.reload()
        expect(page.locator('.article-content')).to_contain_text('再次修改')
        checks.append('edit own diary and refresh')

        page.get_by_label('写下补记', exact=True).fill('过了些时候，我又想到了一点。')
        page.get_by_role('button', name='存下补记', exact=True).click()
        expect(page.locator('.comment-content')).to_contain_text('我又想到了一点')
        for i in range(10):
            assert context.request.post(BASE + '/api/comment/publish', headers=headers,
                data={'articleId': article_id, 'content': 'Later private note ' + str(i)}).ok
        page.reload()
        expect(page.locator('.comment-item')).to_have_count(10)
        page.get_by_role('button', name='翻开更多补记', exact=True).click()
        expect(page.locator('.comment-item')).to_have_count(11)
        checks.append('private supplementary notes and pagination')

        other = browser.new_context(reduced_motion='reduce')
        other_username = username + '_other'
        assert other.request.post(BASE + '/api/user/register',
            data={'username': other_username, 'password': password}).ok
        other_token = other.request.post(BASE + '/api/user/login',
            data={'username': other_username, 'password': password}).json()['data']
        other_headers = {'Authorization': 'Bearer ' + other_token}
        assert other.request.get(BASE + '/api/article/list', headers=other_headers).json()['data']['total'] == 0
        for path in ('/api/article/detail/' + str(article_id), '/api/comment/' + str(article_id)):
            assert other.request.get(BASE + path, headers=other_headers).status == 404
            assert other.request.get(BASE + path).status == 401
        assert other.request.put(BASE + '/api/article/' + str(article_id), headers=other_headers,
            data={'content': 'Cannot overwrite'}).status == 404
        assert other.request.delete(BASE + '/api/article/' + str(article_id), headers=other_headers).status == 404
        assert other.request.post(BASE + '/api/comment/publish', headers=other_headers,
            data={'articleId': article_id, 'content': 'Cannot append'}).status == 404
        for path in ('/api/article/ranking', '/api/article/subscriptions', '/api/user/subscriptions'):
            assert other.request.get(BASE + path, headers=other_headers).status == 410
        other_page = other.new_page()
        other_page.goto(BASE + '/login')
        other_page.evaluate('(value) => localStorage.setItem("blog_token", value)', other_token)
        other_page.goto(BASE + '/article/' + str(article_id))
        expect(other_page.get_by_role('heading', name='这页暂时无法打开')).to_be_visible()
        expect(other_page.locator('.article-content')).to_have_count(0)
        checks.append('second account cannot list, read, edit, delete or append; social routes retired')

        for width in (320, 390, 768, 1440):
            page.set_viewport_size({'width': width, 'height': 920})
            for path in ('/', '/article/' + str(article_id), '/article/' + str(article_id) + '/edit', '/publish', '/login', '/register'):
                page.goto(BASE + path)
                page.wait_for_load_state('networkidle')
                assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), (path, width)
        page.goto(BASE + '/article/' + str(article_id))
        page.wait_for_load_state('networkidle')
        page.screenshot(path=str(OUTPUT / 'private-diary.png'), full_page=True)
        checks.append('private reader, editor and account pages fit 320–1440 pixel layouts')

        page.get_by_role('button', name='删除日记', exact=True).click()
        expect(page.get_by_role('dialog')).to_be_visible()
        page.get_by_role('button', name='再想一想', exact=True).click()
        expect(page.get_by_role('dialog')).not_to_be_visible()
        page.get_by_role('button', name='删除日记', exact=True).click()
        page.get_by_role('button', name='确认删除', exact=True).click()
        expect(page).to_have_url(BASE + '/')
        expect(page.get_by_role('heading', name='书页正等着第一行字')).to_be_visible()
        article_id = None
        page.get_by_role('button', name='退出登录', exact=False).click()
        expect(page).to_have_url(BASE + '/login')
        page.go_back()
        page.wait_for_load_state('networkidle')
        expect(page.locator('.article-content')).to_have_count(0)
        assert page.evaluate('localStorage.getItem("blog_token")') is None
        checks.append('delete confirmation, logout and back navigation reveal no private content')

        assert not errors, errors
        (OUTPUT / 'result.json').write_text(json.dumps({'status': 'passed', 'baseUrl': BASE,
            'pageErrors': errors, 'testUsers': [username, other_username], 'checks': checks},
            ensure_ascii=False, indent=2), encoding='utf-8')
        print('Private journal browser checks passed: ' + str(len(checks)) + ' groups.')
    finally:
        if article_id and token:
            context.request.delete(BASE + '/api/article/' + str(article_id), headers={'Authorization': 'Bearer ' + token})
        if other:
            other.close()
        context.close()
        browser.close()
