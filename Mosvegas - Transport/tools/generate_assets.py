"""Original development audio/icons and a simple scooter; replace audio via a resource pack."""
import sys,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'.tools/python'))
import numpy as np
import soundfile as sf
from PIL import Image,ImageDraw
A=ROOT/'slimcore/src/main/resources/assets/slimvehicle'
(A/'sounds').mkdir(exist_ok=True)
rate=22050
rng=np.random.default_rng(19)
def save(name,x):
    x=np.asarray(x);x=x/max(1,float(np.max(np.abs(x))))*.75
    sf.write(A/'sounds'/f'{name}.ogg',x,rate,format='OGG',subtype='VORBIS')
t=np.arange(rate*2)/rate
engine=sum(np.sin(2*np.pi*48*k*t)/k for k in range(1,9))*(.75+.25*np.sin(2*np.pi*24*t))+.08*rng.normal(size=len(t))
save('engine_loop',engine)
t=np.arange(rate)/rate
save('engine_start',(np.sin(2*np.pi*(30*t+80*t*t))+.5*np.sin(2*np.pi*92*t))*np.sin(np.pi*t)**.5)
save('horn',(np.sin(2*np.pi*349*t)+.6*np.sin(2*np.pi*440*t))*np.minimum(1,t*25)*np.minimum(1,(1-t)*25))
save('bell',(np.sin(2*np.pi*1600*t)+.25*np.sin(2*np.pi*2630*t))*np.exp(-t*7))
save('tyre_squeal',(.5*np.sin(2*np.pi*(900*t+18*np.sin(2*np.pi*9*t)))+.3*rng.normal(size=len(t)))*np.sin(np.pi*t))
t=np.arange(rate*4)/rate
frequency=700+430*np.sin(2*np.pi*.5*t)
save('siren',np.sin(2*np.pi*np.cumsum(frequency)/rate)*.65)
t=np.arange(rate*8)/rate
radio=np.zeros_like(t)
notes=[164.81,196,220,196,146.83,174.61,196,146.83,130.81,164.81,196,164.81,146.83,174.61,220,196]
for i,f in enumerate(notes):
    mask=(t>=i*.5)&(t<(i+1)*.5);q=t[mask]-i*.5
    radio[mask]+=(np.sin(2*np.pi*f*q)+.3*np.sin(2*np.pi*2*f*q))*.35*np.exp(-q*5)
for i in range(16):
    q=t-i*.5;mask=(q>=0)&(q<.12);radio[mask]+=np.sin(2*np.pi*(75*q[mask]-120*q[mask]**2))*.4*np.exp(-q[mask]*30)
save('radio',radio)
(A/'textures/item').mkdir(exist_ok=True)
for name in ['key','fuel_can','volga','police','ambulance','scooter']:
    im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
    if name=='key':
        d.ellipse((3,4,17,18),fill='#e8ba55',outline='#3c3440',width=2);d.ellipse((7,8,12,13),fill='#202731');d.polygon([(13,14),(18,12),(29,23),(26,26),(22,23),(20,25),(16,21),(17,18)],fill='#d9af57')
    elif name=='fuel_can':
        d.rounded_rectangle((7,8,26,29),3,fill='#aa3339',outline='#4c2029',width=2);d.rectangle((10,3,21,10),outline='#ba444b',width=3);d.line((11,13,22,24),fill='#eb7775',width=2);d.line((22,13,11,24),fill='#eb7775',width=2);d.rectangle((23,5,28,10),fill='#38454e')
    elif name=='scooter':
        d.line((10,24,25,24,22,5,15,5),fill='#76baba',width=3);d.ellipse((5,23,11,29),fill='#26343e');d.ellipse((23,23,29,29),fill='#26343e')
    else:
        color={'volga':'#b2bec9','police':'#538acc','ambulance':'#e5e0d5'}[name]
        d.polygon([(3,16),(7,10),(23,10),(28,17),(29,24),(2,24)],fill=color,outline='#26343e');d.polygon([(9,11),(21,11),(24,17),(6,17)],fill='#355767');d.rectangle((4,20,8,22),fill='#ffe7a2');d.rectangle((23,20,27,22),fill='#ffe7a2');d.rectangle((5,24,9,28),fill='#1b2630');d.rectangle((23,24,27,28),fill='#1b2630')
        if name=='police':d.rectangle((12,7,20,9),fill='#e54f4d');d.rectangle((12,7,15,9),fill='#4bd2ff')
        if name=='ambulance':d.rectangle((13,18,18,23),fill='#ce454b')
    im.save(A/'textures/item'/f'{name}.png')
    (A/'models/item'/f'{name}.json').write_text(json.dumps({'parent':'item/generated','textures':{'layer0':f'slimvehicle:item/{name}'}}),encoding='utf-8')

def cube(origin,size):return dict(origin=origin,size=size,uv=[0,0])
bones=[dict(name='Volga',pivot=[0,0,0]),dict(name='chassis',parent='Volga',pivot=[0,5,0],cubes=[cube([-3,3,-9],[6,2,17]),cube([-1,4,-6],[2,1,10])]),
       dict(name='steer_fl',parent='Volga',pivot=[0,3,-8],cubes=[cube([-1,3,-9],[2,20,2]),cube([-7,22,-9],[14,2,2])]),
       dict(name='roll_fl',parent='steer_fl',pivot=[0,3,-8],cubes=[cube([-1.5,0,-11],[3,6,6])]),
       dict(name='roll_rl',parent='Volga',pivot=[0,3,8],cubes=[cube([-1.5,0,5],[3,6,6])])]
geo={'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.scooter','texture_width':512,'texture_height':512,'visible_bounds_width':3,'visible_bounds_height':3,'visible_bounds_offset':[0,1,0]},'bones':bones}]}
(A/'geo/scooter.geo.json').write_text(json.dumps(geo,indent=2),encoding='utf-8')
print('Generated 7 original audio clips, 6 item icons and scooter model.')
