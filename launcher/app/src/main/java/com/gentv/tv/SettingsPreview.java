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
        // Like Home: the dock row and the rows below it sit at the bottom, centred, with tiles sized to fill the
        // width for this column count and shrunk together when the rows would climb into the clock.
        int count=Math.max(1,columns), lines=rows+1;
        float pad=w*.012f, gap=w*.02f, rowGap=h*.045f;
        float tileW=(w-2*w*.07f-gap*(count-1))/count;
        float tileH=tileW*AppAdapter.DESIGN_H_DP/AppAdapter.DESIGN_W_DP;
        float bottom=h*.93f, room=bottom-h*.30f;
        float stack=lines*tileH+(lines-1)*rowGap+pad;
        if(stack>room){ float k=room/stack; tileW*=k; tileH*=k; gap*=k; rowGap*=k; pad*=k; }
        float rowW=count*tileW+(count-1)*gap, x0=(w-rowW)/2f, radius=tileH*.14f;
        float dockY=bottom-lines*tileH-(lines-1)*rowGap;
        box(canvas,x0-pad,dockY-pad,rowW+2*pad,tileH+2*pad,radius+pad*.6f,Color.argb(opacity*255/100,12,22,35));
        for(int row=0;row<lines;row++) for(int col=0;col<count;col++) {
            box(canvas,x0+col*(tileW+gap),dockY+row*(tileH+rowGap),tileW,tileH,radius,row==0?0xff8bb7d8:0xff607e99);
        }
        canvas.restore();
    }
}
