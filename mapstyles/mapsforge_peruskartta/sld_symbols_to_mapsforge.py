from xml.dom.minidom import parse, parseString

dom1 = parse('peruskartta_symbolit.sld')


rules = dom1.getElementsByTagName('Rule')

for r in rules:
    name = r.getElementsByTagName('Name')[0].childNodes[0].data.encode('latin-1')
    title = r.getElementsByTagName('Title')[0].childNodes[0].data.encode('latin-1')
    onr = r.getElementsByTagName('OnlineResource')[0]
    #print title
    href = onr.attributes['xlink:href'].value
    svgfile = href[href.rfind('/')+1:]
    #print svgfile
    print '<osm-tag key="kohdeluokka" value="%s" zoom-appear="10" />' % name
    continue
    print '''
    <!-- %s -->
    <rule e="any" k="kohdeluokka" v="%s" zoom-min="15">
        <symbol src="file:/mml/%s" symbol-height="15"/>
    </rule>
    ''' % (title.decode('utf-8'),name,svgfile)
