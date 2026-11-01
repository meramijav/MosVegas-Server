"""Original procedural textures and a repeatable steering/indicator rig repair."""
import json, math
from pathlib import Path
from PIL import Image
from rig_vehicle_details import rig
ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'slimcore/src/main/resources/assets/slimvehicle'

def refine_model(data):
    bones = data['minecraft:geometry'][0]['bones']
    by_name = {b['name']: b for b in bones}
    spin = by_name['steering_spin']
    if 'steering_hub' not in by_name:
        hub = spin['cubes'].pop()
        spin['pivot'] = [round(hub['origin'][i]+hub['size'][i]/2,6) for i in range(3)]
        bones.append(dict(name='steering_hub',parent='steering_mount',pivot=spin['pivot'][:],cubes=[hub]))
    body = by_name['bone3']
    if 'indicator_front_left' not in by_name:
        for side, sign in [('left',1),('right',-1)]:
            selected = [c for c in body['cubes'] if
                (abs(c['size'][0]-4)<.001 and abs(c['size'][1]-1.6)<.001 and abs(c['size'][2]-.8)<.001
                 or abs(c['size'][0]-1.6)<.001 and abs(c['size'][1]-1.6)<.001 and abs(c['size'][2]-4)<.001)
                and (c['origin'][0]+c['size'][0]/2)*sign>0]
            body['cubes'] = [c for c in body['cubes'] if c not in selected]
            bones.append(dict(name='indicator_front_'+side,parent='chassis',pivot=[0,0,0],cubes=selected))
            tail = next(c for c in by_name['taillights']['cubes'] if (c['origin'][0]+c['size'][0]/2)*sign>0)
            lens = json.loads(json.dumps(tail));lens['size'][0] = 1.6
            if sign>0: lens['origin'][0] += tail['size'][0]-1.6
            else: tail['origin'][0] += 1.6
            tail['size'][0] -= 1.6
            bones.append(dict(name='indicator_rear_'+side,parent='chassis',pivot=[0,0,0],cubes=[lens]))
    return data

def main():
    model = ASSETS/'geo/volga3102.geo.json'
    model.write_text(json.dumps(rig(refine_model(json.loads(model.read_text(encoding='utf-8')))),ensure_ascii=False,indent=2),encoding='utf-8')
    effects=ROOT/'sliminterfacefabric/src/main/resources/assets/slimvehicle/textures/effect';effects.mkdir(parents=True,exist_ok=True)
    tread=Image.new('RGBA',(64,64))
    for y in range(64):
        for x in range(64):
            u=(x+.5)/64;edge=min(1,u*13,(1-u)*13)
            groove=.38 if min(abs(u-.30),abs(u-.50),abs(u-.70))<.022 else 1
            rib=.78 if (y+int(abs(u-.5)*22))%16<2 else 1
            grain=.87+.13*math.sin(x*12.7+y*5.1)**2
            tread.putpixel((x,y),(255,255,255,round(255*edge*groove*rib*grain)))
    tread.save(effects/'tyre_track.png')
    shadow=Image.new('RGBA',(64,128))
    for y in range(128):
        for x in range(64):
            u=abs((x+.5)/32-1);v=abs((y+.5)/64-1)
            a=max(0,1-u*u)**1.5*max(0,1-v**4)**1.6
            shadow.putpixel((x,y),(255,255,255,round(255*a)))
    shadow.save(effects/'vehicle_shadow.png')

if __name__=='__main__': main()
