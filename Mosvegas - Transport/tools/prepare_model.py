"""Prepare project-supplied geometry. Run once before editing the rig manually."""
import json, math, shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'slimcore/src/main/resources/assets/slimvehicle'
source = ROOT / 'Волга-3102 - Converted.geo.json'
if not source.exists():
    source = ROOT / 'model/volga3102.source.geo.json'
data = json.loads(source.read_text(encoding='utf-8'))
geo = data['minecraft:geometry'][0]
bones = geo['bones']
lookup = {b['name']: b for b in bones}
assert len(lookup) == len(bones)
assert sum(len(b.get('cubes', [])) for b in bones) == 190

for old, name in [('wheel2','fl'),('wheel3','fr'),('wheel5','rl'),('wheel4','rr')]:
    b = lookup[old]
    pivot = b['pivot'][:]
    bones += [dict(name=f'steer_{name}', parent='Volga', pivot=pivot),
              dict(name=f'camber_{name}', parent=f'steer_{name}', pivot=pivot,
                   rotation=b.pop('rotation', [0,0,0]))]
    b['name'], b['parent'] = f'roll_{name}', f'camber_{name}'
    cubes = b.pop('cubes')
    bones += [dict(name=f'tyre_{name}',parent=b['name'],pivot=pivot,cubes=cubes[:8]),
              dict(name=f'rim_{name}',parent=b['name'],pivot=pivot,cubes=cubes[8:])]

for old, name in [('frontdoorL','fl'),('frontdoorL2','fr'),('backdoorL','rl'),('backdoorL3','rr')]:
    b=lookup[old]
    b['name']=f'door_{name}'
    glass=[c for c in b['cubes'] if min(c['size']) < .05]
    b['cubes']=[c for c in b['cubes'] if c not in glass]
    bones.append(dict(name=f'glass_{name}',parent=b['name'],pivot=b['pivot'][:],cubes=glass))

lookup['transfers']['name']='gear_lever'
lookup['transfers']['pivot']=[2,15,-5.5]
lookup['wheel']['name']='steering_mount'
steering=lookup['wheel']
bones.append(dict(name='steering_spin',parent='steering_mount',pivot=steering['pivot'][:],cubes=steering.pop('cubes')))
lookup['Gas']['name']='fuel_flap'
lookup['bone17']['name']='pedals'
body=lookup['bone3']
glove=body['cubes'][54]
body['cubes'].remove(glove)
bones.append(dict(name='glovebox',parent='Volga',pivot=[-6.81123,14.1,-13.94],cubes=[glove]))
for label,indices in [('headlights',[36,37]),('taillights',[40,41])]:
    cubes=[body['cubes'][i] for i in indices]
    bones.append(dict(name=label,parent='Volga',pivot=[2,0,0],cubes=cubes))
    for c in cubes: c['_extract']=True
body['cubes']=[c for c in body['cubes'] if not c.pop('_extract',False)]
roof=lookup['bone7']
for index,label in [(5,'glass_windshield'),(10,'glass_rear')]:
    cube=roof['cubes'][index]
    bones.append(dict(name=label,parent='Volga',pivot=cube['pivot'][:],cubes=[cube]))
roof['cubes']=[c for i,c in enumerate(roof['cubes']) if i not in (5,10)]

bones.append(dict(name='chassis',parent='Volga',pivot=[2,12,7]))
for b in bones:
    if b.get('parent')=='Volga' and b['name']!='chassis' and not b['name'].startswith('steer_'):
        b['parent']='chassis'

SCALE = .8
def point(p): return [round((p[0]-2)*SCALE,6), round(p[1]*SCALE,6), round((p[2]-7)*SCALE,6)]
for b in bones:
    b['pivot']=point(b.get('pivot',[2,0,7]))
    for c in b.get('cubes',[]):
        c['origin']=point(c['origin'])
        c['size']=[round(v*SCALE,6) for v in c['size']]
        if 'pivot' in c: c['pivot']=point(c['pivot'])
geo['description'].update(identifier='geometry.volga3102',visible_bounds_width=9,visible_bounds_height=4,visible_bounds_offset=[0,1,0])
assert sum(len(b.get('cubes',[])) for b in bones)==190
names={b['name'] for b in bones}
assert all(b.get('parent') in names for b in bones if 'parent' in b)
for folder in ['geo','textures/entity','animations','lang','models/item']:
    (ASSETS/folder).mkdir(parents=True,exist_ok=True)
from refine_vehicle_assets import refine_model
(ASSETS/'geo/volga3102.geo.json').write_text(json.dumps(refine_model(data),ensure_ascii=False,indent=2),encoding='utf-8')
(ASSETS/'animations/volga3102.animation.json').write_text(json.dumps({'format_version':'1.8.0','animations':{'animation.volga.idle':{'loop':True,'animation_length':1,'bones':{}}}},indent=2),encoding='utf-8')
texture=ROOT/'Волга.png'
if texture.exists(): shutil.copy2(texture,ASSETS/'textures/entity/volga3102.png')
if source.parent==ROOT:
    (ROOT/'model').mkdir(exist_ok=True)
    shutil.move(source,ROOT/'model/volga3102.source.geo.json')
print(f'Prepared {len(bones)} bones, {sum(len(b.get("cubes", [])) for b in bones)} cubes, scale {SCALE}.')
