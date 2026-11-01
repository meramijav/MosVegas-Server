package studio.merami.sliminterfacefabric.gametest;

final class SaveSnapshots {
    static final String G63_SNBT = """
            {Air:300s,BrokenGlass:18,Bumper:"wide",Color:3368618,Definition:"slimvehicle:g63",Dents:[{DX:2.3960736E-5f,DY:0.0f,DZ:-1.0f,Depth:0.25f,Radius:0.86f,X:0.09993834f,Y:0.8f,Z:2.3999026f},{DX:-2.3960736E-5f,DY:0.0f,DZ:1.0f,Depth:0.1708f,Radius:0.70159996f,X:-0.19993418f,Y:0.7f,Z:-2.399905f},{DX:-1.0f,DY:0.0f,DZ:-2.3960736E-5f,Depth:0.12583199f,Radius:0.611664f,X:0.9499533f,Y:0.9f,Z:0.3000103f}],EngineHealth:77.4132f,FallDistance:0.0f,Fire:-1s,Flags:6,Fuel:42.5f,Health:58.488f,Invulnerable:0b,Jammed:49,Lamps:15,Motion:[0.0d,0.0d,0.0d],OnGround:1b,Open:2,Owner:[I;286331153,572666675,1145328981,1431655765],PortalCooldown:0,Pos:[-5448321.0d,-59.0107541d,-1.1736137E7d],Rims:"sport",Rotation:[30.0f,0.0f],Skin:"police",Spoiler:"lip",Storage_glovebox:[{Count:8b,id:"minecraft:diamond"},{Count:12b,id:"minecraft:iron_ingot"}],Storage_trunk:[{Count:7b,id:"minecraft:diamond"},{Count:12b,id:"minecraft:iron_ingot"}],Tint:60,Trusted:[{Id:[I;-1717986919,-2004322441,1717982549,1431655765]}],Tyres:4,UUID:[I;325810618,488787915,-1132540702,1845186118],Windows:2,Zones:[20.0f,0.0f,8.712f,0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,12.8f,0.0f,0.0f,0.0f,0.0f,0.0f,0.0f]}""";
    static final String G63_STATE = """
            definition=slimvehicle:g63
            owner=11111111-2222-3333-4444-555555555555
            health=58.488
            zone0=20.0
            zone1=0.0
            zone2=8.712
            zone3=0.0
            zone4=0.0
            zone5=0.0
            zone6=0.0
            zone7=0.0
            zone8=0.0
            zone9=12.8
            zone10=0.0
            dents=[Dent[pos=(0.09993834048509598, 0.800000011920929, 2.399902582168579), dir=(2.3960736136757455E-5, 0.0, -0.9999999997129416), depth=0.25, radius=0.86], Dent[pos=(-0.199934184551239, 0.699999988079071, -2.39990496635437), dir=(-2.3960736136757455E-5, 0.0, 0.9999999997129416), depth=0.1708, radius=0.70159996], Dent[pos=(0.9499533176422119, 0.8999999761581421, 0.3000102937221527), dir=(-0.9999999997129416, 0.0, -2.3960736136757455E-5), depth=0.12583199, radius=0.611664]]
            flags=LH
            opening.door_fl=false/true
            opening.door_fr=true/false
            opening.door_rl=false/false
            opening.door_rr=false/false
            opening.bonnet=false/true
            opening.trunk=false/true
            opening.fuel=false/false
            opening.glovebox=false/false
            window0=false
            window1=true
            window2=false
            window3=false
            window4=false
            glass0=false
            glass1=true
            glass2=false
            glass3=false
            glass4=true
            glass5=false
            glass6=false
            glass7=false
            glass8=false
            tyre0=false
            tyre1=false
            tyre2=true
            tyre3=false
            lamp1=true
            lamp2=true
            lamp4=true
            lamp8=true
            fuel=42.5
            engine=77.4132
            tint=60
            color=3368618
            styles=sport,wide,lip,police
            storage.trunk.0=7 diamond
            storage.trunk.1=12 iron_ingot
            storage.glovebox.0=8 diamond
            storage.glovebox.1=12 iron_ingot
            """;
    static final String VOLGA_SNBT = """
            {Air:300s,BrokenGlass:18,Bumper:"wide",Color:3368618,Definition:"slimvehicle:volga",Dents:[{DX:2.3960736E-5f,DY:0.0f,DZ:-1.0f,Depth:0.25f,Radius:0.86f,X:0.09993834f,Y:0.8f,Z:2.3999026f},{DX:-2.3960736E-5f,DY:0.0f,DZ:1.0f,Depth:0.1708f,Radius:0.70159996f,X:-0.19993418f,Y:0.7f,Z:-2.399905f},{DX:-1.0f,DY:0.0f,DZ:-2.3960736E-5f,Depth:0.12583199f,Radius:0.611664f,X:0.9499533f,Y:0.9f,Z:0.3000103f}],EngineHealth:77.4132f,FallDistance:0.0f,Fire:-1s,Flags:6,Fuel:42.5f,Health:58.488f,Invulnerable:0b,Jammed:49,Lamps:15,Motion:[0.0d,0.0d,0.0d],OnGround:1b,Open:2,Owner:[I;286331153,572666675,1145328981,1431655765],PortalCooldown:0,Pos:[-5448321.0d,-59.0107541d,-1.1736137E7d],Rims:"sport",Rotation:[30.0f,0.0f],Skin:"police",Spoiler:"lip",Storage_glovebox:[{Count:8b,id:"minecraft:diamond"},{Count:12b,id:"minecraft:iron_ingot"}],Storage_trunk:[{Count:7b,id:"minecraft:diamond"},{Count:12b,id:"minecraft:iron_ingot"}],Tint:60,Trusted:[{Id:[I;-1717986919,-2004322441,1717982549,1431655765]}],Tyres:5,UUID:[I;-63445063,306790973,-2121307579,-922777497],Windows:2,Zones:[20.0f,0.0f,8.712f,0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,12.8f,0.0f,0.0f,0.0f,0.0f,0.0f,0.0f]}""";
    static final String VOLGA_STATE = """
            definition=slimvehicle:volga
            owner=11111111-2222-3333-4444-555555555555
            health=58.488
            zone0=20.0
            zone1=0.0
            zone2=8.712
            zone3=0.0
            zone4=0.0
            zone5=0.0
            zone6=0.0
            zone7=0.0
            zone8=0.0
            zone9=12.8
            zone10=0.0
            dents=[Dent[pos=(0.09993834048509598, 0.800000011920929, 2.399902582168579), dir=(2.3960736136757455E-5, 0.0, -0.9999999997129416), depth=0.25, radius=0.86], Dent[pos=(-0.199934184551239, 0.699999988079071, -2.39990496635437), dir=(-2.3960736136757455E-5, 0.0, 0.9999999997129416), depth=0.1708, radius=0.70159996], Dent[pos=(0.9499533176422119, 0.8999999761581421, 0.3000102937221527), dir=(-0.9999999997129416, 0.0, -2.3960736136757455E-5), depth=0.12583199, radius=0.611664]]
            flags=LH
            opening.door_fl=false/true
            opening.door_fr=true/false
            opening.door_rl=false/false
            opening.door_rr=false/false
            opening.bonnet=false/true
            opening.trunk=false/true
            opening.fuel=false/false
            opening.glovebox=false/false
            window0=false
            window1=true
            window2=false
            window3=false
            glass0=false
            glass1=true
            glass2=false
            glass3=false
            glass4=true
            glass5=false
            tyre0=true
            tyre1=false
            tyre2=true
            tyre3=false
            lamp1=true
            lamp2=true
            lamp4=true
            lamp8=true
            fuel=42.5
            engine=77.4132
            tint=60
            color=3368618
            styles=sport,wide,lip,police
            storage.trunk.0=7 diamond
            storage.trunk.1=12 iron_ingot
            storage.glovebox.0=8 diamond
            storage.glovebox.1=12 iron_ingot
            """;

    private SaveSnapshots() {}
}
