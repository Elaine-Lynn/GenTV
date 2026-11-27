package com.gentv.tv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Remote actions use the exact same optimistic transaction as phone drafts. Every step is a FolderSheet in the
 * Control Center's glass; a new folder takes the place on Home of the app it was made from.
 */
final class DesktopFolders {
    interface Change { void apply(JSONObject profile) throws Exception; }
    private static void save(Activity ctx,JSONObject snapshot,Change change) {
        try {
            JSONObject p=snapshot.getJSONObject("config");change.apply(p);
            DesktopStore.save(ctx,p,snapshot.getInt("revision"));
        }catch(Exception e) { Toast.makeText(ctx,R.string.library_save_failed,Toast.LENGTH_LONG).show(); }
    }
    static void resetImage(Activity ctx,String key) {
        try { JSONObject snap=DesktopStore.snapshot(ctx);save(ctx,snap,p->p.getJSONObject("images").remove(key)); }
        catch(Exception e) { Toast.makeText(ctx,R.string.library_save_failed,Toast.LENGTH_LONG).show(); }
    }

    static void move(Activity ctx,String app) {
        try {
            JSONObject snap=DesktopStore.snapshot(ctx),folders=snap.getJSONObject("config").getJSONObject("folders");
            List<String> ids=new ArrayList<>();List<FolderSheet.Row> rows=new ArrayList<>();
            ids.add("");
            rows.add(new FolderSheet.Row(CompatApi.drawable(ctx,R.drawable.ic_folder_add),true,
                    ctx.getString(R.string.desktop_folder_new),ctx.getString(R.string.folder_new_detail),false));
            for(java.util.Iterator<String> it=folders.keys();it.hasNext();) {
                String key=it.next();JSONObject folder=folders.getJSONObject(key);int count=folder.getJSONArray("apps").length();
                ids.add(key);
                rows.add(new FolderSheet.Row(CompatApi.drawable(ctx,R.drawable.ic_folder_glyph),true,folder.getString("name"),
                        ctx.getResources().getQuantityString(R.plurals.folder_app_count,count,count),false));
            }
            FolderSheet.list(ctx,ctx.getString(R.string.desktop_move_folder),ctx.getString(R.string.folder_move_subtitle,label(snap,app)),rows,which->{
                if(which==0) FolderSheet.name(ctx,ctx.getString(R.string.desktop_folder_new),"",name->save(ctx,snap,p->{
                    String key=DesktopExtras.newFolderKey();DesktopExtras.name(name);
                    p.getJSONObject("folders").put(key,new JSONObject().put("name",name).put("apps",new JSONArray()));
                    // The folder appears where the app was, so it is on screen right away (not after the last tile,
                    // where a full Home would tuck it into More apps).
                    p.put("order",insertBefore(p.getJSONArray("order"),key,app));
                    assign(p,app,key);
                }));
                else save(ctx,snap,p->assign(p,app,ids.get(which)));
            });
        }catch(Exception e) { Toast.makeText(ctx,R.string.library_save_failed,Toast.LENGTH_LONG).show(); }
    }

    /** order with key placed just before anchor (at the front when anchor is not in it). */
    static JSONArray insertBefore(JSONArray order,String key,String anchor) throws Exception {
        JSONArray next=new JSONArray();boolean placed=false;
        for(int i=0;i<order.length();i++) {
            String item=order.getString(i);
            if(item.equals(key))continue;
            if(!placed && item.equals(anchor)) {next.put(key);placed=true;}
            next.put(item);
        }
        if(placed)return next;
        JSONArray front=new JSONArray().put(key);
        for(int i=0;i<next.length();i++)front.put(next.getString(i));
        return front;
    }

    static void assign(JSONObject profile,String app,String target) throws Exception {
        JSONObject folders=profile.getJSONObject("folders");
        for(java.util.Iterator<String> it=folders.keys();it.hasNext();) {
            String key=it.next();JSONArray old=folders.getJSONObject(key).getJSONArray("apps"),next=new JSONArray();
            for(int i=0;i<old.length();i++) if(!app.equals(old.getString(i)))next.put(old.getString(i));
            if(key.equals(target))next.put(app);
            folders.getJSONObject(key).put("apps",next);
        }
        JSONArray hidden=profile.getJSONArray("hidden"),visible=new JSONArray();
        for(int i=0;i<hidden.length();i++) if(!app.equals(hidden.getString(i)))visible.put(hidden.getString(i));
        profile.put("hidden",visible);
    }

    static void edit(Activity ctx,String key) {
        try {
            JSONObject snap=DesktopStore.snapshot(ctx),p=snap.getJSONObject("config"),folder=p.getJSONObject("folders").getJSONObject(key);
            String name=folder.optString("name");int count=folder.getJSONArray("apps").length();
            List<FolderSheet.Row> rows=new ArrayList<>();
            rows.add(new FolderSheet.Row(CompatApi.drawable(ctx,R.drawable.ic_folder_rename),true,ctx.getString(R.string.desktop_folder_rename),null,false));
            rows.add(new FolderSheet.Row(CompatApi.drawable(ctx,R.drawable.ic_qs_extensions),true,ctx.getString(R.string.desktop_folder_edit),
                    ctx.getResources().getQuantityString(R.plurals.folder_app_count,count,count),false));
            rows.add(new FolderSheet.Row(CompatApi.drawable(ctx,R.drawable.ic_folder_move),true,ctx.getString(R.string.tile_move),null,false));
            rows.add(new FolderSheet.Row(CompatApi.drawable(ctx,R.drawable.ic_folder_dissolve),true,ctx.getString(R.string.desktop_folder_dissolve),null,true));
            FolderSheet.list(ctx,name,ctx.getString(R.string.folder_manage),rows,which->{
                if(which==0) FolderSheet.name(ctx,ctx.getString(R.string.desktop_folder_rename),name,value->save(ctx,snap,next->{
                    DesktopExtras.name(value);next.getJSONObject("folders").getJSONObject(key).put("name",value);next.getJSONObject("names").remove(key);
                }));
                else if(which==1) choose(ctx,snap,key);
                else if(which==2) {
                    // Keep the established grid reorder interaction and stable component key.
                    ((MainActivity)ctx).moveFolderTile(key);
                }else GlassInfoDialog.create(ctx,R.drawable.ic_folder_dissolve,R.string.desktop_folder_dissolve,
                        ctx.getString(R.string.folder_dissolve_detail,name),R.string.desktop_folder_dissolve,()->save(ctx,snap,next->{
                            next.getJSONObject("folders").remove(key);next.getJSONObject("names").remove(key);
                            for(String field:new String[]{"order","hidden"}) {JSONArray a=next.getJSONArray(field),b=new JSONArray();for(int i=0;i<a.length();i++)if(!key.equals(a.getString(i)))b.put(a.getString(i));next.put(field,b);}
                        }),R.string.folder_cancel,null).show();
            });
        }catch(Exception e) { Toast.makeText(ctx,R.string.library_save_failed,Toast.LENGTH_LONG).show(); }
    }

    private static void choose(Activity ctx,JSONObject snap,String key) {
        try {
            JSONObject p=snap.getJSONObject("config"),folder=p.getJSONObject("folders").getJSONObject(key);
            JSONArray apps=snap.getJSONArray("apps");List<String> ids=new ArrayList<>();List<FolderSheet.Row> rows=new ArrayList<>();
            java.util.Set<String> selected=new java.util.LinkedHashSet<>();JSONArray old=folder.getJSONArray("apps");
            for(int i=0;i<old.length();i++)selected.add(old.getString(i));
            PackageManager pm=ctx.getPackageManager();
            for(int i=0;i<apps.length();i++) {
                JSONObject app=apps.getJSONObject(i);String id=app.getString("id");
                if(app.getString("package").equals(ctx.getPackageName()))continue;
                FolderSheet.Row row=new FolderSheet.Row(icon(pm,id),false,app.getString("label"),null,false);
                row.checked=selected.contains(id);
                ids.add(id);rows.add(row);
            }
            FolderSheet.choose(ctx,ctx.getString(R.string.desktop_folder_edit),ctx.getString(R.string.folder_choose_subtitle),rows,()->save(ctx,snap,next->{
                next.getJSONObject("folders").getJSONObject(key).put("apps",new JSONArray());
                for(int i=0;i<rows.size();i++) if(rows.get(i).checked) assign(next,ids.get(i),key);
            }));
        }catch(Exception e) { Toast.makeText(ctx,R.string.library_save_failed,Toast.LENGTH_LONG).show(); }
    }

    private static Drawable icon(PackageManager pm,String key) {
        try { ComponentName c=ComponentName.unflattenFromString(key);return c==null?null:pm.getActivityIcon(c); }
        catch(PackageManager.NameNotFoundException | RuntimeException missing) { return null; }
    }

    private static String label(JSONObject snap,String app) {
        JSONArray apps=snap.optJSONArray("apps");
        for(int i=0;apps!=null && i<apps.length();i++) {
            JSONObject item=apps.optJSONObject(i);
            if(item!=null && app.equals(item.optString("id"))) return item.optString("label");
        }
        return app;
    }
}
