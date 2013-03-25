import hashlib 
from dq2.clientapi.DQ2 import DQ2 
dq2=DQ2() 
dataset='mc12_8TeV.159020.ParticleGenerator_gamma_Et7to80.simul.HITS.e1173_s1627_tid01188985_00' 
listFiles = dq2.listFilesInDataset(dataset)[0].values() 
for item in listFiles: 
    scope, lfn = item['scope'], item['lfn'] 
    correctedscope = "/".join(scope.split('.')) 
    hash = hashlib.md5('%s:%s' % (scope, lfn)).hexdigest() 
    print 'rucio/%s/%s/%s/%s' % (correctedscope, hash[0:2], hash[2:4], lfn) 

