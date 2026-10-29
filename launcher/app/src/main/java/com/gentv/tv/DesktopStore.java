package com.gentv.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One atomic authority for owner layout changes, whether made by the remote or phone.
 * Original Order/LibraryApps encodings remain reusable; their text is stored in this transaction.
 * Legacy files are imported once and are never overwritten or deleted.
 */
final class DesktopStore {
    private static final String FILE = "desktop-state.json";
    private static final String[] LEGACY = {"order.txt", "library_apps.txt", "library_home_removed.txt"};
    private static File cachedDir;
    private static JSONObject cached;
    private static boolean editing;
    private static Runnable listener;
    static void listen(Runnable next) { listener = next; }
    static synchronized void editing(boolean active) { editing = active; }
    private static JSONObject view(File dir) throws Exception {
        if (!dir.equals(cachedDir) || cached == null) load(dir);
        return cached;
    }
    private static synchronized JSONObject observed(Context ctx) throws Exception {
        // External theme and local settings edits also invalidate phone drafts. Config.load updates
        // the upstream theme revision; no original setting storage is duplicated here.
        Config.load(ctx);
        String environment = new java.util.TreeMap<>(ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getAll()).toString()
                + ctx.getSharedPreferences("theme_revision", Context.MODE_PRIVATE).getAll().toString();
        JSONObject before = load(ctx.getFilesDir());
        if (!environment.equals(before.optString("environment"))) {
            JSONObject next = copy(before); next.put("environment", environment);
            commit(ctx.getFilesDir(), before, next, true);
            return load(ctx.getFilesDir());
        }
        return before;
    }

    static synchronized JSONObject load(File dir) throws Exception {
        if (dir.equals(cachedDir) && cached != null) return copy(cached);
        AtomicFile file = new AtomicFile(new File(dir, FILE));
        JSONObject state;
        if (file.getBaseFile().exists() || new File(dir, FILE + ".bak").exists()) {
            state = new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8));
            // A broken state is never silently replaced by defaults on the next save.
            state.getJSONObject("files"); state.getJSONObject("names"); state.getJSONArray("hidden");
            state.getInt("revision"); state.getString("effects");
        } else {
            JSONObject files = new JSONObject();
            for (String name : LEGACY) {
                AtomicFile old = new AtomicFile(new File(dir, name));
                if (old.getBaseFile().exists() || new File(dir, name + ".bak").exists())
                    files.put(name, new String(old.readFully(), StandardCharsets.UTF_8));
            }
            state = new JSONObject().put("revision", 0).put("files", files)
                    .put("names", new JSONObject()).put("hidden", new JSONArray()).put("effects", "auto").put("libraryVisibilityVersion", 1);
        }
        if (!state.has("pinnedSources")) state.put("pinnedSources", new JSONArray());
        for (String key : new String[]{"sourceNames", "sourceDevices", "sourceTargets", "folders", "images"}) if (!state.has(key)) state.put(key, new JSONObject());
        cachedDir = dir; cached = copy(state);
        return state;
    }

    private static JSONObject copy(JSONObject value) throws Exception { return new JSONObject(value.toString()); }

    private static void commit(File dir, JSONObject before, JSONObject next, boolean undo) throws Exception {
        JSONObject previous = copy(before); previous.remove("previous");
        if (undo) next.remove("previous"); else next.put("previous", previous);
        next.put("revision", Math.addExact(before.getInt("revision"), 1));
        AtomicFile file = new AtomicFile(new File(dir, FILE));
        FileOutputStream out = null;
        try {
            out = file.startWrite();
            out.write(next.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out);
        } catch (Exception e) { if (out != null) file.failWrite(out); throw e; }
        cachedDir = dir; cached = copy(next);
    }

    static void initialize(Context ctx) {
        try { observed(ctx); }
        catch (Exception e) { android.util.Log.w("GenTV.Desktop", "Keeping unreadable owner state unchanged"); }
    }

    static synchronized String text(File file) throws Exception {
        return load(file.getParentFile()).getJSONObject("files").optString(file.getName(), "");
    }

    static synchronized boolean writeText(File file, String text) {
        try {
            JSONObject before = load(file.getParentFile()), next = copy(before);
            if (text.equals(before.getJSONObject("files").optString(file.getName()))) return true;
            if (file.getName().equals("order.txt")) {
                Order order = Order.decode(text);
                Order old = Order.decode(before.getJSONObject("files").optString("order.txt", ""));
                if (order == null) throw new IllegalArgumentException("Invalid order");
                List<String> keys = new ArrayList<>(order.keys);
                if (old != null) for (int i=0; i<old.keys.size(); i++) if (!keys.contains(old.keys.get(i))) keys.add(Math.min(i, keys.size()), old.keys.get(i));
                text = Order.encode(new Order(order.dock, keys, order.docked));
            }
            next.getJSONObject("files").put(file.getName(), text);
            commit(file.getParentFile(), before, next, false); return true;
        } catch (Exception e) { android.util.Log.e("GenTV.Desktop", "Layout not saved", e); return false; }
    }

    static synchronized boolean library(Context ctx, String pkg, boolean onHome) {
        try {
            JSONObject before = observed(ctx), next = copy(before), files = next.getJSONObject("files");
            Set<String> added = LibraryApps.decode(files.optString("library_apps.txt", ""));
            Set<String> removed = LibraryApps.decode(files.optString("library_home_removed.txt", ""));
            files.put("library_apps.txt", LibraryApps.encode(LibraryApps.change(added, pkg, onHome)));
            files.put("library_home_removed.txt", LibraryApps.encode(LibraryApps.change(removed, pkg, !onHome)));
            Set<String> hidden = strings(next.getJSONArray("hidden"));
            for (String key : catalog(ctx).keySet()) if (ComponentName.unflattenFromString(key).getPackageName().equals(pkg)) {
                if (onHome) hidden.remove(key); else hidden.add(key);
            }
            next.put("hidden", array(hidden));
            commit(ctx.getFilesDir(), before, next, false); return true;
        } catch (Exception e) { android.util.Log.e("GenTV.Desktop", "Shortcut not saved", e); return false; }
    }

    static Map<String, ResolveInfo> catalog(Context ctx) {
        Map<String, ResolveInfo> result = new LinkedHashMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER})
            for (ResolveInfo ri : ctx.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                if (ri.activityInfo == null || !ri.activityInfo.enabled || !ri.activityInfo.exported
                        || ri.activityInfo.packageName.equals(ctx.getPackageName())) continue;
                ComponentName c = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
                result.putIfAbsent(c.flattenToShortString(), ri);
            }
        return result;
    }

    static synchronized JSONObject snapshot(Context ctx) throws Exception {
        JSONObject state = observed(ctx), files = state.getJSONObject("files");
        Config cfg = Config.load(ctx);
        Order saved = Order.decode(files.optString("order.txt", ""));
        List<String> keys = new ArrayList<>(saved == null ? java.util.Collections.emptyList() : saved.keys);
        Set<String> hidden = strings(state.getJSONArray("hidden"));
        Set<String> added = LibraryApps.decode(files.optString("library_apps.txt", ""));
        added.addAll(cfg.libraryApps);
        Set<String> removed = LibraryApps.decode(files.optString("library_home_removed.txt", ""));
        Set<String> tv = new HashSet<>();
        for (ResolveInfo ri : ctx.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0))
            if (ri.activityInfo != null) tv.add(ri.activityInfo.packageName);
        JSONArray apps = new JSONArray();
        Map<String, ResolveInfo> installed = catalog(ctx);
        for (Map.Entry<String, ResolveInfo> entry : installed.entrySet()) {
            String key = entry.getKey(), pkg = entry.getValue().activityInfo.packageName;
            boolean visible = LibraryApps.visible(tv.contains(pkg), cfg.hiddenApps.contains(pkg), added.contains(pkg), removed.contains(pkg));
            if (!visible) hidden.add(key);
            apps.put(new JSONObject().put("id", key).put("package", pkg)
                    .put("label", Cards.cleanLabel(String.valueOf(entry.getValue().loadLabel(ctx.getPackageManager())), pkg)));
        }
        List<String> defaults = new ArrayList<>(installed.keySet());
        defaults.sort((a,b) -> {
            if (hidden.contains(a) != hidden.contains(b)) return hidden.contains(a) ? 1 : -1;
            int fa=cfg.dockApps.indexOf(installed.get(a).activityInfo.packageName), fb=cfg.dockApps.indexOf(installed.get(b).activityInfo.packageName);
            if(fa>=0 || fb>=0) return fa<0 ? 1 : fb<0 ? -1 : Integer.compare(fa,fb);
            return String.valueOf(installed.get(a).loadLabel(ctx.getPackageManager())).compareToIgnoreCase(String.valueOf(installed.get(b).loadLabel(ctx.getPackageManager())));
        });
        for(String key:defaults) if(!keys.contains(key)) keys.add(key);
        Map<String, String> inputs = DesktopInputs.list(ctx);
        JSONArray pinned = state.getJSONArray("pinnedSources");
        for (int i=0;i<pinned.length();i++) {
            String id = pinned.getString(i); if (!inputs.containsKey(id)) continue;
            String key = DesktopInputs.component(ctx, id).flattenToShortString();
            if (!keys.contains(key)) keys.add(key);
            apps.put(new JSONObject().put("id", key).put("package", ctx.getPackageName()).put("label", DesktopInputs.label(state, id, inputs.get(id))));
        }
        JSONObject folders = state.getJSONObject("folders");
        for (Iterator<String> it=folders.keys(); it.hasNext();) {
            String key=it.next(); if (!keys.contains(key)) keys.add(key);
            apps.put(new JSONObject().put("id",key).put("package",ctx.getPackageName())
                    .put("label",folders.getJSONObject(key).getString("name")).put("folder",true));
        }
        JSONObject profile = new JSONObject().put("folders", folders).put("images", state.getJSONObject("images")).put("schema", 2).put("order", array(keys)).put("hidden", array(hidden))
                .put("names", state.getJSONObject("names")).put("effects", state.getString("effects"));
        profile.put("pinnedSources", state.getJSONArray("pinnedSources"));
        for (String field : new String[]{"sourceNames", "sourceDevices", "sourceTargets"}) profile.put(field, state.getJSONObject(field));
        return new JSONObject().put("protocol", 1).put("ok", true).put("backend", "gentv")
                .put("revision", state.getInt("revision")).put("canUndo", state.has("previous"))
                .put("config", profile).put("apps", apps).put("sources", DesktopInputs.sources(ctx))
                .put("capabilities", new JSONArray().put("apps").put("effects").put("pinnedSources").put("folders").put("images"))
                .put("smooth", reduced(ctx, cfg.reducedMotion));
    }

    static void validate(JSONObject p) throws Exception {
        if (android.util.Base64.encodeToString(p.toString().getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP).length()>60000)
            throw new IllegalArgumentException("Desktop profile is full");
        if (!(p.get("schema") instanceof Integer) || p.getInt("schema") != 2) throw new IllegalArgumentException("Schema 2 required");
        Set<String> allowed = new HashSet<>(java.util.Arrays.asList("schema", "order", "hidden", "names", "effects", "pinnedSources", "sourceNames", "sourceDevices", "sourceTargets", "folders", "images"));
        for (Iterator<String> it = p.keys(); it.hasNext();) if (!allowed.contains(it.next())) throw new IllegalArgumentException("Unsupported field");
        for (String field : new String[]{"order", "hidden"}) {
            JSONArray a = p.getJSONArray(field); Set<String> seen = new HashSet<>();
            if (a.length() > 500) throw new IllegalArgumentException("Too many apps");
            for (int i = 0; i < a.length(); i++) {
                Object value = a.get(i);
                if (!(value instanceof String) || !validKey((String)value) || !seen.add((String)value)) throw new IllegalArgumentException("Invalid component");
            }
        }
        JSONObject names = p.getJSONObject("names");
        if (names.length() > 500) throw new IllegalArgumentException("Too many names");
        for (Iterator<String> it = names.keys(); it.hasNext();) {
            String key = it.next(); Object value = names.get(key);
            if (!validKey(key) || !(value instanceof String)) throw new IllegalArgumentException("Invalid name");
            String name = (String)value;
            if (name.trim().isEmpty() || name.length() > 60) throw new IllegalArgumentException("Invalid name");
            for (int i=0; i<name.length(); i++) if (Character.isISOControl(name.charAt(i)) || Character.getType(name.charAt(i)) == Character.FORMAT)
                throw new IllegalArgumentException("Invalid name");
        }
        if (p.has("pinnedSources")) {
            JSONArray pins = p.getJSONArray("pinnedSources"); Set<String> seen = new HashSet<>();
            if (pins.length() > 100) throw new IllegalArgumentException("Too many inputs");
            for (int i=0;i<pins.length();i++) {
                Object id = pins.get(i);
                if (!(id instanceof String) || ((String)id).isEmpty() || ((String)id).length() > 256 || !seen.add((String)id)) throw new IllegalArgumentException("Invalid input");
            }
        }
        for (String field : new String[]{"sourceNames", "sourceDevices", "sourceTargets"}) if (p.has(field)) {
            JSONObject values = p.getJSONObject(field);
            if (values.length() > 100) throw new IllegalArgumentException("Too many inputs");
            for (Iterator<String> it=values.keys();it.hasNext();) {
                String id=it.next(); Object raw=values.get(id);
                if (id.isEmpty() || id.length()>256 || !(raw instanceof String) || ((String)raw).isEmpty() || ((String)raw).length()>256) throw new IllegalArgumentException("Invalid input value");
                String value=(String)raw;
                if (field.equals("sourceNames")) {
                    if(value.trim().isEmpty() || value.length()>60) throw new IllegalArgumentException("Invalid input name");
                    for(int i=0;i<value.length();i++) if(Character.isISOControl(value.charAt(i)) || Character.getType(value.charAt(i))==Character.FORMAT) throw new IllegalArgumentException("Invalid input name");
                }
                if (field.equals("sourceDevices") && !java.util.Arrays.asList("playstation", "switch", "xbox", "appletv", "computer").contains(value)) throw new IllegalArgumentException("Invalid device");
            }
        }
        DesktopExtras.validate(p);
        Object mode = p.get("effects");
        if (!(mode instanceof String) || !java.util.Arrays.asList("auto", "smooth", "full").contains(mode)) throw new IllegalArgumentException("Invalid effects");
    }

    static boolean validKey(String key) {
        if (key == null || key.length() > 256) return false;
        return key.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+") && ComponentName.unflattenFromString(key) != null;
    }

    static synchronized JSONObject save(Context ctx, JSONObject profile, int revision) throws Exception {
        validate(profile);
        if (editing) throw new IllegalStateException("Finish arranging on TV first");
        JSONObject before = observed(ctx);
        if (revision != before.getInt("revision")) throw new IllegalArgumentException("Settings changed; read again");
        JSONObject next = copy(before), files = next.getJSONObject("files");
        Map<String, String> inputs = DesktopInputs.list(ctx);
        JSONArray pins = profile.optJSONArray("pinnedSources");
        if (pins != null) {
            Set<String> existing = strings(before.getJSONArray("pinnedSources"));
            for(int i=0;i<pins.length();i++) if(!inputs.containsKey(pins.getString(i)) && !existing.contains(pins.getString(i))) throw new IllegalArgumentException("Unknown input");
            next.put("pinnedSources", pins);
        }
        for(String field:new String[]{"sourceNames", "sourceDevices", "sourceTargets"}) if(profile.has(field)) {
            JSONObject values=profile.getJSONObject(field), existing=before.getJSONObject(field);
            for(Iterator<String> it=values.keys();it.hasNext();) {
                String id=it.next(), value=values.getString(id);
                if ((!inputs.containsKey(id) || (field.equals("sourceTargets") && !inputs.containsKey(value)))
                        && !value.equals(existing.optString(id, null))) throw new IllegalArgumentException("Unknown input target");
            }
            next.put(field, values);
        }
        for (String field : new String[]{"folders", "images"}) if(profile.has(field)) next.put(field, profile.getJSONObject(field));
        // Old clients must not silently edit a layout they cannot represent.
        for (String field : new String[]{"folders", "images"}) if(!profile.has(field) && before.getJSONObject(field).length()>0)
            throw new IllegalArgumentException("Update the phone app before editing this layout");
        DesktopExtras.validate(next);
        Set<String> hidden = strings(profile.getJSONArray("hidden"));
        Set<String> added = LibraryApps.decode(files.optString("library_apps.txt", ""));
        Set<String> removed = LibraryApps.decode(files.optString("library_home_removed.txt", ""));
        Config cfg = Config.load(ctx);
        Set<String> tvPackages = new HashSet<>();
        for(ResolveInfo ri:ctx.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),0))
            if(ri.activityInfo!=null) tvPackages.add(ri.activityInfo.packageName);
        // Derive package-level library visibility once per edit. Home can keep using the original
        // cheap LibraryApps read instead of querying the entire launcher catalogue on each rebuild.
        Map<String, ResolveInfo> installedKeys = catalog(ctx);
        Map<String, Boolean> packageVisible = new LinkedHashMap<>();
        for (Map.Entry<String, ResolveInfo> e : installedKeys.entrySet()) {
            String pkg = e.getValue().activityInfo.packageName;
            boolean visible = !hidden.contains(e.getKey());
            packageVisible.put(pkg, packageVisible.getOrDefault(pkg, false) || visible);
            if (visible && (!tvPackages.contains(pkg) || cfg.hiddenApps.contains(pkg))) added.add(pkg);
        }
        for (Map.Entry<String, Boolean> e : packageVisible.entrySet()) {
            if (e.getValue()) removed.remove(e.getKey()); else removed.add(e.getKey());
        }
        files.put("library_apps.txt", LibraryApps.encode(added));
        files.put("library_home_removed.txt", LibraryApps.encode(removed));
        Order old = Order.decode(files.optString("order.txt", ""));
        List<String> order = new ArrayList<>(); JSONArray array = profile.getJSONArray("order");
        for (int i=0;i<array.length();i++) order.add(array.getString(i));
        // Keep missing keys during updates even if an importing client omits them.
        if (old != null) for (String key : old.keys) if (!order.contains(key) && !DesktopExtras.folder(key)) order.add(key);
        int docked = old == null ? -1 : old.docked;
        if(docked < 0 && cfg.dockRow) {
            int count=0;
            for(String key:order) if(!hidden.contains(key) && installedKeys.containsKey(key)) {
                if(cfg.fillDock || cfg.dockApps.contains(ComponentName.unflattenFromString(key).getPackageName())) count++;
            }
            docked=Math.min(cfg.columns,count);
        }
        files.put("order.txt", Order.encode(new Order(Order.dockKey(cfg.dockApps), order, docked)));
        next.put("libraryVisibilityVersion", 1);
        next.put("names", profile.getJSONObject("names")).put("hidden", profile.getJSONArray("hidden")).put("effects", profile.getString("effects"));
        commit(ctx.getFilesDir(), before, next, false);
        notifyChanged(ctx);
        return snapshot(ctx);
    }

    static synchronized JSONObject undo(Context ctx, int revision) throws Exception {
        if (editing) throw new IllegalStateException("Finish arranging on TV first");
        JSONObject before = observed(ctx);
        if (revision != before.getInt("revision") || !before.has("previous")) throw new IllegalArgumentException("Cannot undo; read again");
        JSONObject restored=copy(before.getJSONObject("previous"));
        for(String field:new String[]{"folders","images"}) if(!restored.has(field))restored.put(field,new JSONObject());
        commit(ctx.getFilesDir(), before, restored, true);
        notifyChanged(ctx); return snapshot(ctx);
    }

    static synchronized boolean rename(Context ctx, String key, String name) {
        try {
            JSONObject state = snapshot(ctx), p = state.getJSONObject("config");
            if (name.isEmpty()) p.getJSONObject("names").remove(key); else p.getJSONObject("names").put(key, name);
            save(ctx, p, state.getInt("revision")); return true;
        } catch (Exception e) { return false; }
    }


    static synchronized String imageData(Context ctx, String key) {
        try { return view(ctx.getFilesDir()).getJSONObject("images").optString(key, ""); }
        catch(Exception e) { return ""; }
    }
    static synchronized JSONObject inputSettings(Context ctx) {
        try { return load(ctx.getFilesDir()); } catch(Exception e) { return new JSONObject(); }
    }
    static synchronized String name(Context ctx, String key, String fallback) {
        try { return view(ctx.getFilesDir()).getJSONObject("names").optString(key, fallback); }
        catch (Exception e) { return fallback; }
    }
    static synchronized boolean libraryVisibilityReady(Context ctx) {
        try { return view(ctx.getFilesDir()).optInt("libraryVisibilityVersion") == 1; }
        catch (Exception e) { return false; }
    }
    static synchronized Set<String> hiddenKeys(Context ctx) {
        try { return strings(view(ctx.getFilesDir()).getJSONArray("hidden")); }
        catch (Exception e) { return java.util.Collections.emptySet(); }
    }
    static synchronized boolean hidden(Context ctx, String key) {
        try { return strings(view(ctx.getFilesDir()).getJSONArray("hidden")).contains(key); }
        catch (Exception e) { return false; }
    }
    static synchronized int revision(Context ctx) {
        try { return view(ctx.getFilesDir()).getInt("revision"); } catch (Exception e) { return -1; }
    }
    static boolean reduced(Context ctx, boolean fallback) {
        try {
            String mode = load(ctx.getFilesDir()).getString("effects");
            if (mode.equals("full")) return false;
            if (mode.equals("smooth")) return true;
            android.app.ActivityManager am = (android.app.ActivityManager)ctx.getSystemService(Context.ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo info = new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);
            return fallback || am.isLowRamDevice() || info.totalMem < 1800L*1024*1024 || Runtime.getRuntime().availableProcessors() <= 2;
        } catch (Exception e) { return fallback; }
    }
    private static Set<String> strings(JSONArray a) throws Exception {
        Set<String> result = new java.util.LinkedHashSet<>();
        for (int i=0;i<a.length();i++) result.add(a.getString(i));
        return result;
    }
    private static JSONArray array(Iterable<String> values) { JSONArray a = new JSONArray(); for (String s : values) a.put(s); return a; }
    private static void notifyChanged(Context ctx) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> { if (listener != null) listener.run(); });
    }
}
