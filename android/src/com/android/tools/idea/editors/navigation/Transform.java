/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.editors.navigation;

import com.android.tools.idea.editors.navigation.model.ModelDimension;
import com.android.tools.idea.editors.navigation.model.ModelPoint;
import com.android.tools.idea.rendering.RenderedView;

import java.awt.*;

public class Transform {
  public final float myScale;

  public Transform(float scale) {
    myScale = scale;
  }

  // Model to View

  private int scale(int d) {
    return ((int)(d * myScale));
  }

  public int modelToViewX(int x) {
    return scale(x);
  }

  public int modelToViewY(int y) {
    return scale(y);
  }

  public int modelToViewW(int d) {
    return scale(d);
  }

  public int modelToViewH(int d) {
    return scale(d);
  }

  public Point modelToView(ModelPoint loc) {
    return new Point(modelToViewX(loc.x), modelToViewY(loc.y));
  }

  public Dimension modelToView(ModelDimension size) {
    return new Dimension(modelToViewW(size.width), modelToViewH(size.height));
  }

  public Rectangle getBounds(RenderedView v) {
    return new Rectangle(modelToViewX(v.x), modelToViewY(v.y), modelToViewW(v.w), modelToViewH(v.h));
  }

  // View to Model

  private int unScale(int d) {
    return (int)(d / myScale);
  }

  public int viewToModelX(int d) {
    return unScale(d);
  }

  public int viewToModelY(int d) {
    return unScale(d);
  }

  public int viewToModelW(int d) {
    return unScale(d);
  }

  public int viewToModelH(int d) {
    return unScale(d);
  }

  public ModelPoint viewToModel(Point loc) {
    return new ModelPoint(viewToModelX(loc.x), viewToModelY(loc.y));
  }
}
