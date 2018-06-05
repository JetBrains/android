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

import org.jetbrains.annotations.NotNull;
import sun.java2d.SunGraphics2D;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;

public class PaintImmediatelyMethodStat extends MethodStat {
  @NotNull private final JComponent myBufferComponent;
  @NotNull private final AffineTransform myTransform;
  private final int myConstrainX;
  private final int myConstrainY;
  private final int myX;
  private final int myY;
  private final int myW;
  private final int myH;

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
    if (g instanceof SunGraphics2D) {
      SunGraphics2D sg = (SunGraphics2D)g;
      myConstrainX = sg.constrainX;
      myConstrainY = sg.constrainY;
    }
    else {
      myConstrainX = 0;
      myConstrainY = 0;
    }
    myX = x;
    myY = y;
    myW = w;
    myH = h;
  }
}
