import os
import pprint
import sys
import subprocess
import json

basepath = '/convertedpbf'
infiles = [fn for fn in os.listdir(basepath) if len(
    fn) == 14 and fn.endswith('.osm.pbf')]

print(len(infiles), 'files')

def getGroups(files, glen):
    groups = {}
    for fn in files:
        gn = fn[:glen]
        if not gn in groups:
            groups[gn] = []

        groups[gn].append(fn)

    return groups


def createCommandList(filelist, k):
    cmd = []
    for i, fn in enumerate(filelist):
        if i == 0:
            ocmd = './osmconvert %s' % os.path.join(basepath,fn)
        else:
            ocmd = './osmconvert - %s' % os.path.join(basepath,fn)

        if i == len(filelist) - 1:
            ocmd += ' -o=%s' %  % os.path.join(basepath,'%s.osm.pbf' % k)
        else:
            ocmd += ' --out-o5m'

        cmd.append(ocmd)
    return cmd


g1 = getGroups(infiles, 4)
# print g1.keys()
g2 = getGroups(g1.keys(), 3)
# pprint.pprint(g2)
g3 = getGroups(g2.keys(), 2)
# pprint.pprint(g3)
open('merge_all1.sh', 'w+').close()
open('merge_all2.sh', 'w+').close()
open('merge_all3.sh', 'w+').close()
open('merge_all4.sh', 'w+').close()
for k3 in g3:
    #f = open('suomi/merge_%s.bat' % k3[:1],'a+')

    print('###')
    for k2 in (k for k in g2 if k.startswith(k3)):
        for k1 in (k for k in g1 if k.startswith(k2)):
            # print 'g1',g1[k1],'->',k1
            cmd1 = createCommandList(g1[k1], k1)
            print('1', cmd1)
            f = open('merge_all1.sh', 'a+')
            f.write('|'.join(cmd1))
            f.write('; ')
            f.write('rm -f %s' % ' '.join((os.path.join(basepath,f) for f in g1[k1])))
            f.write('\n')
            f.close()
        cmd2 = createCommandList(['%s.osm.pbf' % fn for fn in g2[k2]], k2)
        print('2', cmd2)
        f = open('merge_all2.sh', 'a+')
        f.write('|'.join(cmd2))
        f.write('; ')
        f.write('rm -f %s' % ' '.join((os.path.join(basepath,'%s.osm.pbf' % f) for f in g2[k2])))
        f.write('\n')
        f.close()
    cmd3 = createCommandList(['%s.osm.pbf' % fn for fn in g3[k3]], k3)
    print('3', cmd3)
    f = open('merge_all3.sh', 'a+')
    f.write('|'.join(cmd3))
    f.write('; ')
    f.write('rm -f %s' % ' '.join((os.path.join(basepath,'%s.osm.pbf' % f) for f in g3[k3])))
    f.write('\n')

    f.close()

f = open('merge_all4.sh', 'a+')
g4 = getGroups(g3.keys(), 0)
f.write('|'.join(createCommandList(
    ['%s.osm.pbf' % fn for fn in g4['']], 'all')))
f.close()
# pprint.pprint(g4)
