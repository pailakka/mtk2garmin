import os
import pprint
import sys
import subprocess
import json
infiles = [fn for fn in os.listdir('suomi') if len(fn) == 14 and fn.endswith('.osm.pbf')]

print len(infiles),'files'
'''
outdata = {}
for fn in infiles:
    fp = os.path.join('suomi',fn)
    cmd = ['osmconvert64',fp,'--out-statistics']
    print cmd
    outdata[fn] = subprocess.check_output(cmd,stderr=subprocess.STDOUT)
    #break

f = open('stats.json','wb')
json.dump(outdata,f)
f.close()
sys.exit(1)
'''
def getGroups(files,glen):
    groups = {}
    for fn in files:
        gn = fn[:glen]
        if not gn in groups:
            groups[gn] = []

        groups[gn].append(fn)

    return groups

def createCommandList(filelist,k):
    cmd = []
    for i,fn in enumerate(filelist):
        if i == 0:
            ocmd = 'osmconvert64 suomi/%s' % fn
        else:
            ocmd = 'osmconvert64 - suomi/%s'% fn

        if i == len(filelist)-1:
            ocmd += ' -o=suomi/%s.osm.pbf' % k
        else:
            ocmd += ' --out-o5m'

        cmd.append(ocmd)
    return cmd
g1 = getGroups(infiles,4)
#print g1.keys()
g2 = getGroups(g1.keys(),3)
#pprint.pprint(g2)
g3 = getGroups(g2.keys(),2)
#pprint.pprint(g3)
open('merge_all.bat','w+').close()
for k3 in g3:
    #f = open('suomi/merge_%s.bat' % k3[:1],'a+')
    f = open('merge_all.bat','a+')
    print '###'
    for k2 in (k for k in g2 if k.startswith(k3)):
        for k1 in (k for k in g1 if k.startswith(k2)):
            #print 'g1',g1[k1],'->',k1
            cmd1 = createCommandList(g1[k1],k1)
            print '1',cmd1
            f.write('|'.join(cmd1))
            f.write('\n')
        cmd2 = createCommandList(['%s.osm.pbf' % fn for fn in g2[k2]],k2)
        print '2',cmd2
        f.write('|'.join(cmd2))
        f.write('\n')
    cmd3 = createCommandList(['%s.osm.pbf' % fn for fn in g3[k3]],k3)
    print '3',cmd3
    f.write('|'.join(cmd3))
    f.write('\n')

f = open('merge_all.bat','a+')
g4 = getGroups(g3.keys(),0)
f.write('|'.join(createCommandList(['%s.osm.pbf' % fn for fn in g4['']],'all')))
f.close()
#pprint.pprint(g4)