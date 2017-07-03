package zj.neverland.scandemo.zxing.android;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import io.reactivex.Observer;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import zj.neverland.scandemo.R;
import zj.neverland.scandemo.zxing.android.BeepManager;
import zj.neverland.scandemo.zxing.android.CaptureActivityHandler;
import zj.neverland.scandemo.zxing.android.InactivityTimer;
import zj.neverland.scandemo.zxing.android.IntentSource;
import zj.neverland.scandemo.zxing.camera.CameraManager;
import zj.neverland.scandemo.zxing.view.ViewfinderView;


/**
 * 这个activity打开相机，在后台线程做常规的扫描；
 * 它绘制了一个结果view来帮助正确地显示条形码，在扫描的时候显示反馈信息，
 * 然后在扫描成功的时候覆盖扫描结果
 */
public final class CaptureActivity extends Activity implements
		SurfaceHolder.Callback {

	public static final String CODE_CONTENT = "codedContent";
	public static final String CODE_TYPE = "codedType";
	public static final String CODE_BITMAP = "codedBitmap";

	private static final String TAG = CaptureActivity.class.getSimpleName();
	// 相机控制
	private CameraManager cameraManager;
	private CaptureActivityHandler handler;
	private ViewfinderView viewfinderView;
	private boolean hasSurface;
	private IntentSource source;
	private Collection<BarcodeFormat> decodeFormats;
	private Map<DecodeHintType, ?> decodeHints;
	private String characterSet;
	// 电量控制
	private InactivityTimer inactivityTimer;
	// 声音、震动控制
	private BeepManager beepManager;

	private ImageButton imageButton_back;

	private ImageButton imageButton_flashlight;

	public ViewfinderView getViewfinderView() {
		return viewfinderView;
	}

	public Handler getHandler() {
		return handler;
	}

	public CameraManager getCameraManager() {
		return cameraManager;
	}

	public void drawViewfinder() {
		viewfinderView.drawViewfinder();
	}

	/**
	 * OnCreate中初始化一些辅助类，如InactivityTimer（休眠）、Beep（声音）以及AmbientLight（闪光灯）
	 */
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		// 保持Activity处于唤醒状态
		Window window = getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.capture);

		hasSurface = false;

		inactivityTimer = new InactivityTimer(this);
		beepManager = new BeepManager(this);

		imageButton_back = (ImageButton) findViewById(R.id.capture_imageview_back);
		imageButton_back.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				finish();
			}
		});

		imageButton_flashlight = (ImageButton) findViewById(R.id.capture_imageview_flashlight);
		imageButton_flashlight.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				boolean fl = cameraManager.turnLightOnCamera();
				if (fl) {
					imageButton_flashlight.setBackground(getResources().getDrawable(R.mipmap.lighton));
				} else {
					imageButton_flashlight.setBackground(getResources().getDrawable(R.mipmap.lightoff));
				}
			}
		});
		Boolean isFlashAvailable = getApplicationContext().getPackageManager()
				.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
		if (!isFlashAvailable) {
			imageButton_flashlight.setVisibility(View.GONE);
		} else {
			imageButton_flashlight.setVisibility(View.VISIBLE);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		// CameraManager必须在这里初始化，而不是在onCreate()中。
		// 这是必须的，因为当我们第一次进入时需要显示帮助页，我们并不想打开Camera,测量屏幕大小
		// 当扫描框的尺寸不正确时会出现bug

		cameraManager = new CameraManager(getApplication());

		viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
		viewfinderView.setCameraManager(cameraManager);

		handler = null;

		SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		if (hasSurface) {
			// activity在paused时但不会stopped,因此surface仍旧存在；
			// surfaceCreated()不会调用，因此在这里初始化camera
			initCamera(surfaceHolder);
		} else {
			// 重置callback，等待surfaceCreated()来初始化camera
			surfaceHolder.addCallback(this);
		}

		beepManager.updatePrefs();
		inactivityTimer.onResume();

		source = IntentSource.NONE;
		decodeFormats = null;
		characterSet = null;
	}

	@Override
	protected void onPause() {
		if (handler != null) {
			handler.quitSynchronously();
			handler = null;
		}
		inactivityTimer.onPause();
		beepManager.close();
		cameraManager.closeDriver();
		if (!hasSurface) {
			SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
			SurfaceHolder surfaceHolder = surfaceView.getHolder();
			surfaceHolder.removeCallback(this);
		}
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		inactivityTimer.shutdown();
		super.onDestroy();
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (!hasSurface) {
			hasSurface = true;
			initCamera(holder);
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		hasSurface = false;
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
							   int height) {

	}

	/**
	 * 扫描成功，处理反馈信息
	 *
	 * @param rawResult
	 * @param barcode
	 * @param scaleFactor
	 */
	public void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor) {
		inactivityTimer.onActivity();

		boolean fromLiveScan = barcode != null;
		//这里处理解码完成后的结果，此处将参数回传到Activity处理
		if (fromLiveScan) {
			beepManager.playBeepSoundAndVibrate();
			Intent intent = getIntent();
			intent.putExtra(CODE_CONTENT, rawResult.getText());
			intent.putExtra(CODE_TYPE, rawResult.getBarcodeFormat().name());
			intent.putExtra(CODE_BITMAP, barcode);
			setResult(RESULT_OK, intent);
			finish();
		}

	}

	/**
	 * 初始化Camera
	 *
	 * @param surfaceHolder
	 */
	private void initCamera(final SurfaceHolder surfaceHolder) {
		if (surfaceHolder == null) {
			throw new IllegalStateException("No SurfaceHolder provided");
		}
		if (cameraManager.isOpen()) {
			return;
		}
		//权限控制
		RxPermissions rxPermissions = new RxPermissions(this);
		rxPermissions.request(Manifest.permission.CAMERA)
				.subscribe(new Observer<Boolean>() {
					@Override
					public void onSubscribe(@NonNull Disposable d) {

					}

					@Override
					public void onNext(Boolean value) {
						if (value) {
							try {
								// 打开Camera硬件设备
								cameraManager.openDriver(surfaceHolder);
								// 创建一个handler来打开预览，并抛出一个运行时异常
								if (handler == null) {
									handler = new CaptureActivityHandler(CaptureActivity.this, decodeFormats,
											decodeHints, characterSet, cameraManager);
								}
							} catch (IOException ioe) {
								Log.w(TAG, ioe);
								displayFrameworkBugMessageAndExit();
							} catch (RuntimeException e) {
								Log.w(TAG, "Unexpected error initializing camera", e);
								displayFrameworkBugMessageAndExit();
							}
						} else {
							finish();
							Toast.makeText(CaptureActivity.this, "拒绝权限", Toast.LENGTH_SHORT).show();
						}
					}

					@Override
					public void onError(Throwable e) {

					}

					@Override
					public void onComplete() {

					}
				});

	}

	/**
	 * 显示底层错误信息并退出应用
	 */
	private void displayFrameworkBugMessageAndExit() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.app_name));
		builder.setMessage(getString(R.string.msg_camera_framework_bug));
		builder.setPositiveButton(R.string.button_ok, new FinishListener(this));
		builder.setOnCancelListener(new FinishListener(this));
		builder.show();
	}

}
