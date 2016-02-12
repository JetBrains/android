/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace.viewer.camera;

import com.android.tools.idea.editors.gfxtrace.viewer.CameraModel;
import com.android.tools.idea.editors.gfxtrace.viewer.vec.MatD;

import static com.android.tools.idea.editors.gfxtrace.viewer.Constants.*;

public class CylindricalCameraModel implements CameraModel {
  private MatD myViewTransform;
  private MatD myProjection;
  private double myDistance = MAX_DISTANCE;
  private double myAngleX, myAngleY;
  private double myWidth = STANDARD_WIDTH, myHeight = STANDARD_HEIGHT;
  private double myFocallength = FAR_FOCAL_LENGTH;

  public CylindricalCameraModel() {
    updateModelView();
    updateProjection();
  }

  @Override
  public void updateViewport(double screenWidth, double screenHeight) {
    if (screenWidth * STANDARD_WIDTH > screenHeight * STANDARD_HEIGHT) {
      // Aspect ratio is wider than default.
      myHeight = STANDARD_HEIGHT;
      myWidth = screenWidth * STANDARD_HEIGHT / screenHeight;
    } else {
      // Aspect ratio is taller than or equal to the default.
      myHeight = screenHeight * STANDARD_WIDTH / screenWidth;
      myWidth = STANDARD_WIDTH;
    }
    updateProjection();
  }

  @Override
  public void onDrag(double dx, double dy) {
    myAngleX += dy / 3;
    myAngleY += dx / 3;

    myAngleX = Math.min(Math.max(myAngleX, -90), 90);
    updateModelView();
  }

  @Override
  public void onZoom(double dz) {
    myDistance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, myDistance + dz));
    double scale = (myDistance - MIN_DISTANCE) / (MAX_DISTANCE - MIN_DISTANCE);
    myFocallength = NEAR_FOCAL_LENGTH - (scale * (NEAR_FOCAL_LENGTH - FAR_FOCAL_LENGTH));
    updateModelView();
    updateProjection();
  }

  private void updateModelView() {
    myViewTransform = MatD.makeTranslationRotXY(0, 0, -myDistance, myAngleX, myAngleY);
  }

  private void updateProjection() {
    myProjection = MatD.projection(myWidth, myHeight, myFocallength, Z_NEAR);
  }

  @Override
  public MatD getProjection() {
    return myProjection;
  }

  @Override
  public MatD getViewTransform() {
    return myViewTransform;
  }

  @Override
  public double getZoom() {
    return 1 - ((myDistance - MIN_DISTANCE) / (MAX_DISTANCE - MIN_DISTANCE));
  }
}
