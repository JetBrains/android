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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.AffineTransform;

import static java.lang.Math.min;

/**
 * Helper class to get coordinates from a source coordinate system to a destination coordinate system
 */
public class CoordinateConverter {
  private double myXTransformScale = 1;
  private double myYTransformScale = 1;
  private final Dimension mySourceSize = new Dimension(1, 1);
  private final Dimension myDestSize = new Dimension(1, 1);
  private final Point myDestinationOrigin = new Point(0, 0);
  private final Point mySourceOrigin = new Point(0, 0);

  private boolean myCentered;
  private boolean myFixedRatio = true;
  private float myAdjustScale;
  private AffineTransform myAffineTransform = new AffineTransform();

  public void setDimensions(Dimension destSize, Dimension srcSize) {
    setDimensions(destSize, srcSize, 1f);
  }

  public void setDimensions(Dimension destSize, Dimension srcSize, float adjustScale) {
    setDimensions(destSize.width, destSize.height, srcSize.width, srcSize.height, adjustScale);
  }

  public void setDimensions(int destWidth, int destHeight, int srcWidth, int srcHeight) {
    setDimensions(destWidth, destHeight, srcWidth, srcHeight, 1f);
  }

  public void setDimensions(int destWidth, int destHeight, int srcWidth, int srcHeight, float adjustScale) {
    myAdjustScale = adjustScale;
    myDestSize.setSize(destWidth, destHeight);
    mySourceSize.setSize(srcWidth, srcHeight);
    updateDimensions(adjustScale);
    updateAffineTransform();
  }

  private void updateAffineTransform() {
    myAffineTransform.setToIdentity();
    myAffineTransform.translate(-mySourceOrigin.x, -mySourceOrigin.y);
    myAffineTransform.scale(myXTransformScale, myYTransformScale);
    myAffineTransform.translate(myDestinationOrigin.x, myDestinationOrigin.y);
  }

  private void updateDimensions(float adjustScale) {
    myXTransformScale = myDestSize.width / mySourceSize.getWidth() * adjustScale;
    myYTransformScale = myDestSize.height / mySourceSize.getHeight() * adjustScale;
    if (myFixedRatio) {
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
    if (fixedRatio != myFixedRatio) {
      myFixedRatio = fixedRatio;
      setDimensions(myDestSize, mySourceSize, myAdjustScale);
    }
  }

  public int x(double x) {
    return (int)Math.round((x - mySourceOrigin.x) * myXTransformScale + myDestinationOrigin.x);
  }

  public int inverseX(double x) {
    return (int)Math.round((x - myDestinationOrigin.x) / myXTransformScale + mySourceOrigin.x);
  }

  public int y(double y) {
    return (int)Math.round((y - mySourceOrigin.y) * myYTransformScale + myDestinationOrigin.y);
  }

  public int inverseY(double y) {
    return (int)Math.round((y - myDestinationOrigin.y) / myYTransformScale + mySourceOrigin.y);
  }

  public int dX(double dim) {
    return (int)Math.round(dim * myXTransformScale);
  }

  public int inverseDX(double dim) {
    return (int)Math.round(dim / myXTransformScale);
  }

  public int dY(double dim) {
    return (int)Math.round(dim * myYTransformScale);
  }

  public int inverseDY(double dim) {
    return (int)Math.round(dim / myYTransformScale);
  }

  public double getXScale() {
    return myXTransformScale;
  }

  public double getYScale() {
    return myYTransformScale;
  }

  public Dimension getSourceSize() {
    return mySourceSize;
  }

  /**
   * Set the bounds of destRect using the bounds of srcRect converted with this {@link CoordinateConverter}
   *
   * @param srcRect  Source rectangle to convert from.
   * @param destRect Destination rectangle that will hold the new bounds. Can be the same as srcRect.
   *                 If null, it will a new instance of Rectangle
   * @return destRect with the converted bounds
   */
  public Rectangle convert(@NotNull Rectangle srcRect, @Nullable Rectangle destRect) {
    if (destRect == null) {
      destRect = new Rectangle();
    }
    destRect.setBounds(
      x(srcRect.x),
      y(srcRect.y),
      dX(srcRect.width),
      dY(srcRect.height)
    );
    return destRect;
  }

  /**
   * Set the bounds of destRect using the bounds of srcRect converted
   * with the inverse of this {@link CoordinateConverter}
   *
   * @param srcRect  Source rectangle to convert from.
   * @param destRect Destination rectangle that will hold the new bounds. Can be the same as srcRect.
   *                 If null, it will a new instance of Rectangle
   * @return destRect with the converted bounds
   */
  public Rectangle convertInverse(@NotNull Rectangle srcRect, @Nullable Rectangle destRect) {
    if (destRect == null) {
      destRect = new Rectangle();
    }
    destRect.setBounds(
      inverseX(srcRect.x),
      inverseY(srcRect.y),
      inverseDX(srcRect.width),
      inverseDY(srcRect.height)
    );
    return destRect;
  }

  public AffineTransform getAffineTransform() {
    return myAffineTransform;
  }
}
