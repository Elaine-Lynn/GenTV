package com.gentv.tv;

import android.app.Activity;
import android.view.*;
import android.graphics.drawable.*;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.*;
import android.content.*;
import android.content.pm.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.security.MessageDigest;
import org.json.JSONObject;

/** User-controlled update and anonymous aggregate statistics settings. */
public final class CloudSettingsActivity extends Activity {
    private TextView status;
    private Button check;
    private ProgressBar progress;
    private File verified;
    private Button later;
    private boolean mandatory;
    private void mandatory(boolean required) {
        mandatory=required; later.setVisibility(required?View.GONE:View.VISIBLE);
    }
    @Override public void onBackPressed() { if(mandatory && !TvCloud.required(this)) mandatory(false); if(mandatory) Toast.makeText(this,R.string.cloud_required,Toast.LENGTH_SHORT).show(); else super.onBackPressed(); }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view;
    }
    private GradientDrawable surface(int color, int stroke, int width, int radius) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius));
        shape.setStroke(dp(width),stroke); return shape;
    }
    private Button action(int label, boolean primary) {
        Button button = new Button(this); button.setText(label); button.setTextSize(15); button.setAllCaps(false);
        button.setTextColor(0xFFF5F5F7); button.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused}, surface(primary?0xFF168FE5:0xFF424750,0xFFF5F5F7,2,12));
        background.addState(new int[]{},surface(primary?0xFF117ABF:0xFF2D323B,0x33FFFFFF,1,12));
        button.setBackground(background); button.setPadding(dp(20),0,dp(20),0);
        button.setOnFocusChangeListener((v,on)->v.animate().scaleX(on?1.025f:1f).scaleY(on?1.025f:1f).setDuration(120).start());
        return button;
    }
    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(LauncherLocale.wrap(base)); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout backdrop = new FrameLayout(this); backdrop.setBackgroundColor(0x88080B10); backdrop.setOnClickListener(v->{if(!mandatory) finish();});
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(30),dp(26),dp(30),dp(24));
        card.setClipChildren(false); card.setClipToPadding(false);
        card.setBackground(surface(0xF21C2129,0x40FFFFFF,1,24)); card.setElevation(dp(16)); card.setOnClickListener(v->{});
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(Math.min(dp(550),getResources().getDisplayMetrics().widthPixels-dp(48)),Math.min(dp(440),getResources().getDisplayMetrics().heightPixels-dp(40)),Gravity.CENTER);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.addView(card); backdrop.addView(scroll,position);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        android.text.SpannableString brand = new android.text.SpannableString("GenTV");
        brand.setSpan(new android.text.style.ForegroundColorSpan(0xFF64C7F5),3,5,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView logo = text("",24,0xFFF5F5F7); logo.setText(brand); logo.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        header.addView(logo,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        header.addView(text(getString(R.string.cloud_current_version,BuildConfig.VERSION_NAME),13,0xFFAEB7C4)); card.addView(header);
        TextView heading=text(getString(R.string.cloud_update_title),27,0xFFF5F5F7); heading.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        LinearLayout.LayoutParams headingSpace=new LinearLayout.LayoutParams(-1,-2); headingSpace.topMargin=dp(24); card.addView(heading,headingSpace);
        status=text(getString(R.string.cloud_update_intro),16,0xFFBEC8D6); status.setMinHeight(dp(64)); status.setPadding(0,dp(10),0,dp(12)); card.addView(status);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF64C7F5));
        progress.setVisibility(View.GONE); card.addView(progress,new LinearLayout.LayoutParams(-1,dp(6)));
        LinearLayout actions=new LinearLayout(this); actions.setClipChildren(false); actions.setClipToPadding(false); actions.setGravity(Gravity.END); actions.setPadding(0,dp(18),0,dp(20));
        later=action(R.string.cloud_later,false); later.setOnClickListener(v->finish()); actions.addView(later,new LinearLayout.LayoutParams(dp(150),dp(48)));
        check=action(R.string.cloud_check_update,true); LinearLayout.LayoutParams main=new LinearLayout.LayoutParams(0,dp(48),1); main.leftMargin=dp(14); actions.addView(check,main); card.addView(actions);
        View divider=new View(this); divider.setBackgroundColor(0x20FFFFFF); card.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        Button stats=action(R.string.cloud_statistics,false); stats.setTextSize(13);
        stats.setText(TvCloud.prefs(this).getBoolean("statistics",false)?R.string.cloud_stats_on:R.string.cloud_stats_off);
        stats.setOnClickListener(v->{boolean enabled=!TvCloud.prefs(this).getBoolean("statistics",false);
            TvCloud.prefs(this).edit().putBoolean("statistics",enabled).apply();
            stats.setText(enabled?R.string.cloud_stats_on:R.string.cloud_stats_off);});
        LinearLayout.LayoutParams statsPosition=new LinearLayout.LayoutParams(-1,dp(42)); statsPosition.topMargin=dp(14);statsPosition.bottomMargin=dp(6);card.addView(stats,statsPosition);
        TextView statsHint=text(getString(R.string.cloud_statistics_hint),11,0xFF929EAD); card.addView(statsHint);
        mandatory(TvCloud.required(this));
        check.setOnClickListener(v->check()); setContentView(backdrop); check.requestFocus();
        if(!TvCloud.enabled()) {
            status.setText(R.string.cloud_unconfigured); check.setVisibility(View.GONE); stats.setVisibility(View.GONE); statsHint.setVisibility(View.GONE); later.requestFocus();
        } else if(TvCloud.prefs(this).getLong("pendingVersion",0)>BuildConfig.VERSION_CODE) {
            check.setText(R.string.cloud_install); check.setOnClickListener(v->install());
        } else if(getIntent().getBooleanExtra("check",false)) check();
    }
    private void message(int text) { if(!isFinishing() && !isDestroyed()) { status.setText(text); progress.setVisibility(View.GONE); check.setEnabled(true); } }
    private void check() {
        check.setEnabled(false); status.setText(R.string.cloud_checking); progress.setIndeterminate(true); progress.setVisibility(View.VISIBLE);
        TvCloud.IO.execute(() -> {
            try {
                JSONObject update = TvCloud.json("update?platform=tv");
                TvCloud.remember(this,update);
                long version = update.getLong("versionCode");
                boolean required=UpdatePolicy.required(BuildConfig.VERSION_CODE,version,update.optLong("minSupportedVersionCode",0),update.optBoolean("forceUpdate",false));
                if(version <= BuildConfig.VERSION_CODE) { TvCloud.prefs(this).edit().putLong("requiredVersion",0).apply(); runOnUiThread(()->{mandatory(false);message(R.string.cloud_latest);}); return; }
                String url = update.getString("url"), hash = update.getString("sha256");
                if(!url.startsWith(TvCloud.BASE) || !hash.matches("[a-fA-F0-9]{64}")) throw new IOException("Invalid update");
                runOnUiThread(()->{
                    if(isFinishing() || isDestroyed()) return;
                    TvCloud.prefs(this).edit().putLong("requiredVersion",required?version:0).apply();
                    mandatory(required);
                    message(R.string.cloud_update_available);
                    status.setText((required?getString(R.string.cloud_required)+"\n":"")+getString(R.string.cloud_version_ready,update.optString("versionName",Long.toString(version))));
                    check.setText(R.string.cloud_upgrade_now);
                    check.setOnClickListener(v->download(url,hash,version));
                });
            } catch(Exception e) { runOnUiThread(()->{mandatory(TvCloud.required(this));message(R.string.cloud_failed);}); }
        });
    }
    @SuppressWarnings("deprecation")
    private void download(String url,String hash,long version) {
        check.setEnabled(false); status.setText(R.string.cloud_downloading); progress.setIndeterminate(true); progress.setVisibility(View.VISIBLE);
        TvCloud.IO.execute(()->{
            File file = new File(getCacheDir(),"updates/gentv.apk.part");
            HttpURLConnection c = null;
            try {
                file.getParentFile().mkdirs(); c = TvCloud.open(url);
                if(c.getResponseCode()!=200) throw new IOException("Download refused");
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                try(InputStream in=c.getInputStream(); OutputStream out=new FileOutputStream(file)) {
                    byte[] buf=new byte[8192]; int n,total=0;
                    int length=c.getContentLength();
                    while((n=in.read(buf))!=-1) { total+=n; if(total>8*1024*1024) throw new IOException("APK too large"); out.write(buf,0,n); digest.update(buf,0,n);
                        if(length>0 && length<=8*1024*1024) { final int percent=(int)(total*100L/length); runOnUiThread(()->{if(!isDestroyed()){progress.setIndeterminate(false);progress.setProgress(percent);}}); } }
                }
                StringBuilder actual=new StringBuilder(); for(byte b:digest.digest()) actual.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
                if(!hash.equalsIgnoreCase(actual.toString())) throw new IOException("Checksum mismatch");
                PackageManager pm=getPackageManager();
                PackageInfo apk=pm.getPackageArchiveInfo(file.getPath(),PackageManager.GET_SIGNATURES);
                PackageInfo own=pm.getPackageInfo(getPackageName(),PackageManager.GET_SIGNATURES);
                if(apk==null || !getPackageName().equals(apk.packageName) || apk.versionCode!=version || apk.versionCode<=BuildConfig.VERSION_CODE
                        || apk.signatures==null || !java.util.Arrays.equals(own.signatures,apk.signatures)) throw new IOException("Package verification failed");
                File ready=new File(getCacheDir(),"updates/gentv.apk");
                if(!file.renameTo(ready)) throw new IOException("Cannot commit update");
                TvCloud.prefs(this).edit().putString("pendingHash",hash).putLong("pendingVersion",version).commit();
                verified=UpdatePackage.verified(this); runOnUiThread(()->{ if(!isFinishing()&&!isDestroyed()) install(); });
            } catch(Exception e) { file.delete(); runOnUiThread(()->{message(R.string.cloud_failed);check.setText(R.string.cloud_check_update);check.setOnClickListener(v->check());}); }
            finally { if(c!=null)c.disconnect(); }
        });
    }
    private void install() {
        try {
            verified=UpdatePackage.verified(this);
            if(!getPackageManager().canRequestPackageInstalls()) {
                message(R.string.cloud_install_permission);
                check.setText(R.string.cloud_install); check.setOnClickListener(v->install());
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,android.net.Uri.parse("package:"+getPackageName())));
                return;
            }
            android.net.Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".updates",verified);
            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            message(R.string.cloud_install_confirm);
        } catch(Exception e) {
            TvCloud.prefs(this).edit().remove("pendingHash").remove("pendingVersion").apply();
            message(R.string.cloud_failed); check.setText(R.string.cloud_check_update); check.setOnClickListener(v->check());
        }
    }
}
