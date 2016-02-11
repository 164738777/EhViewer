/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.ui.gl;

import android.graphics.Rect;
import android.graphics.RectF;
import android.support.annotation.IntDef;

import com.hippo.gl.glrenderer.GLCanvas;
import com.hippo.gl.glrenderer.Texture;
import com.hippo.gl.view.GLView;
import com.hippo.yorozuya.MathUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

public class ImageView extends GLView {

    @IntDef({SCALE_ORIGIN, SCALE_FIT_WIDTH, SCALE_FIT_HEIGHT, SCALE_FIT, SCALE_FIXED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Scale {}

    @IntDef({START_POSITION_TOP_LEFT, START_POSITION_TOP_RIGHT, START_POSITION_BOTTOM_LEFT,
            START_POSITION_BOTTOM_RIGHT, START_POSITION_CENTER})
    @Retention(RetentionPolicy.SOURCE)
    public @interface StartPosition {}

    // TODO adjust scale max and min according to image size and screen size
    private static final float SCALE_MIN = 1 / 10.0f;
    private static final float SCALE_MAX = 10.0f;

    public static final int SCALE_ORIGIN = 0;
    public static final int SCALE_FIT_WIDTH = 1;
    public static final int SCALE_FIT_HEIGHT = 2;
    public static final int SCALE_FIT = 3;
    public static final int SCALE_FIXED = 4;

    public static final int START_POSITION_TOP_LEFT = 0;
    public static final int START_POSITION_TOP_RIGHT = 1;
    public static final int START_POSITION_BOTTOM_LEFT = 2;
    public static final int START_POSITION_BOTTOM_RIGHT = 3;
    public static final int START_POSITION_CENTER = 4;

    private Texture mTexture;

    private RectF mDst = new RectF();
    private RectF mSrcActual = new RectF();
    private RectF mDstActual = new RectF();
    private Rect mValidRect = new Rect();

    private int mScaleMode = SCALE_FIT;
    private int mStartPosition = START_POSITION_TOP_RIGHT;
    private float mScaleValue = 1.0f;

    private float mScale = 1.0f;

    private boolean mScaleOffsetDirty = true;
    private boolean mPositionInRootDirty = true;

    @Override
    protected int getSuggestedMinimumWidth() {
        return Math.max(super.getSuggestedMinimumWidth(),
                mTexture == null ? 0 : mTexture.getWidth());
    }

    @Override
    protected int getSuggestedMinimumHeight() {
        return Math.max(super.getSuggestedMinimumHeight(),
                mTexture == null ? 0 : mTexture.getHeight());
    }

    @Override
    protected void onSizeChanged(int newW, int newH, int oldW, int oldH) {
        mScaleOffsetDirty = true;
        mPositionInRootDirty = true;
    }

    @Override
    protected void onPositionInRootChanged(int x, int y, int oldX, int oldY) {
        mPositionInRootDirty = true;
    }

    public void getScaleDefault(float[] scaleDefault) {
        if (mTexture == null) {
            return;
        }

        scaleDefault[0] = 1.0f;

        float scaleX = (float) getWidth() / mTexture.getWidth();
        float scaleY = (float) getHeight() / mTexture.getHeight();
        if (scaleX < scaleY) {
            scaleDefault[1] = scaleX;
        } else {
            scaleDefault[1] = scaleY;
        }

        scaleDefault[2] = scaleDefault[1] * 1.5f;

        Arrays.sort(scaleDefault);
    }

    public void setTexture(Texture texture) {
        mTexture = texture;
        mScaleOffsetDirty = true;
        mPositionInRootDirty = true;
    }

    public boolean isLoaded() {
        return mTexture != null;
    }

    public boolean canFlingVertically() {
        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
            if (mScaleOffsetDirty) {
                return false;
            }
        }

        return mDst.top < 0.0f || mDst.bottom > getHeight();
    }

    public boolean canFlingHorizontally() {
        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
            if (mScaleOffsetDirty) {
                return false;
            }
        }

        return mDst.left < 0.0f || mDst.right > getWidth();
    }

    public boolean canFling() {
        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
            if (mScaleOffsetDirty) {
                return false;
            }
        }

        return mDst.left < 0.0f || mDst.top < 0.0f || mDst.right > getWidth() || mDst.bottom > getHeight();
    }

    public int getMaxDx() {
        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
            if (mScaleOffsetDirty) {
                return 0;
            }
        }
        return Math.max(0, -(int) mDst.left);
    }

    public int getMinDx() {
        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
            if (mScaleOffsetDirty) {
                return 0;
            }
        }
        return Math.min(0, getWidth() - (int) mDst.right);
    }

    public int getMaxDy() {
        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
            if (mScaleOffsetDirty) {
                return 0;
            }
        }
        return Math.max(0, -(int) mDst.top);
    }

    public int getMinDy() {
        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
            if (mScaleOffsetDirty) {
                return 0;
            }
        }
        return Math.min(0, getHeight() - (int) mDst.bottom);
    }

    public float getScale() {
        return mScale;
    }

    /**
     * If target is shorter then screen, make it in screen center. If target is
     * longer then parent, make sure target fill parent over
     */
    private void adjustPosition() {
        RectF dst = mDst;
        int screenWidth = getWidth();
        int screenHeight = getHeight();
        float targetWidth = dst.width();
        float targetHeight = dst.height();

        if (targetWidth > screenWidth) {
            float fixXOffset = dst.left;
            if (fixXOffset > 0) {
                dst.left -= fixXOffset;
                dst.right -= fixXOffset;
            } else if ((fixXOffset = screenWidth - dst.right) > 0) {
                dst.left += fixXOffset;
                dst.right += fixXOffset;
            }
        } else {
            float left = (screenWidth - targetWidth) / 2;
            dst.offsetTo(left, dst.top);
        }
        if (targetHeight > screenHeight) {
            float fixYOffset = dst.top;
            if (fixYOffset > 0) {
                dst.top -= fixYOffset;
                dst.bottom -= fixYOffset;
            } else if ((fixYOffset = screenHeight - dst.bottom) > 0) {
                dst.top += fixYOffset;
                dst.bottom += fixYOffset;
            }
        } else {
            float top = (screenHeight - targetHeight) / 2;
            dst.offsetTo(dst.left, top);
        }
    }

    public void setScaleOffset(@Scale int scaleMode, @StartPosition int startPosition, float scaleValue) {
        mScaleMode = scaleMode;
        mStartPosition = startPosition;
        mScaleValue = scaleValue;

        int screenWidth = getWidth();
        int screenHeight = getHeight();

        if (mTexture == null || screenWidth == 0 || screenHeight == 0) {
            mScaleOffsetDirty = true;
            return;
        }

        int textureWidth = mTexture.getWidth();
        int textureHeight = mTexture.getHeight();
        // Avoid zero and negative
        if (textureWidth <= 0) {
            textureWidth = 1;
        }
        if (textureHeight <= 0) {
            textureHeight = 1;
        }

        // Set scale
        float targetWidth;
        float targetHeight;
        switch (scaleMode) {
            case SCALE_ORIGIN:
                mScale = 1.0f;
                targetWidth = textureWidth;
                targetHeight = textureHeight;
                break;
            case SCALE_FIT_WIDTH:
                mScale = (float) screenWidth / textureWidth;
                targetWidth = screenWidth;
                targetHeight = textureHeight * mScale;
                break;
            case SCALE_FIT_HEIGHT:
                mScale = (float) screenHeight / textureHeight;
                targetWidth = textureWidth * mScale;
                targetHeight = screenHeight;
                break;
            case SCALE_FIT:
                float scaleX = (float) screenWidth / textureWidth;
                float scaleY = (float) screenHeight / textureHeight;
                if (scaleX < scaleY) {
                    mScale = scaleX;
                    targetWidth = screenWidth;
                    targetHeight = textureHeight * scaleX;
                } else {
                    mScale = scaleY;
                    targetWidth = textureWidth * scaleY;
                    targetHeight = screenHeight;
                    break;
                }
                break;
            case SCALE_FIXED:
            default:
                mScale = scaleValue;
                targetWidth = textureWidth * scaleValue;
                targetHeight = textureHeight * scaleValue;
                break;
        }

        // adjust scale, not too big, not too small
        if (mScale < SCALE_MIN) {
            mScale = SCALE_MIN;
            targetWidth = textureWidth * SCALE_MIN;
            targetHeight = textureHeight * SCALE_MIN;
        } else if (mScale > SCALE_MAX) {
            mScale = SCALE_MAX;
            targetWidth = textureWidth * SCALE_MAX;
            targetHeight = textureHeight * SCALE_MAX;
        }

        // Set mDst.left and mDst.right
        RectF dst = mDst;
        switch (startPosition) {
            case START_POSITION_TOP_LEFT:
                dst.left = 0;
                dst.top = 0;
                break;
            case START_POSITION_TOP_RIGHT:
                dst.left = screenWidth - targetWidth;
                dst.top = 0;
                break;
            case START_POSITION_BOTTOM_LEFT:
                dst.left = 0;
                dst.top = screenHeight - targetHeight;
                break;
            case START_POSITION_BOTTOM_RIGHT:
                dst.left = screenWidth - targetWidth;
                dst.top = screenHeight - targetHeight;
                break;
            case START_POSITION_CENTER:
            default:
                dst.left = (screenWidth - targetWidth) / 2;
                dst.top = (screenHeight - targetHeight) / 2;
                break;
        }

        // Set mDst.right and mDst.bottom
        dst.right = dst.left + targetWidth;
        dst.bottom = dst.top + targetHeight;

        // adjust position
        adjustPosition();

        mScaleOffsetDirty = false;
        mPositionInRootDirty = true;
    }

    public void scroll(int dx, int dy, int[] remain) {
        // Only work after layout
        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        }
        if (mScaleOffsetDirty) {
            remain[0] = dx;
            remain[1] = dy;
            return;
        }

        RectF dst = mDst;
        int screenWidth = getWidth();
        int screenHeight = getHeight();
        float targetWidth = dst.width();
        float targetHeight = dst.height();

        if (targetWidth > screenWidth) {
            dst.left -= dx;
            dst.right -= dx;

            float fixXOffset = dst.left;
            if (fixXOffset > 0) {
                dst.left -= fixXOffset;
                dst.right -= fixXOffset;
                remain[0] = -(int) fixXOffset;
            } else if ((fixXOffset = screenWidth - dst.right) > 0) {
                dst.left += fixXOffset;
                dst.right += fixXOffset;
                remain[0] = (int) fixXOffset;
            } else {
                remain[0] = 0;
            }
        } else {
            remain[0] = dx;
        }
        if (targetHeight > screenHeight) {
            dst.top -= dy;
            dst.bottom -= dy;

            float fixYOffset = dst.top;
            if (fixYOffset > 0) {
                dst.top -= fixYOffset;
                dst.bottom -= fixYOffset;
                remain[1] = -(int) fixYOffset;
            } else if ((fixYOffset = screenHeight - dst.bottom) > 0) {
                dst.top += fixYOffset;
                dst.bottom += fixYOffset;
                remain[1] = (int) fixYOffset;
            } else {
                remain[1] = 0;
            }
        } else {
            remain[1] = dy;
        }

        if (dx != remain[0] || dy != remain[1]) {
            mPositionInRootDirty = true;
            invalidate();
        }
    }

    public void scale(float focusX, float focusY, float scale) {
        // Only work after layout
        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        }
        if (mScaleOffsetDirty) {
            return;
        }

        if ((mScale == SCALE_MAX && scale >= 1.0f) || (mScale == SCALE_MIN && scale < 1.0f)) {
            return;
        }

        float newScale = mScale * scale;
        newScale = MathUtils.clamp(newScale, SCALE_MIN, SCALE_MAX);
        mScale = newScale;
        RectF dst = mDst;
        float left = (focusX - ((focusX - dst.left) * scale));
        float top = (focusY - ((focusY - dst.top) * scale));
        dst.set(left, top,
                (left + (mTexture.getWidth() * newScale)),
                (top + (mTexture.getHeight() * newScale)));

        // adjust position
        adjustPosition();

        mPositionInRootDirty = true;
        invalidate();
    }

    private void applyPositionInRoot() {
        int width = mTexture.getWidth();
        int height = mTexture.getHeight();
        RectF dst = mDst;
        RectF dstActual = mDstActual;
        RectF srcActual = mSrcActual;

        dstActual.set(dst);
        getValidRect(mValidRect);
        if (dstActual.intersect(mValidRect.left, mValidRect.top, mValidRect.right, mValidRect.bottom)) {
            srcActual.left = MathUtils.lerp(0, width,
                    MathUtils.delerp(dst.left, dst.right, dstActual.left));
            srcActual.right = MathUtils.lerp(0, width,
                    MathUtils.delerp(dst.left, dst.right, dstActual.right));
            srcActual.top = MathUtils.lerp(0, height,
                    MathUtils.delerp(dst.top, dst.bottom, dstActual.top));
            srcActual.bottom = MathUtils.lerp(0, height,
                    MathUtils.delerp(dst.top, dst.bottom, dstActual.bottom));
        } else {
            // Can't be seen, set src and dst empty
            srcActual.setEmpty();
            dstActual.setEmpty();
        }

        mPositionInRootDirty = false;
    }

    @Override
    public void onRender(GLCanvas canvas) {
        Texture texture = mTexture;
        if (texture == null) {
            return;
        }

        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        }

        if (mPositionInRootDirty) {
            applyPositionInRoot();
        } else {
            mSrcActual.set(0, 0, texture.getWidth(), texture.getHeight());
            mDstActual.set(mDst);
        }

        if (!mSrcActual.isEmpty()) {
            texture.draw(canvas, mSrcActual, mDstActual);
        }
    }
}
