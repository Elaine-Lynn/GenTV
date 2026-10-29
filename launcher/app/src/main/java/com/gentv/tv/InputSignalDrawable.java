package com.gentv.tv;

import android.animation.ValueAnimator;
import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small input signal meter; animation exists only while Home is visible and an input is connected. */
final class InputSignalDrawable extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int size;
    private final ValueAnimator animator=ValueAnimator.ofFloat(0,1);
    private float phase;
    InputSignalDrawable(float density) {
        size=Math.round(18.2f*density);paint.setColor(Color.WHITE);paint.setStrokeWidth(2.6f);paint.setStrokeCap(Paint.Cap.ROUND);
        animator.setDuration(1600);animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new android.view.animation.LinearInterpolator());
        animator.addUpdateListener(a->{phase=(float)a.getAnimatedValue();invalidateSelf();});
    }
    void start(){if(!animator.isStarted())animator.start();}
    void stop(){animator.cancel();phase=0;invalidateSelf();}
    @Override public void draw(Canvas c) {
        c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/28f,getBounds().height()/28f);
        c.translate(0,3.8f); // Resting glyph bottom matches the other header icons.
        for(int i=0;i<5;i++) {
            float h=4+(float)(3+3*Math.sin(phase*Math.PI*2+i*1.2))+(i==2?4:0);
            c.drawLine(5+i*4,14-h/2,5+i*4,14+h/2,paint);
        }c.restore();
    }
    @Override public int getIntrinsicWidth(){return size;}
    @Override public int getIntrinsicHeight(){return size;}
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
