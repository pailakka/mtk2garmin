import os
import sys

basepath = '/opt/mtk2garmin-build/convertedpbf'
infiles = sorted([fn for fn in os.listdir(basepath) if fn.endswith('.osm.pbf') and not fn.endswith("_chunk.osm.pbf") and not fn.startswith('all')])

print(len(infiles), 'files')

def chunks(lst, n):
    """Yield successive n-sized chunks from lst."""
    for i in range(0, len(lst), n):
        yield lst[i:i + n]

def getGroups(files):
    groups = {}
    for fn in files:
        gn = fn.split('_')[0]
        if not gn in groups:
            groups[gn] = []

        groups[gn].append(fn)

    return groups


def createCommandList(filelist, target_file):
    cmd = []
    for i, fn in enumerate(filelist):
        if i == 0:
            ocmd = './osmconvert %s' % os.path.join(basepath, fn)
        else:
            ocmd = './osmconvert - %s' % os.path.join(basepath, fn)

        if i == len(filelist) - 1:
            ocmd += ' -o=%s' % os.path.join(basepath, target_file)
        else:
            ocmd += ' --out-o5m'

        cmd.append(ocmd)
    return cmd

batch_size = 10

f = open('merge_all.sh', 'w+')
f.write('''#!/bin/bash
set -x

''')

for fn in infiles:
    f.write('./osmconvert %s --out-statistics\n' % os.path.join(basepath, fn))

f.write('''

''')
cid = 0
while len(infiles) > batch_size:
    chunked_files = chunks(infiles, batch_size)
    ninfiles = []
    for cf in chunked_files:
        target_file = '%d_chunk.osm.pbf' % cid
        cmds = createCommandList(cf,target_file)
        f.write('|'.join(cmds))
        f.write('\n')
        ninfiles.append(target_file)
        cid += 1
    infiles = ninfiles
    f.write('\n')

cmds = createCommandList(infiles,"all.osm.pbf")
f.write('|'.join(cmds))
f.write('\n')
f.close()
