"""Check private-page cleanup during cross-tab account switching and session expiry."""
import json
import os
import time
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('BLOG_TEST_BASE_URL', 'http://127.0.0.1:8080').rstrip('/')
assert urlparse(BASE).hostname in ('localhost', '127.0.0.1'), 'Session UI checks are local-only.'
OUTPUT = Path(__file__).resolve().parents[1] / 'target' / 'ui-review'
OUTPUT.mkdir(parents=True, exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=os.environ['BROWSER_TEST_CHROME'], headless=True)
    context = browser.new_context(reduced_motion='reduce')
    stamp = str(int(time.time()))
    password = 'private-session-check-password'
    accounts = []
    for suffix in ('a', 'b'):
        payload = {'username': 'session_' + stamp + '_' + suffix, 'password': password}
        registered = context.request.post(BASE + '/api/user/register', data=payload)
        assert registered.ok, registered.status
        token = context.request.post(BASE + '/api/user/login', data=payload).json()['data']
        accounts.append({**payload, 'id': registered.json()['data']['id'], 'token': token})
    first, second = accounts
    headers = {'Authorization': 'Bearer ' + first['token']}
    response = context.request.post(BASE + '/api/article/publish', headers=headers,
        data={'content': 'A private entry for the first account', 'userId': second['id'], 'authorId': second['id']})
    assert response.ok, response.status
    entry = response.json()['data']
    assert entry['userId'] == first['id'], 'Client ownership fields must be ignored.'
    errors = []
    try:
        first_tab = context.new_page()
        first_tab.on('pageerror', lambda error: errors.append(str(error)))
        first_tab.goto(BASE + '/login')
        first_tab.evaluate('(value) => localStorage.setItem("blog_token", value)', first['token'])
        first_tab.goto(BASE + '/article/' + str(entry['id']))
        expect(first_tab.locator('.article-content')).to_contain_text('first account')

        second_tab = context.new_page()
        second_tab.on('pageerror', lambda error: errors.append(str(error)))
        second_tab.goto(BASE + '/login')
        second_tab.get_by_label('用户名', exact=True).fill(second['username'])
        second_tab.get_by_label('密码', exact=True).fill(password)
        second_tab.get_by_role('button', name='回到书房', exact=True).click()
        expect(second_tab).to_have_url(BASE + '/')
        expect(second_tab.get_by_role('heading', name='书页正等着第一行字')).to_be_visible()
        expect(first_tab.get_by_role('heading', name='这页暂时无法打开')).to_be_visible()
        expect(first_tab.locator('.article-content')).to_have_count(0)

        requests = []
        def expire(route):
            requests.append(bool(route.request.headers.get('authorization')))
            route.fulfill(status=401, content_type='application/json',
                body=json.dumps({'code': 401, 'message': '登录已过期', 'data': None}))
        second_tab.route('**/api/article/list?*', expire)
        second_tab.reload()
        expect(second_tab.get_by_role('heading', name='登录行间', exact=True)).to_be_visible()
        expect(first_tab.get_by_role('heading', name='登录行间', exact=True)).to_be_visible()
        assert second_tab.evaluate('localStorage.getItem("blog_token")') is None
        assert requests == [True], 'Private content must not be retried anonymously.'
        expect(first_tab.locator('.article-content')).to_have_count(0)
        assert not errors, errors
        result = {'status': 'passed', 'baseUrl': BASE, 'pageErrors': errors,
            'checks': ['client cannot spoof ownership', 'cross-tab account switch clears private content',
                       '401 clears both tabs and credentials', 'no anonymous private-content retry']}
        (OUTPUT / 'result.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
        print('Private session UI checks passed: 4 groups.')
    finally:
        context.request.delete(BASE + '/api/article/' + str(entry['id']), headers=headers)
        context.close()
        browser.close()
