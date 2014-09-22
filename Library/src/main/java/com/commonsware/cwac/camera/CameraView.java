/***
 Copyright (c) 2013-2014 CommonsWare, LLC
 Portions Copyright (C) 2007 The Android Open Source Project

 Licensed under the Apache License, Version 2.0 (the "License"); you may
 not use this file except in compliance with the License. You may obtain
 a copy of the License at
 http://www.apache.org/licenses/LICENSE-2.0
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package com.commonsware.cwac.camera;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.CameraInfo;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;

import java.io.IOException;

public class CameraView extends ViewGroup implements AutoFocusCallback {

  static final String TAG = "CWAC-Camera";

  private PreviewStrategy previewStrategy;

  private Camera.Size previewSize;

  private Camera camera = null;

  private boolean inPreview = false;

  private OnOrientationChange onOrientationChange = null;

  private int displayOrientation = -1;

  private int outputOrientation = -1;

  private int cameraId = -1;

  private boolean isAutoFocusing = false;

  private boolean isAutoFocus = false;

  private int lastPictureOrientation = -1;

  private Camera.PreviewCallback mPreviewCallback = null;

  private RecordingHint mRecordingHint = RecordingHint.NONE;

  private DeviceProfile mProfile = null;

  public CameraView(Context context) {
    super(context);
    initLayout(context);
  }

  public CameraView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public CameraView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initLayout(context);
  }

  private void initLayout(Context context){
    onOrientationChange = new OnOrientationChange(context.getApplicationContext());
    previewStrategy = new SurfacePreviewStrategy(this);
    mProfile = DeviceProfile.getInstance(context);
  }

  public void setAutoFocus(boolean isAutoFocus) {
    this.isAutoFocus = isAutoFocus;
  }


  @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
  public void onResume() {
    addView(previewStrategy.getWidget());

    if (camera == null) {
      cameraId = getCameraId();

      if (cameraId >= 0) {
        try {
          camera = Camera.open(cameraId);
          if (camera != null && mPreviewCallback != null) {
            camera.setOneShotPreviewCallback(mPreviewCallback);
          }

          if (getActivity().getRequestedOrientation()
              != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            onOrientationChange.enable();
          }

          setCameraDisplayOrientation();
        } catch (Exception e) {
          Log.e(TAG, "Camera is UNKNOWN");
        }
      } else {
        Log.e(TAG, "Camera is NO_CAMERAS_REPORTED");
      }
    }
  }

  private boolean useFrontFacingCamera = false;

  public int getCameraId() {
    if (cameraId == -1) {
      int count = Camera.getNumberOfCameras();
      int result = -1;

      if (count > 0) {
        result = 0; // if we have a camera, default to this one

        Camera.CameraInfo info = new Camera.CameraInfo();

        for (int i = 0; i < count; i++) {
          Camera.getCameraInfo(i, info);

          if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK
              && !useFrontFacingCamera) {
            result = i;
            break;
          } else if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT
              && useFrontFacingCamera) {
            result = i;
            break;
          }
        }
      }

      cameraId = result;
    }

    return (cameraId);
  }

  public void onPause() {

    if (camera != null) {
      camera.setOneShotPreviewCallback(null);
      previewDestroyed();
    }

    removeView(previewStrategy.getWidget());
    onOrientationChange.disable();
    lastPictureOrientation = -1;
  }

  // based on CameraPreview.java from ApiDemos

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    final int width =
        resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
    final int height =
        resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);

    setMeasuredDimension(width, height);

    Log.d(TAG,String.format(" 111111 width : %d , height : %d",width,height));

    if (width > 0 && height > 0) {
      if (camera != null) {
        Camera.Size newSize = null;

        if (getRecordingHint() != RecordingHint.STILL_ONLY){
          newSize = camera.getParameters().getPreferredPreviewSizeForVideo();
        }

        try {
          if (newSize == null || newSize.width * newSize.height < 65536) {
            newSize =
                CameraUtils.getBestAspectPreviewSize(
                    getDisplayOrientation(),
                    width, height,
                    camera.getParameters());
          }
        } catch (Exception e) {
          Log.e(getClass().getSimpleName(),
              "Could not work with camera parameters?",
              e);
          // TODO get this out to library clients
        }

        Log.d(TAG,String.format(" 111111 newSize.width : %d , newSize.height : %d",newSize.width,newSize.height));

        if (newSize != null) {
          if (previewSize == null) {
            previewSize = newSize;
          } else if (previewSize.width != newSize.width
              || previewSize.height != newSize.height) {
            if (inPreview) {
              stopPreview();
            }

            previewSize = newSize;
            initPreview(width, height, false);
          }
        }
        Log.d(TAG,String.format("width : %d , height : %d",previewSize.width, previewSize.height));
      }
    }
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    if (changed && getChildCount() > 0) {
      final View child = getChildAt(0);
      final int width = r - l;
      final int height = b - t;
      int previewWidth = width;
      int previewHeight = height;

      // handle orientation

      if (previewSize != null) {
        if (getDisplayOrientation() == 90
            || getDisplayOrientation() == 270) {
          previewWidth = previewSize.height;
          previewHeight = previewSize.width;
        } else {
          previewWidth = previewSize.width;
          previewHeight = previewSize.height;
        }
      }
      boolean useFirstStrategy=
          (width * previewHeight > height * previewWidth);
      boolean useFullBleed = true;

      if ((useFirstStrategy && !useFullBleed)
          || (!useFirstStrategy && useFullBleed)) {
        final int scaledChildWidth = previewWidth * height / previewHeight;
        child.layout((width - scaledChildWidth) / 2, 0,
            ((width + scaledChildWidth) / 2) - 1, height);
      } else {
        final int scaledChildHeight = previewHeight * width / previewWidth;
        child.layout(0, (height - scaledChildHeight) / 2,
            width - 1, (height + scaledChildHeight) / 2);
      }
    }
  }

  public int getDisplayOrientation() {
    return (displayOrientation);
  }

  public void lockToLandscape(boolean enable) {
    if (enable) {
      getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
      onOrientationChange.enable();
    } else {
      getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
      onOrientationChange.disable();
    }
  }

  public void setPreviewCallback(Camera.PreviewCallback previewCallback) {
    this.mPreviewCallback = previewCallback;
  }

  public void restartPreview() {
    if (!inPreview) {
      startPreview();
    }
  }

  public void autoFocus() {
    if (inPreview) {
      camera.autoFocus(this);
      isAutoFocusing = true;
    }
  }

  public void cancelAutoFocus() {
    camera.cancelAutoFocus();
  }

  public boolean isAutoFocusAvailable() {
    return (inPreview);
  }

  @Override
  public void onAutoFocus(boolean success, final Camera camera) {
    isAutoFocusing = false;

    if (isAutoFocus)
      new Handler().postDelayed(new Runnable() {
        @Override
        public void run() {
          if (camera != null && inPreview) {
            autoFocus();
          }
        }
      }, 1000);
  }
  void previewCreated() {
    if (camera != null) {
      try {
        previewStrategy.attach(camera);
      } catch (IOException e) {
        handleException(e);
      }
    }
  }

  void previewDestroyed() {
    if (camera != null) {
      previewStopped();
      camera.release();
      camera = null;
    }
  }

  void previewReset(int width, int height) {
    previewStopped();
    initPreview(width, height);
  }

  private void previewStopped() {
    if (inPreview) {
      stopPreview();
    }
  }

  public void initPreview(int w, int h) {
    initPreview(w, h, true);
  }

  @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
  public void initPreview(int w, int h, boolean firstRun) {
    if (camera != null) {
      Camera.Parameters parameters = camera.getParameters();

//      if (w > 0 && h > 0)
//        parameters.setPreviewSize(w, h);
//      else
        parameters.setPreviewSize(previewSize.width, previewSize.height);

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
        parameters.setRecordingHint(getRecordingHint() != RecordingHint.STILL_ONLY);
      }

      requestLayout();

      camera.setParameters(parameters);
      startPreview();
    }
  }

  private void startPreview() {
    camera.startPreview();
    inPreview = true;
    if (isAutoFocus)
      autoFocus();
  }

  private void stopPreview() {
    inPreview = false;
    camera.stopPreview();
  }

  // based on
  // http://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
  // and http://stackoverflow.com/a/10383164/115145

  private void setCameraDisplayOrientation() {
    CameraInfo info = new CameraInfo();
    int rotation =
        getActivity().getWindowManager().getDefaultDisplay()
            .getRotation();
    int degrees = 0;
    DisplayMetrics dm = new DisplayMetrics();

    Camera.getCameraInfo(cameraId, info);
    getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);

    switch (rotation) {
      case Surface.ROTATION_0:
        degrees = 0;
        break;
      case Surface.ROTATION_90:
        degrees = 90;
        break;
      case Surface.ROTATION_180:
        degrees = 180;
        break;
      case Surface.ROTATION_270:
        degrees = 270;
        break;
    }

    if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
      displayOrientation = (info.orientation + degrees) % 360;
      displayOrientation = (360 - displayOrientation) % 360;
    } else {
      displayOrientation = (info.orientation - degrees + 360) % 360;
    }

    boolean wasInPreview = inPreview;

    if (inPreview) {
      stopPreview();
    }

    camera.setDisplayOrientation(displayOrientation);

    if (wasInPreview) {
      startPreview();
    }
  }

  // based on:
  // http://developer.android.com/reference/android/hardware/Camera.Parameters.html#setRotation(int)

  private int getCameraPictureRotation(int orientation) {
    CameraInfo info = new CameraInfo();
    Camera.getCameraInfo(cameraId, info);
    int rotation = 0;

    orientation = (orientation + 45) / 90 * 90;

    if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
      rotation = (info.orientation - orientation + 360) % 360;
    } else { // back-facing camera
      rotation = (info.orientation + orientation) % 360;
    }

    return (rotation);
  }

  Activity getActivity() {
    return ((Activity) getContext());
  }

  private class OnOrientationChange extends OrientationEventListener {

    private boolean isEnabled = false;

    public OnOrientationChange(Context context) {
      super(context);
      disable();
    }

    @Override
    public void onOrientationChanged(int orientation) {
      if (camera != null && orientation != ORIENTATION_UNKNOWN) {
        int newOutputOrientation = getCameraPictureRotation(orientation);

        if (newOutputOrientation != outputOrientation) {
          outputOrientation = newOutputOrientation;

          Camera.Parameters params = camera.getParameters();

          params.setRotation(outputOrientation);

          try {
            camera.setParameters(params);
            lastPictureOrientation = outputOrientation;
          } catch (Exception e) {
            Log.e(getClass().getSimpleName(),
                "Exception updating camera parameters in orientation change",
                e);
            // TODO: get this info out to hosting app
          }
        }
      }
    }

    @Override
    public void enable() {
      isEnabled = true;
      super.enable();
    }

    @Override
    public void disable() {
      isEnabled = false;
      super.disable();
    }

    boolean isEnabled() {
      return (isEnabled);
    }
  }

  public void handleException(Exception e) {
    Log.e(getClass().getSimpleName(),
        "Exception in setPreviewDisplay()", e);
  }

  public RecordingHint getRecordingHint() {
    if (mRecordingHint == null) {
      initRecordingHint();
    }

    return(mRecordingHint);
  }

  private void initRecordingHint() {
    mRecordingHint=mProfile.getDefaultRecordingHint();

    if (mRecordingHint==RecordingHint.NONE) {
      mRecordingHint=RecordingHint.ANY;
    }
  }
}