# Run once after the server has started. Builds two П-shaped apartments east of the zone platform.
# A (Z 2..14) is the ready object demo_p from config; B (Z 20..32) has no object, for practising the wand.
forceload add 41 -6 60 33
fill 41 199 -6 60 199 34 minecraft:smooth_stone
fill 44 199 2 58 203 14 minecraft:white_stained_glass hollow
fill 49 199 2 53 203 9 minecraft:air
fill 49 199 2 53 199 9 minecraft:grass_block
fill 48 200 2 48 202 9 minecraft:white_stained_glass
fill 54 200 2 54 202 9 minecraft:white_stained_glass
fill 49 200 10 53 202 10 minecraft:white_stained_glass
fill 45 199 3 47 199 13 minecraft:oak_planks
fill 55 199 3 57 199 13 minecraft:oak_planks
fill 48 199 11 54 199 13 minecraft:oak_planks
setblock 46 200 2 minecraft:oak_door[facing=north,half=lower]
setblock 46 201 2 minecraft:oak_door[facing=north,half=upper]
setblock 51 200 12 minecraft:chest[facing=north]
item replace block 51 200 12 container.0 with minecraft:diamond 3
setblock 45 200 12 minecraft:crafting_table
setblock 56 200 6 minecraft:lever[face=floor,facing=north]
setblock 56 200 5 minecraft:redstone_lamp
summon minecraft:armor_stand 56.5 200 11.5
setblock 51 200 5 minecraft:chest[facing=south]
item replace block 51 200 5 container.0 with minecraft:apple 3
fill 44 199 20 58 203 32 minecraft:white_stained_glass hollow
fill 49 199 20 53 203 27 minecraft:air
fill 49 199 20 53 199 27 minecraft:grass_block
fill 48 200 20 48 202 27 minecraft:white_stained_glass
fill 54 200 20 54 202 27 minecraft:white_stained_glass
fill 49 200 28 53 202 28 minecraft:white_stained_glass
fill 45 199 21 47 199 31 minecraft:spruce_planks
fill 55 199 21 57 199 31 minecraft:spruce_planks
fill 48 199 29 54 199 31 minecraft:spruce_planks
setblock 46 200 20 minecraft:oak_door[facing=north,half=lower]
setblock 46 201 20 minecraft:oak_door[facing=north,half=upper]
setblock 51 200 30 minecraft:chest[facing=north]
give MosvegasOp minecraft:oak_door 2
save-all flush
