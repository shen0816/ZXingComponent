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

import com.commonsware.cwac.camera.CameraHost.FailureReason;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.CameraInfo;
import android.os.Build;
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

  private CameraHost host = null;

  private OnOrientationChange onOrientationChange = null;

  private int displayOrientation = -1;

  private int outputOrientation = -1;

  private int cameraId = -1;

  private Camera.Parameters previewParams = null;

  private boolean isAutoFocusing = false;

  private boolean isAutoFocus = false;

  private int lastPictureOrientation = -1;

  private Camera.PreviewCallback mPreviewCallback = null;

  public CameraView(Context context) {
    super(context);
    onOrientationChange = new OnOrientationChange(context.getApplicationContext());
  }

  public void setAutoFocus(boolean isAutoFocus) {
    this.isAutoFocus = isAutoFocus;
  }

  public CameraHost getHost() {
    return (host);
  }

  public void setHost(CameraHost host) {
    this.host = host;

    if (host.getDeviceProfile().useTextureView()) {
      previewStrategy = new SurfacePreviewStrategy(this);
      addView(previewStrategy.getWidget());
    }
  }

  public void startCameraView() {
    getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        setVisibility(View.VISIBLE);
        if (camera == null) {
          cameraId = getHost().getCameraId();

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
              getHost().onCameraFail(FailureReason.UNKNOWN);
            }
          } else {
            getHost().onCameraFail(FailureReason.NO_CAMERAS_REPORTED);
          }
        }else{
          restartPreview();
        }
      }
    });
  }

  public void stopCameraView() {
    getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (camera != null) {
          camera.setOneShotPreviewCallback(null);
          setVisibility(View.GONE);
        }
      }
    });
  }

  public void releaseCameraView(){
    getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (camera != null)
          previewDestroyed();
        removeView(previewStrategy.getWidget());
        onOrientationChange.disable();
        lastPictureOrientation = -1;
      }
    });
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    final int width =
        resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
    final int height =
        resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
    setMeasuredDimension(width, height);

    if (width > 0 && height > 0) {
      if (camera != null) {
        Camera.Size newSize = null;

        try {
          if (getHost().getRecordingHint() != CameraHost.RecordingHint.STILL_ONLY) {
            // Camera.Size deviceHint=
            // host.getDeviceProfile()
            // .getPreferredPreviewSizeForVideo(getDisplayOrientation(),
            // width,
            // height,
            // camera.getParameters());

            newSize =
                getHost().getPreferredPreviewSizeForVideo(getDisplayOrientation(),
                    width,
                    height,
                    camera.getParameters(),
                    null);

            // if (newSize != null) {
            // android.util.Log.wtf("CameraView",
            // String.format("getPreferredPreviewSizeForVideo: %d x %d",
            // newSize.width,
            // newSize.height));
            // }
          }

          if (newSize == null || newSize.width * newSize.height < 65536) {
            newSize =
                getHost().getPreviewSize(getDisplayOrientation(),
                    width, height,
                    camera.getParameters());
          }
        } catch (Exception e) {
          Log.e(getClass().getSimpleName(),
              "Could not work with camera parameters?",
              e);
          // TODO get this out to library clients
        }

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

      boolean useFirstStrategy =
          (width * previewHeight > height * previewWidth);
      boolean useFullBleed = getHost().useFullBleedPreview();

      if ((useFirstStrategy && !useFullBleed)
          || (!useFirstStrategy && useFullBleed)) {
        final int scaledChildWidth =
            previewWidth * height / previewHeight;
        child.layout((width - scaledChildWidth) / 2, 0,
            (width + scaledChildWidth) / 2, height);
      } else {
        final int scaledChildHeight =
            previewHeight * width / previewWidth;
        child.layout(0, (height - scaledChildHeight) / 2, width,
            (height + scaledChildHeight) / 2);
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

  private Runnable autoFocusRunnable = new Runnable() {
    @Override
    public void run() {
      if (camera != null && inPreview) {
        autoFocus();
      }
    }
  };

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
      postDelayed(autoFocusRunnable, 1000);
  }

  public String getFlashMode() {
    return (camera.getParameters().getFlashMode());
  }

  public void setFlashMode(String mode) {
    if (camera != null) {
      Camera.Parameters params = camera.getParameters();

      params.setFlashMode(mode);
      camera.setParameters(params);
    }
  }

  public ZoomTransaction zoomTo(int level) {
    if (camera == null) {
      throw new IllegalStateException(
          "Yes, we have no camera, we have no camera today");
    } else {
      Camera.Parameters params = camera.getParameters();

      if (level >= 0 && level <= params.getMaxZoom()) {
        return (new ZoomTransaction(camera, level));
      } else {
        throw new IllegalArgumentException(
            String.format("Invalid zoom level: %d",
                level));
      }
    }
  }

  public boolean doesZoomReallyWork() {
    CameraInfo info = new CameraInfo();
    Camera.getCameraInfo(getHost().getCameraId(), info);

    return (getHost().getDeviceProfile()
        .doesZoomActuallyWork(info.facing == CameraInfo.CAMERA_FACING_FRONT));
  }

  void previewCreated() {
    if (camera != null) {
      try {
        previewStrategy.attach(camera);
      } catch (IOException e) {
        getHost().handleException(e);
      }
    }
  }

  void previewDestroyed() {
    if (camera != null) {
      previewStopped();
      camera.release();
      camera = null;
      System.gc();
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

  public void initPreview(int w, int h, boolean firstRun) {
    if (camera != null) {
      Camera.Parameters parameters = camera.getParameters();

      parameters.setPreviewSize(previewSize.width, previewSize.height);

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
        parameters
            .setRecordingHint(getHost().getRecordingHint() != CameraHost.RecordingHint.STILL_ONLY);
      }

      requestLayout();

      camera.setParameters(getHost().adjustPreviewParameters(parameters));
      startPreview();
    }
  }

  private void startPreview() {
    camera.startPreview();
    inPreview = true;
    getHost().autoFocusAvailable();
    if (isAutoFocus)
      autoFocus();
  }

  private void stopPreview() {
    inPreview = false;
    cancelAutoFocus();
    getHost().autoFocusUnavailable();
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
}