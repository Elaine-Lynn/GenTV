package com.gentv.tv;

import android.content.Context;
import android.util.AttributeSet;

import androidx.leanback.widget.VerticalGridView;

/** A TV focus list whose panel stays within the visible area. */
public final class ToolboxGridView extends VerticalGridView {
    public ToolboxGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        // Panel top inset, bottom breathing room, title, title gap, and panel padding.
        int maxHeight = Math.max(Math.round(180 * density),
                screenHeight - Math.round((105 + 24 + 42 + 32 + 12) * density));
        int mode = MeasureSpec.getMode(heightMeasureSpec);
        int available = mode == MeasureSpec.UNSPECIFIED ? maxHeight
                : Math.min(MeasureSpec.getSize(heightMeasureSpec), maxHeight);
        super.onMeasure(widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST));
    }
}
