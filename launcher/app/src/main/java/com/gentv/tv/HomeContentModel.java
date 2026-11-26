package com.gentv.tv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bounded GHCP 1 JSON. No Android views, intents or app-defined Parcelables cross this boundary. */
final class HomeContentModel {
    static final String CHANNEL_PREVIEW = "channel_preview";
    static final int MAX_BYTES = 65536;
    static final int MAX_ITEMS = 6;
    static final int MAX_TTL_SECONDS = 300;
    final String id, template, title, subtitle, privacy, background;
    final int ttlSeconds;
    final List<Item> items;
    static final class Item {
        final String id, title, subtitle, image, activity, actionId, video;
        final double progress;
        Item(String id, String title, String subtitle, String image, String activity, String actionId, double progress, String video) {
            this.id=id; this.title=title; this.subtitle=subtitle; this.image=image;
            this.activity=activity; this.actionId=actionId; this.progress=progress; this.video=video;
        }
    }
    private HomeContentModel(String id, String template, String title, String subtitle, String privacy,
                             String background, int ttl, List<Item> items) {
        this.id=id; this.template=template; this.title=title; this.subtitle=subtitle;
        this.privacy=privacy; this.background=background; this.ttlSeconds=ttl;
        this.items=Collections.unmodifiableList(items);
    }
    static HomeContentModel parse(String json) throws JSONException {
        if (json == null || json.length()>MAX_BYTES || json.getBytes(java.nio.charset.Charset.forName("UTF-8")).length>MAX_BYTES)
            throw new JSONException("snapshot size");
        checkStructure(json);
        JSONTokener tokens=new JSONTokener(json);
        Object value=tokens.nextValue();
        if (!(value instanceof JSONObject) || tokens.nextClean()!=0) throw new JSONException("snapshot object");
        JSONObject root=(JSONObject)value;
        if (integer(root,"protocolMajor",1,1)!=1) throw new JSONException("major");
        String id=text(root,"snapshotId",80,true), template=text(root,"template",20,true);
        if (!template.equals("media_row")&&!template.equals("summary")&&!template.equals("feature")&&!template.equals(CHANNEL_PREVIEW)) throw new JSONException("template");
        String privacy=text(root,"privacy",12,true);
        if (!privacy.equals("public")&&!privacy.equals("personal")) throw new JSONException("privacy");
        int ttl=integer(root,"ttlSeconds",1,MAX_TTL_SECONDS);
        String title=text(root,"title",80,true), subtitle=text(root,"subtitle",160,false);
        String background=image(root,"background");
        Object raw=root.opt("items");
        if (!(raw instanceof JSONArray)) throw new JSONException("items");
        JSONArray array=(JSONArray)raw;
        boolean channels=template.equals(CHANNEL_PREVIEW);
        int max=template.equals("media_row")?6:template.equals("summary")||channels?3:1;
        if (channels && (!privacy.equals("public") || !background.isEmpty())) throw new JSONException("channel preview scope");
        if (array.length()>max) throw new JSONException("item count");
        List<Item> items=new ArrayList<>(); Set<String> ids=new HashSet<>();
        for (int i=0;i<array.length();i++) {
            JSONObject item=array.getJSONObject(i);
            String itemId=text(item,"id",80,true);
            if (!ids.add(itemId)) throw new JSONException("duplicate item");
            String activity=text(item,"activity",240,false), action=text(item,"actionId",80,false);
            if (!action.isEmpty()&&activity.isEmpty()) throw new JSONException("action without activity");
            double progress=-1;
            if (item.has("progress")) {
                Object p=item.get("progress");
                if (!(p instanceof Number)) throw new JSONException("progress");
                progress=((Number)p).doubleValue();
                if (Double.isNaN(progress)||Double.isInfinite(progress)||progress<0||progress>1) throw new JSONException("progress");
            }
            String video=channels?text(item,"video",1024,true):"";
            if (channels && (!video.matches("android\\.resource://[A-Za-z0-9_.]+/[1-9][0-9]{0,9}")
                    || !activity.isEmpty())) throw new JSONException("channel preview item");
            items.add(new Item(itemId,text(item,"title",80,true),text(item,"subtitle",160,false),
                    image(item,"image"),activity,action,progress,video));
        }
        return new HomeContentModel(id,template,title,subtitle,privacy,background,ttl,items);
    }
    private static void checkStructure(String json) throws JSONException {
        // Reject deep trees before Android's recursive parser touches them, and reject duplicate keys.
        List<Set<String>> objects=new ArrayList<>(); int depth=0;
        for(int i=0;i<json.length();i++) {
            char c=json.charAt(i);
            if(c=='{'||c=='[') {if(++depth>12)throw new JSONException("depth");objects.add(c=='{'?new HashSet<>():null);}
            else if(c=='}'||c==']') {if(--depth<0||objects.isEmpty())throw new JSONException("depth");objects.remove(objects.size()-1);}
            else if(c=='"') {
                int start=i;boolean escaped=false;
                while(++i<json.length()) {char next=json.charAt(i);if(next=='"'&&!escaped)break;if(next=='\\'&&!escaped)escaped=true;else escaped=false;}
                if(i>=json.length())throw new JSONException("string");
                int next=i+1;while(next<json.length()&&Character.isWhitespace(json.charAt(next)))next++;
                if(next<json.length()&&json.charAt(next)==':'&&!objects.isEmpty()) {
                    Set<String> keys=objects.get(objects.size()-1);
                    Object key=new JSONTokener(json.substring(start,i+1)).nextValue();
                    if(keys==null||!(key instanceof String)||!keys.add((String)key))throw new JSONException("duplicate key");
                }
            }
            else if(c=='\''||c=='/'||c=='#') throw new JSONException("non JSON syntax");
        }
        if(depth!=0)throw new JSONException("depth");
    }
    static int integer(JSONObject object,String key,int min,int max) throws JSONException {
        Object raw=object.get(key);
        if (!(raw instanceof Integer)&&!(raw instanceof Long)) throw new JSONException(key);
        long n=((Number)raw).longValue();
        if(n<min||n>max)throw new JSONException(key);
        return (int)n;
    }
    static String text(JSONObject object,String key,int max,boolean required) throws JSONException {
        if (!object.has(key)) { if(required)throw new JSONException(key); return ""; }
        Object raw=object.get(key);
        if (!(raw instanceof String)) throw new JSONException(key);
        String s=(String)raw;
        if(s.length()>max||(required&&s.trim().isEmpty()))throw new JSONException(key);
        for(int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            if(Character.isISOControl(c)||c=='\u202a'||c=='\u202b'||c=='\u202d'||c=='\u202e'||c=='\u202c'
                    ||c>='\u2066'&&c<='\u2069')throw new JSONException(key);
        }
        return s;
    }
    private static String image(JSONObject object,String key) throws JSONException {
        if (!object.has(key)||object.isNull(key)) return "";
        String s=text(object,key,1024,false);
        if (!s.isEmpty()&&!s.startsWith("content://")&&!s.startsWith("android.resource://"))throw new JSONException(key);
        return s;
    }
}
