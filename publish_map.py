import sys
import boto3
import os
from collections import OrderedDict
import hashlib
import time
import humanfriendly

path = sys.argv[1]
date = os.path.basename(path)
files = os.listdir(path)

release_files = OrderedDict((
    ('mtk_suomi.img', {
        'type':'garmin_map',
        'name':'mtk_suomi.img',
        'description':'''Suoraan GPS-laitteeseen ladattava tiedosto. Sijoitetaan /Garmin/ kansioon.
                                <br/>Uudemmissa malleissa tiedoston nimellä ei ole merkitystä, vanhemmissa
                                malleissa tiedoston nimeksi täytyy vaihtaa
                                <strong>GMAPSUPP.IMG</strong>''',
        'size':None,
        'hash':None
    }),
    ('mtk_suomi.exe',{
        'type':'garmin_map',
        'name':'mtk_suomi.exe',
        'description':'''MapSource, BaseCamp ja MapInstall yhteensopiva versio. Tämän avulla voit
                                valita haluamasi alueet (aluejako ETRS-TM35 lehtijako) ja siirtää vain
                                ne laitteeseen, tarpeellinen esimerkiksi vanhempien laitteiden kanssa. <strong>Aja mtk_suomi.exe ja seuraa asennusohjelman ohjeita.</strong>''',
        'size':None,
        'hash':None
    }),
    ('mtk_suomi.map',{
        'type':'mapsforge_map',
        'name':'mtk_suomi.map',
        'default_style':'peruskartta.zip',
        'description':'''Mapsforge / Android-yhteensopiva versio<br/>
                            <a href="orux-map://jekku.hylly.org/kartat/%(date)s/%(name)s">Asenna kartta Oruxmapsiin</a><br/>
                            <a href="locus-actions://http/jekku.hylly.org/kartat/%(date)s/%(locus_file)s">Asenna kartta Locukseen</a>''',
        'size':None,
        'hash':None
    }),
    ('peruskartta.zip',{
        'type':'mapsforge_style',
        'name':'peruskartta.zip',
        'description':'''Peruskartta-tyylimäärittelyt Mapsforge / Android<br/>
                            <a href="orux-mf-theme://jekku.hylly.org/kartat/%(date)s/%(name)s">Asenna tyylimäärittelyt Oruxmapsiin</a><br/>
                            <a href="locus-actions://http/jekku.hylly.org/kartat/%(date)s/%(locus_file)s">Asenna tyylimäärittelyt Locukseen</a>''',
        'size':None,
        'hash':None
    }),
    ('tiekartta.zip',{
        'type':'mapsforge_style',
        'name':'tiekartta.zip',
        'description':'''Tiekartta-tyylimäärittelyt Mapsforge / Android<br/>
                            <a href="orux-mf-theme://jekku.hylly.org/kartat/%(date)s/%(name)s">Asenna tyylimäärittelyt Oruxmapsiin</a><br/>
                            <a href="locus-actions://http/jekku.hylly.org/kartat/%(date)s/%(locus_file)s">Asenna tyylimäärittelyt Locukseen</a>''',
        'size':None,
        'hash':None

    })
))

def generate_v3style(fn):
    print('Generating v3 not implemented')


def get_locus_ofn(fn):
    ofn = os.path.splitext(os.path.basename(fn))[0]
    ofn = '%s_locus.xml' % ofn
    return ofn
def generate_map_locus(fn):
    ofn = get_locus_ofn(fn)
    print("generate_map_locus",fn,ofn)
    with open(os.path.join(path,ofn),'w+') as f:
        f.write('''<?xml version='1.0' encoding='UTF-8'?>
<locusActions>
  <download>
    <source><![CDATA[http://jekku.hylly.org/kartat/%(date)s/%(fn)s]]></source>
    <dest><![CDATA[/mapsVector/%(fn)s]]></dest>
    <after>refreshMap</after>
  </download>
  <download>
    <source><![CDATA[http://jekku.hylly.org/kartat/%(date)s/%(default_style)s]]></source>
    <dest><![CDATA[/mapsVector/_themes/%(default_style)s]]></dest>
    <after>extract|deleteSource</after>
  </download>
</locusActions>''' % {'date':date,'fn':fn,'default_style':release_files[fn]['default_style']})
    print("done")
    return ofn

def generate_style_locus(fn):
    ofn = get_locus_ofn(fn)
    print("generate_style_locus",fn,ofn)
    with open(os.path.join(path,ofn),'w+') as f:
        f.write('''<?xml version="1.0" encoding="UTF-8"?>
<locusActions>
  <download>
    <source><![CDATA[http://jekku.hylly.org/kartat/%(date)s/%(fn)s]]></source>
    <dest><![CDATA[/mapsVector/_themes/peruskartta/%(fn)s]]></dest>
    <after>extract|deleteSource</after>
  </download>
</locusActions>''' % {'fn':fn,'date':date})
    return ofn


BUF_SIZE = 1024*1024*5

for fn in files:
    if not fn in release_files:
        continue
    st = time.time()
    print(fn)
    if 'default_style' in release_files[fn]:
        assert release_files[fn]['default_style'] in release_files

    fp = os.path.join(path,fn)
    sha1hash = hashlib.sha1()
    filebytes = None
    with open(fp,'rb') as f:
        while True:
            data = f.read(BUF_SIZE)
            if not data:
                break
            sha1hash.update(data)
    
        filebytes = f.tell()
    
    release_files[fn]['size'] = filebytes
    release_files[fn]['hash'] = sha1hash.hexdigest()

    assert release_files[fn]['size'] and release_files[fn]['size'] > 0

    if release_files[fn]['type'] == 'mapsforge_style':
        generate_v3style(fn)
        release_files[fn]['locus_file'] = generate_style_locus(fn)
    
    if release_files[fn]['type'] == 'mapsforge_map':
        release_files[fn]['locus_file'] = generate_map_locus(fn)

with open(os.path.join(path,'site.html'),'w+') as f:
    with open('site/header.html') as hf:
        header = hf.read()
    with open('site/footer.html') as ff:
        footer = ff.read()

    with open('CHANGES.md') as cf:
        changes = cf.read()

    f.write(header)
    for rf in release_files.values():
        rf['size_text'] = humanfriendly.format_size(rf['size'])
        rf['date'] = date
        rf['description'] = rf['description'] % rf
        f.write('''<tr>
                            <td>
                                <a class="dl" href="http://jekku.hylly.org/kartat/%(date)s/%(name)s">%(name)s</a>
                            </td>
                            <td>%(description)s</td>
                            <td>%(size_text)s</td>
                            <td>%(hash)s</td>
                        </tr>''' % rf)
    f.write(footer % {'changes':changes,'date':date})

    print("site.html written")

print("Publish done!")
    