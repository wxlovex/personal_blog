"""Verify the packaged, same-origin blog against a disposable local database only."""
import json
import os
import time
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

BASE = 'http://127.0.0.1:18080'
OUTPUT = Path(__file__).resolve().parents[1] / 'target' / 'browser-test'
OUTPUT.mkdir(parents=True, exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch(
        executable_path=os.environ['BROWSER_TEST_CHROME'], headless=True)
    context = browser.new_context()
    page = context.new_page()
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    username = 'browser_' + str(int(time.time()))
    password = 'local-browser-test-password'
    page.goto(BASE + '/register')
    page.wait_for_load_state('networkidle')
    page.get_by_label('Username').fill(username)
    page.get_by_label('Email').fill('browser@example.com')
    page.get_by_label('Password', exact=True).fill(password)
    page.get_by_role('button', name='Register', exact=True).click()
    expect(page).to_have_url(BASE + '/login')

    page.get_by_label('Username').fill(username)
    page.get_by_label('Password', exact=True).fill('incorrect-password')
    page.get_by_role('button', name='Login', exact=True).click()
    expect(page.locator('.error-msg')).to_contain_text('用户名或密码错误')
    page.get_by_label('Password', exact=True).fill(password)
    page.get_by_role('button', name='Login', exact=True).click()
    expect(page).to_have_url(BASE + '/')

    page.get_by_role('link', name='Write', exact=True).click()
    page.get_by_label('Title', exact=True).fill('Deployment browser check')
    page.get_by_label('Content', exact=True).fill('Article body and summary are visible.')
    page.get_by_role('button', name='Publish', exact=True).click()
    expect(page.get_by_role('heading', name='Deployment browser check')).to_be_visible()
    expect(page.locator('.article-excerpt').first).to_contain_text('Article body and summary')
    page.get_by_role('link', name='Deployment browser check').click()
    expect(page.locator('.article-content')).to_contain_text('Article body and summary')
    article_id = int(page.url.rsplit('/', 1)[1])
    page.reload()
    page.wait_for_load_state('networkidle')
    expect(page.get_by_role('heading', name='Deployment browser check')).to_be_visible()
    page.get_by_placeholder('Share your thoughts...').fill('Browser comment')
    page.get_by_role('button', name='Post Comment').click()
    expect(page.locator('.comment-content')).to_contain_text('Browser comment')

    token = page.evaluate("localStorage.getItem('blog_token')")
    headers = {'Authorization': 'Bearer ' + token}
    for i in range(10):
        response = context.request.post(BASE + '/api/comment/publish', headers=headers,
            data={'articleId': article_id, 'content': 'Pagination comment ' + str(i)})
        assert response.ok, response.status
    page.reload()
    expect(page.locator('.comment-item')).to_have_count(10)
    page.get_by_role('button', name='Load more comments').click()
    expect(page.locator('.comment-item')).to_have_count(11)
    page.evaluate('window.scrollTo(0, 0)')
    page.screenshot(path=str(OUTPUT / 'article.png'), full_page=True)

    other = browser.new_context()
    other_username = username + '_other'
    assert other.request.post(BASE + '/api/user/register',
        data={'username': other_username, 'password': password}).ok
    other_token = other.request.post(BASE + '/api/user/login',
        data={'username': other_username, 'password': password}).json()['data']
    other_page = other.new_page()
    other_page.goto(BASE + '/login')
    other_page.evaluate('(token) => localStorage.setItem("blog_token", token)', other_token)
    other_page.goto(BASE + '/article/' + str(article_id))
    other_page.wait_for_load_state('networkidle')
    expect(other_page.get_by_role('heading', name='Deployment browser check')).to_be_visible()
    expect(other_page.get_by_role('button', name='Delete', exact=True)).to_have_count(0)
    assert other.request.delete(BASE + '/api/article/' + str(article_id),
        headers={'Authorization': 'Bearer ' + other_token}).status == 403
    assert other.request.post(BASE + '/api/article/publish',
        data={'title': 'unauthorized', 'content': 'unauthorized'}).status == 401
    assert other.request.get(BASE + '/api/not-found').status == 404
    assert other.request.get(BASE + '/assets/not-found.js').status == 404

    page.once('dialog', lambda dialog: dialog.accept())
    page.get_by_role('button', name='Delete', exact=True).click()
    expect(page).to_have_url(BASE + '/')
    expect(page.get_by_role('heading', name='Deployment browser check')).to_have_count(0)
    assert not errors, errors
    (OUTPUT / 'result.json').write_text(json.dumps({
        'status': 'passed', 'pageErrors': errors,
        'checks': ['registration', 'invalid login', 'login', 'publish', 'summary',
            'detail refresh', 'comment', 'comment pagination', 'ownership UI',
            '403 ownership', '401 authentication', 'API and asset 404', 'delete']
    }, indent=2), encoding='utf-8')
    other.close()
    context.close()
    browser.close()
    print('Packaged blog browser smoke checks passed.')
