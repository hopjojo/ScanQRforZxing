package zj.neverland.scandemo;

import android.content.Intent;
import android.graphics.Bitmap;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.zxing.WriterException;

import zj.neverland.scandemo.zxing.android.CaptureActivity;
import zj.neverland.scandemo.zxing.encode.CodeCreator;

public class MainActivity extends AppCompatActivity {
//    private RelativeLayout mScanCropView;
//    private ImageView mScanLine;
//    private ValueAnimator mScanAnimator;
//
//    private CameraPreview mPreviewView;


    private static final int REQUEST_CODE_SCAN = 0x0000;


    TextView qrCoded;
    ImageView qrCodeImage;
    Button creator, scanner;
    EditText qrCodeUrl;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        qrCoded = (TextView) findViewById(R.id.ECoder_title);
        qrCodeImage = (ImageView) findViewById(R.id.ECoder_image);
        creator = (Button) findViewById(R.id.ECoder_creator);
        scanner = (Button) findViewById(R.id.ECoder_scaning);
        qrCodeUrl = (EditText) findViewById(R.id.ECoder_input);

        creator.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {

                String url = qrCodeUrl.getText().toString();
                try {
                    Bitmap bitmap = CodeCreator.createQRCode(url);
                    qrCodeImage.setImageBitmap(bitmap);
                } catch (WriterException e) {
                    e.printStackTrace();
                }

            }
        });

        scanner.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                // TODO Auto-generated method stub
                Intent intent = new Intent(MainActivity.this,
                        CaptureActivity.class);
                startActivityForResult(intent, REQUEST_CODE_SCAN);
            }
        });
//        Toolbar mToolbar = (Toolbar) findViewById(R.id.toolbar);
//        setSupportActionBar(mToolbar);

//        mPreviewView = (CameraPreview) findViewById(R.id.capture_preview);
//        mScanCropView = (RelativeLayout) findViewById(R.id.capture_crop_view);
//        mScanLine = (ImageView) findViewById(R.id.capture_scan_line);
//
//        mPreviewView.setScanCallback(resultCallback);
//
//        findViewById(R.id.capture_restart_scan).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                startScanUnKnowPermission();
//            }
//        });
    }
//    private ScanCallback resultCallback = new ScanCallback() {
//        @Override
//        public void onScanResult(String result) {
//            stopScan();
//            Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
//        }
//    };

//    @Override
//    protected void onResume() {
//        super.onResume();
//        if (mScanAnimator != null) {
//            startScanUnKnowPermission();
//        }
//    }
//
//    @Override
//    public void onPause() {
//        // Must be called here, otherwise the camera should not be released properly.
//        stopScan();
//        super.onPause();
//    }
//
//    /**
//     * Do not have permission to request for permission and start scanning.
//     */
//    private void startScanUnKnowPermission() {
////        AndPermission.with(this)
////                .permission(Manifest.permission.CAMERA)
////                .callback(new PermissionListener() {
////                    @Override
////                    public void onSucceed(int requestCode, @NonNull List<String> grantPermissions) {
////                        startScanWithPermission();
////                    }
////
////                    @Override
////                    public void onFailed(int requestCode, @NonNull List<String> deniedPermissions) {
////                        AndPermission.defaultSettingDialog(MainActivity.this).show();
////                    }
////                })
////                .start();
//    }
//
//    /**
//     * There is a camera when the direct scan.
//     */
//    private void startScanWithPermission() {
//        if (mPreviewView.start()) {
//            mScanAnimator.start();
//        } else {
////            new AlertDialog.Builder(this)
////                    .setTitle(R.string.camera_failure)
////                    .setMessage(R.string.camera_hint)
////                    .setCancelable(false)
////                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
////                        @Override
////                        public void onClick(DialogInterface dialog, int which) {
////                            finish();
////                        }
////                    })
////                    .show();
//        }
//    }
//
//    /**
//     * Stop scan.
//     */
//    private void stopScan() {
//        mScanAnimator.cancel();
//        mPreviewView.stop();
//    }
//
//    @Override
//    public void onWindowFocusChanged(boolean hasFocus) {
//        super.onWindowFocusChanged(hasFocus);
//        if (mScanAnimator == null) {
//            int height = mScanCropView.getMeasuredHeight() - 25;
//            mScanAnimator = ObjectAnimator.ofFloat(mScanLine, "translationY", 0F, height).setDuration(3000);
//            mScanAnimator.setInterpolator(new LinearInterpolator());
//            mScanAnimator.setRepeatCount(ValueAnimator.INFINITE);
//            mScanAnimator.setRepeatMode(ValueAnimator.REVERSE);
//
//            startScanUnKnowPermission();
//        }
//    }
@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    // 扫描二维码/条码回传
    if (requestCode == REQUEST_CODE_SCAN && resultCode == RESULT_OK) {
        if (data != null) {

            String content = data.getStringExtra(CaptureActivity.CODE_CONTENT);
            String type = data.getStringExtra(CaptureActivity.CODE_TYPE);
            Bitmap bitmap = data.getParcelableExtra(CaptureActivity.CODE_BITMAP);

            qrCoded.setText("解码结果： \n" + content+"----|----" + type);
            qrCodeImage.setImageBitmap(bitmap);
        }
    }
}
}
