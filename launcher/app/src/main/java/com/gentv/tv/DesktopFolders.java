package com.gentv.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Remote actions use the exact same optimistic transaction as phone drafts. */
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
            List<String> ids=new ArrayList<>(),labels=new ArrayList<>();
            labels.add(ctx.getString(R.string.desktop_folder_new));ids.add("");
            for(java.util.Iterator<String> it=folders.keys();it.hasNext();) {String key=it.next();ids.add(key);labels.add(folders.getJSONObject(key).getString("name"));}
            new AlertDialog.Builder(ctx).setTitle(R.string.desktop_move_folder).setItems(labels.toArray(new String[0]),(d,which)->{
                if(which==0) {
                    EditText input=new EditText(ctx);input.setSingleLine(true);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(60)});
                    new AlertDialog.Builder(ctx).setTitle(R.string.desktop_folder_new).setView(input).setNegativeButton(android.R.string.cancel,null)
                        .setPositiveButton(android.R.string.ok,(x,w)->save(ctx,snap,p->{
                            String key=DesktopExtras.newFolderKey();DesktopExtras.name(input.getText().toString());
                            p.getJSONObject("folders").put(key,new JSONObject().put("name",input.getText().toString().trim()).put("apps",new JSONArray()));
                            p.getJSONArray("order").put(key);assign(p,app,key);
                        })).show();
                }else save(ctx,snap,p->assign(p,app,ids.get(which)));
            }).setNegativeButton(android.R.string.cancel,null).show();
        }catch(Exception e) { Toast.makeText(ctx,R.string.library_save_failed,Toast.LENGTH_LONG).show(); }
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
            String[] actions={ctx.getString(R.string.desktop_folder_rename),ctx.getString(R.string.desktop_folder_edit),ctx.getString(R.string.tile_move),ctx.getString(R.string.desktop_folder_dissolve)};
            new AlertDialog.Builder(ctx).setTitle(folder.getString("name")).setItems(actions,(d,which)->{
                if(which==0) {
                    EditText input=new EditText(ctx);input.setSingleLine(true);input.setText(folder.optString("name"));input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(60)});
                    new AlertDialog.Builder(ctx).setTitle(R.string.desktop_folder_rename).setView(input).setNegativeButton(android.R.string.cancel,null)
                        .setPositiveButton(android.R.string.ok,(x,w)->save(ctx,snap,next->{DesktopExtras.name(input.getText().toString());next.getJSONObject("folders").getJSONObject(key).put("name",input.getText().toString().trim());next.getJSONObject("names").remove(key);})).show();
                }else if(which==1) choose(ctx,snap,key);
                else if(which==2) {
                    // Keep the established grid reorder interaction and stable component key.
                    ((MainActivity)ctx).moveFolderTile(key);
                }else new AlertDialog.Builder(ctx).setTitle(R.string.desktop_folder_dissolve).setMessage(folder.optString("name"))
                    .setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(x,w)->save(ctx,snap,next->{
                        next.getJSONObject("folders").remove(key);next.getJSONObject("names").remove(key);
                        for(String field:new String[]{"order","hidden"}) {JSONArray a=next.getJSONArray(field),b=new JSONArray();for(int i=0;i<a.length();i++)if(!key.equals(a.getString(i)))b.put(a.getString(i));next.put(field,b);}
                    })).show();
            }).setNegativeButton(android.R.string.cancel,null).show();
        }catch(Exception e) { Toast.makeText(ctx,R.string.library_save_failed,Toast.LENGTH_LONG).show(); }
    }
    private static void choose(Activity ctx,JSONObject snap,String key) {
        try {
            JSONObject p=snap.getJSONObject("config"),folder=p.getJSONObject("folders").getJSONObject(key);
            JSONArray apps=snap.getJSONArray("apps");List<String> ids=new ArrayList<>(),labels=new ArrayList<>();
            java.util.Set<String> selected=new java.util.LinkedHashSet<>();JSONArray old=folder.getJSONArray("apps");
            for(int i=0;i<old.length();i++)selected.add(old.getString(i));
            for(int i=0;i<apps.length();i++) {JSONObject app=apps.getJSONObject(i);String id=app.getString("id");if(!app.getString("package").equals(ctx.getPackageName())) {ids.add(id);labels.add(app.getString("label"));}}
            boolean[] checked=new boolean[ids.size()];for(int i=0;i<ids.size();i++)checked[i]=selected.contains(ids.get(i));
            new AlertDialog.Builder(ctx).setTitle(R.string.desktop_folder_edit).setMultiChoiceItems(labels.toArray(new String[0]),checked,(d,which,on)->{if(on)selected.add(ids.get(which));else selected.remove(ids.get(which));})
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(d,w)->save(ctx,snap,next->{
                    next.getJSONObject("folders").getJSONObject(key).put("apps",new JSONArray());
                    for(String app:selected) assign(next,app,key);
                })).show();
        }catch(Exception e) { Toast.makeText(ctx,R.string.library_save_failed,Toast.LENGTH_LONG).show(); }
    }
}
