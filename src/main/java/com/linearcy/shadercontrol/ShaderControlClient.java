package com.linearcy.shadercontrol;

import com.google.gson.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public final class ShaderControlClient implements ClientModInitializer {
    public static final String MOD_ID = "shader_control";
    private static final Path CONFIG = Minecraft.getInstance().gameDirectory.toPath().resolve("config/shader_control.json");
    public static boolean overrideEnabled = true;
    public static final Map<Identifier, ShaderFile> FILES = new LinkedHashMap<>();
    public static final Map<Identifier, byte[]> OVERRIDES = new ConcurrentHashMap<>();
    public static KeyMapping OPEN_KEY;
    private static int reloadCooldown = 0;

    @Override public void onInitializeClient() {
        loadConfig();
        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "keys"));
        OPEN_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.shader_control.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, cat));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (reloadCooldown > 0) reloadCooldown--;
            while (OPEN_KEY.consumeClick()) client.setScreen(new ShaderControlScreen(client.screen));
        });
    }

    public static void scan() {
        FILES.clear();
        Minecraft mc = Minecraft.getInstance();
        try {
            Map<Identifier, Resource> found = mc.getResourceManager().listResources("shaders", id -> id.getPath().endsWith(".fsh"));
            for (var e : found.entrySet()) {
                String src = OVERRIDES.containsKey(e.getKey()) ? new String(OVERRIDES.get(e.getKey()), StandardCharsets.UTF_8) : new String(e.getValue().open().readAllBytes(), StandardCharsets.UTF_8);
                ShaderFile sf = ShaderFile.parse(e.getKey(), e.getValue().sourcePackId(), src);
                if (!sf.controls.isEmpty()) FILES.put(e.getKey(), sf);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void apply(ShaderFile file) {
        String out = file.render();
        if (overrideEnabled) OVERRIDES.put(file.id, out.getBytes(StandardCharsets.UTF_8));
        saveConfig();
        scheduleReload();
    }

    public static void clearOverrides() {
        OVERRIDES.clear();
        scheduleReload();
    }

    public static void scheduleReload() {
        if (reloadCooldown > 0) return;
        reloadCooldown = 8;
        Minecraft.getInstance().reloadResourcePacks();
    }

    public static void loadConfig() {
        try {
            if (!Files.exists(CONFIG)) return;
            JsonObject root = JsonParser.parseString(Files.readString(CONFIG)).getAsJsonObject();
            overrideEnabled = root.has("overrideResourcePack") && root.get("overrideResourcePack").getAsBoolean();
            if (root.has("values")) {
                for (var e : root.getAsJsonObject("values").entrySet()) {
                    try {
                        ShaderFileValueKey k = ShaderFileValueKey.parse(e.getKey());
                        OVERRIDES.put(Identifier.parse(k.id), e.getValue().getAsString().getBytes(StandardCharsets.UTF_8));
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    public static void saveConfig() {
        try {
            Files.createDirectories(CONFIG.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("overrideResourcePack", overrideEnabled);
            JsonObject values = new JsonObject();
            for (var e : OVERRIDES.entrySet()) values.addProperty(e.getKey().toString(), new String(e.getValue(), StandardCharsets.UTF_8));
            root.add("values", values);
            Files.writeString(CONFIG, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    public static final class ShaderFileValueKey {
        final String id; ShaderFileValueKey(String id){this.id=id;}
        static ShaderFileValueKey parse(String s){return new ShaderFileValueKey(s);}
    }

    public static final class ShaderFile {
        public final Identifier id; public final String pack; public final String original; public final List<Control> controls;
        ShaderFile(Identifier id,String pack,String original,List<Control> controls){this.id=id;this.pack=pack;this.original=original;this.controls=controls;}
        static ShaderFile parse(Identifier id,String pack,String src){
            List<Control> c=new ArrayList<>();
            Pattern p=Pattern.compile("(?m)^\\s*const\\s+float\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(-?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)\\s*;");
            Matcher m=p.matcher(src);
            while(m.find()){
                String name=m.group(1); double v=Double.parseDouble(m.group(2));
                String label=pretty(name); double min=v-5, max=v+5;
                String directive=findDirective(src,name);
                if(directive!=null){String[] a=directive.split("\\|",-1); label=a[0]; min=Double.parseDouble(a[1]); max=Double.parseDouble(a[2]);}
                if(min==max){min=v-1;max=v+1;}
                c.add(new Control(name,label,min,max,v));
            }
            return new ShaderFile(id,pack,src,c);
        }
        static String findDirective(String src,String name){
            Pattern p=Pattern.compile("(?m)^\\s*//\\s*@sc-slider\\s+"+Pattern.quote(name)+"\\s+([^\\s]+)\\s+(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)");
            Matcher m=p.matcher(src); if(!m.find())return null; return m.group(1)+"|"+m.group(2)+"|"+m.group(3);
        }
        static String pretty(String s){return s.replace('_',' ').toLowerCase().replaceAll("\\b\\w",x->x.group().toUpperCase());}
        String render(){
            String out=original;
            for(Control c:controls){
                String regex="(?m)(^\\s*const\\s+float\\s+"+Pattern.quote(c.name)+"\\s*=\\s*)(-?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)(\\s*;)");
                out=out.replaceFirst(regex, Matcher.quoteReplacement("$1"+format(c.value)+"$3"));
            }
            return out;
        }
        static String format(double v){ if(Double.isNaN(v)||Double.isInfinite(v)) return "0.0"; return String.format(Locale.ROOT,"%.6f",v); }
    }
    public static final class Control { final String name,label; final double min,max,defaultValue; double value; Control(String n,String l,double mi,double ma,double v){name=n;label=l;min=mi;max=ma;defaultValue=v;value=v;} }

    public static class ShaderControlScreen extends Screen {
        private final Screen parent; private List<ShaderFile> files=new ArrayList<>(); private ShaderFile selected; private final List<EditBox> edits=new ArrayList<>(); private int page=0;
        protected ShaderControlScreen(Screen parent){super(Component.literal("Shader Control"));this.parent=parent;}
        @Override protected void init(){ super.init(); scan(); files=new ArrayList<>(FILES.values());
            int left=20, top=38; addRenderableWidget(Button.builder(Component.literal("Refresh Shaders"),b->{scan();rebuild();}).bounds(left,top,120,20).build());
            addRenderableWidget(Button.builder(Component.literal("Override: "+(overrideEnabled?"ON":"OFF")),b->{overrideEnabled=!overrideEnabled;b.setMessage(Component.literal("Override: "+(overrideEnabled?"ON":"OFF"))); if(!overrideEnabled)clearOverrides(); else {for(ShaderFile f:files)apply(f);} saveConfig();}).bounds(left+125,top,120,20).build());
            addRenderableWidget(Button.builder(Component.literal("Close"),b->onClose()).bounds(width-80,top,60,20).build());
            if(!files.isEmpty()){
                if(selected==null || !files.contains(selected)) selected=files.get(0);
                int y=top+30;
                int start=Math.min(page*10,Math.max(0,files.size()-1));
                for(int i=start;i<Math.min(files.size(),start+10);i++){ShaderFile f=files.get(i); addRenderableWidget(Button.builder(Component.literal(shortName(f)),b->{selected=f;rebuild();}).bounds(left,y,250,20).build()); y+=23;}
                if(files.size()>10){addRenderableWidget(Button.builder(Component.literal("<"),b->{page=Math.max(0,page-1);rebuild();}).bounds(left,285,30,20).build());addRenderableWidget(Button.builder(Component.literal(">"),b->{page=Math.min((files.size()-1)/10,page+1);rebuild();}).bounds(left+35,285,30,20).build());}
                buildControls(selected,290, top+30);
            }
        }
        private String shortName(ShaderFile f){return f.pack+" : "+f.id.getPath();}
        private void buildControls(ShaderFile f,int x,int y){
            edits.clear(); int row=0;
            for(Control c:f.controls){ if(row>=9)break; int yy=y+row*34; addRenderableWidget(Button.builder(Component.literal(c.label),b->{}).bounds(x,yy,150,20).build());
                EditBox box=new EditBox(font,x+155,yy,80,20,Component.literal(c.label)); box.setValue(ShaderFile.format(c.value)); box.setFilter(s->s.matches("[-+]?((\\d+(\\.\\d*)?)|(\\.\\d+))([eE][-+]?\\d+)?")||s.equals("-")||s.equals(".")||s.equals("-.")||s.isEmpty()); box.setResponder(s->{try{c.value=Double.parseDouble(s);apply(f);}catch(Exception ignored){}}); addRenderableWidget(box); edits.add(box);
                row++;
            }
            if(f.controls.size()>9)addRenderableWidget(Button.builder(Component.literal("More controls available in shader source"),b->{}).bounds(x,y+row*34,240,20).build());
        }
        private void rebuild(){clearWidgets();init();}
        @Override public void onClose(){saveConfig();minecraft.gui.setScreen(parent);}
        @Override public boolean isPauseScreen(){return false;}
    }
}
