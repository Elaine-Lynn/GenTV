package com.gentv.tv;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Runs only on an isolated emulator with the reference provider installed. No real-TV or Home changes. */
public final class HomeContentIntegration {
    private final Instrumentation runner;
    private HomeContentIntegration(Instrumentation runner){this.runner=runner;}
    static void run(Instrumentation runner){new HomeContentIntegration(runner).run();}
    private static final String KEY="com.gentv.example.homecontent/com.gentv.example.ExampleActivity";
    private int checks;
    private void check(boolean okay,String why){if(!okay)throw new AssertionError(why);checks++;}
    private Object field(Object object,String name) throws Exception {Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private View text(View v,String needle){if(v instanceof TextView&&((TextView)v).getText().toString().contains(needle))return v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=text(((ViewGroup)v).getChildAt(i),needle);if(found!=null)return found;}return null;}
    private void ui(Runnable action){Throwable[] failed={null};runner.runOnMainSync(()->{try{action.run();}catch(Throwable e){failed[0]=e;}});if(failed[0]!=null)throw new AssertionError(failed[0]);}
    private boolean accessibleText(android.view.accessibility.AccessibilityNodeInfo node,String needle){
        if(node==null)return false;
        if(node.getText()!=null&&node.getText().toString().contains(needle))return true;
        for(int i=0;i<node.getChildCount();i++)if(accessibleText(node.getChild(i),needle))return true;
        return false;
    }
    private void key(int code){
        android.app.UiAutomation automation=runner.getUiAutomation();long time=android.os.SystemClock.uptimeMillis();
        boolean down=false,up;
        try{down=automation.injectInputEvent(new KeyEvent(time,time,KeyEvent.ACTION_DOWN,code,0),true);}
        finally{up=automation.injectInputEvent(new KeyEvent(time,android.os.SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,code,0),true);}
        if(!down||!up)throw new AssertionError("key injection failed: "+code);
    }
    private void awaitTemplate(HomeContentController controller,String mode) throws Exception {
        long until=android.os.SystemClock.elapsedRealtime()+5000;
        boolean[] ready={false};
        while(android.os.SystemClock.elapsedRealtime()<until){
            ui(()->{try{HomeContentModel model=(HomeContentModel)field(controller,"snapshot");ready[0]=controller.visible()&&model!=null&&model.id.equals(mode+"-1");}catch(Exception e){throw new AssertionError(e);}});
            if(ready[0])return;Thread.sleep(50);
        }
        throw new AssertionError("template not ready: "+mode+" after "+checks+" checks; key="+field(controller,"key")+", snapshot="+field(controller,"snapshot"));
    }
    private boolean awaitAccessibleText(String text) throws Exception {
        long until=android.os.SystemClock.elapsedRealtime()+4000;
        while(android.os.SystemClock.elapsedRealtime()<until){if(accessibleText(runner.getUiAutomation().getRootInActiveWindow(),text))return true;Thread.sleep(100);}
        return false;
    }
    private void mode(String mode) {
        runner.getTargetContext().startActivity(new Intent().setComponent(new ComponentName("com.gentv.example.homecontent","com.gentv.example.ExampleActivity"))
                .putExtra("example.mode",mode).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }
    private void run(){
        Bundle result=new Bundle();int status=0;Activity home=null;
        try {
            if(!(android.os.Build.HARDWARE.contains("ranchu")||android.os.Build.HARDWARE.contains("goldfish")))throw new IllegalStateException("emulator only");
            android.app.UiAutomation automation=runner.getUiAutomation();
            for(int code:new int[]{KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_BACK,KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT}){
                long time=android.os.SystemClock.uptimeMillis();automation.injectInputEvent(new KeyEvent(time,time,KeyEvent.ACTION_UP,code,0),true);
            }
            runner.setInTouchMode(false); // AOSP phone/tablet images must start in remote navigation mode.
            android.content.Context ctx=runner.getTargetContext();
            HomeContentRegistry.Extension e=HomeContentRegistry.find(ctx,KEY);check(e!=null,"reference provider discovered");
            HomeContentRegistry.allow(ctx,e,false);check(!HomeContentRegistry.allowed(ctx,e),"unapproved provider is disabled");
            HomeContentRegistry.allow(ctx,e,true);check(HomeContentRegistry.allowed(ctx,e),"signer-bound permission saved");
            // This fixture owns the fresh emulator's app data; no stock Home or device settings are changed.
            DesktopStore.initialize(ctx);Config cfg=Config.load(ctx);
            List<String> order=new ArrayList<>();order.add(KEY);
            check(Order.save(Order.file(ctx),new Order(Order.dockKey(cfg.dockApps),order,1)),"fixture first-row order");
            mode("summary");Thread.sleep(300);
            home=runner.startActivitySync(new Intent(ctx,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Thread.sleep(1700);Activity page=home;
            HomeContentController controller=(HomeContentController)field(page,"homeContent");
            awaitTemplate(controller,"summary");
            ui(()->{try {check(controller.visible(),"real service response rendered: height="+field(controller,"availableHeight")+", key="+field(controller,"key")+", focus="+page.getCurrentFocus()+", window="+page.hasWindowFocus());}catch(Exception ex){throw new AssertionError(ex);}
                check(text(page.findViewById(android.R.id.content),"18°")!=null,"summary metric rendered");
                check(controller.enter(),"Up path can focus first content action");check(controller.inContent(),"content owns focus");});
            key(KeyEvent.KEYCODE_BACK);Thread.sleep(200);
            ui(()->check(!controller.inContent(),"Back returns to tile without opening screensaver"));
            check(!page.isFinishing(),"Home remains open");
            ui(()->{HomeContentRegistry.allow(ctx,e,false);controller.stop(true);check(!controller.visible(),"revocation clears content");});
            Thread.sleep(1500);ui(()->check(!controller.visible(),"revoked provider stays hidden"));
            HomeContentRegistry.allow(ctx,e,true);
            mode("media_row");awaitTemplate(controller,"media_row");
            ui(()->{check(controller.visible(),"media row rendered");check(text(page.findViewById(android.R.id.content),"GenTV Content Example")!=null,"host source attribution");check(text(page.findViewById(android.R.id.content),"山与海 · 6")!=null,"sixth item exists, not discarded");check(controller.enter(),"media entry focus");});
            for(int i=0;i<5;i++)key(KeyEvent.KEYCODE_DPAD_RIGHT);
            ui(()->check("film-5".equals(page.getCurrentFocus().getTag()),"sixth card reachable with remote"));
            key(KeyEvent.KEYCODE_DPAD_DOWN);Thread.sleep(100);
            mode("feature");awaitTemplate(controller,"feature");Thread.sleep(1000);
            ui(()->{check(controller.visible(),"feature rendered");try{ViewGroup bg=(ViewGroup)field(controller,"backgrounds");
                check(bg.getChildCount()==1,"optional background loaded asynchronously");
                android.widget.ImageView art=(android.widget.ImageView)bg.getChildAt(0);
                check(art.getWidth()>0&&art.getHeight()>0&&art.getAlpha()>.95f&&art.getImageAlpha()==255,
                        "optional background drawable is actually visible");}catch(Exception err){throw new AssertionError(err);}});
            Object beforeRefresh=field(controller,"snapshot");
            ui(()->{try{java.lang.reflect.Method method=controller.getClass().getDeclaredMethod("refreshSnapshot");method.setAccessible(true);method.invoke(controller);}catch(Exception err){throw new AssertionError(err);}});
            long refreshDeadline=android.os.SystemClock.elapsedRealtime()+4000;boolean[] replaced={false};
            while(android.os.SystemClock.elapsedRealtime()<refreshDeadline){ui(()->{try{replaced[0]=field(controller,"snapshot")!=beforeRefresh;}catch(Exception err){throw new AssertionError(err);}});if(replaced[0])break;Thread.sleep(50);}
            Thread.sleep(500);
            ui(()->{try{check(replaced[0]&&controller.visible()&&((java.util.List<?>)field(controller,"images")).size()<=2,"image refresh releases retired snapshot bitmaps");}catch(Exception err){throw new AssertionError(err);}});
            mode("background_failure");awaitTemplate(controller,"background_failure");Thread.sleep(750);
            ui(()->{check(controller.visible(),"bad optional background preserves summary");try{check(((ViewGroup)field(controller,"backgrounds")).getChildCount()==0,"bad background restores wallpaper");}catch(Exception err){throw new AssertionError(err);}});
            mode("empty");Thread.sleep(1300);ui(()->check(!controller.visible(),"empty response safely clears"));
            mode("invalid");Thread.sleep(1300);ui(()->check(!controller.visible(),"malformed response safely rejected"));
            mode("foreign_action");Thread.sleep(1300);ui(()->check(!controller.visible(),"foreign package action rejected"));
            mode("timeout");Thread.sleep(450);
            android.os.Messenger callback=null;
            long callbackDeadline=android.os.SystemClock.elapsedRealtime()+2500;
            while(callback==null&&android.os.SystemClock.elapsedRealtime()<callbackDeadline){
                Object[] probe={null};ui(()->{try{probe[0]=field(controller,"reply");}catch(Exception ex){throw new AssertionError(ex);}});
                callback=(android.os.Messenger)probe[0];if(callback==null)Thread.sleep(50);
            }
            check(callback!=null,"callback bound for foreign UID test");
            if(callback!=null){android.os.Message forged=android.os.Message.obtain(null,HomeContentController.SNAPSHOT);
                forged.arg1=(Integer)field(controller,"generation");Bundle b=new Bundle();b.putString("snapshot","{\"protocolMajor\":1,\"snapshotId\":\"forged\",\"template\":\"summary\",\"privacy\":\"public\",\"ttlSeconds\":30,\"title\":\"forged\",\"items\":[{\"id\":\"x\",\"title\":\"forged\"}]}");forged.setData(b);callback.send(forged);}
            Thread.sleep(2300);ui(()->check(!controller.visible(),"timeout including bounded retry and foreign UID response cannot display"));
            mode("expiry");awaitTemplate(controller,"expiry");Thread.sleep(1150);ui(()->check(!controller.visible(),"snapshot TTL expires without stale display"));
            mode("refresh");awaitTemplate(controller,"refresh");ui(()->check(controller.enter(),"focus before refresh"));
            Thread.sleep(3300);ui(()->{check(controller.visible(),"foreground refresh remains visible");check(controller.inContent(),"foreground refresh preserves input focus");});
            key(KeyEvent.KEYCODE_BACK);
            mode("summary");awaitTemplate(controller,"summary");ui(()->check(controller.visible(),"healthy extension recovers on refocus"));
            ui(()->{controller.trimImages();check(controller.visible(),"memory trim preserves text and navigation");check(controller.enter(),"content action can be selected");});
            key(KeyEvent.KEYCODE_DPAD_CENTER);Thread.sleep(1000);
            check(awaitAccessibleText("weather"),"real external activity receives actionId");
            key(KeyEvent.KEYCODE_BACK);
            awaitTemplate(controller,"summary");
            ui(()->check(controller.visible(),"return from content activity restores Home content"));
            result.putString("result","PASS "+checks+" checks; Messenger, templates, images, focus, revocation, expiry, malformed and foreign input");
        }catch(Throwable error){status=-1;result.putString("error",error.toString());}
        finally{if(home!=null){Activity page=home;ui(page::finish);}}
        runner.finish(status,result);
    }
}
