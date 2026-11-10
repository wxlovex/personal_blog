"""Local security acceptance with a disposable file-backed H2 database and real browser.

Requires a packaged backend, a frontend dist build and Python Playwright.
Only binds/connects to loopback. Restarts only the JVM created by this script.
"""
import http.client
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import time
import uuid
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'target' / 'security-browser'
OUTPUT.mkdir(parents=True, exist_ok=True)
PORT = 18081
BASE = f'http://127.0.0.1:{PORT}'
RUN = uuid.uuid4().hex[:10]
PASSWORD = 'local-security-browser-password'
checks = []


def api(path, method='GET', data=None, token=None):
    headers = {}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if data is not None:
        headers['Content-Type'] = 'application/json'
        data = json.dumps(data, ensure_ascii=False).encode()
    request = Request(BASE + path, data=data, headers=headers, method=method)
    try:
        response = urlopen(request, timeout=20)
    except HTTPError as error:
        response = error
    with response:
        return response.status, dict(response.headers), json.loads(response.read())


def start_server():
    jar = ROOT / 'target' / 'personal_blog-0.0.1-SNAPSHOT.jar'
    h2 = sorted((ROOT / 'target' / 'maven-repository' / 'com' / 'h2database' / 'h2').glob('*/*[0-9].jar'))
    if not h2:
        raise RuntimeError('H2 test dependency not found in target/maven-repository')
    assert jar.is_file(), 'Build and package the backend with the frontend first'
    command = [shutil.which('java'), '-Duser.timezone=UTC', f'-Dloader.path={h2[-1]}', '-cp', str(jar),
               'org.springframework.boot.loader.launch.PropertiesLauncher',
               f'--server.address=127.0.0.1', f'--server.port={PORT}',
               f'--spring.datasource.url=jdbc:h2:file:{OUTPUT.as_posix()}/db-{RUN};MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER,COMMENT;WRITE_DELAY=0',
               '--spring.datasource.username=sa', '--spring.datasource.password=',
               '--spring.sql.init.mode=always', '--spring.sql.init.schema-locations=classpath:db/schema.sql',
               '--blog.jwt.secret=local-security-test-only-secret-at-least-32-bytes', '--blog.mail.enabled=false']
    log = (OUTPUT / 'runtime.log').open('ab')
    process = subprocess.Popen(command, cwd=ROOT, stdout=log, stderr=log,
                               creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    try:
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            if process.poll() is not None:
                raise RuntimeError('Local JVM exited; inspect target/security-browser/runtime.log')
            try:
                if api('/api/health')[0] == 200:
                    return process, log
            except (OSError, ValueError):
                pass
            time.sleep(0.25)
        raise RuntimeError('Local JVM startup timed out')
    except BaseException:
        stop_server(process, log)
        raise


def stop_server(process, log):
    process.terminate()
    try:
        process.wait(timeout=15)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=10)
    log.close()


with socket.socket() as probe:
    if probe.connect_ex(('127.0.0.1', PORT)) == 0:
        raise RuntimeError(f'Port {PORT} is already occupied; no existing service will be changed')

process, log = start_server()
try:
    with sync_playwright() as playwright:
        executable = os.environ.get('BROWSER_TEST_CHROME')
        if not executable and os.name == 'nt':
            executable = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
        browser = playwright.chromium.launch(executable_path=executable, headless=True)
        context = browser.new_context(timezone_id='Asia/Shanghai')
        page = context.new_page()
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(BASE + '/register')
        page.wait_for_load_state('networkidle')
        expect(page.get_by_role('button', name='开启第一章', exact=True)).to_be_visible()
        page.get_by_label('用户名', exact=True).fill('browser_' + RUN)
        page.get_by_label('密码', exact=True).fill(PASSWORD)
        page.get_by_role('button', name='开启第一章', exact=True).click()
        expect(page).to_have_url(BASE + '/login')
        page.wait_for_load_state('networkidle')
        expect(page.get_by_role('link', name='忘记密码？', exact=True)).to_have_count(0)
        for route in ('/forgot-password', '/reset-password', '/account/verify-email'):
            page.goto(BASE + route + ('#token=' + 'A' * 43 if route != '/forgot-password' else ''))
            page.wait_for_load_state('networkidle')
            expect(page.get_by_role('status')).to_have_text('邮箱验证与邮件找回密码暂未启用。')
            expect(page.locator('form')).to_have_count(0)
            assert page.evaluate('location.hash') == ''
        checks.append('Disabled mail hides recovery entry and all direct email routes show an unavailable notice')
        page.goto(BASE + '/login')
        page.wait_for_load_state('networkidle')
        page.get_by_label('用户名', exact=True).fill('browser_' + RUN)
        page.get_by_label('密码', exact=True).fill(PASSWORD)
        page.get_by_role('button', name='回到书房', exact=True).click()
        expect(page).to_have_url(BASE + '/')
        checks.append('Browser registration and login work against the packaged backend')
        token = page.evaluate('localStorage.getItem("blog_token")')
        page.get_by_role('link', name='账号安全', exact=True).click()
        expect(page.get_by_role('heading', name='修改密码', exact=True)).to_be_visible()
        expect(page.get_by_role('heading', name='登录会话', exact=True)).to_be_visible()
        expect(page.get_by_role('heading', name='验证邮箱', exact=True)).to_have_count(0)
        assert api('/api/user/security', token=token)[2]['data']['verifiedEmailRequiredForPublication'] is False
        page.screenshot(path=str(OUTPUT / 'personal-security.png'), full_page=True, animations='disabled')
        page.set_viewport_size({'width': 390, 'height': 844})
        page.screenshot(path=str(OUTPUT / 'personal-security-mobile.png'), full_page=True, animations='disabled')
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
        page.set_viewport_size({'width': 1280, 'height': 900})
        checks.append('Personal account security keeps password and session controls while hiding email verification')

        page.get_by_role('link', name='写一页', exact=True).click()
        page.get_by_label('正文', exact=True).fill('<script>alert("xss")</script>\n这是私有日记。')
        page.get_by_role('button', name='存下这一页', exact=True).click()
        expect(page.get_by_role('heading', name='<script>alert("xss")</script>', exact=True)).to_be_visible()
        article_id = int(page.url.rsplit('/', 1)[1])
        checks.append('Private writing still works and HTML is displayed as literal text')
        publication = '/api/article/' + str(article_id) + '/publication'
        assert api(publication, 'PUT', {'published': True, 'category': '日常'}, token)[0] == 200
        assert api('/api/plaza/articles/' + str(article_id))[0] == 200
        assert api(publication, 'PUT', {'published': False}, token)[0] == 200
        checks.append('Without configured email, publication and withdrawal still work with the default settings')

        api('/api/user/register', 'POST', {'username': 'other_' + RUN, 'password': PASSWORD})
        status, _, result = api('/api/user/login', 'POST', {'username': 'other_' + RUN, 'password': PASSWORD})
        assert status == 200
        other_token = result['data']
        assert api('/api/article/detail/' + str(article_id), token=other_token)[0] == 404
        assert api('/api/plaza/articles/' + str(article_id))[0] == 404
        checks.append('Other accounts and anonymous plaza readers cannot access a private diary')

        second_tab = context.new_page()
        second_tab.goto(BASE + '/')
        second_tab.wait_for_load_state('networkidle')
        page.route('**/api/user/logout', lambda route: route.abort('failed'))
        page.get_by_role('button', name='退出登录', exact=False).click()
        expect(page.get_by_role('alert')).to_contain_text('退出暂未完成')
        assert page.evaluate('localStorage.getItem("blog_token")') == token
        assert api('/api/user/me', token=token)[0] == 200
        checks.append('Failed logout keeps the session and displays a retry message')
        page.unroute('**/api/user/logout')
        page.get_by_role('button', name='退出登录', exact=False).click()
        expect(page).to_have_url(BASE + '/login')
        expect(second_tab).to_have_url(BASE + '/login?redirect=/')
        assert page.evaluate('localStorage.getItem("blog_token")') is None
        assert api('/api/user/me', token=token)[0] == 401
        assert api('/user/me', token=token)[0] == 401
        assert api('/api/user/me', token=other_token)[0] == 200
        page.goto(BASE + '/article/' + str(article_id))
        expect(page).to_have_url(BASE + '/login?redirect=/article/' + str(article_id))
        checks.append('Successful logout clears both tabs and blocks token replay on both API aliases')
        page.screenshot(path=str(OUTPUT / 'logout.png'), full_page=True)
        assert not errors, errors
        checks.append('No browser runtime errors with the content security policy enabled')
        browser.close()

    status, headers, _ = api('/api/user/login', 'POST', {'padding': 'x' * (1024 * 1024)})
    assert status == 413
    assert headers.get('X-Content-Type-Options') == 'nosniff'
    checks.append('Oversized JSON is rejected with HTTP 413 before DTO validation')
    connection = http.client.HTTPConnection('127.0.0.1', PORT, timeout=20)
    try:
        connection.request('POST', '/api/user/login', body=iter([b'x' * 65536] * 17),
                           headers={'Content-Type': 'application/json'}, encode_chunked=True)
        response = connection.getresponse()
        assert response.status == 413
        response.read()
    finally:
        connection.close()
    checks.append('Chunked bodies without Content-Length cannot bypass the byte limit')

    statuses = []
    for i in range(21):
        status, headers, _ = api(('/api' if i % 2 == 0 else '') + '/user/login', 'POST',
                                 {'username': 'missing_' + str(i), 'password': PASSWORD})
        statuses.append(status)
        if status == 429:
            assert int(headers['Retry-After']) > 0
            break
    assert statuses[-1] == 429 and all(status == 401 for status in statuses[:-1])
    checks.append('Login throttling returns HTTP 429 and Retry-After across API aliases')

    stop_server(process, log)
    process, log = start_server()
    assert api('/api/user/me', token=token)[0] == 401
    assert api('/api/user/me', token=other_token)[0] == 200
    checks.append('After a real JVM restart, revoked sessions stay revoked and active sessions remain valid')
    (OUTPUT / 'result.json').write_text(json.dumps({'checks': checks, 'count': len(checks)}, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'checks': checks, 'count': len(checks)}, ensure_ascii=True, indent=2))
finally:
    stop_server(process, log)
