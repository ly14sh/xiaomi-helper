package com.pdf.guide;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

public class PageTurnView extends View {
    private Bitmap currentPage;
    private Bitmap nextPage;
    private Bitmap prevPage;
    
    private Paint paint = new Paint();
    private Paint shadowPaint = new Paint();
    
    private float touchX = 0;
    private float startX = 0;
    private float startY = 0;
    private boolean isDragging = false;
    private boolean isPanning = false;
    private float lastPanX = 0;
    private float lastPanY = 0;
    private float panOffsetX = 0;
    private float panOffsetY = 0;
    private boolean isAnimating = false;
    private boolean isForward = true; // true = next page, false = prev page
    
    private OnPageTurnListener pageTurnListener;
    
    private static final float TURN_THRESHOLD = 0.15f;
    
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    private boolean isScaling = false;
    private float currentScale = 1.0f;
    private float minScale = 1.0f;
    private float maxScale = 4.0f;
    private Matrix imageMatrix = new Matrix();
    
    private ValueAnimator animator;
    
    public interface OnPageTurnListener {
        void onNextPage();
        void onPrevPage();
        void onPageChanged(int page);
    }
    
    public PageTurnView(Context context) {
        super(context);
        init();
    }
    
    private void init() {
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        
        shadowPaint.setAntiAlias(true);
        
        scaleGestureDetector = new ScaleGestureDetector(getContext(), new ScaleListener());
        gestureDetector = new GestureDetector(getContext(), new GestureListener());
    }
    
    public void setOnPageTurnListener(OnPageTurnListener listener) {
        this.pageTurnListener = listener;
    }
    
    public void setCurrentPage(Bitmap page) {
        this.currentPage = page;
        this.nextPage = null;
        this.prevPage = null;
        resetTransform();
        invalidate();
    }
    
    public void setNextPage(Bitmap page) {
        this.nextPage = page;
    }
    
    public void setPrevPage(Bitmap page) {
        this.prevPage = page;
    }
    
    public void resetTransform() {
        currentScale = minScale;
        imageMatrix.reset();
        panOffsetX = 0;
        panOffsetY = 0;
        if (currentPage != null) {
            fitToCenter();
        }
        touchX = 0;
        isDragging = false;
        isPanning = false;
        invalidate();
    }
    
    private void fitToCenter() {
        if (currentPage == null) return;
        
        int viewW = getWidth();
        int viewH = getHeight();
        if (viewW == 0 || viewH == 0) return;
        
        int bmpW = currentPage.getWidth();
        int bmpH = currentPage.getHeight();
        
        float scaleX = (float) viewW / bmpW;
        float scaleY = (float) viewH / bmpH;
        float scale = Math.min(scaleX, scaleY);
        
        currentScale = scale;
        minScale = scale;
        
        imageMatrix.reset();
        
        float dx = (viewW - bmpW * scale) / 2f;
        float dy = (viewH - bmpH * scale) / 2f;
        
        imageMatrix.setScale(scale, scale);
        imageMatrix.postTranslate(dx, dy);
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fitToCenter();
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (currentPage == null) return;
        
        canvas.drawColor(Color.parseColor("#F5F5F5"));
        
        int w = getWidth();
        int h = getHeight();
        
        if (isDragging && touchX != 0) {
            // Draw page turn following finger
            drawPageTurn(canvas, w, h);
        } else {
            canvas.drawBitmap(currentPage, imageMatrix, paint);
        }
    }
    
    private void drawPageTurn(Canvas canvas, int w, int h) {
        if (isForward) {
            // Swipe left -> next page (page reveals from right, current slides left)
            drawNextPageTurn(canvas, w, h);
        } else {
            // Swipe right -> prev page (page reveals from left, current slides right)
            drawPrevPageTurn(canvas, w, h);
        }
    }
    
    private void drawNextPageTurn(Canvas canvas, int w, int h) {
        // Swipe left: deltaX < 0, touchX is negative
        // Current page slides left as we reveal next page from right
        float slideOffset = touchX; // Negative value, page slides left
        
        // Draw next page underneath (fully visible, sliding in from right)
        if (nextPage != null) {
            Matrix nextMatrix = new Matrix(imageMatrix);
            nextMatrix.postTranslate(w + slideOffset, 0); // Next page slides in from right
            canvas.save();
            canvas.clipRect(0, 0, w + (int)slideOffset, h);
            canvas.drawBitmap(nextPage, nextMatrix, paint);
            canvas.restore();
        }
        
        // Draw current page sliding left with shadow on its right edge
        canvas.save();
        
        // Clip to the remaining visible portion of current page
        float remainingRight = w + (int)slideOffset;
        if (remainingRight > 0) {
            canvas.clipRect(0, 0, (int)remainingRight, h);
        }
        
        Matrix currentMatrix = new Matrix(imageMatrix);
        currentMatrix.postTranslate(slideOffset, 0);
        canvas.drawBitmap(currentPage, currentMatrix, paint);
        canvas.restore();
        
        // Draw shadow on the edge of current page (right side that is peeling away)
        if (nextPage != null && slideOffset < -dp(5)) {
            float shadowX = remainingRight;
            LinearGradient shadowGrad = new LinearGradient(
                shadowX - dp(30), 0, shadowX, 0,
                Color.TRANSPARENT,
                Color.argb(40, 0, 0, 0),
                Shader.TileMode.CLAMP
            );
            shadowPaint.setShader(shadowGrad);
            canvas.drawRect(shadowX - dp(30), 0, shadowX, h, shadowPaint);
        }
        
        // Draw a subtle curl effect at the edge
        if (nextPage != null && slideOffset < -dp(10)) {
            Paint curlPaint = new Paint();
            float curlWidth = Math.min(dp(10), Math.abs(slideOffset) * 0.08f);
            int alpha = (int)(Math.min(30, Math.abs(slideOffset) * 0.15f));
            curlPaint.setColor(Color.argb(alpha, 255, 255, 255));
            
            float curlX = remainingRight;
            canvas.drawRect(curlX - curlWidth, 0, curlX, h, curlPaint);
        }
    }
    
    private void drawPrevPageTurn(Canvas canvas, int w, int h) {
        // Swipe right: deltaX > 0, touchX is positive
        // Current page slides right as we reveal prev page from left
        float slideOffset = touchX; // Positive value, page slides right
        
        // Draw current page sliding right
        canvas.save();
        canvas.clipRect((int)slideOffset, 0, w, h);
        
        Matrix currentMatrix = new Matrix(imageMatrix);
        currentMatrix.postTranslate(slideOffset, 0);
        canvas.drawBitmap(currentPage, currentMatrix, paint);
        canvas.restore();
        
        // Draw shadow on the left edge of current page (peeling away)
        if (prevPage != null && slideOffset > dp(5)) {
            float shadowX = slideOffset;
            LinearGradient shadowGrad = new LinearGradient(
                shadowX, 0, shadowX + dp(30), 0,
                Color.argb(40, 0, 0, 0),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            );
            shadowPaint.setShader(shadowGrad);
            canvas.drawRect(shadowX, 0, shadowX + dp(30), h, shadowPaint);
        }
        
        // Draw prev page underneath (sliding in from left)
        if (prevPage != null) {
            Matrix prevMatrix = new Matrix(imageMatrix);
            // Prev page revealed as current slides right
            canvas.save();
            canvas.clipRect(0, 0, (int)slideOffset, h);
            canvas.drawBitmap(prevPage, prevMatrix, paint);
            canvas.restore();
        }
        
        // Draw curl highlight
        if (prevPage != null && slideOffset > dp(10)) {
            Paint curlPaint = new Paint();
            float curlWidth = Math.min(dp(10), slideOffset * 0.08f);
            int alpha = (int)(Math.min(30, slideOffset * 0.15f));
            curlPaint.setColor(Color.argb(alpha, 255, 255, 255));
            
            float curlX = slideOffset;
            canvas.drawRect(curlX, 0, curlX + curlWidth, h, curlPaint);
        }
    }
    
    private int dp(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isAnimating) return true;
        
        scaleGestureDetector.onTouchEvent(event);
        if (isScaling) return true;
        
        gestureDetector.onTouchEvent(event);
        
        int action = event.getActionMasked();
        
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (animator != null) animator.cancel();
                
                startX = event.getX();
                startY = event.getY();
                touchX = 0;
                lastPanX = startX;
                lastPanY = startY;
                isDragging = false;
                isPanning = false;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
                
            case MotionEvent.ACTION_MOVE:
                float deltaX = event.getX() - startX;
                float deltaY = event.getY() - startY;
                
                // If zoomed in, allow panning (no page turn)
                if (currentScale > minScale + 0.01f) {
                    if (!isPanning) {
                        isPanning = true;
                    }
                    // Pan the content
                    float moveX = event.getX() - lastPanX;
                    float moveY = event.getY() - lastPanY;
                    panOffsetX += moveX;
                    panOffsetY += moveY;
                    imageMatrix.postTranslate(moveX, moveY);
                    lastPanX = event.getX();
                    lastPanY = event.getY();
                    invalidate();
                    return true;
                }
                
                // Not zoomed - allow page turn
                if (!isDragging && Math.abs(deltaX) > 15) {
                    if (Math.abs(deltaX) > Math.abs(deltaY)) {
                        isDragging = true;
                        isForward = deltaX < 0;
                    }
                }
                
                if (isDragging) {
                    touchX = deltaX;
                    int w = getWidth();
                    float maxDrag = w * 0.4f;
                    touchX = Math.max(-maxDrag, Math.min(maxDrag, touchX));
                    invalidate();
                }
                return true;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                
                if (isPanning) {
                    isPanning = false;
                    return true;
                }
                
                if (isDragging) {
                    finishDrag();
                }
                isDragging = false;
                return true;
        }
        return super.onTouchEvent(event);
    }
    
    private void finishDrag() {
        int w = getWidth();
        float progress = Math.abs(touchX) / w;
        
        boolean shouldTurn = false;
        
        if (isForward && nextPage != null) {
            // Swiped left to next
            shouldTurn = progress > TURN_THRESHOLD;
        } else if (!isForward && prevPage != null) {
            // Swiped right to prev
            shouldTurn = progress > TURN_THRESHOLD;
        }
        
        if (shouldTurn) {
            animateTurn();
        } else {
            animateReturn();
        }
    }
    
    private void animateTurn() {
        isAnimating = true;
        final float startX = touchX;
        final int w = getWidth();
        final float endX = isForward ? -w : w;
        final long duration = 80; // Very fast
        
        animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(duration);
        animator.setInterpolator(new DecelerateInterpolator(2f));
        animator.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            touchX = startX + (endX - startX) * t;
            invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                isAnimating = false;
                isDragging = false;
                touchX = 0;
                
                if (isForward && pageTurnListener != null) {
                    pageTurnListener.onNextPage();
                } else if (!isForward && pageTurnListener != null) {
                    pageTurnListener.onPrevPage();
                }
                
                invalidate();
            }
        });
        animator.start();
    }
    
    private void animateReturn() {
        isAnimating = true;
        final float startX = touchX;
        final float endX = 0;
        final long duration = 80; // Very fast
        
        animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(duration);
        animator.setInterpolator(new OvershootInterpolator(1.2f));
        animator.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            touchX = startX + (endX - startX) * t;
            invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                isAnimating = false;
                touchX = 0;
                invalidate();
            }
        });
        animator.start();
    }
    
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            isScaling = true;
            if (isDragging) {
                isDragging = false;
                touchX = 0;
                invalidate();
            }
            return true;
        }
        
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            float newScale = currentScale * scaleFactor;
            newScale = Math.max(minScale, Math.min(newScale, maxScale));
            
            scaleFactor = newScale / currentScale;
            currentScale = newScale;
            
            imageMatrix.postScale(scaleFactor, scaleFactor, 
                detector.getFocusX(), detector.getFocusY());
            invalidate();
            
            return true;
        }
        
        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            isScaling = false;
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
        
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (isScaling || isAnimating || isDragging) return false;
            
            float x = e.getX();
            float width = getWidth();
            
            // Left side -> prev page
            if (x < width * 0.3f) {
                if (prevPage != null) {
                    isForward = false;
                    touchX = getWidth();
                    animateTurn();
                }
                return true;
            }
            // Right side -> next page
            else if (x > width * 0.7f) {
                if (nextPage != null) {
                    isForward = true;
                    touchX = -getWidth();
                    animateTurn();
                }
                return true;
            }
            // Center -> toggle bars
            else {
                if (pageTurnListener != null) {
                    pageTurnListener.onPageChanged(-1); // -1 means center tap
                }
                return true;
            }
        }
        
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (isScaling || isAnimating || isDragging) return false;
            
            float deltaX = e2.getX() - e1.getX();
            
            if (Math.abs(velocityX) > 500 || Math.abs(deltaX) > 80) {
                if (deltaX < 0 && nextPage != null) {
                    // Fling left -> next page
                    isForward = true;
                    touchX = -getWidth();
                    animateTurn();
                    return true;
                } else if (deltaX > 0 && prevPage != null) {
                    // Fling right -> prev page
                    isForward = false;
                    touchX = getWidth();
                    animateTurn();
                    return true;
                }
            }
            return false;
        }
    }
}
