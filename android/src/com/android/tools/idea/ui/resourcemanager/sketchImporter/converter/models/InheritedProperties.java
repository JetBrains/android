/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models;

import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchShapeGroup;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchSymbolMaster;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchSymbolMaster;
import java.awt.geom.Point2D;
import org.jetbrains.annotations.NotNull;

/**
 * Class that holds properties that have to be inherited from the upper layers, such as {@link SketchShapeGroup} and
 * {@link SketchSymbolMaster}: translations, rotations, flipping, opacity and constraints.
 * <p>
 * As the engine goes down the JSON tree, translations, rotations and opacities are accumulated in this class in the
 * corresponding fields. Flipping is also recorded in this class, but instead of accumulating all the flipping,
 * they are being kept track of by performing the XOR operation between the previous and current flipping value. For example,
 * if a group is flipped, all the elements inside it will be flipped as well. However, if one element inside has its own
 * flipping of the same type, it is going to look like the shape was not flipped at all.
 */
public class InheritedProperties {
  public static final int DEFAULT_OPACITY = 1;
  public static final int DEFAULT_ROTATION = 0;
  public static final boolean DEFAULT_FLIP_X = false;
  public static final boolean DEFAULT_FLIP_Y = false;

  @NotNull private Point2D.Double myInheritedTranslation;
  private int myInheritedRotations;
  private boolean myInheritedFlipX;
  private boolean myInheritedFlipY;
  private double myInheritedOpacity;
  @NotNull private ResizingConstraint mInheritedResizingConstraint;

  public InheritedProperties() {
    myInheritedTranslation = new Point2D.Double();
    myInheritedRotations = DEFAULT_ROTATION;
    myInheritedFlipX = DEFAULT_FLIP_X;
    myInheritedFlipY = DEFAULT_FLIP_Y;
    myInheritedOpacity = DEFAULT_OPACITY;
    mInheritedResizingConstraint = new ResizingConstraint();
  }

  public InheritedProperties(@NotNull InheritedProperties inheritedProperties,
                             @NotNull Point2D.Double translation,
                             boolean flipX,
                             boolean flipY,
                             int rotation,
                             double opacity,
                             @NotNull ResizingConstraint constraint) {
    myInheritedTranslation =
      new Point2D.Double(inheritedProperties.getInheritedTranslation().getX() + translation.getX(),
                         inheritedProperties.getInheritedTranslation().getY() + translation.getY());
    myInheritedRotations = inheritedProperties.getInheritedRotation() + rotation;
    myInheritedFlipX = inheritedProperties.isInheritedFlipX() ^ flipX;
    myInheritedFlipY = inheritedProperties.isInheritedFlipY() ^ flipY;
    myInheritedOpacity = inheritedProperties.getInheritedOpacity() * opacity;
    mInheritedResizingConstraint = inheritedProperties.getInheritedResizingConstraint().updateConstraint(constraint);
  }

  @NotNull
  public Point2D.Double getInheritedTranslation() {
    return myInheritedTranslation;
  }

  public int getInheritedRotation() {
    return myInheritedRotations;
  }

  public boolean isInheritedFlipX() {
    return myInheritedFlipX;
  }

  public boolean isInheritedFlipY() {
    return myInheritedFlipY;
  }

  public double getInheritedOpacity() {
    return myInheritedOpacity;
  }

  @NotNull
  public ResizingConstraint getInheritedResizingConstraint() {
    return mInheritedResizingConstraint;
  }
}