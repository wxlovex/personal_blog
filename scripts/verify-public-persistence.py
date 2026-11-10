"""Create and then remove an isolated article around an independently verified service restart."""
import argparse
import json
import secrets
import urllib.request
import urllib.error
import time
from pathlib import Path

root = Path(__file__).resolve().parents[1]
base = 'https://bounce-club.onrender.com'
report = root / 'target/render-persistence.json'
parser = argparse.ArgumentParser()
parser.add_argument('action', choices=['prepare', 'verify'])
args = parser.parse_args()
smoke = json.loads((root / 'target/browser-test/result.json').read_text())
assert smoke['status'] == 'passed' and smoke['baseUrl'] == base
username = smoke['testUsers'][0]
assert username.startswith('browser_')

def api(path, method='GET', data=None, token=None):
    headers = {'Content-Type': 'application/json', 'User-Agent': 'personal-blog-verification/1.0'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    request = urllib.request.Request(base + path, method=method, headers=headers,
        data=None if data is None else json.dumps(data).encode())
    with urllib.request.urlopen(request, timeout=40) as response:
        return json.load(response)

if args.action == 'verify':
    deadline = time.monotonic() + 180
    while True:
        try:
            if api('/api/health').get('status') == 'UP':
                break
        except (urllib.error.URLError, OSError, ValueError):
            pass
        if time.monotonic() >= deadline:
            raise RuntimeError('The restarted service did not become healthy within three minutes.')
        time.sleep(2)
token = api('/api/user/login', 'POST', {'username': username, 'password': 'local-browser-test-password'})['data']
if args.action == 'prepare':
    marker = 'Deployment persistence check ' + secrets.token_hex(12)
    article = api('/api/article/publish', 'POST', {'title': 'Deployment persistence check', 'content': marker}, token)['data']
    other_token = api('/api/user/login', 'POST', {'username': smoke['testUsers'][1],
        'password': 'local-browser-test-password'})['data']
    try:
        api('/api/article/detail/' + str(article['id']), token=other_token)
        raise AssertionError('Another account can read a private diary')
    except urllib.error.HTTPError as error:
        assert error.code == 404
    for path in ('/api/article/ranking', '/api/article/subscriptions', '/api/user/subscriptions'):
        try:
            api(path, token=token)
            raise AssertionError('A retired social endpoint is still available')
        except urllib.error.HTTPError as error:
            assert error.code == 410
    report.write_text(json.dumps({'articleId': article['id'], 'ownerId': article['userId'],
        'marker': marker, 'privacyChecks': 'passed', 'status': 'waiting-for-restart'}, indent=2))
    print('Isolated persistence article prepared; waiting for a verified Render restart.')
else:
    record = json.loads(report.read_text())
    article = api('/api/article/detail/' + str(record['articleId']), token=token)['data']
    assert article['content'] == record['marker'] and article['userId'] == record['ownerId']
    api('/api/article/' + str(record['articleId']), 'DELETE', token=token)
    record['status'] = 'passed'
    record['testArticleRemoved'] = True
    report.write_text(json.dumps(record, indent=2))
    print('Article retained across the verified service restart; test article removed.')
