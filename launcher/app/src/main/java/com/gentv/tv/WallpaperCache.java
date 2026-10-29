package com.gentv.tv;

import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Bounded, app-private persistent images keyed by the complete versioned URL. Worker thread only. */
final class WallpaperCache {
    static final long LIMIT=64L*1024*1024;
    private final File directory;
    private final AtomicFile manifest;
    private final String endpoint;
    private final List<String> retained = new ArrayList<>();
    String etag = "";
    WallpaperCache(File directory,String endpoint) {
        this.directory=directory;this.endpoint=endpoint;
        directory.mkdirs();manifest=new AtomicFile(new File(directory,"playlist.json"));
    }
    JSONObject state() throws Exception {
        byte[] data=manifest.readFully();if(data.length>512*1024)throw new IOException("Manifest too large");
        JSONObject state=new JSONObject(new String(data,StandardCharsets.UTF_8));
        if(!endpoint.equals(state.optString("endpoint")))throw new IOException("Different source");
        etag=state.optString("etag", "");
        JSONArray saved=state.optJSONArray("cached");
        if(saved==null)saved=state.optJSONArray("wallpapers");
        if(saved!=null)for(int i=0;i<saved.length();i++) {
            String url=saved.getJSONObject(i).getString("url");
            if(!retained.contains(url))retained.add(url);
        }
        return state;
    }
    void saveState(List<String> urls,long fetchedAt,String selected) throws Exception {
        JSONArray array=new JSONArray();for(String url:urls)array.put(new JSONObject().put("url",url));
        JSONArray cached=new JSONArray();for(String url:available(urls))cached.put(new JSONObject().put("url",url));
        retained.clear();for(int i=0;i<cached.length();i++)retained.add(cached.getJSONObject(i).getString("url"));
        write(manifest,new JSONObject().put("cached",cached).put("etag",etag).put("endpoint",endpoint).put("wallpapers",array).put("fetchedAt",fetchedAt).put("selected",selected).toString().getBytes(StandardCharsets.UTF_8));
    }
    byte[] read(String url) throws Exception {
        File file=file(url);if(!file.isFile() || file.length()>8*1024*1024)return null;
        byte[] data=new AtomicFile(file).readFully();file.setLastModified(System.currentTimeMillis());return data;
    }
    void write(String url,byte[] data) throws Exception {
        write(new AtomicFile(file(url)),data);if(!retained.contains(url))retained.add(url);
    }
    byte[] policy() throws IOException {
        try {return new AtomicFile(new File(directory,"traffic.properties")).readFully();}
        catch(FileNotFoundException absent){return null;}
    }
    void savePolicy(WallpaperPolicy policy) throws IOException {
        write(new AtomicFile(new File(directory,"traffic.properties")),policy.bytes());
    }
    List<String> available(List<String> urls) throws Exception {
        List<String> result=new ArrayList<>();
        for(String url:urls)if(file(url).isFile() && !result.contains(url))result.add(url);
        for(String url:retained)if(file(url).isFile() && !result.contains(url))result.add(url);
        return result;
    }
    void remove(String url) throws Exception {new AtomicFile(file(url)).delete();}
    long used() {
        long total=0;File[] files=directory.listFiles((d,n)->n.endsWith(".img"));
        if(files!=null)for(File f:files)total+=f.length();return total;
    }
    boolean needsReplacement(int bytes) {
        File[] files=directory.listFiles((d,n)->n.endsWith(".img"));
        return used()+bytes>LIMIT || files!=null && files.length>=100;
    }
    boolean store(String url,byte[] data,String selected,boolean replace) throws Exception {
        if(data.length>LIMIT || needsReplacement(data.length) && !replace)return false;
        File[] files=directory.listFiles((d,n)->n.endsWith(".img"));
        if(files!=null) {
            Arrays.sort(files,Comparator.comparingLong(File::lastModified));
            long removable=0;
            for(File f:files)if(!f.equals(file(selected)))removable+=f.length();
            if(used()+data.length-removable>LIMIT)return false;
            for(File f:files)if(needsReplacement(data.length) && !f.equals(file(selected)))new AtomicFile(f).delete();
        }
        write(url,data);return true;
    }
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
        File[] files=directory.listFiles((dir,name)->name.endsWith(".img"));if(files==null)return;
        Arrays.sort(files,Comparator.comparingLong(File::lastModified));long total=0;
        for(File file:files)total+=file.length();
        for(File file:files)if(total>LIMIT && file.isFile()){total-=file.length();new AtomicFile(file).delete();}
    }
}
