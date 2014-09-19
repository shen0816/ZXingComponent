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

import android.hardware.Camera;

/**
 * Specification of a CameraHost, which is the primary way
 * by which an app will interact with the library. This
 * allows for a single code base supporting those using
 * CameraView directly, CameraFragment, the CameraFragment
 * for the Android Support package's backport of fragments,
 * and who knows what else in the future.
 * 
 * A concrete implementation of this class,
 * SimpleCameraHost, provides reasonable defaults for all of
 * the functionality. Hence, you can either extend
 * SimpleCameraHost and override where needed, or implement
 * your own CameraHost from scratch. *
 */
public interface CameraHost {
  /**
   * Indication of what purpose we plan to put the camera
   * towards. If your use of the camera is single-purpose,
   * return STILL_ONLY (for photos) or VIDEO_ONLY (for
   * videos). If you support both (all the time or via some
   * sort of user-selectable mode), use ANY. NONE indicates
   * that something else should be making this decision
   * (for internal use only).
   */
  public enum RecordingHint {
    STILL_ONLY, VIDEO_ONLY, ANY, NONE
  }

  /**
   * Indication of why we were unable to open up a camera.
   * NO_CAMERAS_REPORTED will be used if getCameraId()
   * returns a negative number. Exceptions raised when the
   * camera is opened will return UNKNOWN.
   */
  public enum FailureReason {
    NO_CAMERAS_REPORTED(1), UNKNOWN(2);

    int value;

    private FailureReason(int value) {
      this.value=value;
    }
  }

  /**
   * Implement this to configure the Camera.Parameters for
   * the purposes of the preview. Note that you will have
   * another chance to configure the Camera.Parameters for a
   * specific photo via adjustPictureParameters().
   * 
   * @param parameters
   *          the Camera.Parameters to be modified
   * @return the Camera.Parameters that was passed in
   */
  Camera.Parameters adjustPreviewParameters(Camera.Parameters parameters);

  /**
   * This will be called by the library to let you know that
   * auto-focus is available for your use, so you can update
   * your UI accordingly.
   */
  void autoFocusAvailable();

  /**
   * This will be called by the library to let you know that
   * auto-focus is not available for your use, so you can
   * update your UI accordingly.
   */
  void autoFocusUnavailable();

  /**
   * @return the ID of the camera that you want to use for
   *         previews and picture/video taking with the
   *         associated CameraView instance
   */
  int getCameraId();

  /**
   * Called to allow you to indicate what size preview
   * should be used
   * 
   * @param displayOrientation
   *          orientation of the display in degrees
   * @param width
   *          width of the available preview space
   * @param height
   *          height of the available preview space
   * @param parameters
   *          the current camera parameters
   * @return the size of the preview to use (note: must be a
   *         supported preview size!)
   */
  Camera.Size getPreviewSize(int displayOrientation, int width,
      int height, Camera.Parameters parameters);

  /**
   * Same as getPreviewSize(), but called when we anticipate
   * taking videos, as some devices may work better with
   * lower-resolution previews, to reduce CPU load
   * 
   * @param displayOrientation
   *          orientation of the display in degrees
   * @param width
   *          width of the available preview space
   * @param height
   *          height of the available preview space
   * @param parameters
   *          the current camera parameters
   * @param deviceHint
   *          the size that the device itself thinks should
   *          be used for video, which sometimes is
   *          ridiculously low
   * @return the size of the preview to use (note: must be a
   *         supported preview size!)
   */
  Camera.Size getPreferredPreviewSizeForVideo(int displayOrientation,
      int width,
      int height,
      Camera.Parameters parameters,
      Camera.Size deviceHint);

  /**
   * Called when something blows up in CameraView, to allow
   * you to alert the user as you see fit
   * 
   * @param e
   *          an Exception indicating what went wrong
   */
  void handleException(Exception e);

  /**
   * Called when we failed to open the camera for one reason
   * or another, so you can let the user know
   * 
   * @param reason
   *          a FailureReason indicating what went wrong
   */
  void onCameraFail(FailureReason reason);
  
  boolean useFullBleedPreview();
  
}
