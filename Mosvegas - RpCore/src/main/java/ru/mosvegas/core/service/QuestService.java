package ru.mosvegas.core.service;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.item.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import ru.mosvegas.core.config.*;
import ru.mosvegas.core.data.*;
import ru.mosvegas.core.util.*;

import java.util.*;

public final class QuestService {
    public static final Identifier MARKERS_PACKET = new Identifier("mosvegas_core","quest_markers");
    private static int ticks;
    private QuestService() {}

    public static MosvegasConfig.Quest find(String id) {
        return ConfigManager.quest(id);
    }

    public static void startById(ServerPlayerEntity player,String id){
        if(!Permissions.has(player,"mosvegas.quests.accept",0)){player.sendMessage(Text.literal("§cНет права mosvegas.quests.accept"));return;}
        MosvegasConfig.Quest quest=find(id);
        if(quest==null){player.sendMessage(Text.literal("§cКвест с ID '"+id+"' не найден."));return;}
        accept(player,quest);
    }

    public static void open(ServerPlayerEntity player) {
        PlayerData d=DataStore.player(player.getUuid());
        Ui.menu(player,"Квесты",m->{
            if(d.faction==null) m.label(4,Ui.item(Items.MAP,"Доступны начальные городские квесты"));
            else m.label(4,Ui.item(Items.NETHER_STAR,"Фракция: "+d.faction+" | активно "+d.activeQuests.size()+"/"+ConfigManager.get().maxActiveQuests));
            int slot=9;
            for(PlayerData.ActiveQuest active:d.activeQuests){
                MosvegasConfig.Quest q=find(active.templateId);
                if(q!=null) {
                    int activeSlot = slot++;
                    m.button(activeSlot,Ui.item(Items.BARRIER,"Отказаться: "+q.name+" — "+progress(q,active)),
                            ()->confirmCancel(player,active,q));
                }
                if(slot>=18) break;
            }
            slot=27;
            for(MosvegasConfig.Quest q:ConfigManager.get().quests){
                boolean available=isAvailable(d,q);
                if(!q.enabled || !available || hasActive(d,q.id)) continue;
                int s=slot++;
                if(hasCompleted(d,q))m.label(s,Ui.item(Items.LIME_DYE,"Выполнено: "+q.name));
                else m.button(s,Ui.item(icon(q.type),q.name+" | награда "+EconomyService.money(q.reward)),()->accept(player,q));
                if(slot>=45) break;
            }
        });
    }

    private static Item icon(String type){
        return switch(type==null?"":type.toUpperCase(Locale.ROOT)){
            case "ROUTE"->Items.COMPASS; case "STAY"->Items.CLOCK; case "FIND_ITEM"->Items.SPYGLASS;
            case "NPC_ITEM"->Items.VILLAGER_SPAWN_EGG; default->Items.MAP;
        };
    }

    public static void accept(ServerPlayerEntity player,MosvegasConfig.Quest q){
        if(!Permissions.has(player,"mosvegas.quests.accept",0)){ player.sendMessage(Text.literal("§cНет права mosvegas.quests.accept")); return; }
        PlayerData d=DataStore.player(player.getUuid());
        boolean allowed=isAvailable(d,q);
        if(!q.enabled){ player.sendMessage(Text.literal("§cЭтот квест отключён.")); return; }
        if(!allowed){ player.sendMessage(Text.literal("§cЭтот квест сейчас недоступен.")); return; }
        if(hasCompleted(d,q)){ player.sendMessage(Text.literal("§eЭтот квест уже выполнен.")); return; }
        if(hasActive(d,q.id)){ player.sendMessage(Text.literal("§eЭтот квест уже активен.")); return; }
        if(d.activeQuests.size()>=ConfigManager.get().maxActiveQuests){ player.sendMessage(Text.literal("§cДостигнут лимит активных квестов.")); return; }
        PlayerData.ActiveQuest a=new PlayerData.ActiveQuest();
        a.templateId=q.id; a.acceptedAt=System.currentTimeMillis();
        if(("TRAVEL".equalsIgnoreCase(q.type)||"FIND_ITEM".equalsIgnoreCase(q.type))&&!q.points.isEmpty()) a.pointIndex=new Random().nextInt(q.points.size());
        if(!q.randomItems.isEmpty()) a.chosenItem=q.randomItems.get(new Random().nextInt(q.randomItems.size()));
        d.activeQuests.add(a); DataStore.save();
        player.closeHandledScreen();
        player.sendMessage(Text.literal("§aКвест принят: "+q.name+". "+q.description));
    }

    private static void confirmCancel(ServerPlayerEntity player, PlayerData.ActiveQuest active, MosvegasConfig.Quest quest) {
        Ui.menu(player,"Отказ от квеста",m->{
            m.label(13,Ui.item(Items.FILLED_MAP,quest.name+" — прогресс будет потерян"));
            m.button(29,Ui.item(Items.RED_WOOL,"Подтвердить отказ"),()->cancel(player,active,quest));
            m.button(33,Ui.item(Items.LIME_WOOL,"Продолжить выполнение"),()->open(player));
        });
    }

    private static void cancel(ServerPlayerEntity player, PlayerData.ActiveQuest active, MosvegasConfig.Quest quest) {
        if(!Permissions.has(player,"mosvegas.quests.cancel",0)){
            player.sendMessage(Text.literal("§cНет права mosvegas.quests.cancel"));
            return;
        }
        PlayerData data=DataStore.player(player.getUuid());
        if(!data.activeQuests.remove(active)){
            player.sendMessage(Text.literal("§eЭтот квест уже не активен."));
            open(player);
            return;
        }
        DataStore.save();
        player.sendMessage(Text.literal("§eВы отказались от квеста: "+quest.name));
        open(player);
    }

    public static void tick(MinecraftServer server){
        if(++ticks%10!=0)return;
        for(ServerPlayerEntity player:server.getPlayerManager().getPlayerList()) tickPlayer(player);
    }

    private static void tickPlayer(ServerPlayerEntity player){
        PlayerData d=DataStore.player(player.getUuid());
        List<MosvegasConfig.Point> markers=new ArrayList<>();
        for(PlayerData.ActiveQuest a:new ArrayList<>(d.activeQuests)){
            MosvegasConfig.Quest q=find(a.templateId);
            if(q==null||!q.enabled)continue;
            String type=q.type==null?"":q.type.toUpperCase(Locale.ROOT);
            if(type.equals("NPC_ITEM"))continue;
            MosvegasConfig.Point point=point(q,a,type);
            if(point==null)continue;
            double distance=distance(player,point);
            if(player.getWorld().getRegistryKey().getValue().toString().equals(point.dimension))markers.add(point);
            if(type.equals("FIND_ITEM")){
                player.sendMessage(Text.literal("§6"+directionArrow(player,point)+" §eИщите: "+a.chosenItem+" | "+(int)distance+" блоков"),true);
            }
            boolean inside=distance<=point.radius;
            if(type.equals("STAY")){
                a.progressTicks=inside?a.progressTicks+10:0;
                player.sendMessage(Text.literal(inside?"§a● §e"+q.name+": "+(a.progressTicks/20)+"/"+q.staySeconds+" сек.":"§6"+directionArrow(player,point)+" §e"+q.name+": "+(int)distance+" блоков"),true);
                if(a.progressTicks>=q.staySeconds*20)complete(player,a,q);
            }else if(inside){
                if(type.equals("ROUTE")&&a.pointIndex<q.points.size()-1){a.pointIndex++;DataStore.save();player.sendMessage(Text.literal("§eТочка пройдена: "+a.pointIndex+"/"+q.points.size()));}
                else complete(player,a,q);
            }else if(type.equals("TRAVEL")||type.equals("ROUTE")) player.sendMessage(Text.literal("§6"+directionArrow(player,point)+" §e"+q.name+": "+(int)distance+" блоков"),true);
        }
        sendTargetMarkers(player,markers);
    }

    private static MosvegasConfig.Point point(MosvegasConfig.Quest q,PlayerData.ActiveQuest a,String type){
        if(q.points.isEmpty())return null;
        int index=type.equals("ROUTE")?a.pointIndex:Math.min(a.pointIndex,q.points.size()-1);
        return q.points.get(Math.max(0,index));
    }

    private static double distance(ServerPlayerEntity p,MosvegasConfig.Point point){
        String dimension=p.getWorld().getRegistryKey().getValue().toString();
        if(!Objects.equals(dimension,point.dimension))return Double.MAX_VALUE;
        double dx=p.getX()-point.x,dy=p.getY()-point.y,dz=p.getZ()-point.z;
        return Math.sqrt(dx*dx+dy*dy+dz*dz);
    }

    private static void sendTargetMarkers(ServerPlayerEntity player,List<MosvegasConfig.Point> points){
        PacketByteBuf buffer=PacketByteBufs.create();
        int count=Math.min(32,points.size());
        buffer.writeVarInt(count);
        for(int i=0;i<count;i++){
            MosvegasConfig.Point point=points.get(i);
            buffer.writeDouble(point.x);
            buffer.writeDouble(point.y);
            buffer.writeDouble(point.z);
            buffer.writeDouble(Math.max(1,point.radius));
        }
        ServerPlayNetworking.send(player,MARKERS_PACKET,buffer);
    }

    private static String directionArrow(ServerPlayerEntity player,MosvegasConfig.Point point){
        double dx=point.x-player.getX(),dz=point.z-player.getZ();
        double targetYaw=Math.toDegrees(Math.atan2(-dx,dz));
        double relative=net.minecraft.util.math.MathHelper.wrapDegrees(targetYaw-player.getYaw());
        int sector=(int)Math.round(relative/45.0);sector=((sector%8)+8)%8;
        return switch(sector){case 0->"↑";case 1->"↗";case 2->"→";case 3->"↘";case 4->"↓";case 5->"↙";case 6->"←";default->"↖";};
    }

    public static boolean onUseEntity(ServerPlayerEntity player,Entity entity){
        PlayerData d=DataStore.player(player.getUuid());
        for(PlayerData.ActiveQuest a:new ArrayList<>(d.activeQuests)){
            MosvegasConfig.Quest q=find(a.templateId);
            if(q==null||!"NPC_ITEM".equalsIgnoreCase(q.type)||q.npcName==null||!entity.getName().getString().equalsIgnoreCase(q.npcName))continue;
            openNpc(player,a,q); return true;
        }
        List<MosvegasConfig.Quest> offered=new ArrayList<>();
        boolean isQuestGiver=false;
        String entityName=entity.getName().getString();
        for(MosvegasConfig.Quest q:ConfigManager.get().quests){
            if(q==null||q.giverNpcName==null||q.giverNpcName.isBlank()||!entityName.equalsIgnoreCase(q.giverNpcName))continue;
            isQuestGiver=true;
            if(q.enabled&&isAvailable(d,q)&&!hasActive(d,q.id)&&!hasCompleted(d,q))offered.add(q);
        }
        if(isQuestGiver){openQuestGiver(player,entityName,offered);return true;}
        return false;
    }

    private static void openQuestGiver(ServerPlayerEntity player,String npcName,List<MosvegasConfig.Quest> quests){
        Ui.menu(player,npcName,m->{
            if(quests.isEmpty())m.label(22,Ui.item(Items.BARRIER,"Сейчас нет доступных квестов"));
            int slot=9;
            for(MosvegasConfig.Quest q:quests){
                int s=slot++;
                m.button(s,Ui.item(icon(q.type),q.name+" | "+q.description+" | награда "+EconomyService.money(q.reward)),()->accept(player,q));
                if(slot>=45)break;
            }
        });
    }

    private static void openNpc(ServerPlayerEntity player,PlayerData.ActiveQuest a,MosvegasConfig.Quest q){
        Ui.menu(player,q.npcName,m->{
            Item item=ItemsUtil.byId(a.chosenItem);
            m.label(13,Ui.item(item==Items.AIR?Items.BARRIER:item,"Предмет: "+a.chosenItem));
            m.button(31,Ui.item(Items.EMERALD,"GIVE".equalsIgnoreCase(q.npcMode)?"Передать предмет":"Забрать предмет"),()->{
                if("GIVE".equalsIgnoreCase(q.npcMode)&&!ItemsUtil.take(player,item,1)){player.sendMessage(Text.literal("§cУ вас нет нужного предмета."));return;}
                if("TAKE".equalsIgnoreCase(q.npcMode))ItemsUtil.give(player,new ItemStack(item));
                complete(player,a,q);
            });
        });
    }

    private static void complete(ServerPlayerEntity player,PlayerData.ActiveQuest a,MosvegasConfig.Quest q){
        PlayerData d=DataStore.player(player.getUuid());
        if(!d.activeQuests.remove(a))return;
        if(isStarter(q))d.completedStarterQuests.add(q.id);
        else d.completedFactionQuests.add(q.id);
        d.bankBalance+=q.reward; DataStore.save(); if(!isStarter(q))FactionService.addXp(player,q.factionXp);
        player.closeHandledScreen();
        player.sendMessage(Text.literal("§aКвест выполнен: "+q.name+". Награда: "+EconomyService.money(q.reward)));
    }

    private static boolean hasActive(PlayerData d,String id){return d.activeQuests.stream().anyMatch(a->Objects.equals(a.templateId,id));}
    private static boolean hasCompleted(PlayerData d,MosvegasConfig.Quest q){return (isStarter(q)?d.completedStarterQuests:d.completedFactionQuests).contains(q.id);}
    private static boolean isStarter(MosvegasConfig.Quest q){return q.faction==null||q.faction.isBlank()||"public".equalsIgnoreCase(q.faction)||"starter".equalsIgnoreCase(q.faction);}
    private static boolean isAvailable(PlayerData d,MosvegasConfig.Quest q){return d.faction==null?isStarter(q):!isStarter(q)&&Objects.equals(d.faction,q.faction);}
    private static String progress(MosvegasConfig.Quest q,PlayerData.ActiveQuest a){
        if("ROUTE".equalsIgnoreCase(q.type))return "точка "+(a.pointIndex+1)+"/"+q.points.size();
        if("STAY".equalsIgnoreCase(q.type))return (a.progressTicks/20)+"/"+q.staySeconds+" сек.";
        return q.description;
    }

    public static void openAdmin(ServerPlayerEntity player){
        if(!Permissions.hasAny(player,3,"mosvegas.admin.quests.manage","mosvegas.admin.quests")){player.sendMessage(Text.literal("§cНет прав администратора квестов."));return;}
        Ui.menu(player,"Админ: квесты",m->{
            m.button(49,Ui.item(Items.LIME_DYE,"Создать квест в текущей точке"),()->createWizard(player));
            int slot=9;
            for(MosvegasConfig.Quest q:ConfigManager.get().quests){int s=slot++;m.button(s,Ui.item(q.enabled?Items.LIME_WOOL:Items.RED_WOOL,q.id+": "+q.name),()->{q.enabled=!q.enabled;ConfigManager.save();openAdmin(player);});if(slot>=45)break;}
        });
    }

    private static void createWizard(ServerPlayerEntity p){
        PlayerData d=DataStore.player(p.getUuid());
        Ui.input(p,"ID квеста","quest_id",id->Ui.input(p,"Название","Новый квест",name->Ui.input(p,"Описание","Доберитесь до точки",desc->{
            String safe=id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]","_");
            if(find(safe)!=null){p.sendMessage(Text.literal("§cТакой ID уже существует."));return;}
            MosvegasConfig.Quest q=new MosvegasConfig.Quest(safe,name,desc,"TRAVEL",d.faction);q.points.add(new MosvegasConfig.Point(p.getWorld().getRegistryKey().getValue().toString(),p.getX(),p.getY(),p.getZ(),3));
            ConfigManager.get().quests.add(q);ConfigManager.save();p.sendMessage(Text.literal("§aКвест создан. Тип, точки и варианты предметов можно расширить в конфиге."));openAdmin(p);
        })));
    }
}
