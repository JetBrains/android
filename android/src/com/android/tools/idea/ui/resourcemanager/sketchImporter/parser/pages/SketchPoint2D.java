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
package com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages;

import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.deserializers.PointDeserializer;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import org.jetbrains.annotations.NotNull;

/**
 * {@link Point2D.Double} that represents a sketch point, which is actually a string in the sketch file.
 *
 * @see PointDeserializer
 */
public class SketchPoint2D extends Point2D.Double {
  public SketchPoint2D(double x, double y) {
    super(x, y);
  }

  /**
   * Method called on a point whose coordinates are the ones relative to the frame of the shapePath
   *
   * @param ownFrame
   */
  @NotNull
  public SketchPoint2D makeAbsolutePosition(@NotNull Rectangle2D ownFrame) {
    return new SketchPoint2D(getX() * ownFrame.getWidth(),
                             getY() * ownFrame.getHeight());
  }
}