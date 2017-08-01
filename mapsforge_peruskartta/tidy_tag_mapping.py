import xml.dom.minidom

styledom = xml.dom.minidom.parse('Peruskartta.xml')

rules = styledom.getElementsByTagName('rule')

rule_zooms = {}
for r in rules:
    k = r.attributes['k'].value
    vs = r.attributes['v'].value.split('|')

    z = None
    if 'zoom-min' in r.attributes.keys():
        z = r.attributes['zoom-min'].value

    for v in vs:
        rule_zooms[(k,v)] = z

mapdom = xml.dom.minidom.parse('mml_tag-mapping.xml')

mapped_kvs = set()
tags = mapdom.getElementsByTagName('osm-tag')
for tag in tags:
    key = tag.attributes['key'].value
    value = tag.attributes['value'].value
    z = tag.attributes['zoom-appear'].value
    mapped_kvs.add((key,value))
    #print key,value,z,(key,value) in rule_zooms
    if not (key,value) in rule_zooms:
        parentNode = tag.parentNode
        parentNode.insertBefore(mapdom.createComment(tag.toxml()), tag)
        parentNode.removeChild(tag)
        print('missing style',(key,value))
        continue
    if not rule_zooms[(key,value)]:
        tag.setAttribute("zoom-appear"  , z)
    else:
        tag.setAttribute("zoom-appear"  , rule_zooms[(key,value)])

f = open('mml_tag-mapping_tidy.xml', "w")
f.write(mapdom.toxml())
f.close()

for missing in set(rule_zooms.keys()) - mapped_kvs:
    print('missing mapping',missing)
