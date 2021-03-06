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
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import java.io.IOException;

class SurfacePreviewStrategy implements PreviewStrategy,
    SurfaceHolder.Callback {
  private final CameraView cameraView;
  private SurfaceView preview=null;
  private SurfaceHolder previewHolder=null;

  public SurfacePreviewStrategy(CameraView cameraView) {
    this.cameraView=cameraView;
    preview=new SurfaceView(cameraView.getContext());
    previewHolder=preview.getHolder();
    previewHolder.addCallback(this);
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    cameraView.previewCreated();
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format,
                             int width, int height) {
    cameraView.initPreview(width, height);
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    cameraView.previewDestroyed();
  }

  @Override
  public void attach(Camera camera) throws IOException {
    camera.setPreviewDisplay(previewHolder);
  }

  @Override
  public View getWidget() {
    return(preview);
  }
}