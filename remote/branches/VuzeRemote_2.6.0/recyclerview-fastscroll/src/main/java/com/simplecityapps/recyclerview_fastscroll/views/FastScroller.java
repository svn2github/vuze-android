/*
 * Copyright (c) 2016 Tim Malseed
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.simplecityapps.recyclerview_fastscroll.views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.support.annotation.ColorInt;
import android.support.v4.view.animation.FastOutLinearInInterpolator;
import android.support.v4.view.animation.LinearOutSlowInInterpolator;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.simplecityapps.recyclerview_fastscroll.R;
import com.simplecityapps.recyclerview_fastscroll.utils.Utils;

public class FastScroller {
    private static final int DEFAULT_AUTO_HIDE_DELAY = 1500;

    private FastScrollRecyclerView mRecyclerView;
    private FastScrollPopup mPopup;

    private int mThumbHeight;
    private int mWidth;

    private Paint mThumb;
    private Paint mTrack;

    private Rect mTmpRect = new Rect();
    private Rect mInvalidateRect = new Rect();
    private Rect mInvalidateTmpRect = new Rect();

    // The inset is the buffer around which a point will still register as a click on the scrollbar
    private int mTouchInset;

    // This is the offset from the top of the scrollbar when the user first starts touching.  To
    // prevent jumping, this offset is applied as the user scrolls.
    private int mTouchOffset;

    public Point mThumbPosition = new Point(-1, -1);
    public Point mOffset = new Point(0, 0);

    private boolean mIsDragging;

    private Animator mAutoHideAnimator;
    boolean mAnimatingShow;
    private int mAutoHideDelay = DEFAULT_AUTO_HIDE_DELAY;
    private boolean mAutoHideEnabled = true;
    private final Runnable mHideRunnable;
    private FastScrollerOffsetXAnimator mFastScrollerOffsetxObjectAnimator;

    public FastScroller(Context context, FastScrollRecyclerView recyclerView, AttributeSet attrs) {

        Resources resources = context.getResources();

        mRecyclerView = recyclerView;
        mPopup = new FastScrollPopup(resources, recyclerView);

        mThumbHeight = Utils.toPixels(resources, 48);
        mWidth = Utils.toPixels(resources, 8);

        mTouchInset = Utils.toPixels(resources, -24);

        mThumb = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTrack = new Paint(Paint.ANTI_ALIAS_FLAG);

        TypedArray typedArray = context.getTheme().obtainStyledAttributes(
            attrs, R.styleable.FastScrollRecyclerView, 0, 0);
        try {
            mAutoHideEnabled = typedArray.getBoolean(R.styleable.FastScrollRecyclerView_fastScrollAutoHide, true);
            mAutoHideDelay = typedArray.getInteger(R.styleable.FastScrollRecyclerView_fastScrollAutoHideDelay, DEFAULT_AUTO_HIDE_DELAY);

            int trackColor = typedArray.getColor(R.styleable.FastScrollRecyclerView_fastScrollTrackColor, 0x1f000000);
            int thumbColor = typedArray.getColor(R.styleable.FastScrollRecyclerView_fastScrollThumbColor, 0xff000000);
            int popupBgColor = typedArray.getColor(R.styleable.FastScrollRecyclerView_fastScrollPopupBgColor, 0xff000000);
            int popupTextColor = typedArray.getColor(R.styleable.FastScrollRecyclerView_fastScrollPopupTextColor, 0xffffffff);

            mTrack.setColor(trackColor);
            mThumb.setColor(thumbColor);
            mPopup.setBgColor(popupBgColor);
            mPopup.setTextColor(popupTextColor);
        } finally {
            typedArray.recycle();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            mFastScrollerOffsetxObjectAnimator = new FastScrollerApi14OffsetXAnimatorImp();
        } else  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            mFastScrollerOffsetxObjectAnimator = new FastScrollerPreApi14OffsetXAnimatorImp();
        }

        mHideRunnable = new Runnable() {
            @Override
            public void run() {
                if (mIsDragging) {
                    return;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    hideRunnable_11();
                } else {
                    FastScroller.this.setOffsetX(
                        (Utils.isRtl(mRecyclerView.getResources()) ? -1 : 1) *
                            mWidth);
                }
            }
        };

        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                show();
            }
        });

        if (mAutoHideEnabled) {
            postAutoHideDelayed();
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void hideRunnable_11() {
        if (mAutoHideAnimator != null) {
            mAutoHideAnimator.cancel();
        }
        mAutoHideAnimator = mFastScrollerOffsetxObjectAnimator.getOffsetXAnimator(FastScroller.this, (Utils.isRtl(mRecyclerView.getResources()) ? -1 : 1) * mWidth);
        mAutoHideAnimator.setInterpolator(new FastOutLinearInInterpolator());
        mAutoHideAnimator.setDuration(200);
        try {
            mAutoHideAnimator.start();
        } catch (NullPointerException ignore) {
            // found in wild.  Possible due to view already destroying since
            // this is posted delayed.
        }
    }

    public int getThumbHeight() {
        return mThumbHeight;
    }

    public int getWidth() {
        return mWidth;
    }

    public boolean isDragging() {
        return mIsDragging;
    }

    /**
     * Handles the touch event and determines whether to show the fast scroller (or updates it if
     * it is already showing).
     */
    public void handleTouchEvent(MotionEvent ev, int downX, int downY, int lastY) {
        ViewConfiguration config = ViewConfiguration.get(mRecyclerView.getContext());

        int action = ev.getAction();
        int y = (int) ev.getY();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (isNearPoint(downX, downY)) {
                    mTouchOffset = downY - mThumbPosition.y;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                // Check if we should start scrolling
                if (!mIsDragging && isNearPoint(downX, downY) &&
                    Math.abs(y - downY) > config.getScaledTouchSlop()) {
                    mRecyclerView.getParent().requestDisallowInterceptTouchEvent(true);
                    mIsDragging = true;
                    mTouchOffset += (lastY - downY);
                    mPopup.animateVisibility(true);
                }
                if (mIsDragging) {
                    // Update the fastscroller section name at this touch position
                    int top = 0;
                    int bottom = mRecyclerView.getHeight() - mThumbHeight;
                    float boundedY = (float) Math.max(top, Math.min(bottom, y - mTouchOffset));
                    String sectionName = mRecyclerView.scrollToPositionAtProgress((boundedY - top) / (bottom - top));
                    mPopup.setSectionName(sectionName);
                    mPopup.animateVisibility(sectionName.length() > 0);
                    mRecyclerView.invalidate(mPopup.updateFastScrollerBounds(mRecyclerView, mThumbPosition.y));
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mTouchOffset = 0;
                if (mIsDragging) {
                    mIsDragging = false;
                    mPopup.animateVisibility(false);
                }
                break;
        }
    }

    public void draw(Canvas canvas) {

        if (mThumbPosition.x < 0 || mThumbPosition.y < 0) {
            return;
        }

        //Background
        canvas.drawRect(mThumbPosition.x + mOffset.x, mThumbHeight / 2 + mOffset.y, mThumbPosition.x + mOffset.x + mWidth, mRecyclerView.getHeight() + mOffset.y - mThumbHeight / 2, mTrack);

        //Handle
        canvas.drawRect(mThumbPosition.x + mOffset.x, mThumbPosition.y + mOffset.y, mThumbPosition.x + mOffset.x + mWidth, mThumbPosition.y + mOffset.y + mThumbHeight, mThumb);

        //Popup
        mPopup.draw(canvas);
    }

    /**
     * Returns whether the specified points are near the scroll bar bounds.
     */
    private boolean isNearPoint(int x, int y) {
        mTmpRect.set(mThumbPosition.x, mThumbPosition.y,
            mThumbPosition.x + mWidth,
            mThumbPosition.y + mThumbHeight);
        mTmpRect.inset(mTouchInset, mTouchInset);
        return mTmpRect.contains(x, y);
    }

    public void setThumbPosition(int x, int y) {
        if (mThumbPosition.x == x && mThumbPosition.y == y) {
            return;
        }
        // do not create new objects here, this is called quite often
        mInvalidateRect.set(mThumbPosition.x + mOffset.x, mOffset.y, mThumbPosition.x + mOffset.x + mWidth, mRecyclerView.getHeight() + mOffset.y);
        mThumbPosition.set(x, y);
        mInvalidateTmpRect.set(mThumbPosition.x + mOffset.x, mOffset.y, mThumbPosition.x + mOffset.x + mWidth, mRecyclerView.getHeight() + mOffset.y);
        mInvalidateRect.union(mInvalidateTmpRect);
        mRecyclerView.invalidate(mInvalidateRect);
    }


    public void setOffset(int x, int y) {
        if (mOffset.x == x && mOffset.y == y) {
            return;
        }
        // do not create new objects here, this is called quite often
        mInvalidateRect.set(mThumbPosition.x + mOffset.x, mOffset.y, mThumbPosition.x + mOffset.x + mWidth, mRecyclerView.getHeight() + mOffset.y);
        mOffset.set(x, y);
        mInvalidateTmpRect.set(mThumbPosition.x + mOffset.x, mOffset.y, mThumbPosition.x + mOffset.x + mWidth, mRecyclerView.getHeight() + mOffset.y);
        mInvalidateRect.union(mInvalidateTmpRect);
        mRecyclerView.invalidate(mInvalidateRect);
    }

    // Setter/getter for the popup alpha for animations
    public void setOffsetX(int x) {
        setOffset(x, mOffset.y);
    }

    public int getOffsetX() {
        return mOffset.x;
    }

    public void show() {
        if (!mAnimatingShow) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                show_11();
            } else {
                setOffsetX(0);
            }
        }
        if (mAutoHideEnabled) {
            postAutoHideDelayed();
        } else {
            cancelAutoHide();
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void show_11() {
        if (mAutoHideAnimator != null) {
            mAutoHideAnimator.cancel();
        }
        mAutoHideAnimator = mFastScrollerOffsetxObjectAnimator.getOffsetXAnimator(this, 0);
        mAutoHideAnimator.setInterpolator(new LinearOutSlowInInterpolator());
        mAutoHideAnimator.setDuration(150);
        mAutoHideAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                super.onAnimationCancel(animation);
                mAnimatingShow = false;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mAnimatingShow = false;
            }
        });
        mAnimatingShow = true;
        try {
            mAutoHideAnimator.start();
        } catch (NullPointerException ignore) {
            // in the wild:
            // NullPointerException
            // tyValuesHolder.setupSetterAndGetter:408,
            // ObjectAnimator.initAnimation:304,
            // ValueAnimator.setCurrentPlayTime:521,
            // ValueAnimator.start:909,
            // ValueAnimator.start:931,
            // ObjectAnimator.start:282,
        }
    }

    protected void postAutoHideDelayed() {
        if (mRecyclerView != null && !mRecyclerView.isInEditMode()) {
            cancelAutoHide();
            mRecyclerView.postDelayed(mHideRunnable, mAutoHideDelay);
        }
    }

    protected void cancelAutoHide() {
        if (mRecyclerView != null) {
            mRecyclerView.removeCallbacks(mHideRunnable);
        }
    }

    public void setThumbColor(@ColorInt int color) {
        mThumb.setColor(color);
        mRecyclerView.invalidate(mInvalidateRect);
    }

    public void setTrackColor(@ColorInt int color) {
        mTrack.setColor(color);
        mRecyclerView.invalidate(mInvalidateRect);
    }

    public void setPopupBgColor(@ColorInt int color) {
        mPopup.setBgColor(color);
    }

    public void setPopupTextColor(@ColorInt int color) {
        mPopup.setTextColor(color);
    }

    public void setAutoHideDelay(int hideDelay) {
        mAutoHideDelay = hideDelay;
        if (mAutoHideEnabled) {
            postAutoHideDelayed();
        }
    }

    public void setAutoHideEnabled(boolean autoHideEnabled) {
        mAutoHideEnabled = autoHideEnabled;
        if (autoHideEnabled) {
            postAutoHideDelayed();
        } else {
            cancelAutoHide();
        }
    }

    public void setPopupTypeface(Typeface typeface) {
        mPopup.setTypeface(typeface);
    }
}