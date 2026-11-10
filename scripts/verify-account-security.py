"""Second-batch acceptance: packaged UI + disposable H2 + loopback SMTP.

All mail stays in memory on this machine. Does not connect to business databases or external mail services.
"""
import email
from email import policy
import json
import os
from pathlib import Path
import re
import shutil
import socket
import socketserver
import subprocess
import threading
import time
import uuid
from urllib.error import HTTPError
from urllib.parse import urlparse
from urllib.request import Request, urlopen
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'target' / 'account-security-browser'
OUT.mkdir(parents=True, exist_ok=True)
PORT = 18082
BASE = f'http://127.0.0.1:{PORT}'
RUN = uuid.uuid4().hex[:10]
USER = 'account_' + RUN
ADDRESS = USER + '@example.test'
PASSWORD = 'initial-browser-password'
CHANGED = 'changed-browser-password'
RESET = 'recovered-browser-password'
checks = []
received = []
mail_lock = threading.Lock()


class SMTPHandler(socketserver.StreamRequestHandler):
    def handle(self):
        self.request.settimeout(10)
        self.wfile.write(b'220 localhost local-test-smtp\r\n')
        while True:
            line = self.rfile.readline(8192)
            if not line:
                return
            command = line.split(b' ', 1)[0].strip().upper()
            if command in (b'EHLO', b'HELO'):
                self.wfile.write(b'250-localhost\r\n250 SIZE 1048576\r\n')
            elif command == b'DATA':
                self.wfile.write(b'354 End data with dot\r\n')
                data = bytearray()
                while True:
                    part = self.rfile.readline(1048577)
                    if not part or part == b'.\r\n':
                        break
                    data.extend(part[1:] if part.startswith(b'..') else part)
                    if len(data) > 1048576:
                        raise RuntimeError('Test SMTP message exceeded limit')
                message = email.message_from_bytes(bytes(data), policy=policy.default)
                with mail_lock:
                    received.append(message)
                self.wfile.write(b'250 queued locally\r\n')
            elif command == b'QUIT':
                self.wfile.write(b'221 closing\r\n')
                return
            else:
                self.wfile.write(b'250 OK\r\n')


class SMTPServer(socketserver.ThreadingTCPServer):
    daemon_threads = True
    allow_reuse_address = True


def mail_link(subject):
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        with mail_lock:
            matches = [item for item in received if subject in str(item['Subject']) and ADDRESS in str(item['To'])]
        if matches:
            body = matches[-1].get_content()
            match = re.search(r'http://127\.0\.0\.1:18082/[^\s]+#token=[A-Za-z0-9_-]{43}', body)
            assert match, 'Expected a local single-use email link'
            return match.group()
        time.sleep(.1)
    raise AssertionError('Expected email did not arrive at the local SMTP service')


def api(path, method='GET', data=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    request = Request(BASE + path, method=method, headers=headers,
                      data=None if data is None else json.dumps(data, ensure_ascii=False).encode())
    try:
        response = urlopen(request, timeout=20)
    except HTTPError as failure:
        response = failure
    with response:
        return response.status, json.loads(response.read())


def start():
    h2 = sorted((ROOT / 'target/maven-repository/com/h2database/h2').glob('*/*[0-9].jar'))[-1]
    jar = ROOT / 'target/personal_blog-0.0.1-SNAPSHOT.jar'
    command = [shutil.which('java'), '-Duser.timezone=UTC', f'-Dloader.path={h2}', '-cp', str(jar),
               'org.springframework.boot.loader.launch.PropertiesLauncher', '--server.address=127.0.0.1', f'--server.port={PORT}',
               # This script terminates the JVM on Windows. Flush commits synchronously so
               # the test models a separately persisted production database, not H2's 500ms write cache.
               f'--spring.datasource.url=jdbc:h2:file:{OUT.as_posix()}/db-{RUN};MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER,COMMENT;WRITE_DELAY=0',
               '--spring.datasource.username=sa', '--spring.datasource.password=', '--spring.sql.init.mode=always',
               '--spring.sql.init.schema-locations=classpath:db/schema.sql', '--blog.jwt.secret=local-account-test-only-secret-at-least-32-bytes',
               '--blog.mail.enabled=true', '--blog.mail.from=noreply@example.test', f'--blog.mail.public-base-url={BASE}',
               '--spring.mail.host=127.0.0.1', f'--spring.mail.port={smtp.server_address[1]}',
               '--spring.mail.username=', '--spring.mail.password=', '--spring.mail.properties.mail.smtp.starttls.enable=false',
               '--spring.mail.properties.mail.smtp.starttls.required=false', '--spring.mail.properties.mail.smtp.ssl.enable=false',
               '--blog.security.max-storage-bytes=256', '--blog.security.max-articles=2',
               '--blog.security.require-verified-email-for-publication=true']
    stream = (OUT / 'runtime.log').open('ab')
    process = subprocess.Popen(command, cwd=ROOT, stdout=stream, stderr=stream,
                               creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    try:
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            if process.poll() is not None:
                raise RuntimeError('Local application exited; inspect the test runtime log')
            try:
                if api('/api/health')[0] == 200:
                    return process, stream
            except (OSError, ValueError):
                pass
            time.sleep(.25)
        raise RuntimeError('Local test application did not become ready')
    except BaseException:
        stop(process, stream)
        raise


def stop(process, stream):
    process.terminate()
    try:
        process.wait(timeout=15)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=10)
    stream.close()


def login_page(page, password):
    page.goto(BASE + '/login')
    page.wait_for_load_state('networkidle')
    page.get_by_label('用户名', exact=True).fill(USER)
    page.get_by_label('密码', exact=True).fill(password)
    page.get_by_role('button', name='回到书房', exact=True).click()
    expect(page).to_have_url(BASE + '/')
    return page.evaluate('localStorage.getItem("blog_token")')


with socket.socket() as probe:
    if probe.connect_ex(('127.0.0.1', PORT)) == 0:
        raise RuntimeError(f'Port {PORT} is occupied; existing services will not be changed')
smtp = SMTPServer(('127.0.0.1', 0), SMTPHandler)
threading.Thread(target=smtp.serve_forever, daemon=True).start()
process, stream = start()
try:
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True, executable_path=os.environ.get('BROWSER_TEST_CHROME',
                'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'))
        context = browser.new_context(viewport={'width': 1280, 'height': 1000}, timezone_id='Asia/Shanghai', reduced_motion='reduce')
        page = context.new_page()
        errors = []
        page.on('pageerror', lambda failure: errors.append(str(failure)))
        page.goto(BASE + '/register')
        page.wait_for_load_state('networkidle')
        expect(page.get_by_label('用户名', exact=True)).to_be_visible()
        page.get_by_label('用户名', exact=True).fill(USER)
        page.get_by_label('邮箱').fill(ADDRESS)
        page.get_by_label('密码', exact=True).fill(PASSWORD)
        page.get_by_role('button', name='开启第一章', exact=True).click()
        expect(page).to_have_url(BASE + '/login')
        first = login_page(page, PASSWORD)
        second = api('/api/user/login', 'POST', {'username': USER, 'password': PASSWORD})[1]['data']
        checks.append('Existing registration and login remain compatible')

        page.get_by_role('link', name='写一页', exact=True).click()
        page.get_by_label('正文', exact=True).fill('私有书页😀')
        page.get_by_role('button', name='存下这一页', exact=True).click()
        expect(page.get_by_role('heading', name='私有书页😀', exact=True)).to_be_visible()
        article = int(page.url.rsplit('/', 1)[1])
        status, result = api('/api/article/publish', 'POST', {'title': 'two', 'content': 'second'}, first)
        assert status == 200
        second_article = result['data']['id']
        assert api('/api/article/publish', 'POST', {'content': 'over quota'}, first)[0] == 409
        assert api('/api/article/' + str(article), 'PUT', {'content': 'x' * 300}, first)[0] == 409
        assert api('/api/article/' + str(second_article), 'DELETE', token=first)[0] == 200
        assert api('/api/article/publish', 'POST', {'content': 'space released'}, first)[0] == 200
        checks.append('Byte and article quotas reject growth and deletion releases capacity')
        assert api('/api/article/' + str(article) + '/publication', 'PUT', {'published': True, 'category': '技术'}, first)[0] == 403
        checks.append('Unverified users can write privately but cannot publish to the plaza')

        page.get_by_role('link', name='账号安全', exact=True).click()
        expect(page.get_by_role('heading', name='我的书架', exact=True)).to_be_visible()
        expect(page.get_by_role('heading', name='验证邮箱', exact=True)).to_be_visible()
        page.screenshot(path=str(OUT / 'security-desktop.png'), full_page=True)
        page.get_by_label('验证邮箱时的当前密码', exact=True).fill('wrong-password')
        page.get_by_role('button', name='发送验证邮件', exact=True).click()
        expect(page.get_by_role('alert')).to_contain_text('当前密码不正确')
        assert page.evaluate('localStorage.getItem("blog_token")') == first
        page.get_by_label('验证邮箱时的当前密码', exact=True).fill(PASSWORD)
        page.get_by_role('button', name='发送验证邮件', exact=True).click()
        expect(page.get_by_role('status')).to_contain_text('验证请求已受理')
        verify_link = mail_link('验证邮箱')
        assert urlparse(verify_link).hostname == '127.0.0.1'
        page.goto(verify_link)
        page.wait_for_load_state('networkidle')
        assert '#token=' not in page.url
        page.get_by_role('button', name='确认验证邮箱', exact=True).click()
        expect(page.get_by_role('status')).to_contain_text('邮箱已验证')
        for token in (first, second):
            assert api('/api/user/me', token=token)[0] == 401
        assert api('/api/user/email/confirm', 'POST', {'token': verify_link.split('#token=')[1]})[0] == 400
        checks.append('Real local SMTP verification requires the password, binds the email once, and revokes sessions')

        verified = login_page(page, PASSWORD)
        assert api('/api/user/me', token=verified)[1]['data']['emailVerified'] is True
        assert api('/api/article/' + str(article) + '/publication', 'PUT', {'published': True, 'category': '技术'}, verified)[0] == 200
        checks.append('Verified accounts can publish and the public projection remains available')
        page.goto(BASE + '/account/security')
        page.wait_for_load_state('networkidle')
        page.get_by_label('当前密码', exact=True).fill(PASSWORD)
        page.get_by_label('新密码', exact=True).fill(CHANGED)
        page.get_by_label('再次输入新密码', exact=True).fill(CHANGED)
        page.get_by_role('button', name='修改密码并退出所有设备', exact=True).click()
        expect(page).to_have_url(BASE + '/login?notice=password-changed')
        assert api('/api/user/me', token=verified)[0] == 401
        assert api('/api/user/login', 'POST', {'username': USER, 'password': PASSWORD})[0] == 401
        changed = login_page(page, CHANGED)
        checks.append('Browser password changes invalidate old credentials and require a fresh login')

        page.goto(BASE + '/forgot-password')
        page.wait_for_load_state('networkidle')
        page.get_by_label('已验证的邮箱', exact=True).fill(ADDRESS)
        page.get_by_role('button', name='发送重置邮件', exact=True).click()
        expect(page.get_by_role('status')).to_contain_text('如果该邮箱已验证')
        reset_link = mail_link('重置密码')
        assert api('/api/user/password/forgot', 'POST', {'email': 'unknown_' + RUN + '@example.test'})[0] == 200
        page.goto(reset_link)
        page.wait_for_load_state('networkidle')
        assert '#token=' not in page.url
        page.get_by_label('新密码', exact=True).fill(RESET)
        page.get_by_label('再次输入新密码', exact=True).fill(RESET)
        page.get_by_role('button', name='重置密码', exact=True).click()
        expect(page.get_by_role('status')).to_contain_text('密码已重置')
        assert api('/api/user/me', token=changed)[0] == 401
        assert api('/api/user/password/reset', 'POST', {'token': reset_link.split('#token=')[1], 'newPassword': PASSWORD})[0] == 400
        assert api('/api/user/login', 'POST', {'username': USER, 'password': CHANGED})[0] == 401
        recovered = login_page(page, RESET)
        checks.append('Forgot-password mail reaches local SMTP, resets the password once, and invalidates old sessions')

        page.goto(BASE + '/account/security')
        page.wait_for_load_state('networkidle')
        page.set_viewport_size({'width': 320, 'height': 740})
        assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
        page.set_viewport_size({'width': 390, 'height': 844})
        expect(page.get_by_role('heading', name='登录会话', exact=True)).to_be_visible()
        assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
        page.screenshot(path=str(OUT / 'security-mobile.png'), full_page=True)
        page.emulate_media(color_scheme='dark')
        page.screenshot(path=str(OUT / 'security-dark.png'), full_page=True)
        checks.append('Account security renders on desktop, narrow mobile, and dark mode without horizontal overflow')
        another = api('/api/user/login', 'POST', {'username': USER, 'password': RESET})[1]['data']
        page.get_by_label('退出所有设备时的当前密码', exact=True).fill(RESET)
        page.get_by_role('button', name='退出所有设备', exact=True).click()
        expect(page).to_have_url(BASE + '/login?notice=all-sessions-ended')
        for token in (recovered, another):
            assert api('/api/user/me', token=token)[0] == 401
        checks.append('All-device logout ends every session through the browser')
        assert not errors, errors
        checks.append('No browser runtime errors in the new account and recovery pages')
        browser.close()

    status, response = api('/api/user/login', 'POST', {'username': USER, 'password': RESET})
    assert status == 200, 'A fresh session must be issued before the restart check'
    active = response['data']
    stop(process, stream)
    process, stream = start()
    status, response = api('/api/user/me', token=active)
    assert status == 200, f'Persisted session rejected after restart: HTTP {status}, {response.get("message", "")}'
    assert api('/api/user/me', token=recovered)[0] == 401
    assert api('/api/user/security', token=active)[1]['data']['emailVerified'] is True
    checks.append('A real JVM restart preserves verified email and revoked/active session states')
    (OUT / 'result.json').write_text(json.dumps({'checks': checks, 'count': len(checks)}, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'checks': checks, 'count': len(checks)}, ensure_ascii=True, indent=2))
finally:
    stop(process, stream)
    smtp.shutdown()
    smtp.server_close()
