package com.fxdaily.einkhome;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class CellLayout extends ViewGroup {
    private int mColumns = 5;
    private int mRows = 6;

    public CellLayout(Context context) {
        super(context);
    }

    public CellLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setGridSize(int cols, int rows) {
        mColumns = cols;
        mRows = rows;
        requestLayout();
    }
    
    public int getColumnCount() {
        return mColumns;
    }
    
    public int getRowCount() {
        return mRows;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);

        int cellWidth = width / mColumns;
        int cellHeight = height / mRows;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                int childWidthSpec = MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.EXACTLY);
                int childHeightSpec = MeasureSpec.makeMeasureSpec(cellHeight, MeasureSpec.EXACTLY);
                child.measure(childWidthSpec, childHeightSpec);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int height = b - t;
        int cellWidth = width / mColumns;
        int cellHeight = height / mRows;

        int childIndex = 0; // 只考虑非GONE的子视图
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                int column = childIndex % mColumns;
                int row = childIndex / mColumns;

                int childLeft = column * cellWidth;
                int childTop = row * cellHeight;
                child.layout(childLeft, childTop, childLeft + cellWidth, childTop + cellHeight);
                
                childIndex++;
            }
        }
    }
}