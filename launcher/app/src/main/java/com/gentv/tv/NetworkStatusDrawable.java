package com.gentv.tv;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Same restrained white glyph family, with live transport, signal and validation state. */
final class NetworkStatusDrawable extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int size,level;
    private final boolean ethernet,online,validated;
    NetworkStatusDrawable(float density,boolean ethernet,boolean online,boolean validated,int level) {
        size=Math.round(18.2f*density);this.ethernet=ethernet;this.online=online;this.validated=validated;this.level=level;
    }
    @Override public void draw(Canvas c) {
        c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/28f,getBounds().height()/28f);
        paint.setColor(Color.WHITE);paint.setStrokeWidth(2.1f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStyle(Paint.Style.STROKE);
        if(ethernet) {
            c.drawRoundRect(5,3,23,19,2,2,paint);c.drawLine(10,19,10,23,paint);c.drawLine(18,19,18,23,paint);c.drawLine(10,23,18,23,paint);
            for(int x=10;x<=18;x+=4)c.drawLine(x,7,x,11,paint);
        } else {
            float[][] arcs={{3,10,14,0,25,10},{7,15,14,8,21,15},{11,19,14,16,17,19}};
            for(int i=0;i<3;i++) {
                paint.setAlpha(online && level>=4-i?255:55);
                Path path=new Path();float[] a=arcs[i];path.moveTo(a[0],a[1]);path.quadTo(a[2],a[3],a[4],a[5]);c.drawPath(path,paint);
            }
            paint.setAlpha(online?255:70);paint.setStyle(Paint.Style.FILL);c.drawCircle(14,23,1.5f,paint);
        }
        paint.setAlpha(255);paint.setStyle(Paint.Style.STROKE);
        if(!online)c.drawLine(4,3,24,25,paint);
        else if(!validated) {paint.setColor(0xFFFFCB75);c.drawLine(25,17,25,21,paint);paint.setStyle(Paint.Style.FILL);c.drawCircle(25,24,1,paint);}
        c.restore();
    }
    @Override public int getIntrinsicWidth(){return size;}
    @Override public int getIntrinsicHeight(){return size;}
    @Override public void setAlpha(int alpha){ }
    @Override public void setColorFilter(ColorFilter filter){ }
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
