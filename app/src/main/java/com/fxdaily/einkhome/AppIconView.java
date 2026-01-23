package com.fxdaily.einkhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;

public class AppIconView extends androidx.appcompat.widget.AppCompatImageView {
    private Paint borderPaint;
    private Paint bgPaint;
    private Path clipPath;
    private float borderWidth;
    private boolean forceShowMask = false;

    public AppIconView(Context context) {
        super(context);
        init();
    }

    public AppIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AppIconView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        borderWidth = getResources().getDisplayMetrics().density * 2; // 2dp 描边

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(0xFFFFFFFF); // 白色背景
        bgPaint.setStyle(Paint.Style.FILL);

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(0xFF000000); // 黑色描边
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(borderWidth);

        clipPath = new Path();

        // 创建置灰（黑白）颜色矩阵
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0); // 设置饱和度为 0 即可实现置灰
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);
        setColorFilter(filter);
    }

    public void setForceShowMask(boolean force) {
        this.forceShowMask = force;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        clipPath.reset();
        float radius = Math.min(w, h) / 2f;
        clipPath.addCircle(w / 2f, h / 2f, radius, Path.Direction.CW);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!forceShowMask && getDrawable() == null) {
            super.onDraw(canvas);
            return;
        }

        int saveCount = canvas.save();
        
        // 1. 应用圆形剪裁
        canvas.clipPath(clipPath);
        
        // 2. 绘制白色背景
        canvas.drawPath(clipPath, bgPaint);
        
        // 3. 绘制图标本身（已被剪裁，且通过 setColorFilter 实现了置灰）
        super.onDraw(canvas);
        
        canvas.restoreToCount(saveCount);

        // 4. 在最上层绘制圆形描边
        float w = getWidth();
        float h = getHeight();
        float radius = Math.min(w, h) / 2f - borderWidth / 2f;
        canvas.drawCircle(w / 2f, h / 2f, radius, borderPaint);
    }
}
