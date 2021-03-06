/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.zensar.sharescreendemomarshmellow;


import static java.lang.System.out;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.support.v7.widget.ThemedSpinnerAdapter;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class MediaProjectionDemo extends Activity {
    private static final String TAG = "MediaProjectionDemo";
    private static final int PERMISSION_CODE = 1;
    private static final List<Resolution> RESOLUTIONS = new ArrayList<Resolution>() {{
        add(new Resolution(640,360));
        add(new Resolution(960,540));
        add(new Resolution(1366,768));
        add(new Resolution(1600,900));
    }};

    private int mScreenDensity;
    private MediaProjectionManager mProjectionManager;

    private int mDisplayWidth;
    private int mDisplayHeight;
    private boolean mScreenSharing;

    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private Surface mSurface;
    private SurfaceView mSurfaceView;
    private ToggleButton mToggle;

    public ImageView mScreenShotImageView;
    ImageReader imageReader;
    int counter = 1;
    byte[] displayData = null;
    private Context context;
    TextView mMgsTextView;
    private TextView output;
    private Button mSendToServer;
    private OkHttpClient client;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.media_projection);
        client = new OkHttpClient();
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;

        mSurfaceView = (SurfaceView) findViewById(R.id.surface);
        mMgsTextView = (TextView) findViewById(R.id.tv_mgs);
        mSurface = mSurfaceView.getHolder().getSurface();
        mScreenShotImageView = (ImageView) findViewById(R.id.im_screen_shot);
        mSendToServer = (Button) findViewById(R.id.but_send);
        output = (TextView) findViewById(R.id.tv_output);
        mProjectionManager =
            (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        ArrayAdapter<Resolution> arrayAdapter = new ArrayAdapter<Resolution>(
                this, android.R.layout.simple_list_item_1, RESOLUTIONS);
        Spinner s = (Spinner) findViewById(R.id.spinner);
        s.setAdapter(arrayAdapter);
        s.setOnItemSelectedListener(new ResolutionSelector());
        s.setSelection(0);

        mToggle = (ToggleButton) findViewById(R.id.screen_sharing_toggle);
        mToggle.setSaveEnabled(false);
        mSendToServer.setVisibility(View.GONE);
        mSendToServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start();
            }
        });
    }

    private void start() {
        out.println(" !!!!!!!!!!! in the Start !!!!!!!!!!!!!");
       OkHttpClient lClient = new OkHttpClient();
        Request request = new Request.Builder().url("ws://192.168.43.103:9111/websocket").build();
        //  Request request = new Request.Builder().url("http://middlewarepoc-env.eba-k3yvmgxs.us-east-1.elasticbeanstalk.com/").build();
        EchoWebSocketListener listener = new EchoWebSocketListener();
        WebSocket ws = lClient.newWebSocket(request, listener);
       // client.dispatcher().executorService().shutdown();
    }

    @Override
    protected void onStop() {
        stopScreenSharing();
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != PERMISSION_CODE) {
            Log.e(TAG, "Unknown request code: " + requestCode);
            return;
        }
        if (resultCode != RESULT_OK) {
            Toast.makeText(this,
                    "User denied screen sharing permission", Toast.LENGTH_SHORT).show();
            return;
        }
        mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
        mMediaProjection.registerCallback(new MediaProjectionCallback(), null);
        mVirtualDisplay = createVirtualDisplay();
    }

    public void onToggleScreenShare(View view) {
        if (((ToggleButton) view).isChecked() && imageReader == null) {

            shareScreen();
        } else {
            stopScreenSharing();
        }
    }

    private void shareScreen() {
        mScreenSharing = true;
        if (mSurface == null) {
            return;
        }
        if (mMediaProjection == null) {
            startActivityForResult(mProjectionManager.createScreenCaptureIntent(),
                    PERMISSION_CODE);
            return;
        }
        mVirtualDisplay = createVirtualDisplay();
    }

    private void stopScreenSharing() {
        if (imageReader == null) {
            return;
        }
        counter =1;
        imageReader.close();//release();
        imageReader = null;

        if (mToggle.isChecked()) {
            mToggle.setChecked(false);
        }

        mScreenSharing = false;
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }

        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }

    private VirtualDisplay createVirtualDisplay() {
        Log.i(TAG, "Setting up a VirtualDisplay: " +
                mSurfaceView.getWidth() + "x" + mSurfaceView.getHeight() +
                " (" + mScreenDensity + ")");
        // for imager reader callback
        if (imageReader!=null)
            imageReader.close();
        imageReader = ImageReader.newInstance(mScreenShotImageView.getWidth(), mScreenShotImageView.getHeight(), PixelFormat.RGBA_8888, 60);

       VirtualDisplay virtualDisplay = mMediaProjection.createVirtualDisplay("ScreenCapture",
                mScreenShotImageView.getWidth(), mScreenShotImageView.getHeight(), mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.getSurface(), null, null);

        imageReader.setOnImageAvailableListener(new ImageReaderOnImageAvailable(), null);

        return virtualDisplay;/*mMediaProjection.createVirtualDisplay("ScreenSharingDemo",
                mDisplayWidth, mDisplayHeight, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mSurface, null *//*Callbacks*//*, null *//*Handler*//*);*/
    }

    private void resizeVirtualDisplay() {
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.resize(mDisplayWidth, mDisplayHeight, mScreenDensity);
    }

    private class ResolutionSelector implements Spinner.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
            Resolution r = (Resolution) parent.getItemAtPosition(pos);
            ViewGroup.LayoutParams lp = mScreenShotImageView.getLayoutParams();
            if (getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE) {
                mDisplayHeight = r.y;
                mDisplayWidth = r.x;
            } else {
                mDisplayHeight = r.x;
                mDisplayWidth = r.y;
            }
            lp.height = mDisplayHeight;
            lp.width = mDisplayWidth;
            mSurfaceView.setLayoutParams(lp);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) { /* Ignore */ }
    }

    private class MediaProjectionCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            mMediaProjection = null;
         //   stopScreenSharing();
        }
    }

    private class SurfaceCallbacks implements SurfaceHolder.Callback {
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            mDisplayWidth = width;
            mDisplayHeight = height;
            resizeVirtualDisplay();
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            mSurface = holder.getSurface();
            if (mScreenSharing) {
                shareScreen();
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            if (!mScreenSharing) {
                stopScreenSharing();
            }
        }
    }

    private static class Resolution {
        int x;
        int y;

        public Resolution(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return x + "x" + y;
        }
    }

    private class ImageReaderOnImageAvailable implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            //reset the counter if imagereader reads 60 frames/images and call
            // setUpVirtualDisplay to continue fetching/rendering the images form
            //ImageReader
            if (!(counter < 59)) {
                //mMediaProjection.registerCallback(new MediaProjectionCallback(), null);
               // mVirtualDisplay = createVirtualDisplay();
                imageReader.close();
                stopScreenSharing();
                counter = 1;
                shareScreen();


            }
            Image image = null;
            Bitmap bitmap = null;

            ByteArrayOutputStream stream = null;

            try {
                image = reader.acquireNextImage();

                if (image != null) {
                    Image.Plane[] planes = image.getPlanes();
                    ByteBuffer buffer = planes[0].getBuffer();
                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * mScreenShotImageView.getWidth();

                    // create bitmap
                    bitmap = Bitmap.createBitmap(mScreenShotImageView.getWidth() + rowPadding / pixelStride,
                            mScreenShotImageView.getHeight(), Bitmap.Config.ARGB_8888);
                    System.out.println("mSurfaceView.getht >> " + mSurfaceView.getHeight() + "mSurfaceView.getWidth() >> " + mSurfaceView.getWidth() + " >> rpdng >>" + rowPadding + " >> pixlStrid >> " + pixelStride);
                    bitmap.copyPixelsFromBuffer(buffer);
                    stream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
                    if (bitmap != null) {

                        counter++;
                        Thread.sleep(100);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mMgsTextView.setText("Counter = "+counter);
                            }
                        });

                        System.out.println("counter >>>>>>>>>>>>> " + counter);
                        mScreenShotImageView.setImageBitmap(bitmap);
                        mScreenShotImageView.invalidate();
                    }
                    displayData = convertBitmapToByteArray(bitmap);


                    System.out.println("byte Array data is:" +displayData);
                    //calling the dynamic update of image to backend
                    start();

                }

            } catch (Exception e) {
                System.out.println("Exception >>>>>>>>>>>>>>.. "+e);
                e.printStackTrace();
            }
           /* if (displayData !=null)
                mainActivity.getByteArrayData(displayData);*/
        }

    }

/****** 1 way ******/

   /* private byte[] bitMapToByteArray(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(bitmap.getByteCount());
        bitmap.copyPixelsToBuffer(byteBuffer);
        byteBuffer.rewind();
        return byteBuffer.array();
    }*/

    /******* 2 way ******/

    public byte[] convertBitmapToByteArray(Bitmap bitmap) {
        ByteArrayOutputStream stream = null;
        try {
            stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);

            return stream.toByteArray();
        }finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    Log.e(ThemedSpinnerAdapter.Helper.class.getSimpleName(), "ByteArrayOutputStream was not closed");
                }
            }
        }
    }

    /***** bitmap to string */
   /* public String getStringImage(Bitmap bmp){
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, baos);
        byte[] imageBytes = baos.toByteArray();
        String encodedImage = android.util.Base64.encodeToString(imageBytes, Base64.DEFAULT);
        return encodedImage;
    }*/

    public class EchoWebSocketListener extends WebSocketListener {
        private static final int NORMAL_CLOSURE_STATUS = 1000;
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            //webSocket.send("Hello, it's SSaurel !");
            out.println(" !!!!!!!!!!! in onOpen mthd!!!!!!!!!!!!!");
            String imageString = Base64.encodeToString(displayData, Base64.DEFAULT);
            System.out.println("!!!!!!!!!!!!! sending mgs !!!!!!!!!!!!!!!!!.. "+counter);
            webSocket.send(String.valueOf(imageString));
            //webSocket.close(NORMAL_CLOSURE_STATUS, "Goodbye !");//TODO close this on certain command from bckend
        }
        @Override
        public void onMessage(WebSocket webSocket, String text) {
            output("Receiving : From Admin" + text);
        }
        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            output("Receiving bytes : from Admin " + bytes.hex());
        }
        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
         //   webSocket.close(NORMAL_CLOSURE_STATUS, null);
  //          output("Closing : " + code + " / " + reason);
        }
        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
  //          output("Error : " + t.getMessage());
        }
    }

    private void output(final String txt) {
        Objects.requireNonNull(MediaProjectionDemo.this).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                output.setText(output.getText().toString() + "\n\n" + txt);
            }
        });
    }

}
