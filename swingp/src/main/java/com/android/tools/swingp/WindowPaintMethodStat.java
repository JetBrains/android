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

import java.awt.*;
import java.awt.geom.AffineTransform;

public class WindowPaintMethodStat extends MethodStat {
  @NotNull private final AffineTransform myTransform;
  @NotNull private final Point myLocation;

  public WindowPaintMethodStat(@NotNull Window owner) {
    super(owner);
    if (owner.getParent() == null) {
      myLocation = new Point(0, 0);
      myTransform = new AffineTransform();
    }
    else {
      myTransform = ((Graphics2D)owner.getGraphics()).getTransform();
      if (owner.getParent().isShowing()) {
        Point parentLocationOnScreen = owner.getParent().getLocationOnScreen();
        Point locationOnScreen = owner.getLocationOnScreen();
        myLocation = new Point(locationOnScreen.x - parentLocationOnScreen.x, locationOnScreen.y - parentLocationOnScreen.y);
      }
      else {
        myLocation = owner.getLocationOnScreen();
      }
    }
  }
}
