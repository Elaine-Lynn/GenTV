package com.gentv.tv;

import android.content.Context;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.widget.ImageView;

/** Focus-aware icon tint without XML ImageView tint, which crashes on Android 4.4. */
public final class FocusTintImageView extends ImageView {
    public FocusTintImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override protected void drawableStateChanged() {
        super.drawableStateChanged();
        boolean focused = false;
        for (int state : getDrawableState()) {
            if (state == android.R.attr.state_focused) {
                focused = true;
                break;
            }
        }
        setColorFilter(focused ? 0xFF111111 : 0xFFFFFFFF, PorterDuff.Mode.SRC_IN);
    }
}
