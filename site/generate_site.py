# -*- coding: utf-8 -*-
import datetime
import hashlib
import os
import pickle
import pprint
import sys
import time
from collections import OrderedDict
import jinja2

import humanfriendly

path = '/output/dist'
files = os.listdir(path)
publishdate = datetime.datetime.now().strftime("%Y-%m-%d")
if len(sys.argv) == 2:
    publishdate = sys.argv[1]

release_files = OrderedDict((
    ('mtk_suomi.img', {
        'type': 'garmin_map',
        'name': 'mtk_suomi.img',
        'description': u'''''',
        'size': None,
        'hash': None
    }),
    ('mtk_suomi_noparcel.img', {
        'type': 'garmin_map',
        'name': 'mtk_suomi_noparcel.img',
        'description': u'''Versio ilman kiintestörajoja''',
        'size': None,
        'hash': None
    }),
    ('mtk_suomi.exe', {
        'type': 'garmin_map',
        'name': 'mtk_suomi.exe',
        'description': u'''''',
        'size': None,
        'hash': None
    }),
    ('mtk_suomi_noparcel.exe', {
        'type': 'garmin_map',
        'name': 'mtk_suomi_noparcel.exe',
        'description': u'''Versio ilman kiintestörajoja''',
        'size': None,
        'hash': None
    }),
    ('mtk_suomi_osx.zip', {
        'type': 'garmin_map',
        'name': 'mtk_suomi_osx.zip',
        'description': u'''macOS BaseCamp yhteensopiva versio Garmin kartasta.''',
        'size': None,
        'hash': None
    }),
    ('mtk_suomi_noparcel_osx.zip', {
        'type': 'garmin_map',
        'name': 'mtk_suomi_eikr_osx.zip',
        'description': u'''Versio ilman kiintestörajoja. macOS BaseCamp yhteensopiva versio Garmin kartasta.''',
        'size': None,
        'hash': None
    }),
    ('mtk_suomi.map', {
        'type': 'mapsforge_map',
        'name': 'mtk_suomi.map',
        'default_style': 'peruskartta/peruskartta.zip',
        'description': u'''Mapsforge / Android-yhteensopiva versio<br/>
                            <a href="orux-map://jekku.hylly.org/kartat/%(date)s/%(name)s">Asenna kartta Oruxmapsiin</a><br/>
                            <a href="locus-actions://http/jekku.hylly.org/kartat/%(date)s/%(locus_file)s">Asenna kartta Locukseen</a>''',
        'size': None,
        'hash': None
    }),
    ('peruskartta.zip', {
        'type': 'mapsforge_style',
        'name': 'peruskartta.zip',
        'description': u'''Peruskartta-tyylimäärittelyt Mapsforge / Android<br/>
                            <a href="orux-mf-theme://jekku.hylly.org/kartat/%(date)s/%(name)s">Asenna tyylimäärittelyt Oruxmapsiin</a><br/>
                            <a href="locus-actions://http/jekku.hylly.org/kartat/%(date)s/%(locus_file)s">Asenna tyylimäärittelyt Locukseen</a>''',
        'size': None,
        'hash': None
    }),
    ('tiekartta.zip', {
        'type': 'mapsforge_style',
        'name': 'tiekartta.zip',
        'description': u'''Tiekartta-tyylimäärittelyt Mapsforge / Android<br/>
                            <a href="orux-mf-theme://jekku.hylly.org/kartat/%(date)s/%(name)s">Asenna tyylimäärittelyt Oruxmapsiin</a><br/>
                            <a href="locus-actions://http/jekku.hylly.org/kartat/%(date)s/%(locus_file)s">Asenna tyylimäärittelyt Locukseen</a>''',
        'size': None,
        'hash': None

    })
))


# ('peruskartta_v3.zip', {
#     'type': 'mapsforge_style',
#     'name': 'peruskartta_v3.zip',
#     'description': '''Peruskartta-tyylimäärittelyt Mapsforge / Android< (v3-yhteensopiva)<br/>
#                             <a href="orux-mf-theme://jekku.hylly.org/kartat/%(date)s/%(name)s">Asenna tyylimäärittelyt Oruxmapsiin</a><br/>
#                             <a href="locus-actions://http/jekku.hylly.org/kartat/%(date)s/%(locus_file)s">Asenna tyylimäärittelyt Locukseen</a>''',
#     'size': None,
#     'hash': None
# })

def generate_v3style(fn):
    print('Generating v3 not implemented')


def get_locus_ofn(fn):
    ofn = os.path.splitext(os.path.basename(fn))[0]
    ofn = '%s_locus.xml' % ofn
    return ofn


def generate_map_locus(fn):
    ofn = get_locus_ofn(fn)
    print("generate_map_locus", fn, ofn)
    with open(os.path.join(path, ofn), 'w+') as f:
        f.write('''<?xml version='1.0' encoding='UTF-8'?>
<locusActions>
  <download>
    <source><![CDATA[https://kartat-dl.hylly.org/%(date)s/%(fn)s]]></source>
    <dest><![CDATA[/mapsVector/%(fn)s]]></dest>
    <after>refreshMap</after>
  </download>
  <download>
    <source><![CDATA[https://kartat-dl.hylly.org/%(date)s/%(default_style)s]]></source>
    <dest><![CDATA[/mapsVector/_themes/%(default_style)s]]></dest>
    <after>extract|deleteSource</after>
  </download>
</locusActions>''' % {'date': publishdate, 'fn': fn, 'default_style': release_files[fn]['default_style']})
    print("done")
    return ofn


def generate_style_locus(fn):
    ofn = get_locus_ofn(fn)
    print("generate_style_locus", fn, ofn)
    with open(os.path.join(path, ofn), 'w+') as f:
        f.write('''<?xml version="1.0" encoding="UTF-8"?>
<locusActions>
  <download>
    <source><![CDATA[https://kartat-dl.hylly.org/%(date)s/%(fn)s]]></source>
    <dest><![CDATA[/mapsVector/_themes/peruskartta/%(fn)s]]></dest>
    <after>extract|deleteSource</after>
  </download>
</locusActions>''' % {'fn': fn, 'date': publishdate})
    return ofn


BUF_SIZE = 1024 * 1024 * 5


if False and os.path.exists('cache.dat'):
    with open('cache.dat', 'rb') as f:
        release_files = pickle.load(f)
else:
    for fn in files:
        if not fn in release_files:
            print(fn, 'not found')
            continue
        st = time.time()
        print(fn)
        if 'default_style' in release_files[fn]:
            assert release_files[fn]['default_style'] in release_files

        fp = os.path.join(path, fn)
        sha1hash = hashlib.sha1()
        filebytes = None
        with open(fp, 'rb') as f:
            while True:
                data = f.read(BUF_SIZE)
                if not data:
                    break
                sha1hash.update(data)

            filebytes = f.tell()

        release_files[fn]['size'] = filebytes
        try:
            mtime = os.path.getmtime(fp)
        except OSError:
            mtime = 0
        last_modified_date = datetime.datetime.fromtimestamp(mtime)

        release_files[fn]['updated'] = last_modified_date
        release_files[fn]['hash'] = sha1hash.hexdigest()

        assert release_files[fn]['size'] and release_files[fn]['size'] > 0

        if release_files[fn]['type'] == 'mapsforge_style':
            generate_v3style(fn)
            release_files[fn]['locus_file'] = generate_style_locus(fn)

        if release_files[fn]['type'] == 'mapsforge_map':
            release_files[fn]['locus_file'] = generate_map_locus(fn)

        release_files[fn]['size_text'] = humanfriendly.format_size(release_files[fn]['size'])
        release_files[fn]['date'] = publishdate
        release_files[fn]['description'] = release_files[fn]['description'] % release_files[fn]
    with open('cache.dat', 'wb') as f:
        pickle.dump(release_files, f)

changes = ''
# with open('CHANGES.md') as cf:
#     changes = cf.read()


from jinja2 import Environment, FileSystemLoader, select_autoescape
env = Environment(
    loader=FileSystemLoader('.'),
    autoescape=select_autoescape(['html', 'xml'])
)

template = env.get_template('index.html')

with open(os.path.join(path, 'site.html'), 'w+') as f:
    f.write(template.render(release_files=release_files, changes=changes, publishdate=publishdate))

print("Publish done!")
