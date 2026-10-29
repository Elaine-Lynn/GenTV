package com.gentv.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Lightweight schematic preview, drawn without wallpaper decoding or extra allocations per frame. */
public final class SettingsPreview extends View {
    private final Paint paint = new Paint(3);
    private final RectF rect = new RectF();
    private int columns = 4, rows, opacity = 30;
    public SettingsPreview(Context context, AttributeSet attrs) { super(context, attrs); }
    void update(int columns, int rows, int opacity) {
        this.columns = columns; this.rows = rows; this.opacity = opacity; invalidate();
    }
    private void box(Canvas canvas, float x, float y, float w, float h, float radius, int color) {
        paint.setColor(color); rect.set(x, y, x+w, y+h); canvas.drawRoundRect(rect, radius, radius, paint);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w=getWidth(), h=w*0.5625f, top=Math.max(0,(getHeight()-h)/2f);
        canvas.save(); canvas.translate(0,top);
        box(canvas,0,0,w,h,14,0xff304a62);
        box(canvas,w*.06f,h*.13f,w*.23f,h*.035f,3,0xffd8e8f5);
        box(canvas,w*.06f,h*.21f,w*.36f,h*.016f,2,0xff7e9db6);
        box(canvas,w*.06f,h*.42f,w*.88f,h*.5f,9,Color.argb(opacity*255/100,12,22,35));
        float gap=w*.025f, tile=Math.min(w*.11f,(w*.82f-gap*(columns-1))/columns);
        for(int row=0;row<=rows;row++) for(int col=0;col<columns;col++) {
            float x=w*.09f+col*(tile+gap), y=h*.46f+row*h*.105f;
            box(canvas,x,y,tile,h*.075f,4,row==0?0xff8bb7d8:0xff607e99);
        }
        canvas.restore();
    }
}
