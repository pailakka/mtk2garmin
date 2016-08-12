import os,sys,time
import pprint
from xml.sax import make_parser, handler
import requests
import datetime
import StringIO

api_key = sys.argv[1]

class MMLGrid(handler.ContentHandler):
    def __init__(self):
        self.tags = None
        self.tag = None
        self.tagdata = {}
        self.opentags = []
        self.catchtag = 'link'
        self.files = []

        self.nfiles = 8

        for i in xrange(self.nfiles):
            self.files.append(open('update_bats/loaddata_%d.bat' % i,'wb'))

        self.gridnum = 0
        self.next = None

    def startElement(self,name,attrs):
        if name == self.catchtag:
            self.tags = {}

        if self.tags == None:
            return None

        self.opentag = {}
        self.tags[name] = self.opentag
        self.opentags.append(name)

        for a in attrs.getQNames():
            if not self.opentag.has_key('attributes'):
                self.opentag['attributes'] = {}
            self.opentag['attributes'][a] = attrs.getValueByQName(a)

    def characters(self,data):
        if self.tags == None:
            return None

        if not self.opentag.has_key('textdata'):
            self.opentag['textdata'] = ''
        self.opentag['textdata'] += data

    def endElement(self,name):
        if self.tags == None:
            return None

        if name == self.catchtag:
            self.handleFeature(self.tags[self.catchtag])
            self.tags = None

        self.opentags.remove(name)
        if len(self.opentags) == 0:
            return None

        pn = self.opentags[-1]
        parent = self.tags[pn]
        if parent.has_key(name):
            if not isinstance(parent[name],list):
                parent[name] = [parent[name],self.tags[name]]
            else:
                parent[name].append(self.tags[name])
        else:
            parent[name] = self.tags[name]

        del self.tags[name]

    def endDocument(self):
        pass

    def handleFeature(self,feature):
        if feature['attributes'][u'rel'] == u'next':
            self.next = feature['attributes']['href']
            return True

        elif feature['attributes'][u'rel'] in (u'alternate',u'related'):
            href = feature['attributes']['href']

            filename = href[href.find('gml/')+4:]
            filename = filename[:filename.find('?')]
            filename = filename[filename.rfind('/')+1:]
            filepath = os.path.join('mtk',filename[:2],filename[:3])

            if not os.path.exists(filepath):
                os.makedirs(filepath)

            #print filename,os.path.exists(os.path.join(filepath,filename))
            if not os.path.exists(os.path.join(filepath,filename)):
                self.files[self.gridnum%self.nfiles].write('wget -O %s --no-check-certificate %s\n' % (os.path.join(filepath,filename),href))
                self.gridnum+=1
        else:
            pass



last_updated = '2011-05-01'
'''
if len(sys.argv) == 1:
    print 'LAST UPDATE FROM FILE'
    if os.path.exists('last_updated.dat'):
      f = open('last_updated.dat')
      last_updated = f.read().strip()
      f.close()
#'''

print 'NOW:',datetime.datetime.now().replace(microsecond=0).isoformat()
print 'LAST UPDATE:',last_updated
files_to_update = []

parser = make_parser()
k = MMLGrid()
parser.setContentHandler(k)

url = 'https://tiedostopalvelu.maanmittauslaitos.fi/tp/feed/mtp/maastotietokanta/kaikki?api_key=%s&format=application/gml%%2Bxml&updated=%s' % (api_key,last_updated)
#url = 'https://tiedostopalvelu.maanmittauslaitos.fi/tp/feed/mtp/peruskarttarasteri_jhs180/painovari_ei_pehmennysta?api_key=%s&format=image/png&updated=%s' % (api_key,last_updated)
all_got = False
while not all_got:
    k.next = None
    url = url.replace('application/gml+xml',r'application/gml%2Bxml')
    print url
    st=time.time()
    r = requests.get(url)
    sxml = StringIO.StringIO(r.content)
    sxml.seek(0)
    parser.parse(sxml)
    print time.time()-st,'Have next:',k.next != None
    url = k.next


    if k.next == None:
        all_got = True
        break

for f in k.files:
    f.close()

if len(sys.argv) == 1:
    f = open('last_updated.dat','wb')
    f.write(str(datetime.datetime.now().replace(microsecond=0).isoformat()))
    f.close()
print 'All done',k.gridnum,'files to download'

#https://tiedostopalvelu.maanmittauslaitos.fi/tp/feed/mtp/maastotietokanta/kaikki?api_key=jm1u1qq0quv14ofvgk8mr4jf3k&format=application/gml%2Bxml&updated=2011-05-01