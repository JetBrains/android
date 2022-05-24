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

import com.android.tools.swingp.json.IncludeMethodsSerializer;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import sun.java2d.SunGraphics2D;

@JsonAdapter(IncludeMethodsSerializer.class)
public class PaintImmediatelyMethodStat extends MethodStat {
  @NotNull private final JComponent myBufferComponent;
  @SerializedName("xform")
  @NotNull private final AffineTransform myTransform;
  @SerializedName("constrain")
  private final int[] myConstrain;
  @SerializedName("bounds")
  private final int[] myBounds;

  @SerializedName("bufferBounds")
  private final int[] myBufferBounds;
  @SerializedName("bufferType")
  private String getBufferType() {
    return myBufferComponent.getClass().getSimpleName();
  }
  @SerializedName("bufferId")
  private long getBufferId() {
    return System.identityHashCode(myBufferComponent);
  }

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
}
