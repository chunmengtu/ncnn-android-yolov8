// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

package com.tencent.yolov8ncnn;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import java.io.FileNotFoundException;
import java.io.InputStream;

public class MainActivity extends Activity implements SurfaceHolder.Callback
{
    public static final int REQUEST_CAMERA = 100;
    private static final int SELECT_IMAGE = 1;

    private YOLOv8Ncnn yolov8ncnn = new YOLOv8Ncnn();
    private int facing = 0;
    
    private Spinner spinnerTask;
    private Spinner spinnerModel;
    private Spinner spinnerCPUGPU;
    private int current_task = 0;
    private int current_model = 0;
    private int current_cpugpu = 0;

    private ImageView imageView;
    private SurfaceView cameraView;
    private Bitmap yourSelectedImage = null;

    private Button buttonMode;
    private LinearLayout layoutImageControls;
    private LinearLayout layoutCameraControls;

    // 0 = Image Mode, 1 = Camera Mode
    private int currentMode = 1;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        imageView = (ImageView) findViewById(R.id.imageView);
        cameraView = (SurfaceView) findViewById(R.id.cameraview);
        
        cameraView.getHolder().setFormat(PixelFormat.RGBA_8888);
        cameraView.getHolder().addCallback(this);

        layoutImageControls = (LinearLayout) findViewById(R.id.layoutImageControls);
        layoutCameraControls = (LinearLayout) findViewById(R.id.layoutCameraControls);

        buttonMode = (Button) findViewById(R.id.buttonMode);
        buttonMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentMode == 0) {
                    // Switch to Camera Mode
                    currentMode = 1;
                    buttonMode.setText("切换模式 (当前: 摄像头)");
                    layoutImageControls.setVisibility(View.GONE);
                    layoutCameraControls.setVisibility(View.VISIBLE);
                    imageView.setVisibility(View.GONE);
                    cameraView.setVisibility(View.VISIBLE);
                    
                    checkPermissionsAndOpenCamera();
                } else {
                    // Switch to Image Mode
                    currentMode = 0;
                    buttonMode.setText("切换模式 (当前: 图片)");
                    layoutImageControls.setVisibility(View.VISIBLE);
                    layoutCameraControls.setVisibility(View.GONE);
                    imageView.setVisibility(View.VISIBLE);
                    cameraView.setVisibility(View.GONE);
                    
                    yolov8ncnn.closeCamera();
                }
            }
        });

        Button buttonSelectImage = (Button) findViewById(R.id.buttonSelectImage);
        buttonSelectImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Intent i = new Intent(Intent.ACTION_PICK);
                i.setType("image/*");
                startActivityForResult(i, SELECT_IMAGE);
            }
        });

        Button buttonDetect = (Button) findViewById(R.id.buttonDetect);
        buttonDetect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (yourSelectedImage == null) {
                    Toast.makeText(MainActivity.this, "请先选择图片", Toast.LENGTH_SHORT).show();
                    return;
                }
                runDetect();
            }
        });

        Button buttonSwitchCamera = (Button) findViewById(R.id.buttonSwitchCamera);
        buttonSwitchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                int new_facing = 1 - facing;
                yolov8ncnn.closeCamera();
                yolov8ncnn.openCamera(new_facing);
                facing = new_facing;
            }
        });

        spinnerTask = (Spinner) findViewById(R.id.spinnerTask);
        spinnerTask.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id)
            {
                if (position != current_task)
                {
                    current_task = position;
                    reload();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0)
            {
            }
        });

        spinnerModel = (Spinner) findViewById(R.id.spinnerModel);
        spinnerModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id)
            {
                if (position != current_model)
                {
                    current_model = position;
                    reload();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0)
            {
            }
        });

        spinnerCPUGPU = (Spinner) findViewById(R.id.spinnerCPUGPU);
        spinnerCPUGPU.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id)
            {
                if (position != current_cpugpu)
                {
                    current_cpugpu = position;
                    reload();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0)
            {
            }
        });

        if (currentMode == 1) {
            buttonMode.setText("切换模式 (当前: 摄像头)");
            layoutImageControls.setVisibility(View.GONE);
            layoutCameraControls.setVisibility(View.VISIBLE);
            imageView.setVisibility(View.GONE);
            cameraView.setVisibility(View.VISIBLE);
        }

        reload();
    }

    private void reload()
    {
        boolean ret_init = yolov8ncnn.loadModel(getAssets(), current_task, current_model, current_cpugpu);
        if (!ret_init)
        {
            Log.e("MainActivity", "yolov8ncnn loadModel failed");
            Toast.makeText(this, "模型加载失败", Toast.LENGTH_LONG).show();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SELECT_IMAGE && resultCode == RESULT_OK && null != data) {
            Uri selectedImage = data.getData();

            try {
                InputStream imageStream = getContentResolver().openInputStream(selectedImage);
                Bitmap bitmap = BitmapFactory.decodeStream(imageStream);
                
                // Copy to mutable bitmap with correct config
                yourSelectedImage = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                
                imageView.setImageBitmap(yourSelectedImage);
                
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void runDetect() {
        if (yourSelectedImage == null) return;
        
        // The detect function modifies the bitmap in-place
        boolean ret = yolov8ncnn.detect(yourSelectedImage);
        if (!ret) {
            Toast.makeText(this, "检测失败", Toast.LENGTH_SHORT).show();
        }
        imageView.invalidate();
    }

    private void checkPermissionsAndOpenCamera() {
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA);
        } else {
            yolov8ncnn.openCamera(facing);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
    {
        yolov8ncnn.setOutputWindow(holder.getSurface());
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder)
    {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder)
    {
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (currentMode == 1) { // Camera mode
            checkPermissionsAndOpenCamera();
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();

        if (currentMode == 1) {
            yolov8ncnn.closeCamera();
        }
    }
}
