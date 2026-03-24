package com.pdf.guide;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

public class PDFImageView extends View {
    private Bitmap bitmap;
    private Matrix matrix = new Matrix();
    private Paint paint = new Paint();
    
    private float currentScale = 1.0f;
    private float minScale = 1.0f;
    private float maxScale = 4.0f;
    
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    
    private float lastTouchX;
    private float lastTouchY;
    private float startX;
    private float startY;
    private boolean isPanning = false;
    
    private OnTapListener tapListener;
    
    public interface OnTapListener {
        void onLeftTap();
        void onRightTap();
        void onCenterTap();
    }
    
    public PDFImageView(Context context) {
        super(context);
        init(context);
    }
    
    public PDFImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    
    private void init(Context context) {
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
        
        paint.setFilterBitmap(true);
        paint.setAntiAlias(true);
    }
    
    public void setOnTapListener(OnTapListener listener) {
        this.tapListener = listener;
    }
    
    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        resetTransform();
        invalidate();
    }
    
    public void resetTransform() {
        currentScale = minScale;
        matrix.reset();
        fitToScreen();
        invalidate();
    }
    
    private void fitToScreen() {
        if (bitmap == null) return;
        
        matrix.reset();
        
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        int bmpWidth = bitmap.getWidth();
        int bmpHeight = bitmap.getHeight();
        
        if (viewWidth == 0 || viewHeight == 0) return;
        
        // Calculate scale to fit
        float scaleX = (float) viewWidth / bmpWidth;
        float scaleY = (float) viewHeight / bmpHeight;
        float scale = Math.min(scaleX, scaleY);
        
        currentScale = scale;
        minScale = scale;
        
        // Center the bitmap
        float dx = (viewWidth - bmpWidth * scale) / 2f;
        float dy = (viewHeight - bmpHeight * scale) / 2f;
        
        matrix.setScale(scale, scale);
        matrix.postTranslate(dx, dy);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, matrix, paint);
        }
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        
        int action = event.getActionMasked();
        
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                startX = event.getX();
                startY = event.getY();
                lastTouchX = startX;
                lastTouchY = startY;
                return true;
                
            case MotionEvent.ACTION_MOVE:
                if (currentScale > minScale + 0.01f) {
                    float x = event.getX();
                    float y = event.getY();
                    float dx = x - lastTouchX;
                    float dy = y - lastTouchY;
                    
                    matrix.postTranslate(dx, dy);
                    invalidate();
                    
                    lastTouchX = x;
                    lastTouchY = y;
                }
                return true;
                
            case MotionEvent.ACTION_UP:
                float endX = event.getX();
                float endY = event.getY();
                float deltaX = Math.abs(endX - startX);
                float deltaY = Math.abs(endY - startY);
                
                if (deltaX < 30 && deltaY < 30 && tapListener != null) {
                    float width = getWidth();
                    if (endX < width * 0.3f) {
                        tapListener.onLeftTap();
                    } else if (endX > width * 0.7f) {
                        tapListener.onRightTap();
                    } else {
                        tapListener.onCenterTap();
                    }
                }
                return true;
        }
        return super.onTouchEvent(event);
    }
    
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float focusX = detector.getFocusX();
            float focusY = detector.getFocusY();
            float scaleFactor = detector.getScaleFactor();
            
            float newScale = currentScale * scaleFactor;
            newScale = Math.max(minScale, Math.min(newScale, maxScale));
            
            scaleFactor = newScale / currentScale;
            currentScale = newScale;
            
            matrix.postScale(scaleFactor, scaleFactor, focusX, focusY);
            invalidate();
            
            return true;
        }
        
        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            if (currentScale < minScale + 0.01f) {
                resetTransform();
            }
        }
    }
    
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }
    }
}
