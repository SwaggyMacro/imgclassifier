package com.julym.camera;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;
import com.julym.camera.CameraPreview;
import com.julym.camera.ml.efficientnetLite4Fp322;


import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private Button buttonShot;
    private Button buttonChoose;
    private Button buttonFlash;
    private static boolean reShot = false;
    public static Camera c;
    public static CameraPreview mPreview;
    public static Context context;
    private Camera.Parameters parameters;
    public static FrameLayout preview;
    private static efficientnetLite4Fp322 model;
    private ImageView imageView;
    public static final int CAMERA_REQ_CODE = 111;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Display display = getWindowManager().getDefaultDisplay();
        int screenHeight = display.getHeight();
        int screenWidth = display.getWidth();
        context = this;
        try {
            model = efficientnetLite4Fp322.newInstance(MainActivity.this); // 预先加载模型
        } catch (IOException e) {
            System.out.println("Load Model Error");
            e.printStackTrace();
        }
        //getSupportActionBar().hide();//去掉Action Bar 单纯不喜欢
        imageView = (ImageView)findViewById(R.id.imageView);
        requestPermission();//请求摄像头权限
        registerScreenService();//注册屏幕监听事件 监听关闭 开启
        c = getCameraInstance(); // 实例化一个摄像头对象
        parameters = c.getParameters();
        //实例化preview显示 摄像头内容
        mPreview = new CameraPreview(this, c);
        preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.getLayoutParams().height = (int) (screenHeight*0.6);//设置摄像头预览显示区域高度
        imageView.getLayoutParams().height = (int) (screenHeight*0.6);//设置ImageView高度
        imageView.getLayoutParams().width = (int) (screenWidth);
        imageView.setDrawingCacheEnabled(true);
        //c.getParameters().setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO); // 如果声明自动对焦的话 再次调用Camera.autoFocus 将会无效
        c.setDisplayOrientation(90);//设置旋转90° 不然是横屏
        preview.addView(mPreview);// 将mPreview添加到preview上显示
        buttonShot = (Button)findViewById(R.id.button_capture);
        buttonChoose = (Button)findViewById(R.id.button_choose);
        buttonFlash = (Button)findViewById(R.id.button_flash);
        setOnAutoFocusClickListener();
        initListen();
    }
    private void initListen(){
        buttonFlash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (parameters.getFlashMode() != Camera.Parameters.FLASH_MODE_TORCH){
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    c.setParameters(parameters);
                    buttonFlash.setText("FLASH OFF");
                }else{
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                    c.setParameters(parameters);
                    buttonFlash.setText("FLASH ON");
                }
            }
        });
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                imageView.setVisibility(View.INVISIBLE);
                preview.setVisibility(View.VISIBLE);
                buttonShot.setText("SHOT");
                reShot = false;
            }
        });
        buttonShot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!reShot){
                    reShot = true;
                    imageView.setVisibility(View.VISIBLE);
                    preview.setVisibility(View.INVISIBLE);
                    buttonShot.setText("RESHOT");
                    getPreViewImage();
                }else{
                    reShot = false;
                    imageView.setVisibility(View.INVISIBLE);
                    preview.setVisibility(View.VISIBLE);
                    buttonShot.setText("SHOT");
                }
            }
        });
        buttonChoose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(Intent.createChooser(intent, "Select Picture"),1);
            }
        });
    }
    private void getPreViewImage() {
        c.setPreviewCallback(new Camera.PreviewCallback(){
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                Camera.Size size = camera.getParameters().getPreviewSize();
                try{
                    YuvImage image = new YuvImage(data, ImageFormat.NV21, size.width, size.height, null);
                    if(image!=null){
                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        image.compressToJpeg(new Rect(0, 0, size.width, size.height), 100, stream);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());
                        //旋转90°至原来的方向
                        bitmap = rotateBitmap(bitmap,90);
                        imageView.setImageBitmap(bitmap);
                        imageView.setVisibility(View.VISIBLE);
                        preview.setVisibility(View.INVISIBLE);
                        Map result = efficientnet(bitmap);
                        DecimalFormat df = new DecimalFormat("0.00");
                        String score = String.valueOf(df.format(Double.valueOf(result.get("score").toString()) * 100));
                        showMessage(getApplication(),"This " + score + "% may be "+result.get("predict").toString(),1);
                        stream.close();
                        c.setPreviewCallback(null);
                    }
                }catch(Exception ex){
                    Log.e("Sys","Error:"+ex.getMessage());
                }
            }
        });
    }
    public static Bitmap rotateBitmap(Bitmap source, float angle)
    {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }
    private String GetSubStr(String str, String left, String right){
        System.out.println(str);
        int leftIndex = str.indexOf(left) + left.length();
        System.out.println(leftIndex);
        int rightIndex = str.replace(left,"").indexOf(right) + right.length() + left.length();
        System.out.println(rightIndex);
        return str.substring(leftIndex,rightIndex);
    }
    private boolean isFromGallery(String Url){
        if (GetSubStr(Url,"content://","/").indexOf("gallery") != -1){
            return true;
        }else{
            return false;
        }
    }
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            Uri uri = data.getData();
            Log.e("URL", uri.toString());
            ContentResolver cr = this.getContentResolver();
            try {
                Bitmap bitmap = BitmapFactory.decodeStream(cr.openInputStream(uri));
                System.out.println(GetSubStr(uri.toString(),"content://","/"));
                System.out.println(uri.toString());
                if (isFromGallery(uri.toString())){
                    bitmap = rotateBitmap(bitmap,90);//将图片 旋转90度 回归正常方向
                }
                //ImageView显示选中图片
                preview.setVisibility(View.INVISIBLE);
                imageView.setVisibility(View.VISIBLE);
                imageView.setImageBitmap(bitmap);
                reShot = true;
                //bitmap = imageView.getDrawingCache(); 从ImageView 获取Bitmap 在此之前须执行 imageView.setDrawingCacheEnabled(true);
                Map result = efficientnet(bitmap);
                DecimalFormat df = new DecimalFormat("0.00");
                String score = String.valueOf(df.format(Double.valueOf(result.get("score").toString()) * 100));
                showMessage(getApplication(),"This " + score + "% may be "+result.get("predict").toString(),1);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
    public Map efficientnet(Bitmap bitmap){
        Map result = new HashMap();
        TensorImage image = TensorImage.fromBitmap(bitmap);
        // Runs model inference and gets result.
        efficientnetLite4Fp322.Outputs outputs = model.process(image);
        List<Category> probability = outputs.getProbabilityAsCategoryList();
        double temp = 0;
        String predict = null;
        for( int i = 0 ; i < probability.size() ; i++) {
            if (temp>probability.get(i).getScore()){
            }else{
                temp=probability.get(i).getScore();
                predict = probability.get(i).getLabel();
            }
        }
        System.out.println(probability);
        Log.e("Predict", predict);
        Log.e("Score", String.valueOf(temp));
        result.put("predict", predict);
        result.put("score", String.valueOf(temp));
        return result;
    }
    public static void setOnAutoFocusClickListener(){
        mPreview.setOnClickListener(new View.OnClickListener() { // 设置点击Preview 自动对焦
            @Override
            public void onClick(View view) {
                if (c!=null) {
                    c.autoFocus(new Camera.AutoFocusCallback() {
                        @Override
                        public void onAutoFocus(boolean success, Camera camera) {
                            if (success) {
                                System.out.println("AutoFocus Succeed");
                            }else{
                                camera.autoFocus(this);//如果失败，再次自动对焦
                            }
                        }
                    });
                }
            }
        });
    }
    private void showMessage(Context context, String Message, int isLong){
        Toast.makeText(context,Message,isLong).show();
    }
    private void requestPermission() {
        // 判断当前Activity是否已经获得了该权限
        if (ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // 如果App的权限申请曾经被用户拒绝过，就需要在这里跟用户做出解释
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.CAMERA)) {
                Toast.makeText(MainActivity.this,"请进入设置-应用管理-打开相机权限",Toast.LENGTH_SHORT).show();
            } else {
                // 进行权限请求
                ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},CAMERA_REQ_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,String permissions[], int[] grantResults) {
        if(requestCode==CAMERA_REQ_CODE) {
            // 如果请求被拒绝，那么通常grantResults数组为空
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 申请成功，进行相应操作
            } else {
                // 申请失败，可以继续向用户解释。
                Toast.makeText(MainActivity.this, "没有相机权限,您可能无法正常使用本应用", Toast.LENGTH_LONG).show();
            }
        }
    }
    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            // attempt to get a Camera instance
            int numberOfCameras = Camera.getNumberOfCameras(); //获取总共的摄像头数量
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();//获取摄像头信息
            for (int i = 0; i < numberOfCameras; i++) {
                Camera.getCameraInfo(i, cameraInfo);
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) { //循环输出后置摄像头ID 并执行open函数 返回该对象
                    if (checkCameraHardware(context)){
                        System.out.println("Has Camera\n Open Camera");
                        System.out.println("Total Cameras: " + String.valueOf(Camera.getNumberOfCameras()));
                        System.out.println("Openning Camera");
                        c = Camera.open(i);
                    }else{
                        System.out.println("Does not has Camera");
                    }
                    return c;
                }
            }
            System.out.println("Opened Succeed");
        }
        catch (Exception e){
            System.out.println("Camera is not available");
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }
    private static boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    private void registerScreenService() {
        ScreenLock screenLock = new ScreenLock(); //实例化监听事件
        IntentFilter filter = new IntentFilter(); //实例化一个Intent过滤器
        filter.addAction(Intent.ACTION_SCREEN_OFF);//添加过滤事件
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        getApplicationContext().registerReceiver(screenLock, filter);//注册接收
    }
}