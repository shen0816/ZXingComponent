/***
  Copyright (c) 2013 CommonsWare, LLC
  
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
import android.content.Context;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SimpleCameraHost implements CameraHost {
  private static final String[] SCAN_TYPES= { "image/jpeg" };
  private Context ctxt=null;
  private int cameraId=-1;
  private File photoDirectory=null;
  private File videoDirectory=null;
  private RecordingHint recordingHint=null;
  private boolean mirrorFFC=false;
  private boolean useFrontFacingCamera=false;
  private boolean scanSavedImage=true;
  private boolean useFullBleedPreview=true;
  private boolean useSingleShotMode=false;

  public SimpleCameraHost(Context _ctxt) {
    this.ctxt=_ctxt.getApplicationContext();
  }

  @Override
  public Camera.Parameters adjustPreviewParameters(Camera.Parameters parameters) {
    return(parameters);
  }

  public void configureRecorderProfile(int cameraId,
                                       MediaRecorder recorder) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB
        || CamcorderProfile.hasProfile(cameraId,
                                       CamcorderProfile.QUALITY_HIGH)) {
      recorder.setProfile(CamcorderProfile.get(cameraId,
                                               CamcorderProfile.QUALITY_HIGH));
    }
    else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB
        && CamcorderProfile.hasProfile(cameraId,
                                       CamcorderProfile.QUALITY_LOW)) {
      recorder.setProfile(CamcorderProfile.get(cameraId,
                                               CamcorderProfile.QUALITY_LOW));
    }
    else {
      throw new IllegalStateException(
                                      "cannot find valid CamcorderProfile");
    }
  }

  @Override
  public int getCameraId() {
    if (cameraId == -1) {
      initCameraId();
    }

    return(cameraId);
  }

  private void initCameraId() {
    int count=Camera.getNumberOfCameras();
    int result=-1;

    if (count > 0) {
      result=0; // if we have a camera, default to this one

      Camera.CameraInfo info=new Camera.CameraInfo();

      for (int i=0; i < count; i++) {
        Camera.getCameraInfo(i, info);

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK
            && !useFrontFacingCamera()) {
          result=i;
          break;
        }
        else if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT
            && useFrontFacingCamera()) {
          result=i;
          break;
        }
      }
    }

    cameraId=result;
  }

  @Override
  public Camera.Size getPreviewSize(int displayOrientation, int width,
                                    int height,
                                    Camera.Parameters parameters) {
    return(CameraUtils.getBestAspectPreviewSize(displayOrientation,
                                                width, height,
                                                parameters));
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  @Override
  public Camera.Size getPreferredPreviewSizeForVideo(int displayOrientation,
                                                     int width,
                                                     int height,
                                                     Camera.Parameters parameters,
                                                     Camera.Size deviceHint) {
    if (deviceHint != null) {
      return(deviceHint);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      return(parameters.getPreferredPreviewSizeForVideo());
    }

    return(null);
  }

  @Override
  public void handleException(Exception e) {
    Log.e(getClass().getSimpleName(),
          "Exception in setPreviewDisplay()", e);
  }
  @Override
  public void autoFocusAvailable() {
    // no-op
  }

  @Override
  public void autoFocusUnavailable() {
    // no-op
  }

  @Override
  public void onCameraFail(FailureReason reason) {
    Log.e("CWAC-Camera",
          String.format("Camera access failed: %d", reason.value));
  }

  @Override
  public boolean useFullBleedPreview() {
    return(useFullBleedPreview);
  }

  protected File getPhotoDirectory() {
    if (photoDirectory == null) {
      initPhotoDirectory();
    }

    return(photoDirectory);
  }

  private void initPhotoDirectory() {
    photoDirectory=
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
  }

  protected String getPhotoFilename() {
    String ts=
        new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());

    return("Photo_" + ts + ".jpg");
  }

  protected File getVideoPath() {
    File dir=getVideoDirectory();

    dir.mkdirs();

    return(new File(dir, getVideoFilename()));
  }

  protected File getVideoDirectory() {
    if (videoDirectory == null) {
      initVideoDirectory();
    }

    return(videoDirectory);
  }

  private void initVideoDirectory() {
    videoDirectory=
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
  }

  protected String getVideoFilename() {
    String ts=
        new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());

    return("Video_" + ts + ".mp4");
  }

  protected boolean useFrontFacingCamera() {
    return(useFrontFacingCamera);
  }

  protected boolean scanSavedImage() {
    return(scanSavedImage);
  }
}
