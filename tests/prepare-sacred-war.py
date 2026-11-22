"""Acquire a public recording for an end-to-end audio demo. Never write lyrics to disk."""
import json
import os
from pathlib import Path
import subprocess
from urllib.parse import quote, urlencode
from urllib.request import Request, urlopen

USER_AGENT = 'VisionMod/2.0.0 World Lyrics demo (https://github.com/De1po3oN/VisionMod-2.0.0)'
LIMIT = 16 * 1024 * 1024
ROOT = Path(os.environ['RUNNER_TEMP']) / 'sacred-war'
ROOT.mkdir(exist_ok=True)


def fetch(url, limit=LIMIT):
    with urlopen(Request(url, headers={'User-Agent': USER_AGENT}), timeout=10) as response:
        result = response.read(limit + 1)
    if len(result) > limit:
        raise ValueError('Response exceeds size limit')
    return result


def api(url, **params):
    return json.loads(fetch(url + '?' + urlencode(params), 1024 * 1024))


def archive_item(identifier, label, album=False):
    page = json.loads(fetch('https://archive.org/metadata/' + identifier, 1024 * 1024))
    formats = {'VBR MP3', 'MP3', 'Ogg Vorbis', 'FLAC', 'WAVE', 'MPEG4', 'Apple Audio Lossless'}
    for item in page.get('files', []):
        name = item.get('name', '').casefold()
        known_track = any(term in name for term in ('sacred war', 'sacred_war', 'svyashchenn', 'holy war', 'священная'))
        if (not album or known_track) and item.get('format') in formats and 0 < int(item.get('size', 0)) <= LIMIT:
            yield 'https://archive.org/download/' + identifier + '/' + quote(item['name']), label


def inspect(source, label):
    original = ROOT / 'original-audio'
    original.write_bytes(fetch(source))
    probe = json.loads(subprocess.check_output([
        'ffprobe', '-v', 'error', '-select_streams', 'a:0', '-show_format', '-show_streams', '-of', 'json', original
    ], timeout=8))
    if not probe.get('streams'):
        raise ValueError('Recording has no audio stream')
    tags = {k.lower(): v for entry in (probe['format'], *probe['streams']) for k, v in entry.get('tags', {}).items()}
    return original, {'source': source, 'recording': label, 'duration': float(probe['format']['duration']),
                      'artistTag': tags.get('artist', ''), 'titleTag': tags.get('title', ''), 'albumTag': tags.get('album', '')}


tracks = []
for title in ('The Sacred War', 'Священная война'):
    for entry in api('https://lrclib.net/api/search', q=title):
        if entry.get('trackName', '').casefold() == title.casefold() and entry.get('syncedLyrics'):
            tracks.append({k: entry[k] for k in ('id', 'trackName', 'artistName', 'albumName', 'duration')})

sources = [('https://upload.wikimedia.org/wikipedia/commons/c/cd/The_Sacred_War.ogg', 'Red Army Choir recording on Wikimedia Commons')]
for identifier in ('piste-02_202011',):
    try:
        sources.extend(archive_item(identifier, 'A. Alexandrov Red Army Choir recording on Internet Archive'))
    except Exception as error:
        print(json.dumps({'id': identifier, 'error': str(error)}, ensure_ascii=False), flush=True)
try:
    result = api('https://archive.org/advancedsearch.php',
                 q='(title:"red army choir" OR title:"alexandrov ensemble") AND mediatype:audio',
                 output='json', rows=24, **{'fl[]': 'identifier'})
    for entry in result.get('response', {}).get('docs', []):
        try:
            sources.extend(archive_item(entry['identifier'], 'Red Army Choir album on Internet Archive', album=True))
        except Exception as error:
            print(json.dumps({'id': entry['identifier'], 'error': str(error)}, ensure_ascii=False), flush=True)
        if len(sources) >= 8:
            break
except Exception as error:
    print(json.dumps({'service': 'Internet Archive', 'error': str(error)}, ensure_ascii=False), flush=True)

for url, label in sources[:8]:
    try:
        original, report = inspect(url, label)
    except Exception as error:
        print(json.dumps({'source': url, 'error': str(error)}, ensure_ascii=False), flush=True)
        continue
    # Independent metadata establishes the performance attribution; duration separates common recordings.
    # Do not select a singer, cover, live release, instrumental, or remixed variant by title alone.
    choir = ('The Red Army Choir', 'The Alexandrov Red Army Chorus', 'Ансамбль песни и пляски Российской Армии им. А.В. Александрова')
    matches = [t for t in tracks if t['artistName'] in choir and 'live' not in t['albumName'].casefold()
               and abs(t['duration']-report['duration']) <= 2]
    report['nearbyTimedCatalogEntries'] = matches
    print(json.dumps(report, ensure_ascii=False), flush=True)
    if not matches:
        continue
    tag_artist = report['artistTag'].casefold()
    tag_title = report['titleTag'].casefold()
    match = min(matches, key=lambda t: (t['artistName'].casefold() != tag_artist,
                t['trackName'].casefold() != tag_title, abs(t['duration']-report['duration'])))
    report['catalogMetadata'] = match
    # VLC publishes ordinary display tags to MPRIS. The source audio is copied without re-encoding.
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', original, '-map', '0:a:0',
                    '-c:a', 'copy', '-map_metadata', '-1', '-metadata', 'title=' + match['trackName'],
                    '-metadata', 'artist=' + match['artistName'], ROOT/'recording.mka'], timeout=12, check=True)
    (ROOT/'recording.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    break
else:
    raise SystemExit('No public choir recording matched available timed lyrics closely enough')
