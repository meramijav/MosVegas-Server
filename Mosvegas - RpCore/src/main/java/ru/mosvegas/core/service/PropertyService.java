package ru.mosvegas.core.service;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.*;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import ru.mosvegas.core.config.*;
import ru.mosvegas.core.data.*;
import ru.mosvegas.core.util.*;
import ru.mosvegas.core.zone.*;

import java.util.*;

public final class PropertyService {
    public static final Identifier OUTLINES_PACKET=new Identifier("mosvegas_core","property_outlines");
    public static final int MAX_OUTLINES=64;
    private static final byte DOOR_OUTLINE=0;
    private static final byte PROPERTY_OUTLINE=1;
    private static final byte SELECTION_OUTLINE=2;
    private static final String WAND_KEY="mosvegas_property_wand";
    /** Everything an outsider could do to someone else's object. PvP, shooting and vehicles in transit stay with the city zones. */
    private static final Set<ZoneAction> GUARDED=EnumSet.of(ZoneAction.BUILD,ZoneAction.DOORS,ZoneAction.CONTAINERS,ZoneAction.INTERACT,ZoneAction.ENTITIES,ZoneAction.VEHICLE_PLACE);
    private static final Map<UUID,String> selected = new HashMap<>();
    private static final Map<UUID,Selection> selections = new HashMap<>();
    private static final int PAGE=36;
    private static int ticks;
    private PropertyService() {}
    private static boolean admin(ServerPlayerEntity player,String node){return Permissions.hasAny(player,3,node,"mosvegas.admin.properties");}

    private static final class Selection {
        String dimension;
        BlockPos first;
        BlockPos second;
        BlockPos lastSecondClick;
        long lastSecondTick=-1;
    }

    private record VisualBox(double minX,double minY,double minZ,double maxX,double maxY,double maxZ,int color,byte kind) {}

    public static void giveWand(ServerPlayerEntity player){if(!admin(player,"mosvegas.admin.properties.wand")){player.sendMessage(Text.literal("§cНет права mosvegas.admin.properties.wand"));return;}ItemStack wand=new ItemStack(Items.BLAZE_ROD);wand.setCustomName(Text.literal("§bПалка выделения недвижимости"));wand.getOrCreateNbt().putBoolean(WAND_KEY,true);ItemsUtil.give(player,wand);player.sendMessage(Text.literal("§aЛКМ — первая точка, ПКМ — вторая. Затем /property admin."));player.sendMessage(Text.literal("§7Квартиру сложной формы (Г, П) выделяйте прямоугольными частями: создайте объект из первой части, остальные добавьте кнопкой «Добавить выделение к границе»."));}
    public static boolean isWand(ItemStack stack){return stack.hasNbt()&&stack.getNbt().getBoolean(WAND_KEY);}
    public static boolean selectFirst(ServerPlayerEntity player,BlockPos pos){if(!isWand(player.getMainHandStack())&&!isWand(player.getOffHandStack()))return false;if(!admin(player,"mosvegas.admin.properties.wand"))return true;Selection s=selections.computeIfAbsent(player.getUuid(),ignored->new Selection());s.dimension=player.getWorld().getRegistryKey().getValue().toString();s.first=pos.toImmutable();s.second=null;player.sendMessage(Text.literal("§bПервая точка: "+pos.toShortString()));return true;}
    public static boolean selectSecond(ServerPlayerEntity player,BlockPos pos){if(!isWand(player.getMainHandStack())&&!isWand(player.getOffHandStack()))return false;if(!admin(player,"mosvegas.admin.properties.wand"))return true;Selection s=selections.computeIfAbsent(player.getUuid(),ignored->new Selection());String dimension=player.getWorld().getRegistryKey().getValue().toString();long tick=player.getWorld().getTime();if(tick==s.lastSecondTick&&pos.equals(s.lastSecondClick))return true;if(s.first==null||!Objects.equals(s.dimension,dimension)){player.sendMessage(Text.literal("§cСначала выберите первую точку в этом измерении."));return true;}s.second=pos.toImmutable();s.lastSecondClick=s.second;s.lastSecondTick=tick;player.sendMessage(Text.literal("§bВторая точка: "+pos.toShortString()+". Зона выделена."));return true;}

    /** The player's own objects only: owned, rented or shared with them. Free objects are offered at their doors instead. */
    public static void open(ServerPlayerEntity player) {
        String self=player.getUuidAsString();
        List<MosvegasConfig.Property> own=ConfigManager.get().properties.stream().filter(p->role(self,DataStore.property(p.id))!=null).toList();
        Ui.menu(player,"Моя недвижимость",m->{
            m.label(4,Ui.item(Items.GOLD_INGOT,"Баланс: "+EconomyService.money(DataStore.player(player.getUuid()).bankBalance)));
            if(own.isEmpty()){
                m.label(22,Ui.item(Items.BARRIER,"У вас пока нет недвижимости"));
                m.label(31,Ui.item(Items.OAK_DOOR,"Купить или арендовать: ПКМ по двери свободного объекта"));
            }
            int slot=9;
            for(MosvegasConfig.Property property:own){
                int s=slot++;
                m.button(s,Ui.item(icon(property),property.name+" | "+role(self,DataStore.property(property.id))),()->detail(player,property));
                if(slot>=45)break;
            }
        });
    }

    private static String role(String self,DataStore.PropertyRecord r){return self.equals(r.owner)?"§aВладелец":self.equals(r.tenant)?"§eАрендатор":r.members.contains(self)?"§bУчастник":null;}
    private static Item icon(MosvegasConfig.Property property){return "PARKING".equalsIgnoreCase(property.type)?Items.MINECART:Items.OAK_DOOR;}
    private static ItemStack info(MosvegasConfig.Property property){return Ui.item(Items.PAPER,"Тип: "+property.type+" | цена: "+EconomyService.money(property.price)+" | аренда: "+EconomyService.money(property.rentPrice));}

    private static void detail(ServerPlayerEntity player,MosvegasConfig.Property property){
        DataStore.PropertyRecord r=DataStore.property(property.id);
        String self=player.getUuidAsString();
        String role=role(self,r);
        if(role==null){open(player);return;}
        Ui.menu(player,property.name,m->{
            m.label(4,info(property));
            MosvegasConfig.Point at=property.location;
            if(at!=null)m.label(13,Ui.item(Items.COMPASS,"Координаты: "+(int)Math.floor(at.x)+" "+(int)Math.floor(at.y)+" "+(int)Math.floor(at.z)+" | "+role));
            if(self.equals(r.owner)){
                m.button(29,Ui.item(Items.GOLD_INGOT,"Продать государству за 70%"),()->sell(player,property));
                if(property.rentPrice>0)m.button(31,Ui.item(Items.OAK_SIGN,r.forRent?"Снять с аренды":"Выставить в аренду"),()->toggleRent(player,property));
            }
            if(self.equals(r.tenant))m.button(33,Ui.item(Items.BARRIER,"Прекратить аренду"),()->cancelRent(player,property));
            m.button(49,Ui.item(Items.ARROW,"Назад"),()->open(player));
        });
    }

    private static void offer(ServerPlayerEntity player,MosvegasConfig.Property property){
        DataStore.PropertyRecord r=DataStore.property(property.id);
        Ui.menu(player,property.name,m->{
            m.label(4,info(property));
            if(r.owner==null)m.button(29,Ui.item(Items.EMERALD,"Купить за "+EconomyService.money(property.price)),()->buy(player,property));
            else if(r.forRent&&r.tenant==null&&property.rentPrice>0)m.button(29,Ui.item(Items.EMERALD,"Арендовать за "+EconomyService.money(property.rentPrice)),()->rent(player,property));
        });
    }

    /** Right click on the door of a free or rentable object opens its offer for a player without access. Both hands are consumed so the door stays shut. */
    public static boolean offerAtDoor(ServerPlayerEntity player,Hand hand,BlockPos pos){
        World world=player.getWorld();
        if(ZoneService.blockAction(world,pos)!=ZoneAction.DOORS)return false;
        MosvegasConfig.Property property=at(world,pos);
        if(property==null||canAccess(player,property))return false;
        DataStore.PropertyRecord r=DataStore.property(property.id);
        if(r.owner!=null&&!(r.forRent&&r.tenant==null&&property.rentPrice>0))return false;
        if(hand==Hand.MAIN_HAND)offer(player,property);
        return true;
    }

    /** Object that forbids this action to the player at pos, or null. Free objects are closed to everyone without a role too. */
    public static MosvegasConfig.Property denying(ServerPlayerEntity player,World world,BlockPos pos,ZoneAction action){
        if(!GUARDED.contains(action))return null;
        String dimension=world.getRegistryKey().getValue().toString();
        for(MosvegasConfig.Property p:ConfigManager.get().properties)if(contains(p,dimension,pos)&&!canAccess(player,p))return p;
        return null;
    }

    public static boolean canBreak(ServerPlayerEntity player,BlockPos pos){
        MosvegasConfig.Property property=at(player.getWorld(),pos);if(property==null||!property.protectBlocks)return true;
        if(Permissions.has(player,"mosvegas.property.build.bypass",3))return true;
        player.sendMessage(Text.literal("§cВ границах квартиры или парковки нельзя ломать блоки."),true);return false;
    }

    public static void tick(net.minecraft.server.MinecraftServer server){
        if(++ticks%10!=0)return;
        for(ServerPlayerEntity player:server.getPlayerManager().getPlayerList()){
            List<VisualBox> outlines=new ArrayList<>();
            HitResult hit=player.raycast(5,0,false);
            if(hit instanceof BlockHitResult blockHit){BlockPos pos=blockHit.getBlockPos();var block=player.getWorld().getBlockState(pos).getBlock();if(block instanceof DoorBlock||block instanceof TrapdoorBlock){MosvegasConfig.Property property=at(player.getWorld(),pos);if(property!=null)outlines.add(doorOutline(player,pos,canAccess(player,property)));}}
            boolean wand=isWand(player.getMainHandStack())||isWand(player.getOffHandStack());
            if(wand){
                Selection selection=selections.get(player.getUuid());
                if(selection!=null&&selection.first!=null&&selection.second!=null&&player.getWorld().getRegistryKey().getValue().toString().equals(selection.dimension)){
                    BlockPos a=selection.first,b=selection.second;
                    outlines.add(new VisualBox(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()),Math.max(a.getX(),b.getX())+1,Math.max(a.getY(),b.getY())+1,Math.max(a.getZ(),b.getZ())+1,0xFFCF36,SELECTION_OUTLINE));
                }
                MosvegasConfig.Property property=find(selected.get(player.getUuid()));
                if(property!=null)outlines.addAll(propertyOutlines(player,property));
            }
            sendOutlines(player,outlines);
        }
    }

    private static boolean canAccess(ServerPlayerEntity player,MosvegasConfig.Property property){DataStore.PropertyRecord r=DataStore.property(property.id);String self=player.getUuidAsString();return self.equals(r.owner)||self.equals(r.tenant)||r.members.contains(self)||Permissions.has(player,"mosvegas.property.access.bypass",3);}
    private static MosvegasConfig.Property find(String id){if(id==null)return null;for(MosvegasConfig.Property p:ConfigManager.get().properties)if(id.equals(p.id))return p;return null;}
    private static MosvegasConfig.Property at(World world,BlockPos pos){String dimension=world.getRegistryKey().getValue().toString();for(MosvegasConfig.Property p:ConfigManager.get().properties)if(contains(p,dimension,pos))return p;return null;}
    private static boolean contains(MosvegasConfig.Property p,String dimension,BlockPos pos){
        MosvegasConfig.Point base=p.location;if(base==null||!Objects.equals(base.dimension,dimension))return false;
        if(p.areas!=null&&!p.areas.isEmpty()){for(MosvegasConfig.Area area:p.areas)if(area.contains(pos.getX(),pos.getY(),pos.getZ()))return true;return false;}
        double x=pos.getX()+.5,y=pos.getY()+.5,z=pos.getZ()+.5,radius=Math.max(1,base.radius);
        return Math.abs(x-base.x)<=radius&&Math.abs(z-base.z)<=radius&&y>=base.y-1&&y<=base.y+4;
    }

    private static VisualBox doorOutline(ServerPlayerEntity player,BlockPos pos,boolean access){
        boolean door=player.getWorld().getBlockState(pos).getBlock() instanceof DoorBlock;
        BlockPos base=door&&player.getWorld().getBlockState(pos.down()).getBlock() instanceof DoorBlock?pos.down():pos;
        List<BlockPos> parts=door?List.of(base,base.up()):List.of(base);
        Box combined=null;
        for(BlockPos part:parts){
            var state=player.getWorld().getBlockState(part);
            if((door&&!(state.getBlock() instanceof DoorBlock))||(!door&&!(state.getBlock() instanceof TrapdoorBlock)))continue;
            var shape=state.getOutlineShape(player.getWorld(),part);
            if(shape.isEmpty())continue;
            Box local=shape.getBoundingBox();
            Box worldBox=new Box(local.minX+part.getX(),local.minY+part.getY(),local.minZ+part.getZ(),local.maxX+part.getX(),local.maxY+part.getY(),local.maxZ+part.getZ());
            combined=combined==null?worldBox:combined.union(worldBox);
        }
        if(combined==null)combined=new Box(base.getX(),base.getY(),base.getZ(),base.getX()+1,base.getY()+(door?2:1),base.getZ()+1);
        return new VisualBox(combined.minX,combined.minY,combined.minZ,combined.maxX,combined.maxY,combined.maxZ,access?0x35FF65:0xFF4050,DOOR_OUTLINE);
    }
    private static List<VisualBox> propertyOutlines(ServerPlayerEntity player,MosvegasConfig.Property p){
        String dimension=player.getWorld().getRegistryKey().getValue().toString();if(p.location==null||!dimension.equals(p.location.dimension))return List.of();
        List<VisualBox> boxes=new ArrayList<>();
        if(p.areas!=null&&!p.areas.isEmpty())for(MosvegasConfig.Area a:p.areas)boxes.add(new VisualBox(a.minX,a.minY,a.minZ,a.maxX+1,a.maxY+1,a.maxZ+1,0x29C7FF,PROPERTY_OUTLINE));
        else{double r=Math.max(1,p.location.radius);boxes.add(new VisualBox(p.location.x-r,p.location.y-1,p.location.z-r,p.location.x+r,p.location.y+4,p.location.z+r,0x29C7FF,PROPERTY_OUTLINE));}
        return boxes;
    }
    private static void sendOutlines(ServerPlayerEntity player,List<VisualBox> outlines){PacketByteBuf buffer=PacketByteBufs.create();int count=Math.min(MAX_OUTLINES,outlines.size());buffer.writeVarInt(count);for(int i=0;i<count;i++){VisualBox box=outlines.get(i);buffer.writeDouble(box.minX);buffer.writeDouble(box.minY);buffer.writeDouble(box.minZ);buffer.writeDouble(box.maxX);buffer.writeDouble(box.maxY);buffer.writeDouble(box.maxZ);buffer.writeInt(box.color);buffer.writeByte(box.kind);}ServerPlayNetworking.send(player,OUTLINES_PACKET,buffer);}

    private static void buy(ServerPlayerEntity p,MosvegasConfig.Property property){
        if(!Permissions.has(p,"mosvegas.property.buy",0)){p.sendMessage(Text.literal("§cНет права mosvegas.property.buy"));return;}
        DataStore.PropertyRecord r=DataStore.property(property.id);
        if(r.owner!=null){p.sendMessage(Text.literal("§cОбъект уже куплен."));return;}
        if(!EconomyService.debit(p,property.price)){p.sendMessage(Text.literal("§cНедостаточно денег."));return;}
        r.owner=p.getUuidAsString();r.tenant=null;r.forRent=false;DataStore.save();
        p.sendMessage(Text.literal("§aКуплено: "+property.name));detail(p,property);
    }

    private static void sell(ServerPlayerEntity p,MosvegasConfig.Property property){
        DataStore.PropertyRecord r=DataStore.property(property.id);
        if(!p.getUuidAsString().equals(r.owner))return;
        if(r.tenant!=null){p.sendMessage(Text.literal("§cСначала дождитесь окончания аренды."));return;}
        long refund=property.price*70/100; r.owner=null;r.forRent=false;r.members.clear();EconomyService.credit(p,refund);DataStore.save();
        p.sendMessage(Text.literal("§aПродано. Получено: "+EconomyService.money(refund)));open(p);
    }

    private static void toggleRent(ServerPlayerEntity p,MosvegasConfig.Property property){
        DataStore.PropertyRecord r=DataStore.property(property.id);if(!p.getUuidAsString().equals(r.owner))return;
        if(r.tenant!=null){p.sendMessage(Text.literal("§cОбъект уже арендуют."));return;}
        r.forRent=!r.forRent;DataStore.save();detail(p,property);
    }

    private static void rent(ServerPlayerEntity p,MosvegasConfig.Property property){
        if(!Permissions.has(p,"mosvegas.property.rent",0)){p.sendMessage(Text.literal("§cНет права mosvegas.property.rent"));return;}
        DataStore.PropertyRecord r=DataStore.property(property.id);
        if(!r.forRent||r.tenant!=null){p.sendMessage(Text.literal("§cОбъект недоступен для аренды."));return;}
        if(!EconomyService.debit(p,property.rentPrice)){p.sendMessage(Text.literal("§cНедостаточно денег."));return;}
        if(r.owner!=null){try{DataStore.player(UUID.fromString(r.owner)).bankBalance+=property.rentPrice;}catch(IllegalArgumentException ignored){}}
        r.tenant=p.getUuidAsString();DataStore.save();p.sendMessage(Text.literal("§aОбъект арендован."));detail(p,property);
    }

    private static void cancelRent(ServerPlayerEntity p,MosvegasConfig.Property property){
        DataStore.PropertyRecord r=DataStore.property(property.id);if(p.getUuidAsString().equals(r.tenant)){r.tenant=null;DataStore.save();detail(p,property);}
    }

    private static void teleport(ServerPlayerEntity p,MosvegasConfig.Property property){
        if(!admin(p,"mosvegas.admin.properties.view"))return;
        MosvegasConfig.Point at=property.location;if(at==null)return;
        Identifier id=Identifier.tryParse(at.dimension);if(id==null)return;
        ServerWorld world=p.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD,id));
        if(world==null){p.sendMessage(Text.literal("§cИзмерение объекта не загружено."));return;}
        p.teleport(world,at.x,at.y,at.z,p.getYaw(),p.getPitch());
    }

    public static void openAdmin(ServerPlayerEntity p){openAdmin(p,0);}
    /** Objects are listed {@value #PAGE} per page in slots 9..44; arrows on the bottom row turn pages like in shops. */
    private static void openAdmin(ServerPlayerEntity p,int page){
        if(!admin(p,"mosvegas.admin.properties.view")){p.sendMessage(Text.literal("§cНет права mosvegas.admin.properties.view"));return;}
        List<MosvegasConfig.Property> all=ConfigManager.get().properties;
        int pages=Math.max(1,(all.size()+PAGE-1)/PAGE),current=Math.max(0,Math.min(page,pages-1));
        Ui.menu(p,"Админ: недвижимость"+(pages>1?" • "+(current+1)+"/"+pages:""),m->{
            m.label(4,Ui.item(Items.PAPER,"Объектов: "+all.size()+" | страница "+(current+1)+" из "+pages));
            for(int i=current*PAGE;i<Math.min(all.size(),(current+1)*PAGE);i++){MosvegasConfig.Property property=all.get(i);m.button(9+i%PAGE,Ui.item(icon(property),property.id+": "+property.name),()->adminDetail(p,property));}
            if(current>0)m.button(45,Ui.item(Items.ARROW,"Предыдущая страница"),()->openAdmin(p,current-1));
            if(current+1<pages)m.button(53,Ui.item(Items.ARROW,"Следующая страница"),()->openAdmin(p,current+1));
            if(admin(p,"mosvegas.admin.properties.wand"))m.button(46,Ui.item(Items.BLAZE_ROD,"Получить палку выделения"),()->giveWand(p));
            if(admin(p,"mosvegas.admin.properties.create")){m.button(48,Ui.item(Items.OAK_DOOR,"Создать квартиру из выделения"),()->createWizard(p,"APARTMENT"));m.button(50,Ui.item(Items.MINECART,"Создать парковку из выделения"),()->createWizard(p,"PARKING"));}
        });
    }
    private static int pageOf(MosvegasConfig.Property property){return Math.max(0,ConfigManager.get().properties.indexOf(property))/PAGE;}

    private static void createWizard(ServerPlayerEntity p,String type){
        if(!admin(p,"mosvegas.admin.properties.create")){p.sendMessage(Text.literal("§cНет права mosvegas.admin.properties.create"));return;}
        Selection selection=selections.get(p.getUuid());if(selection==null||selection.first==null||selection.second==null){p.sendMessage(Text.literal("§cСначала выделите зону палкой: ЛКМ и ПКМ."));return;}
        Ui.input(p,"ID объекта","property_id",id->Ui.input(p,"Название","PARKING".equals(type)?"Новая парковка":"Новая квартира",name->Ui.input(p,"Цена покупки","50000",priceText->Ui.input(p,"Цена аренды","2500",rentText->{
            try{
                long price=Long.parseLong(priceText.replace(" ","")),rent=Long.parseLong(rentText.replace(" ",""));
                String safe=safeId(id);
                if(ConfigManager.get().properties.stream().anyMatch(x->x.id.equals(safe))){p.sendMessage(Text.literal("§cТакой ID уже существует."));return;}
                if(selection.first==null||selection.second==null){p.sendMessage(Text.literal("§cВыделение сброшено. Выделите зону заново."));return;}
                BlockPos a=selection.first,b=selection.second;double cx=(a.getX()+b.getX()+1)/2.0,cy=Math.min(a.getY(),b.getY())+1,cz=(a.getZ()+b.getZ()+1)/2.0;
                MosvegasConfig.Point point=new MosvegasConfig.Point(selection.dimension,cx,cy,cz,0);MosvegasConfig.Property property=new MosvegasConfig.Property(safe,name,type,price,rent,point);
                property.areas.add(area(selection));selection.first=null;selection.second=null;
                ConfigManager.get().properties.add(property);ConfigManager.save();DataStore.property(safe);DataStore.save();selected.put(p.getUuid(),safe);
                p.sendMessage(Text.literal("§aСоздан объект "+safe+". Для сложной формы выделите следующую часть и нажмите «Добавить выделение к границе»."));adminDetail(p,property);
            }catch(NumberFormatException e){p.sendMessage(Text.literal("§cЦена должна быть целым числом."));}
        }))));
    }

    private static String safeId(String id){return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]","_");}
    private static MosvegasConfig.Area area(Selection s){BlockPos a=s.first,b=s.second;return new MosvegasConfig.Area(a.getX(),a.getY(),a.getZ(),b.getX(),b.getY(),b.getZ());}

    private static void addArea(ServerPlayerEntity p,MosvegasConfig.Property property){
        if(!admin(p,"mosvegas.admin.properties.bounds")){p.sendMessage(Text.literal("§cНет права mosvegas.admin.properties.bounds"));return;}
        Selection s=selections.get(p.getUuid());
        if(s==null||s.first==null||s.second==null){p.sendMessage(Text.literal("§cСначала выделите новую часть палкой: ЛКМ и ПКМ."));return;}
        if(property.location==null||!Objects.equals(s.dimension,property.location.dimension)){p.sendMessage(Text.literal("§cВыделение находится в другом измерении."));return;}
        MosvegasConfig.Area area=area(s);
        if(property.areas.stream().anyMatch(area::sameAs)){p.sendMessage(Text.literal("§cЭта часть уже входит в границу."));return;}
        property.areas.add(area);s.first=null;s.second=null;ConfigManager.save();selected.put(p.getUuid(),property.id);
        p.sendMessage(Text.literal("§aЧасть добавлена: "+area+". Всего частей: "+property.areas.size()));adminDetail(p,property);
    }

    private static void areasMenu(ServerPlayerEntity p,MosvegasConfig.Property property){
        if(!admin(p,"mosvegas.admin.properties.bounds")){p.sendMessage(Text.literal("§cНет права mosvegas.admin.properties.bounds"));return;}
        selected.put(p.getUuid(),property.id);
        Ui.menu(p,"Граница: "+property.name,m->{
            m.label(4,Ui.item(Items.PAPER,property.areas.isEmpty()?"Граница по радиусу вокруг точки объекта":"Частей: "+property.areas.size()+". ЛКМ по части — удалить"));
            int slot=9;
            for(MosvegasConfig.Area area:new ArrayList<>(property.areas)){int s=slot++;int number=s-8;m.button(s,Ui.item(Items.MAP,"#"+number+": "+area),()->removeArea(p,property,area));if(slot>=45)break;}
            m.button(48,Ui.item(Items.SCAFFOLDING,"Добавить выделение к границе"),()->addArea(p,property));
            m.button(49,Ui.item(Items.ARROW,"Назад"),()->adminDetail(p,property));
        });
    }

    private static void removeArea(ServerPlayerEntity p,MosvegasConfig.Property property,MosvegasConfig.Area area){
        if(!admin(p,"mosvegas.admin.properties.bounds")){p.sendMessage(Text.literal("§cНет права mosvegas.admin.properties.bounds"));return;}
        if(property.areas.size()<=1){p.sendMessage(Text.literal("§cНельзя удалить единственную часть. Сначала добавьте новую."));areasMenu(p,property);return;}
        if(property.areas.remove(area)){ConfigManager.save();p.sendMessage(Text.literal("§eЧасть удалена: "+area));}
        areasMenu(p,property);
    }

    private static void adminDetail(ServerPlayerEntity p,MosvegasConfig.Property property){if(!admin(p,"mosvegas.admin.properties.view")){p.sendMessage(Text.literal("§cНет права mosvegas.admin.properties.view"));return;}selected.put(p.getUuid(),property.id);DataStore.PropertyRecord r=DataStore.property(property.id);Ui.menu(p,"Админ: "+property.name,m->{m.label(4,Ui.item(Items.PAPER,"ID: "+property.id+" | "+property.type));m.label(13,Ui.item(Items.PLAYER_HEAD,"Владелец: "+(r.owner==null?"нет":displayPlayer(p,r.owner))));m.label(22,Ui.item(Items.NAME_TAG,"Участников: "+r.members.size()));m.button(10,Ui.item(Items.ENDER_PEARL,"Телепорт и показать границу"),()->teleport(p,property));m.button(19,Ui.item(Items.WRITABLE_BOOK,"Название: "+property.name+" — изменить"),()->nameInput(p,property));m.button(20,Ui.item(Items.NAME_TAG,"ID: "+property.id+" — изменить"),()->idInput(p,property));m.button(24,Ui.item(Items.EMERALD,"Цена покупки: "+EconomyService.money(property.price)+" — изменить"),()->priceInput(p,property,false));m.button(25,Ui.item(Items.GOLD_NUGGET,"Цена аренды: "+EconomyService.money(property.rentPrice)+" — изменить"),()->priceInput(p,property,true));m.button(28,Ui.item(Items.LIME_DYE,"Назначить владельца"),()->ownerInput(p,property));m.button(29,Ui.item(Items.RED_DYE,"Удалить владельца"),()->removeOwner(p,property));m.button(31,Ui.item(Items.LIME_WOOL,"Добавить участника"),()->memberInput(p,property));m.button(32,Ui.item(Items.BOOK,"Список участников"),()->membersMenu(p,property));m.button(37,Ui.item(Items.SCAFFOLDING,"Добавить выделение к границе"),()->addArea(p,property));m.button(38,Ui.item(Items.MAP,"Части границы: "+(property.areas.isEmpty()?"радиус":property.areas.size())),()->areasMenu(p,property));m.button(49,Ui.item(Items.ARROW,"Назад"),()->openAdmin(p,pageOf(property)));m.button(53,Ui.item(Items.BARRIER,"Удалить объект"),()->confirmDelete(p,property));});}
    /** Anvil edits apply to the live object: after a delete or /mosvegas reload the captured one is no longer in the config. */
    private static boolean editable(ServerPlayerEntity p,MosvegasConfig.Property property){if(!admin(p,"mosvegas.admin.properties.edit")){p.sendMessage(Text.literal("§cНет права mosvegas.admin.properties.edit"));return false;}if(ConfigManager.get().properties.contains(property))return true;p.sendMessage(Text.literal("§cОбъект удалён или конфиг перезагружен. Откройте его заново."));openAdmin(p);return false;}
    private static void nameInput(ServerPlayerEntity p,MosvegasConfig.Property property){if(!editable(p,property))return;Ui.input(p,"Новое название",property.name,name->{if(!editable(p,property))return;property.name=name;ConfigManager.save();p.sendMessage(Text.literal("§aНазвание изменено: "+name));adminDetail(p,property);});}
    private static void priceInput(ServerPlayerEntity p,MosvegasConfig.Property property,boolean rent){if(!editable(p,property))return;Ui.input(p,rent?"Цена аренды":"Цена покупки",String.valueOf(rent?property.rentPrice:property.price),text->{if(!editable(p,property))return;long value;try{value=Long.parseLong(text.replace(" ",""));}catch(NumberFormatException e){value=-1;}if(value<0){p.sendMessage(Text.literal("§cЦена должна быть целым числом от 0."));adminDetail(p,property);return;}if(rent)property.rentPrice=value;else property.price=value;ConfigManager.save();p.sendMessage(Text.literal("§a"+(rent?"Цена аренды":"Цена покупки")+": "+EconomyService.money(value)));adminDetail(p,property);});}
    /** Owner, tenant and members are keyed by ID in the world database, so they move with it. Both stores are written at once so a crash cannot leave them apart. */
    private static void idInput(ServerPlayerEntity p,MosvegasConfig.Property property){if(!editable(p,property))return;Ui.input(p,"Новый ID",property.id,text->{if(!editable(p,property))return;String old=property.id,safe=safeId(text);if(safe.equals(old)){adminDetail(p,property);return;}if(find(safe)!=null){p.sendMessage(Text.literal("§cТакой ID уже существует."));adminDetail(p,property);return;}property.id=safe;ConfigManager.save();DataStore.renameProperty(old,safe);DataStore.flush();selected.replaceAll((uuid,id)->id.equals(old)?safe:id);p.sendMessage(Text.literal("§aID изменён: "+old+" → "+safe+". Владелец, арендатор и участники сохранены."));adminDetail(p,property);});}
    private static void ownerInput(ServerPlayerEntity p,MosvegasConfig.Property property){if(!admin(p,"mosvegas.admin.properties.owner")){p.sendMessage(Text.literal("§cНет права mosvegas.admin.properties.owner"));return;}Ui.input(p,"Ник владельца","Player",name->{ServerPlayerEntity target=p.getServer().getPlayerManager().getPlayer(name);if(target==null){p.sendMessage(Text.literal("§cИгрок должен быть онлайн."));return;}DataStore.PropertyRecord r=DataStore.property(property.id);r.owner=target.getUuidAsString();r.tenant=null;r.forRent=false;DataStore.save();p.sendMessage(Text.literal("§aВладелец назначен: "+target.getName().getString()));adminDetail(p,property);});}
    private static void removeOwner(ServerPlayerEntity p,MosvegasConfig.Property property){if(!admin(p,"mosvegas.admin.properties.owner")){p.sendMessage(Text.literal("§cНет права mosvegas.admin.properties.owner"));return;}DataStore.PropertyRecord r=DataStore.property(property.id);r.owner=null;r.tenant=null;r.forRent=false;r.members.clear();DataStore.save();p.sendMessage(Text.literal("§eВладелец и участники удалены."));adminDetail(p,property);}
    private static void memberInput(ServerPlayerEntity p,MosvegasConfig.Property property){if(!admin(p,"mosvegas.admin.properties.members")){p.sendMessage(Text.literal("§cНет права mosvegas.admin.properties.members"));return;}Ui.input(p,"Ник участника","Player",name->{ServerPlayerEntity target=p.getServer().getPlayerManager().getPlayer(name);if(target==null){p.sendMessage(Text.literal("§cИгрок должен быть онлайн."));return;}DataStore.property(property.id).members.add(target.getUuidAsString());DataStore.save();p.sendMessage(Text.literal("§aУчастник добавлен: "+target.getName().getString()));adminDetail(p,property);});}
    private static void membersMenu(ServerPlayerEntity p,MosvegasConfig.Property property){if(!admin(p,"mosvegas.admin.properties.members")){p.sendMessage(Text.literal("§cНет права mosvegas.admin.properties.members"));return;}DataStore.PropertyRecord r=DataStore.property(property.id);Ui.menu(p,"Участники: "+property.name,m->{int slot=9;for(String uuid:new ArrayList<>(r.members)){int s=slot++;m.button(s,Ui.item(Items.PLAYER_HEAD,"Удалить: "+displayPlayer(p,uuid)),()->{r.members.remove(uuid);DataStore.save();p.sendMessage(Text.literal("§eУчастник удалён: "+displayPlayer(p,uuid)));membersMenu(p,property);});if(slot>=45)break;}m.button(49,Ui.item(Items.ARROW,"Назад"),()->adminDetail(p,property));});}
    private static String displayPlayer(ServerPlayerEntity viewer,String uuid){try{ServerPlayerEntity online=viewer.getServer().getPlayerManager().getPlayer(UUID.fromString(uuid));return online==null?uuid:online.getName().getString();}catch(IllegalArgumentException e){return uuid;}}
    private static void confirmDelete(ServerPlayerEntity p,MosvegasConfig.Property property){if(!admin(p,"mosvegas.admin.properties.delete")){p.sendMessage(Text.literal("§cНет права mosvegas.admin.properties.delete"));return;}Ui.menu(p,"Удаление объекта",m->{m.label(13,Ui.item(Items.BARRIER,"Удалить "+property.id+" безвозвратно?"));m.button(29,Ui.item(Items.RED_WOOL,"Удалить"),()->{int page=pageOf(property);ConfigManager.get().properties.remove(property);ConfigManager.save();DataStore.deleteProperty(property.id);selected.values().removeIf(property.id::equals);p.sendMessage(Text.literal("§cОбъект удалён: "+property.id));openAdmin(p,page);});m.button(33,Ui.item(Items.LIME_WOOL,"Отмена"),()->adminDetail(p,property));});}
}
