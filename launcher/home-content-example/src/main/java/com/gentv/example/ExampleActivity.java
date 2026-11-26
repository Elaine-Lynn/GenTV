package com.gentv.example;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Real app entry and same-package deep-link target for all example templates. No network or device data. */
public final class ExampleActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);
        String fixture=getIntent().getStringExtra("example.mode");
        if(fixture!=null&&java.util.Arrays.asList("summary","media_row","feature","empty","invalid","timeout","background_failure","expiry","foreign_action","refresh").contains(fixture)){
            getSharedPreferences("example",MODE_PRIVATE).edit().putString("mode",fixture).apply();
            new android.os.Handler(getMainLooper()).postDelayed(this::finish,200);return;
        }

        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(40,28,40,28);
        ScrollView scroll=new ScrollView(this);scroll.addView(page);setContentView(scroll);
        TextView title=new TextView(this);title.setTextSize(24);title.setText("GenTV 桌面内容示例 / GHCP 1");page.addView(title);
        TextView help=new TextView(this);help.setText("选择样式后返回 GenTV。在桌面设置 → 桌面内容中允许此应用，再把图标加入第一行。以下数据均为离线演示，不是真实天气。\n内容入口："+getIntent().getStringExtra("com.gentv.tv.home.ACTION_ID"));page.addView(help);
        String[] modes={"summary","media_row","feature","empty","invalid","timeout","background_failure"};
        String[] names={"天气摘要（无大图）","继续观看（六张卡片）","相册（可选大图）","空内容","畸形数据（安全回退）","不响应（超时回退）","失效背景（保留文字）"};
        for(int i=0;i<modes.length;i++){String mode=modes[i];Button b=new Button(this);b.setText(names[i]);
            b.setOnClickListener(v->{getSharedPreferences("example",MODE_PRIVATE).edit().putString("mode",mode).apply();finish();});page.addView(b);}
    }
}
