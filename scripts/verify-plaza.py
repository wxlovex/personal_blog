"""Exercise opt-in publication, search and withdrawal on an isolated local test app."""
import json
import os
import time
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('BLOG_TEST_BASE_URL', 'http://127.0.0.1:18080').rstrip('/')
assert urlparse(BASE).hostname in ('localhost', '127.0.0.1')
OUTPUT = Path(__file__).resolve().parents[1] / 'target' / 'plaza-review'
OUTPUT.mkdir(parents=True, exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=os.environ['BROWSER_TEST_CHROME'], headless=True)
    author_context = browser.new_context(reduced_motion='reduce')
    reader_context = browser.new_context(reduced_motion='reduce')
    author = author_context.new_page()
    reader = reader_context.new_page()
    errors = []
    for page in (author, reader):
        page.on('pageerror', lambda error: errors.append(str(error)))
    stamp = str(time.time_ns())
    prefix = '广场验收' + stamp[-10:]
    checks = []

    def account(suffix):
        payload = {'username': 'plaza_' + stamp + suffix, 'password': 'local-plaza-test-password'}
        assert author_context.request.post(BASE + '/api/user/register', data=payload).ok
        token = author_context.request.post(BASE + '/api/user/login', data=payload).json()['data']
        return payload, {'Authorization': 'Bearer ' + token}

    alice, alice_headers = account('a')
    bob, bob_headers = account('b')

    def api(method, path, data=None, auth=None):
        response = author_context.request.fetch(BASE + '/api' + path, method=method, headers=auth or {}, data=data)
        assert response.ok, (path, response.status, response.text())
        return response.json()['data']

    def login(page, credentials):
        page.goto(BASE + '/login')
        page.get_by_label('用户名', exact=True).fill(credentials['username'])
        page.get_by_label('密码', exact=True).fill(credentials['password'])
        page.get_by_role('button', name='回到书房', exact=True).click()
        expect(page).to_have_url(BASE + '/')
        page.wait_for_load_state('networkidle')

    try:
        category = api('POST', '/category', {'name': '不能公开的私人分类'}, alice_headers)['id']
        login(author, alice)
        author.goto(BASE + '/publish')
        author.wait_for_load_state('networkidle')
        title = prefix + ' 我的发现'
        body = '这是可以分享的文章正文。\n\n<script>window.publicScriptRan = true</script>'
        author.locator('#publish-title').fill(title)
        author.locator('#publish-content').fill(body)
        author.locator('#publish-category').select_option(str(category))
        author.get_by_role('button', name='存下这一页', exact=True).click()
        expect(author.get_by_role('heading', name=title, exact=True)).to_be_visible()
        article_id = int(author.url.rsplit('/', 1)[-1])
        api('POST', '/comment/publish', {'articleId': article_id, 'content': '不能公开的补记'}, alice_headers)
        assert api('GET', '/article/detail/' + str(article_id), auth=alice_headers)['published'] is False
        assert api('GET', '/plaza/articles?keyword=' + prefix)['total'] == 0
        assert reader_context.request.get(BASE + f'/api/plaza/articles/{article_id}').status == 404
        reader.goto(BASE + '/plaza')
        reader.wait_for_load_state('networkidle')
        for name in ('技术', '日常', '读书', '娱乐'):
            expect(reader.get_by_role('button', name=name, exact=True)).to_be_visible()
        checks.append('new entries remain private; guests see four fixed plaza categories and cannot read private IDs')

        author.get_by_role('button', name='发布到广场', exact=True).click()
        publication = author.get_by_role('dialog', name='发布到广场', exact=True)
        expect(publication.get_by_label('广场分类', exact=True)).to_be_enabled()
        assert publication.get_by_label('广场分类', exact=True).evaluate('(select) => !select.checkValidity()')
        publication.get_by_role('button', name='取消', exact=True).click()
        assert api('GET', '/plaza/articles?keyword=' + prefix)['total'] == 0
        author.get_by_role('button', name='发布到广场', exact=True).click()
        publication.get_by_label('广场分类', exact=True).select_option('技术')
        def fail_publication(route):
            route.fulfill(status=503, content_type='application/json', body=json.dumps({'message': '暂时无法发布'}))
        author.route('**/api/article/*/publication', fail_publication)
        publication.get_by_role('button', name='确认发布', exact=True).click()
        expect(publication.get_by_role('alert')).to_contain_text('暂时无法发布')
        assert api('GET', '/article/detail/' + str(article_id), auth=alice_headers)['published'] is False
        author.unroute('**/api/article/*/publication', fail_publication)
        publication.get_by_role('button', name='确认发布', exact=True).click()
        expect(publication).not_to_be_visible()
        expect(author.get_by_text('已公开 · 技术', exact=True)).to_be_visible()
        reader.reload()
        reader.wait_for_load_state('networkidle')
        expect(reader.locator('.plaza-card')).to_contain_text(title)
        reader.get_by_role('link', name=title, exact=True).click()
        expect(reader.locator('.public-body')).to_contain_text('这是可以分享的文章正文')
        expect(reader.locator('.public-detail')).not_to_contain_text('不能公开的私人分类')
        expect(reader.locator('.public-detail')).not_to_contain_text('不能公开的补记')
        expect(reader.locator('.comment-section')).to_have_count(0)
        assert reader.evaluate('window.publicScriptRan') is None
        public = api('GET', '/plaza/articles/' + str(article_id))
        assert 'categoryId' not in public and 'categoryName' not in public
        checks.append('explicit publish confirmation, cancelled/failed publication stays private, public body excludes private categories/notes and escapes HTML')

        author.get_by_role('link', name='修改这一页', exact=True).click()
        expect(author.get_by_text('这篇文章已发布到广场。保存后，标题和正文会同步更新到公开页面。', exact=True)).to_be_visible()
        edited_body = '编辑后同步到广场的正文。'
        author.locator('#publish-content').fill(edited_body)
        author.get_by_role('button', name='保存并同步更新', exact=True).click()
        expect(author.get_by_role('heading', name=title, exact=True)).to_be_visible()
        reader.reload()
        expect(reader.locator('.public-body')).to_have_text(edited_body)
        author.get_by_role('button', name='广场设置', exact=True).click()
        settings = author.get_by_role('dialog', name='管理广场发布', exact=True)
        expect(settings.get_by_label('广场分类', exact=True)).to_be_enabled()
        settings.get_by_label('广场分类', exact=True).select_option('日常')
        settings.get_by_role('button', name='保存广场分类', exact=True).click()
        expect(settings).not_to_be_visible()
        own = api('GET', '/article/detail/' + str(article_id), auth=alice_headers)
        assert own['categoryId'] == category and own['plazaCategory'] == '日常'
        checks.append('published editing is clearly marked and synced; public category changes preserve the personal category')

        other_private = api('POST', '/article/publish', {'title': prefix + ' 私密文章', 'content': '别人的秘密'}, bob_headers)
        assert author_context.request.put(BASE + f'/api/article/{article_id}/publication', headers=bob_headers,
               data={'published': False}).status == 404
        for i in range(12):
            entry = api('POST', '/article/publish', {'title': f'{prefix} 技术 {i:02}', 'content': '第二位作者的公开文章'}, bob_headers)
            api('PUT', f'/article/{entry["id"]}/publication', {'published': True, 'category': '技术'}, bob_headers)
        body_only = api('POST', '/article/publish', {'title': '标题不匹配', 'content': prefix}, bob_headers)
        api('PUT', f'/article/{body_only["id"]}/publication', {'published': True, 'category': '技术'}, bob_headers)
        reader.goto(BASE + '/plaza')
        reader.wait_for_load_state('networkidle')
        reader.get_by_label('搜索广场文章标题', exact=True).fill(prefix)
        reader.get_by_role('button', name='搜索', exact=True).click()
        reader.get_by_role('button', name='技术', exact=True).click()
        expect(reader.get_by_text('12 篇公开文章 · 最新发布在前', exact=True)).to_be_visible()
        expect(reader.locator('.plaza-card')).to_have_count(10)
        reader.get_by_label('搜索广场文章标题', exact=True).fill('尚未提交的新关键词')
        reader.get_by_role('button', name='下一页', exact=True).click()
        expect(reader.get_by_text('第 2 / 2 页', exact=True)).to_be_visible()
        expect(reader.locator('.plaza-card')).to_have_count(2)
        expect(reader.locator('.plaza-articles')).not_to_contain_text('私密文章')
        expect(reader.locator('.plaza-articles')).not_to_contain_text('标题不匹配')
        reader.locator('.public-title').first.click()
        expect(reader.locator('.public-body')).to_be_visible()
        reader.get_by_role('link', name='返回广场', exact=True).click()
        expect(reader.get_by_text('第 2 / 2 页', exact=True)).to_be_visible()
        reader.get_by_label('搜索广场文章标题', exact=True).fill('不存在' + prefix)
        reader.get_by_role('button', name='搜索', exact=True).click()
        expect(reader.get_by_role('heading', name='没有找到符合条件的文章', exact=True)).to_be_visible()
        reader.get_by_role('button', name='查看全部文章', exact=True).click()
        expect(reader.locator('.plaza-card').first).to_contain_text('标题不匹配')
        checks.append('multi-author title-only search, category filtering, newest-publication order, paging, empty states and returning with filters preserved')

        login(reader, bob)
        expect(reader.locator('.article-list')).not_to_contain_text(title)
        assert reader_context.request.get(BASE + f'/api/article/detail/{article_id}', headers=bob_headers).status == 404
        reader.goto(BASE + '/plaza/' + str(article_id))
        expect(reader.locator('.public-body')).to_have_text(edited_body)
        author.get_by_role('button', name='广场设置', exact=True).click()
        settings.get_by_role('button', name='撤回为私密', exact=True).click()
        expect(settings).not_to_be_visible()
        expect(author.get_by_text('仅自己可见', exact=True)).to_be_visible()
        reader.reload()
        expect(reader.get_by_role('heading', name='这篇文章暂不可见', exact=True)).to_be_visible()
        assert api('GET', '/article/detail/' + str(article_id), auth=alice_headers)['content'] == edited_body
        assert reader_context.request.get(BASE + f'/api/plaza/articles/{article_id}').status == 404
        assert reader_context.request.get(BASE + f'/api/plaza/articles/{other_private["id"]}').status == 404
        checks.append('other users can only read shared routes; withdrawal immediately hides public reads while preserving the private original')

        reader.goto(BASE + '/plaza')
        reader.wait_for_load_state('networkidle')
        reader.evaluate('localStorage.setItem("blog_token", "expired-token")')
        public_auth = []
        reader.on('request', lambda request: public_auth.append(request.headers.get('authorization')) if '/api/plaza/' in request.url else None)
        reader.reload()
        reader.wait_for_load_state('networkidle')
        expect(reader).to_have_url(BASE + '/plaza')
        assert public_auth and not any(public_auth)
        reader.set_viewport_size({'width': 375, 'height': 812})
        assert reader.evaluate('document.documentElement.scrollWidth <= innerWidth')
        reader.screenshot(path=str(OUTPUT / 'plaza-mobile.png'), full_page=True)
        reader.set_viewport_size({'width': 320, 'height': 740})
        assert reader.evaluate('document.documentElement.scrollWidth <= innerWidth')
        reader.set_viewport_size({'width': 1280, 'height': 900})
        reader.screenshot(path=str(OUTPUT / 'plaza-desktop.png'), full_page=True)
        reader.emulate_media(color_scheme='dark')
        reader.screenshot(path=str(OUTPUT / 'plaza-dark.png'), full_page=True)
        author.get_by_role('button', name='发布到广场', exact=True).click()
        expect(publication.get_by_label('广场分类', exact=True)).to_be_enabled()
        author.set_viewport_size({'width': 375, 'height': 812})
        author.screenshot(path=str(OUTPUT / 'publication-dialog.png'), full_page=True)
        assert publication.evaluate('(element) => element.scrollWidth <= element.clientWidth')
        publication.get_by_role('button', name='取消', exact=True).click()
        assert not errors, errors
        checks.append('public browsing tolerates stale credentials, requests no token, and works on mobile and in dark mode without page errors')
        result = {'passed': len(checks), 'checks': checks, 'pageErrors': errors}
        (OUTPUT / 'result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps(result, ensure_ascii=False))
    finally:
        author_context.close(); reader_context.close(); browser.close()
