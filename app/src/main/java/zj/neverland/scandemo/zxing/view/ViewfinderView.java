/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zj.neverland.scandemo.zxing.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import com.google.zxing.ResultPoint;

import java.util.ArrayList;
import java.util.List;

import zj.neverland.scandemo.zxing.android.Scanner;
import zj.neverland.scandemo.zxing.camera.CameraManager;
import zj.neverland.scandemo.R;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder
 * rectangle and partial transparency outside it, as well as the laser scanner
 * animation and result points. 这是一个位于相机顶部的预览view,它增加了一个外部部分透明的取景框，以及激光扫描动画和结果组件
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView extends View {

    private static final int[] SCANNER_ALPHA = {0, 64, 128, 192, 255, 192,
            128, 64};
    private static final long ANIMATION_DELAY = 80L;
    private static final int CURRENT_POINT_OPACITY = 0xA0;
    private static final int MAX_RESULT_POINTS = 20;
    private static final int POINT_SIZE = 6;
    private static final int DEFAULT_LASER_LINE_HEIGHT = 2;//扫描线默认高度

    private CameraManager cameraManager;
    private final Paint paint;
    private Bitmap resultBitmap;
    private int maskColor; // 取景框外的背景颜色
    private int resultColor;// result Bitmap的颜色
    private int laserColor; // 红色扫描线的颜色
    private int resultPointColor; // 特征点的颜色
    private int statusColor; // 提示文字颜色
    private int scannerAlpha;
    private List<ResultPoint> possibleResultPoints;
    private List<ResultPoint> lastPossibleResultPoints;

    //扫描框
    int frameBoundColor;
    int frameBound_CornerColor;
    float cornerWidth;
    float cornerLength;
    //提示文字
    float statusTextSize;//提示文字大小
    String statusText;
    String statusSubText;
    boolean isTextGravityBottom;

    private int scanLineType;// 扫描线类型
    private int animationDelay = 0;
    // 扫描线移动的y
    private int scanLineTop;// 扫描线最顶端位置
    private int scanLineHeight;//扫描线默认高度
    // 扫描线移动速度
    private final int SCAN_VELOCITY = 10;
    // 扫描线
    int laserLineResId;
    Bitmap scanLight;

    public ViewfinderView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Initialize these once for performance rather than calling them every
        // time in onDraw().
        setTypeArray(context, attrs);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        scannerAlpha = 0;
        possibleResultPoints = new ArrayList<ResultPoint>(5);
        lastPossibleResultPoints = null;

        scanLineHeight = Scanner.dp2px(context, DEFAULT_LASER_LINE_HEIGHT);

    }

    private void setTypeArray(Context context, AttributeSet attrs) {
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.ViewfinderView);
        Resources resources = getResources();
        maskColor = ta.getColor(R.styleable.ViewfinderView_OutSideColor, resources.getColor(R.color.viewfinder_mask));
        statusColor = ta.getColor(R.styleable.ViewfinderView_StatusTextColor, resources.getColor(R.color.status_text));
        //扫描线颜色
        laserColor = ta.getColor(R.styleable.ViewfinderView_ScanLightColor, resources.getColor(R.color.viewfinder_laser));
        //提示文字
        statusTextSize = Scanner.sp2px(getContext(), ta.getDimension(R.styleable.ViewfinderView_StatusTextSize, 16));
        statusText = ta.getString(R.styleable.ViewfinderView_StatusText);
        statusSubText = ta.getString(R.styleable.ViewfinderView_StatusSubText);
        isTextGravityBottom = ta.getBoolean(R.styleable.ViewfinderView_TextGravityBottom, true);
        //扫描框
        scanLineType = ta.getInt(R.styleable.ViewfinderView_ScanLightType, 0);
        BitmapDrawable bitmapDrawable = (BitmapDrawable) ta.getDrawable(R.styleable.ViewfinderView_ScanLight);
        if (bitmapDrawable != null)
            scanLight = bitmapDrawable.getBitmap();
        else
            scanLight = BitmapFactory.decodeResource(resources, R.mipmap.scan_light);
        //边框颜色
        frameBoundColor = ta.getColor(R.styleable.ViewfinderView_FrameBoundColor, Color.TRANSPARENT);
        //四角
        frameBound_CornerColor = ta.getColor(R.styleable.ViewfinderView_FrameBound_CornerColor, Color.GREEN);
        cornerWidth = ta.getDimension(R.styleable.ViewfinderView_FrameBound_CornerWidth, 5);
        cornerLength = ta.getDimension(R.styleable.ViewfinderView_FrameBound_CornerLength, 45);

        resultColor = resources.getColor(R.color.result_view);
        resultPointColor = resources.getColor(R.color.possible_result_points);

        ta.recycle();
    }

    public void setCameraManager(CameraManager cameraManager) {
        this.cameraManager = cameraManager;
    }

    @SuppressLint("DrawAllocation")
    @Override
    public void onDraw(Canvas canvas) {
        if (cameraManager == null) {
            return; // not ready yet, early draw before done configuring
        }

        // frame为取景框
        Rect frame = cameraManager.getFramingRect();
        Rect previewFrame = cameraManager.getFramingRectInPreview();
        if (frame == null || previewFrame == null) {
            return;
        }
        int width = canvas.getWidth();
        int height = canvas.getHeight();

        // Draw the exterior (i.e. outside the framing rect) darkened
        // 绘制取景框外的暗灰色的表面，分四个矩形绘制
        paint.setColor(resultBitmap != null ? resultColor : maskColor);
        canvas.drawRect(0, 0, width, frame.top, paint);// Rect_1
        canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint); // Rect_2
        canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1,
                paint); // Rect_3
        canvas.drawRect(0, frame.bottom + 1, width, height, paint); // Rect_4

        if (resultBitmap != null) {
            // 如果有二维码结果的Bitmap，在扫取景框内绘制不透明的result Bitmap
            paint.setAlpha(CURRENT_POINT_OPACITY);
            canvas.drawBitmap(resultBitmap, null, frame, paint);
        } else {
            //扫描边框
            drawFrameBounds(canvas, frame);
            // 提示文字
            drawStatusText(canvas, frame, width);
            // 绘制扫描线
            drawScanLight(canvas, frame);
            moveLaserSpeed(frame);//计算移动位置

            float scaleX = frame.width() / (float) previewFrame.width();
            float scaleY = frame.height() / (float) previewFrame.height();

            // 绘制扫描线周围的特征点
            List<ResultPoint> currentPossible = possibleResultPoints;
            List<ResultPoint> currentLast = lastPossibleResultPoints;
            int frameLeft = frame.left;
            int frameTop = frame.top;
            if (currentPossible.isEmpty()) {
                lastPossibleResultPoints = null;
            } else {
                possibleResultPoints = new ArrayList<ResultPoint>(5);
                lastPossibleResultPoints = currentPossible;
                paint.setAlpha(CURRENT_POINT_OPACITY);
                paint.setColor(resultPointColor);
                synchronized (currentPossible) {
                    for (ResultPoint point : currentPossible) {
                        canvas.drawCircle(frameLeft
                                        + (int) (point.getX() * scaleX), frameTop
                                        + (int) (point.getY() * scaleY), POINT_SIZE,
                                paint);
                    }
                }
            }
            if (currentLast != null) {
                paint.setAlpha(CURRENT_POINT_OPACITY / 2);
                paint.setColor(resultPointColor);
                synchronized (currentLast) {
                    float radius = POINT_SIZE / 2.0f;
                    for (ResultPoint point : currentLast) {
                        canvas.drawCircle(frameLeft
                                + (int) (point.getX() * scaleX), frameTop
                                + (int) (point.getY() * scaleY), radius, paint);
                    }
                }
            }

            // Request another update at the animation interval, but only
            // repaint the laser line,
            // not the entire viewfinder mask.
            postInvalidateDelayed(ANIMATION_DELAY, frame.left - POINT_SIZE,
                    frame.top - POINT_SIZE, frame.right + POINT_SIZE,
                    frame.bottom + POINT_SIZE);
        }
    }

    /**
     * 绘制取景框边框
     *
     * @param canvas
     * @param frame
     */
    private void drawFrameBounds(Canvas canvas, Rect frame) {

        paint.setColor(frameBoundColor);
        paint.setStrokeWidth(2);
        paint.setStyle(Paint.Style.STROKE);

        canvas.drawRect(frame, paint);

        paint.setColor(frameBound_CornerColor);
        paint.setStyle(Paint.Style.FILL);

        float corWidth = cornerWidth;
        float corLength = cornerLength;

        // 左上角
        canvas.drawRect(frame.left - corWidth, frame.top, frame.left, frame.top
                + corLength, paint);
        canvas.drawRect(frame.left - corWidth, frame.top - corWidth, frame.left
                + corLength, frame.top, paint);
        // 右上角
        canvas.drawRect(frame.right, frame.top, frame.right + corWidth,
                frame.top + corLength, paint);
        canvas.drawRect(frame.right - corLength, frame.top - corWidth,
                frame.right + corWidth, frame.top, paint);
        // 左下角
        canvas.drawRect(frame.left - corWidth, frame.bottom - corLength,
                frame.left, frame.bottom, paint);
        canvas.drawRect(frame.left - corWidth, frame.bottom, frame.left
                + corLength, frame.bottom + corWidth, paint);
        // 右下角
        canvas.drawRect(frame.right, frame.bottom - corLength, frame.right
                + corWidth, frame.bottom, paint);
        canvas.drawRect(frame.right - corLength, frame.bottom, frame.right
                + corWidth, frame.bottom + corWidth, paint);
    }

    /**
     * 绘制提示文字
     *
     * @param canvas
     * @param frame
     * @param width
     */
    private void drawStatusText(Canvas canvas, Rect frame, int width) {
        String statusText1 = statusText;
        String statusText2 = statusSubText;
        if (TextUtils.isEmpty(statusText1))
            statusText1 = getResources().getString(R.string.viewfinderview_status_text1);
        if (TextUtils.isEmpty(statusText2))
            statusText2 = getResources().getString(R.string.viewfinderview_status_text2);

        TextPaint textPaint = new TextPaint();
        textPaint.setAntiAlias(true);
        textPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(statusColor);
        textPaint.setTextSize(statusTextSize);

        float x = frame.left;//文字开始位置
        //根据 drawTextGravityBottom 文字在扫描框上方还是下文，默认下方
        float y = isTextGravityBottom ? frame.bottom + 10
                : frame.top - 180;

        StaticLayout staticLayout = new StaticLayout(statusText1 + statusText2, textPaint, frame.width()
                , Layout.Alignment.ALIGN_CENTER, 1.0f, 0, false);
        canvas.save();
        canvas.translate(x, y);
        staticLayout.draw(canvas);
        canvas.restore();
    }

    /**
     * 绘制移动扫描线
     *
     * @param canvas
     * @param frame
     */
    private void drawScanLight(Canvas canvas, Rect frame) {
        switch (scanLineType) {
            case 0:
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(laserColor);// 设置扫描线颜色
                canvas.drawRect(frame.left, scanLineTop, frame.right, scanLineTop + scanLineHeight, paint);
                break;
            case 1:
                if (scanLight == null)//图片资源文件转为 Bitmap
                    scanLight = BitmapFactory.decodeResource(getResources(), laserLineResId);
                int height = scanLight.getHeight();//取原图高

                Rect laserRect = new Rect(frame.left, scanLineTop, frame.right, height);
                canvas.drawBitmap(scanLight, null, laserRect, paint);
                break;
            case 2:
                if (scanLight == null)//图片资源文件转为 Bitmap
                    scanLight = BitmapFactory.decodeResource(getResources(), laserLineResId);
                int gridheight = scanLight.getHeight();//取原图高
                RectF dstRectF = new RectF(frame.left, frame.top, frame.right, scanLineTop);
                Rect srcRect = new Rect(0, (int) (gridheight - dstRectF.height()), scanLight.getWidth(), gridheight);
                canvas.drawBitmap(scanLight, srcRect, dstRectF, paint);
                break;
        }

    }

    //扫描速度
    private void moveLaserSpeed(Rect frame) {
        //初始化扫描线最顶端位置
        if (scanLineTop == 0) {
            scanLineTop = frame.top;
        }
        // 每次刷新界面，扫描线往下移动 LASER_VELOCITY
        scanLineTop += SCAN_VELOCITY;
        if (scanLineTop >= frame.bottom) {
            scanLineTop = frame.top;
        }
        if (animationDelay == 0) {
            animationDelay = (int) ((1.0f * 1000 * SCAN_VELOCITY) / (frame.bottom - frame.top));
        }

        // 只刷新扫描框的内容，其他地方不刷新
        postInvalidateDelayed(animationDelay, frame.left - POINT_SIZE, frame.top - POINT_SIZE
                , frame.right + POINT_SIZE, frame.bottom + POINT_SIZE);
    }

    public void drawViewfinder() {
        Bitmap resultBitmap = this.resultBitmap;
        this.resultBitmap = null;
        if (resultBitmap != null) {
            resultBitmap.recycle();
        }
        invalidate();
    }

    /**
     * Draw a bitmap with the result points highlighted instead of the live
     * scanning display.
     *
     * @param barcode An image of the decoded barcode.
     */
    public void drawResultBitmap(Bitmap barcode) {
        resultBitmap = barcode;
        invalidate();
    }

    public void addPossibleResultPoint(ResultPoint point) {
        List<ResultPoint> points = possibleResultPoints;
        synchronized (points) {
            points.add(point);
            int size = points.size();
            if (size > MAX_RESULT_POINTS) {
                // trim it
                points.subList(0, size - MAX_RESULT_POINTS / 2).clear();
            }
        }
    }

}
