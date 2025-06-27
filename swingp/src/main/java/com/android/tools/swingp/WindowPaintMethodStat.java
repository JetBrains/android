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
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Window;
import java.awt.geom.AffineTransform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@JsonAdapter(IncludeMethodsSerializer.class)
public class WindowPaintMethodStat extends MethodStat {
  @Nullable private final Window myOwnerWindow;

  @SerializedName("xform")
  @NotNull private final AffineTransform myTransform;
  @SerializedName("location")
  @NotNull private final Point myLocation;

  @SerializedName("windowId")
  private long getWindowId() {
    return System.identityHashCode(myOwner.get());
  }

  public WindowPaintMethodStat(@NotNull Window owner) {
    super(owner);
    myOwnerWindow = owner.getOwner();
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
