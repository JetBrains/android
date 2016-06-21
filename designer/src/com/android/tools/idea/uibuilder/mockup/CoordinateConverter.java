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
package com.android.tools.idea.uibuilder.mockup;

import java.awt.*;

import static java.lang.Math.min;

/**
 * Helper class to get coordinates from a source coordinate system to a destination coordinate system
 */
class CoordinateConverter {
  private double myXTransformScale = 1;
  private double myYTransformScale = 1;
  private final Dimension mySourceSize = new Dimension(1, 1);
  private final Dimension myDestSize = new Dimension(1, 1);
  private final Point myDestinationOrigin = new Point(0, 0);
  private final Point mySourceOrigin = new Point(0, 0);

  private boolean myCentered;
  private boolean myFixedRatio;
  private float myAdjustScale;

  public void setDimensions(Dimension destSize, Dimension srcSize) {
    setDimensions(destSize, srcSize, 1f);
  }

  public void setDimensions(Dimension destSize, Dimension srcSize, float adjustScale) {
    if (mySourceSize.equals(destSize) && myDestSize.equals(srcSize)) {
      return;
    }
    mySourceSize.setSize(srcSize);
    myDestSize.setSize(destSize);
    myAdjustScale = adjustScale;
    myXTransformScale = myDestSize.width / srcSize.getWidth() * adjustScale;
    myYTransformScale = myDestSize.height / srcSize.getHeight() * adjustScale;
    if(myFixedRatio) {
      myXTransformScale = myYTransformScale = min(myXTransformScale, myYTransformScale);
    }
    if (myCentered) {
      setCenterInDestination();
    }
  }

  public void setDestinationPosition(double x, double y) {
    myDestinationOrigin.setLocation(x, y);
    myCentered = false;
  }

  public void setSourcePosition(double x, double y) {
    mySourceOrigin.setLocation(x, y);
  }

  public void setCenterInDestination() {
    setDestinationPosition(
      myDestSize.width / 2. - (mySourceSize.width / 2.) * myXTransformScale,
      myDestSize.height / 2. - (mySourceSize.height / 2.) * myYTransformScale
    );
    myCentered = true;
  }

  public void setFixedRatio(boolean fixedRatio) {
    if(fixedRatio != myFixedRatio) {
      myFixedRatio = fixedRatio;
      setDimensions(myDestSize, mySourceSize, myAdjustScale);
    }
  }

  public int x(double x) {
    return (int)Math.round(myDestinationOrigin.x + myXTransformScale * (x - mySourceOrigin.x));
  }

  public int y(double y) {
    return (int)Math.round(myDestinationOrigin.y + myYTransformScale * (y - mySourceOrigin.y));
  }

  public int dX(double dim) {
    return (int)Math.round(myXTransformScale * dim);
  }

  public int dY(double dim) {
    return (int)Math.round(myYTransformScale * dim);
  }
}
