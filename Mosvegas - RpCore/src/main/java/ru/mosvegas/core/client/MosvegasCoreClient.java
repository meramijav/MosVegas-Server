package ru.mosvegas.core.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import ru.mosvegas.core.service.PropertyService;
import ru.mosvegas.core.service.QuestService;

import java.util.ArrayList;
import java.util.List;

public final class MosvegasCoreClient implements ClientModInitializer {
    private static List<Marker> markers=List.of();
    private static long markersExpireAt;
    private static List<Outline> outlines=List.of();
    private static long outlinesExpireAt;

    private record Marker(double x,double y,double z,double radius) {}
    private record Outline(double minX,double minY,double minZ,double maxX,double maxY,double maxZ,int color,byte kind) {}

    @Override public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(QuestService.MARKERS_PACKET,(client,handler,buffer,responseSender)->{
            int count=Math.min(32,buffer.readVarInt());
            List<Marker> received=new ArrayList<>(count);
            for(int i=0;i<count;i++)received.add(new Marker(buffer.readDouble(),buffer.readDouble(),buffer.readDouble(),buffer.readDouble()));
            client.execute(()->{
                markers=List.copyOf(received);
                markersExpireAt=System.currentTimeMillis()+1_000;
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(PropertyService.OUTLINES_PACKET,(client,handler,buffer,responseSender)->{
            int count=Math.min(PropertyService.MAX_OUTLINES,buffer.readVarInt());
            List<Outline> received=new ArrayList<>(count);
            for(int i=0;i<count;i++)received.add(new Outline(buffer.readDouble(),buffer.readDouble(),buffer.readDouble(),buffer.readDouble(),buffer.readDouble(),buffer.readDouble(),buffer.readInt(),buffer.readByte()));
            client.execute(()->{
                outlines=List.copyOf(received);
                outlinesExpireAt=System.currentTimeMillis()+1_000;
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{
            markers=List.of();
            outlines=List.of();
        });
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context,hitResult)->!replacesVanillaOutline(context,hitResult));
        WorldRenderEvents.LAST.register(MosvegasCoreClient::renderWorldOverlays);
    }

    private static boolean replacesVanillaOutline(WorldRenderContext context,HitResult hitResult) {
        if(outlines.isEmpty()||System.currentTimeMillis()>outlinesExpireAt)return false;
        boolean wand=context.camera().getFocusedEntity() instanceof net.minecraft.client.network.ClientPlayerEntity player&&(PropertyService.isWand(player.getMainHandStack())||PropertyService.isWand(player.getOffHandStack()));
        if(wand&&outlines.stream().anyMatch(outline->outline.kind==2))return true;
        if(!(hitResult instanceof BlockHitResult blockHit))return false;
        double minX=blockHit.getBlockPos().getX(),minY=blockHit.getBlockPos().getY(),minZ=blockHit.getBlockPos().getZ();
        return outlines.stream().anyMatch(outline->outline.kind==0&&outline.maxX>minX&&outline.minX<minX+1&&outline.maxY>minY&&outline.minY<minY+1&&outline.maxZ>minZ&&outline.minZ<minZ+1);
    }

    private static void renderWorldOverlays(WorldRenderContext context) {
        long now=System.currentTimeMillis();
        boolean renderMarkers=!markers.isEmpty()&&now<=markersExpireAt;
        boolean renderOutlines=!outlines.isEmpty()&&now<=outlinesExpireAt;
        if(!renderMarkers&&!renderOutlines)return;
        MatrixStack matrices=context.matrixStack();
        Vec3d camera=context.camera().getPos();
        matrices.push();
        matrices.translate(-camera.x,-camera.y,-camera.z);
        Matrix4f matrix=matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(2.5f);

        BufferBuilder buffer=Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.DEBUG_LINES,VertexFormats.POSITION_COLOR);
        double time=context.world().getTime()+context.tickDelta();
        if(renderMarkers)for(Marker marker:markers)drawMarker(buffer,matrix,marker,time);
        if(renderOutlines)for(Outline outline:outlines)drawOutline(buffer,matrix,outline,time);
        Tessellator.getInstance().draw();

        RenderSystem.lineWidth(1f);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        matrices.pop();
    }

    private static void drawOutline(BufferBuilder buffer,Matrix4f matrix,Outline outline,double time) {
        int red=outline.color>>16&255,green=outline.color>>8&255,blue=outline.color&255;
        int alpha=outline.kind==0?(int)(210+35*Math.sin(time*.18)):225;
        double inset=.003;
        double minX=outline.minX-inset,minY=outline.minY-inset,minZ=outline.minZ-inset;
        double maxX=outline.maxX+inset,maxY=outline.maxY+inset,maxZ=outline.maxZ+inset;
        box(buffer,matrix,minX,minY,minZ,maxX,maxY,maxZ,red,green,blue,alpha);
        cornerAccents(buffer,matrix,minX,minY,minZ,maxX,maxY,maxZ,red,green,blue,255);
        if(outline.kind==0){
            double scan=minY+(maxY-minY)*(.5+.36*Math.sin(time*.08));
            rectangle(buffer,matrix,minX,scan,minZ,maxX,maxZ,red,green,blue,190);
        }
    }

    private static void box(BufferBuilder buffer,Matrix4f matrix,double minX,double minY,double minZ,double maxX,double maxY,double maxZ,int red,int green,int blue,int alpha) {
        rectangle(buffer,matrix,minX,minY,minZ,maxX,maxZ,red,green,blue,alpha);
        rectangle(buffer,matrix,minX,maxY,minZ,maxX,maxZ,red,green,blue,alpha);
        line(buffer,matrix,minX,minY,minZ,minX,maxY,minZ,red,green,blue,alpha);
        line(buffer,matrix,maxX,minY,minZ,maxX,maxY,minZ,red,green,blue,alpha);
        line(buffer,matrix,minX,minY,maxZ,minX,maxY,maxZ,red,green,blue,alpha);
        line(buffer,matrix,maxX,minY,maxZ,maxX,maxY,maxZ,red,green,blue,alpha);
    }

    private static void rectangle(BufferBuilder buffer,Matrix4f matrix,double minX,double y,double minZ,double maxX,double maxZ,int red,int green,int blue,int alpha) {
        line(buffer,matrix,minX,y,minZ,maxX,y,minZ,red,green,blue,alpha);
        line(buffer,matrix,maxX,y,minZ,maxX,y,maxZ,red,green,blue,alpha);
        line(buffer,matrix,maxX,y,maxZ,minX,y,maxZ,red,green,blue,alpha);
        line(buffer,matrix,minX,y,maxZ,minX,y,minZ,red,green,blue,alpha);
    }

    private static void cornerAccents(BufferBuilder buffer,Matrix4f matrix,double minX,double minY,double minZ,double maxX,double maxY,double maxZ,int red,int green,int blue,int alpha) {
        double length=Math.min(.32,Math.min(maxX-minX,Math.min(maxY-minY,maxZ-minZ))*.28);
        for(int xi=0;xi<=1;xi++)for(int yi=0;yi<=1;yi++)for(int zi=0;zi<=1;zi++){
            double x=xi==0?minX:maxX,y=yi==0?minY:maxY,z=zi==0?minZ:maxZ;
            line(buffer,matrix,x,y,z,x+(xi==0?length:-length),y,z,red,green,blue,alpha);
            line(buffer,matrix,x,y,z,x,y+(yi==0?length:-length),z,red,green,blue,alpha);
            line(buffer,matrix,x,y,z,x,y,z+(zi==0?length:-length),red,green,blue,alpha);
        }
    }

    private static void drawMarker(BufferBuilder buffer,Matrix4f matrix,Marker marker,double time) {
        double radius=Math.max(1,marker.radius);
        double ground=marker.y+.08;
        double pulse=.82+.12*Math.sin(time*.12);
        double inner=Math.max(.55,radius*pulse);
        double rotation=time*.035;

        circle(buffer,matrix,marker.x,ground,marker.z,radius,0,210,255,230,64);
        circle(buffer,matrix,marker.x,ground+.035,marker.z,inner,255,205,55,245,48,rotation);

        double top=marker.y+3.25;
        line(buffer,matrix,marker.x,ground,marker.z,marker.x,top,marker.z,255,235,120,235);
        double diamond=.42;
        line(buffer,matrix,marker.x-diamond,top-diamond,marker.z,marker.x,top,marker.z,0,225,255,245);
        line(buffer,matrix,marker.x,top,marker.z,marker.x+diamond,top-diamond,marker.z,0,225,255,245);
        line(buffer,matrix,marker.x+diamond,top-diamond,marker.z,marker.x,top-diamond*2,marker.z,0,225,255,245);
        line(buffer,matrix,marker.x,top-diamond*2,marker.z,marker.x-diamond,top-diamond,marker.z,0,225,255,245);
        line(buffer,matrix,marker.x,top-diamond,marker.z-diamond,marker.x,top,marker.z,0,225,255,245);
        line(buffer,matrix,marker.x,top,marker.z,marker.x,top-diamond,marker.z+diamond,0,225,255,245);
        line(buffer,matrix,marker.x,top-diamond,marker.z+diamond,marker.x,top-diamond*2,marker.z,0,225,255,245);
        line(buffer,matrix,marker.x,top-diamond*2,marker.z,marker.x,top-diamond,marker.z-diamond,0,225,255,245);
    }

    private static void circle(BufferBuilder buffer,Matrix4f matrix,double cx,double y,double cz,double radius,int red,int green,int blue,int alpha,int segments) {
        circle(buffer,matrix,cx,y,cz,radius,red,green,blue,alpha,segments,0);
    }

    private static void circle(BufferBuilder buffer,Matrix4f matrix,double cx,double y,double cz,double radius,int red,int green,int blue,int alpha,int segments,double rotation) {
        for(int i=0;i<segments;i++){
            double a=rotation+Math.PI*2*i/segments;
            double b=rotation+Math.PI*2*(i+1)/segments;
            line(buffer,matrix,cx+Math.cos(a)*radius,y,cz+Math.sin(a)*radius,cx+Math.cos(b)*radius,y,cz+Math.sin(b)*radius,red,green,blue,alpha);
        }
    }

    private static void line(BufferBuilder buffer,Matrix4f matrix,double ax,double ay,double az,double bx,double by,double bz,int red,int green,int blue,int alpha) {
        buffer.vertex(matrix,(float)ax,(float)ay,(float)az).color(red,green,blue,alpha).next();
        buffer.vertex(matrix,(float)bx,(float)by,(float)bz).color(red,green,blue,alpha).next();
    }
}
