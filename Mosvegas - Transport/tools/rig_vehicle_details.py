"""Give the existing engine and windscreen wipers independent animation bones."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def rig(data):
    bones=data['minecraft:geometry'][0]['bones']
    by_name={b['name']:b for b in bones}
    body=by_name['bone3']
    if 'engine' not in by_name:
        engine=[c for c in body['cubes'] if c.get('rotation')==[95,0,0]]
        assert len(engine)==4
        body['cubes']=[c for c in body['cubes'] if c not in engine]
        bones.append(dict(name='engine',parent='chassis',pivot=[0,12,-33],cubes=engine))
    if 'wiper_left' not in by_name:
        wipers=[c for c in body['cubes'] if c['size'][1]==0 and c['origin'][2]<-19]
        assert len(wipers)==2
        body['cubes']=[c for c in body['cubes'] if c not in wipers]
        for name,cube in zip(['left','right'],wipers):
            pivot=[cube['origin'][0],cube['origin'][1],cube['origin'][2]+cube['size'][2]/2]
            bones.append(dict(name='wiper_'+name,parent='chassis',pivot=pivot,cubes=[cube]))
    return data
if __name__=='__main__':
    path=ROOT/'slimcore/src/main/resources/assets/slimvehicle/geo/volga3102.geo.json'
    path.write_text(json.dumps(rig(json.loads(path.read_text(encoding='utf-8'))),ensure_ascii=False,separators=(',',':')),encoding='utf-8')
