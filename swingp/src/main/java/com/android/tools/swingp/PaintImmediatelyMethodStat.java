/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.swingp;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import sun.java2d.SunGraphics2D;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;

public class PaintImmediatelyMethodStat extends MethodStat {
  @NotNull private final JComponent myBufferComponent;
  @NotNull private final AffineTransform myTransform;
  private final int[] myBufferBounds;
  private final int[] myConstrain;
  private final int[] myBounds;

  public PaintImmediatelyMethodStat(@NotNull Object owner,
                                    @NotNull JComponent bufferComponent,
                                    @NotNull Graphics g,
                                    int x,
                                    int y,
                                    int w,
                                    int h) {
    super(owner);
    myTransform = ((Graphics2D)g).getTransform();
    myBufferComponent = bufferComponent;
    myBufferBounds =
      new int[]{myBufferComponent.getX(), myBufferComponent.getY(), myBufferComponent.getWidth(), myBufferComponent.getHeight()};
    if (g instanceof SunGraphics2D) {
      SunGraphics2D sg = (SunGraphics2D)g;
      myConstrain = new int[]{sg.constrainX, sg.constrainY};
    }
    else {
      myConstrain = new int[]{0, 0};
    }
    myBounds = new int[]{x, y, w, h};
  }

  @Override
  protected void addAttributeDescriptions(@NotNull JsonObject description) {
    super.addAttributeDescriptions(description);

    description.add("constrain", SerializationHelpers.arrayToJsonArray(myConstrain));
    description.add("bounds", SerializationHelpers.arrayToJsonArray(myBounds));

    double[] matrix = new double[6];
    myTransform.getMatrix(matrix);
    description.add("xform", SerializationHelpers.arrayToJsonArray(matrix));

    description.add("bufferBounds", SerializationHelpers.arrayToJsonArray(myBufferBounds));
    description.addProperty("bufferType", myBufferComponent.getClass().getSimpleName());
    description.addProperty("bufferId", System.identityHashCode(myBufferComponent));
  }
}
