package com.fxdaily.einkhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

public class DragLayer extends FrameLayout {
    private View mDraggingView;
    private ImageView mDragImageView;
    private float mTouchOffsetX, mTouchOffsetY;

    public DragLayer(Context context) { super(context); }
    public DragLayer(Context context, AttributeSet attrs) { super(context, attrs); }
    public DragLayer(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }

    public void startDrag(View v, MotionEvent event) {
        mDraggingView = v;
        Bitmap bitmap = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        v.draw(canvas);

        mDragImageView = new ImageView(getContext());
        mDragImageView.setImageBitmap(bitmap);
        mDragImageView.setAlpha(0.7f);
        
        int[] loc = new int[2];
        v.getLocationInWindow(loc);
        int[] layerLoc = new int[2];
        getLocationInWindow(layerLoc);
        
        mTouchOffsetX = event.getRawX() - loc[0];
        mTouchOffsetY = event.getRawY() - loc[1];

        LayoutParams lp = new LayoutParams(v.getWidth(), v.getHeight());
        lp.leftMargin = loc[0] - layerLoc[0];
        lp.topMargin = loc[1] - layerLoc[1];
        
        addView(mDragImageView, lp);
        v.setVisibility(View.INVISIBLE);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mDragImageView == null) return super.onTouchEvent(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE:
                mDragImageView.setX(event.getX() - mTouchOffsetX);
                mDragImageView.setY(event.getY() - mTouchOffsetY);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                completeDrag();
                break;
        }
        return true;
    }

    private void completeDrag() {
        if (mDraggingView != null) mDraggingView.setVisibility(View.VISIBLE);
        removeView(mDragImageView);
        mDragImageView = null;
        mDraggingView = null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int pL = getPaddingLeft();
        int pR = getPaddingRight();
        int pT = getPaddingTop();
        int pB = getPaddingBottom();

        View deleteZone = findViewById(R.id.delete_zone);
        View hotseat = findViewById(R.id.hotseat);
        View viewPager = findViewById(R.id.view_pager);
        View indicator = findViewById(R.id.page_indicator);

        if (deleteZone != null) {
            int dzH = (int)(60 * getResources().getDisplayMetrics().density);
            deleteZone.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dzH + pT, MeasureSpec.EXACTLY)
            );
        }

        if (hotseat != null) {
            hotseat.measure(
                MeasureSpec.makeMeasureSpec(width - pL - pR, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(hotseat.getMeasuredHeight(), MeasureSpec.EXACTLY)
            );
        }

        if (indicator != null) {
            indicator.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(indicator.getMeasuredHeight(), MeasureSpec.EXACTLY)
            );
        }

        if (viewPager != null) {
            int hH = (hotseat != null) ? hotseat.getMeasuredHeight() : 0;
            int iH = (indicator != null) ? indicator.getMeasuredHeight() : 0;
            int availableHeight = height - pT - pB - hH - iH;
            viewPager.measure(
                MeasureSpec.makeMeasureSpec(width - pL - pR, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(availableHeight, MeasureSpec.EXACTLY)
            );
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int pL = getPaddingLeft();
        int pT = getPaddingTop();
        int pR = getPaddingRight();
        int pB = getPaddingBottom();
        
        int width = right - left;
        int height = bottom - top;

        View deleteZone = findViewById(R.id.delete_zone);
        View hotseat = findViewById(R.id.hotseat);
        View viewPager = findViewById(R.id.view_pager);
        View indicator = findViewById(R.id.page_indicator);

        // 1. Hotseat 在底部
        int hotseatHeight = 0;
        if (hotseat != null) {
            hotseatHeight = hotseat.getMeasuredHeight();
            hotseat.layout(pL, height - pB - hotseatHeight, pL + hotseat.getMeasuredWidth(), height - pB);
        }

        // 2. 指示器在 Hotseat 之上
        int indicatorHeight = 0;
        if (indicator != null) {
            indicatorHeight = indicator.getMeasuredHeight();
            indicator.layout(0, height - pB - hotseatHeight - indicatorHeight, width, height - pB - hotseatHeight);
        }

        // 3. ViewPager 填满剩余空间
        if (viewPager != null) {
            viewPager.layout(pL, pT, pL + viewPager.getMeasuredWidth(), height - pB - hotseatHeight - indicatorHeight);
        }

        // 4. DeleteZone 置顶
        if (deleteZone != null && deleteZone.getVisibility() == VISIBLE) {
            deleteZone.layout(0, 0, width, deleteZone.getMeasuredHeight());
            if (deleteZone.getPaddingTop() != pT) deleteZone.setPadding(0, pT, 0, 0);
            deleteZone.bringToFront();
        }

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == mDragImageView || child == hotseat || child == viewPager || child == deleteZone || child == indicator) continue;
            child.layout(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
        }
    }
}