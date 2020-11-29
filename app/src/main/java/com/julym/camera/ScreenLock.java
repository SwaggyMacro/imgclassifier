package com.julym.camera;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.View;

public class ScreenLock extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        //
        if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {    //屏幕关闭
            MainActivity.c.stopPreview(); //停止预览
            MainActivity.c.setPreviewCallback(null);//清空回调函数
            MainActivity.c.release();//释放相机资源
            MainActivity.c = null;
            MainActivity.mPreview.setVisibility(View.INVISIBLE);
            System.out.println("Screen Off");

        } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {   //屏幕打开
            MainActivity.c = MainActivity.getCameraInstance(); // 实例化一个摄像头对象
            MainActivity.c.setDisplayOrientation(90);//旋转90°
            MainActivity.mPreview = new CameraPreview(MainActivity.context, MainActivity.c);
            MainActivity.mPreview.setVisibility(View.VISIBLE); //重新实现预览
            MainActivity.preview.addView(MainActivity.mPreview);// 将mPreview添加到preview上显示
            MainActivity.setOnAutoFocusClickListener();
            System.out.println("Screen On");
        }

    }
}