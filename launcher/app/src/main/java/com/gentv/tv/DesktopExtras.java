package com.gentv.tv;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Base64;
import android.util.LruCache;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** Bounded, offline layout extras. All bytes live in the same atomic/undo transaction. */
final class DesktopExtras {
    static final int IMAGE_BYTES = 12 * 1024, TOTAL_IMAGE_BYTES = 24 * 1024;
    private static final LruCache<String, Bitmap> images = new LruCache<String, Bitmap>(512 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getAllocationByteCount(); }
    };
    static void clearImages() { images.evictAll(); }
    static boolean folder(String key) { return key != null && key.matches("com\\.gentv\\.home/Folder\\.[a-f0-9]{32}"); }
    static String newFolderKey() { return "com.gentv.tv/Folder." + java.util.UUID.randomUUID().toString().replace("-", ""); }
    static void name(String text) {
        if(text.trim().isEmpty() || text.length()>60) throw new IllegalArgumentException("Invalid folder name");
        for(int i=0;i<text.length();i++) if(Character.isISOControl(text.charAt(i)) || Character.getType(text.charAt(i))==Character.FORMAT)
            throw new IllegalArgumentException("Invalid folder name");
    }
    static void validate(JSONObject profile) throws Exception {
        JSONObject folders=profile.optJSONObject("folders");
        if(profile.has("folders") && folders==null) throw new IllegalArgumentException("Invalid folders");
        Set<String> members=new HashSet<>();
        if(folders!=null) {
            if(folders.length()>30) throw new IllegalArgumentException("Too many folders");
            for(Iterator<String> it=folders.keys();it.hasNext();) {
                String key=it.next(); if(!folder(key)) throw new IllegalArgumentException("Invalid folder ID");
                JSONObject f=folders.getJSONObject(key);
                if(f.length()!=2 || !(f.get("name") instanceof String)) throw new IllegalArgumentException("Invalid folder");
                name(f.getString("name")); JSONArray apps=f.getJSONArray("apps");
                if(apps.length()>100) throw new IllegalArgumentException("Too many folder apps");
                for(int i=0;i<apps.length();i++) {
                    Object raw=apps.get(i);
                    if(!(raw instanceof String) || !DesktopStore.validKey((String)raw)
                            || ComponentName.unflattenFromString((String)raw).getPackageName().equals("com.gentv.tv")
                            || !members.add((String)raw)) throw new IllegalArgumentException("Duplicate, nested or invalid folder app");
                }
            }
        }
        JSONObject pictures=profile.optJSONObject("images");
        if(profile.has("images") && pictures==null) throw new IllegalArgumentException("Invalid images");
        int total=0;
        if(pictures!=null) {
            if(pictures.length()>12) throw new IllegalArgumentException("Too many images");
            for(Iterator<String> it=pictures.keys();it.hasNext();) {
                String key=it.next();
                if(!DesktopStore.validKey(key) || !(pictures.get(key) instanceof String)
                        || ComponentName.unflattenFromString(key).getPackageName().equals("com.gentv.tv")) throw new IllegalArgumentException("Invalid image app");
                byte[] bytes=bytes(pictures.getString(key)); total+=bytes.length;
                if(total>TOTAL_IMAGE_BYTES) throw new IllegalArgumentException("Images exceed 24 KiB total");
                Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                if(bitmap==null) throw new IllegalArgumentException("Unreadable image");
                bitmap.recycle();
            }
        }
    }
    private static byte[] bytes(String value) {
        if(value.length()>((IMAGE_BYTES+2)/3)*4 || !value.matches("[A-Za-z0-9+/]+={0,2}")) throw new IllegalArgumentException("Invalid image encoding");
        byte[] b=Base64.decode(value,Base64.NO_WRAP);
        if(b.length<4 || b.length>IMAGE_BYTES || b[0]!=(byte)0xff || b[1]!=(byte)0xd8 || b[b.length-2]!=(byte)0xff || b[b.length-1]!=(byte)0xd9)
            throw new IllegalArgumentException("JPEG required");
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(b,0,b.length,bounds);
        if(bounds.outWidth<1 || bounds.outHeight<1 || bounds.outWidth>320 || bounds.outHeight>180)
            throw new IllegalArgumentException("Image exceeds 320 x 180");
        return b;
    }
    static Drawable image(Context ctx,String key) {
        String encoded=DesktopStore.imageData(ctx,key);
        if(encoded.isEmpty()) return null;
        try {
            Bitmap bitmap=images.get(encoded);
            if(bitmap==null) { byte[] b=bytes(encoded);bitmap=BitmapFactory.decodeByteArray(b,0,b.length);if(bitmap==null)return null;images.put(encoded,bitmap); }
            return new BitmapDrawable(ctx.getResources(),bitmap);
        } catch(Exception e) { return null; }
    }
    static Set<String> members(JSONObject folders) {
        Set<String> members=new HashSet<>();
        for(Iterator<String> it=folders.keys();it.hasNext();) {
            JSONArray a=folders.optJSONObject(it.next()).optJSONArray("apps");
            for(int i=0;i<a.length();i++) members.add(a.optString(i));
        }
        return members;
    }
}
