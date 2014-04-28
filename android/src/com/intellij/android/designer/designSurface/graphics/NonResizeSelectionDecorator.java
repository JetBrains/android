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
package com.intellij.android.designer.designSurface.graphics;

import com.intellij.designer.designSurface.DecorationLayer;
import com.intellij.designer.model.RadComponent;

import java.awt.*;

public class NonResizeSelectionDecorator extends com.intellij.designer.designSurface.selection.NonResizeSelectionDecorator {
  private final DrawingStyle myStyle;

  public NonResizeSelectionDecorator(DrawingStyle style) {
    super(Color.RED /* should not be used */, 1 /* should not be used */);
    myStyle = style;
  }

  @Override
  protected void paint(DecorationLayer layer, Graphics2D g, RadComponent component) {
    Rectangle bounds = getBounds(layer, component);
    DesignerGraphics.drawRect(myStyle, g, bounds.x, bounds.y, bounds.width, bounds.height);
  }
}
