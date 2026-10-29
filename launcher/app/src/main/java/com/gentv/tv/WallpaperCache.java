package com.gentv.tv;

import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Bounded, app-private persistent images keyed by the complete versioned URL. Worker thread only. */
final class WallpaperCache {
    private static final long LIMIT=64L*1024*1024;
    private final File directory;
    private final AtomicFile manifest;
    private final String endpoint;
    WallpaperCache(File directory,String endpoint) {
        this.directory=directory;this.endpoint=endpoint;
        directory.mkdirs();manifest=new AtomicFile(new File(directory,"playlist.json"));
    }
    JSONObject state() throws Exception {
        byte[] data=manifest.readFully();if(data.length>256*1024)throw new IOException("Manifest too large");
        JSONObject state=new JSONObject(new String(data,StandardCharsets.UTF_8));
        if(!endpoint.equals(state.optString("endpoint")))throw new IOException("Different source");return state;
    }
    void saveState(List<String> urls,long fetchedAt,String selected) throws Exception {
        JSONArray array=new JSONArray();for(String url:urls)array.put(new JSONObject().put("url",url));
        write(manifest,new JSONObject().put("endpoint",endpoint).put("wallpapers",array).put("fetchedAt",fetchedAt).put("selected",selected).toString().getBytes(StandardCharsets.UTF_8));
    }
    byte[] read(String url) throws Exception {
        File file=file(url);if(!file.isFile() || file.length()>8*1024*1024)return null;
        byte[] data=new AtomicFile(file).readFully();file.setLastModified(System.currentTimeMillis());return data;
    }
    void write(String url,byte[] data) throws Exception {write(new AtomicFile(file(url)),data);}
    private void write(AtomicFile target,byte[] data) throws IOException {
        FileOutputStream out=null;
        try {out=target.startWrite();out.write(data);target.finishWrite(out);}
        catch(IOException e){if(out!=null)target.failWrite(out);throw e;}
    }
    private File file(String url) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
        StringBuilder name=new StringBuilder();for(byte b:digest)name.append(String.format(Locale.ROOT,"%02x",b&255));
        return new File(directory,name+".img");
    }
    void prune(List<String> urls) throws Exception {
        Set<String> keep=new HashSet<>();for(String url:urls)keep.add(file(url).getName());
        File[] files=directory.listFiles((dir,name)->name.endsWith(".img"));if(files==null)return;
        Arrays.sort(files,Comparator.comparingLong(File::lastModified));long total=0;
        for(File file:files) {if(!keep.contains(file.getName()))new AtomicFile(file).delete();else total+=file.length();}
        for(File file:files)if(total>LIMIT && file.isFile()){total-=file.length();new AtomicFile(file).delete();}
    }
}
