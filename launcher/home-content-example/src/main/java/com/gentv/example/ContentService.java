package com.gentv.example;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import org.json.JSONArray;
import org.json.JSONObject;

/** GHCP 1 public offline sample. NEVER expose personal data using this sample's public-data policy. */
public final class ContentService extends Service {
    private HandlerThread thread;
    private Messenger messenger;
    private Handler incoming;
    private Messenger callback;
    private int requestToken;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener changed=(prefs,key)->{
        if("mode".equals(key)&&incoming!=null)incoming.post(this::invalidate);
    };
    private void invalidate(){
        if(callback==null)return;
        try{Message invalidate=Message.obtain(null,3);invalidate.arg1=requestToken;callback.send(invalidate);}catch(Exception ignored){}
    }
    @Override public void onCreate(){super.onCreate();thread=new HandlerThread("example-content");thread.start();
        incoming=new Handler(thread.getLooper(),request->{
            if(request.what!=1||request.replyTo==null)return true;
            // sendingUid is assigned by Android. Do not trust a caller-supplied package name.
            String[] packages=getPackageManager().getPackagesForUid(request.sendingUid);
            if(packages==null||!java.util.Arrays.asList(packages).contains("com.gentv.tv"))return true;
            try {
                if(request.getData().getInt("protocolMajor")!=1)return true;
                callback=request.replyTo;requestToken=request.arg1;
                String mode=getSharedPreferences("example",MODE_PRIVATE).getString("mode","summary");
                if(mode.equals("timeout"))return true;
                JSONObject snapshot=new JSONObject().put("protocolMajor",1).put("snapshotId",mode+"-1")
                        .put("template",mode.equals("media_row")?"media_row":mode.equals("feature")?"feature":"summary")
                        .put("privacy","public").put("ttlSeconds",30).put("title","今天，也有好心情")
                        .put("subtitle","离线协议示例 · 此处不读取任何个人信息");
                JSONArray items=new JSONArray();
                String target=getPackageName()+"/com.gentv.example.ExampleActivity";
                if(mode.equals("media_row")){
                    snapshot.put("title","接着看 · 六张示例卡片");
                    for(int i=0;i<6;i++)items.put(new JSONObject().put("id","film-"+i).put("title","山与海 · "+(i+1)).put("subtitle","第 "+(i+1)+" 集 · 离线示例")
                            .put("image",resource()).put("progress",(i+1)/7.0).put("activity",target).put("actionId","episode-"+i));
                }else if(mode.equals("feature")){
                    snapshot.put("title","留住这一刻").put("subtitle","原创几何画 · 可选背景异步显示").put("background",resource());
                    items.put(new JSONObject().put("id","photo").put("title","山川之间").put("image",resource()).put("activity",target).put("actionId","album"));
                }else if(!mode.equals("empty")){
                    items.put(new JSONObject().put("id","temperature").put("title","18°").put("subtitle","多云 · 演示数据").put("activity",target).put("actionId","weather"));
                    items.put(new JSONObject().put("id","air").put("title","优良").put("subtitle","空气质量 · 演示数据"));
                    items.put(new JSONObject().put("id","wind").put("title","12 km/h").put("subtitle","风速 · 演示数据"));
                    if(mode.equals("background_failure"))snapshot.put("background","android.resource://"+getPackageName()+"/0");
                }
                snapshot.put("items",items);
                if(mode.equals("expiry"))snapshot.put("ttlSeconds",1);
                if(mode.equals("refresh"))snapshot.put("ttlSeconds",3);
                if(mode.equals("foreign_action"))items.getJSONObject(0).put("activity","com.android.settings/.Settings");
                Message response=Message.obtain(null,2);response.arg1=request.arg1;
                Bundle data=new Bundle();data.putString("snapshot",mode.equals("invalid")?"{ invalid":snapshot.toString());response.setData(data);
                request.replyTo.send(response);
            }catch(Exception ignored){/* Host times out safely. */}
            return true;
        });
        messenger=new Messenger(incoming);
        getSharedPreferences("example",MODE_PRIVATE).registerOnSharedPreferenceChangeListener(changed);
    }
    private String resource(){return "android.resource://"+getPackageName()+"/"+R.raw.landscape;}
    @Override public IBinder onBind(Intent intent){return messenger.getBinder();}
    @Override public void onDestroy(){getSharedPreferences("example",MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(changed);callback=null;thread.quitSafely();super.onDestroy();}
}
