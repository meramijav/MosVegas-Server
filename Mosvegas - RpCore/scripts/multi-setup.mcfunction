# Run once after both clients have joined. Builds the platform and gives the initial inventory.
fill 0 199 0 40 199 20 minecraft:smooth_stone
fill 0 199 0 20 199 0 minecraft:blue_concrete
fill 0 199 20 20 199 20 minecraft:blue_concrete
fill 0 199 0 0 199 20 minecraft:blue_concrete
fill 20 199 0 20 199 20 minecraft:blue_concrete
fill 22 199 0 40 199 0 minecraft:lime_concrete
fill 22 199 20 40 199 20 minecraft:lime_concrete
fill 40 199 0 40 199 20 minecraft:lime_concrete
fill 3 200 6 8 202 6 minecraft:stone_bricks
setblock 6 200 6 minecraft:oak_door[facing=south,half=lower]
setblock 6 201 6 minecraft:oak_door[facing=south,half=upper]
setblock 10 200 6 minecraft:chest[facing=south]
item replace block 10 200 6 container.0 with minecraft:diamond 3
setblock 14 200 6 minecraft:lever[face=floor,facing=south]
setblock 14 200 5 minecraft:redstone_lamp
setblock 28 200 6 minecraft:chest[facing=south]
setblock 24 200 6 minecraft:oak_door[facing=south,half=lower]
setblock 24 201 6 minecraft:oak_door[facing=south,half=upper]
setblock 32 200 6 minecraft:target
op MosvegasOp
gamemode creative MosvegasOp
gamemode survival MosvegasCitizen
tp MosvegasOp 8 200 12 180 15
tp MosvegasCitizen 6 200 12 180 15
spawnpoint MosvegasOp 8 200 12
spawnpoint MosvegasCitizen 6 200 12
mosvegas zone world minecraft:overworld true
execute as MosvegasOp at @s run mosvegas zone pos1 0 199 0
execute as MosvegasOp at @s run mosvegas zone pos2 20 215 20
execute as MosvegasOp at @s run mosvegas zone create police_test 10
mosvegas zone flag police_test build deny
mosvegas zone flag police_test terrain deny
mosvegas zone flag police_test doors members
mosvegas zone flag police_test containers members
mosvegas zone flag police_test interact members
mosvegas zone flag police_test pvp deny
mosvegas zone flag police_test shoot deny
mosvegas zone grant police_test faction police doors
mosvegas zone grant police_test faction police containers
mosvegas zone grant police_test faction police interact
give MosvegasCitizen minecraft:stone 64
give MosvegasCitizen minecraft:iron_pickaxe
give MosvegasCitizen minecraft:wooden_sword
give MosvegasCitizen minecraft:bow
give MosvegasCitizen minecraft:arrow 64
give MosvegasCitizen minecraft:bread 16
give MosvegasCitizen tacz:modern_kinetic_gun{GunId:"tacz:db_short",GunCurrentAmmoCount:2,GunFireMode:"SEMI"}
give MosvegasCitizen tacz:ammo{AmmoId:"tacz:12g"} 64
give MosvegasOp minecraft:stone 64
give MosvegasOp minecraft:oak_door 4
give MosvegasOp minecraft:chest 4
execute as MosvegasOp at @s run mosvegas shopadmin create demo Тестовый магазин
item replace entity MosvegasOp weapon.mainhand with minecraft:bread 16
execute as MosvegasOp at @s run mosvegas shopadmin add demo bread16 100
item replace entity MosvegasOp weapon.mainhand with tacz:modern_kinetic_gun{GunId:"tacz:db_short",GunCurrentAmmoCount:2,GunFireMode:"SEMI"}
execute as MosvegasOp at @s run mosvegas shopadmin add demo shotgun 1000
item replace entity MosvegasOp weapon.mainhand with minecraft:stick
save-all flush
list
mosvegas zone info police_test
