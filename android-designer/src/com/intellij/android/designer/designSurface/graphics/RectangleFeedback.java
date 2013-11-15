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

import java.awt.*;

public class RectangleFeedback extends com.intellij.designer.designSurface.feedbacks.RectangleFeedback {
  private final DrawingStyle myStyle;

  public RectangleFeedback(DrawingStyle style) {
    super(Color.RED /* should not be used */, 1 /* should not be used */);
    myStyle = style;
  }

  @Override
  protected void paintFeedback(Graphics g) {
    Dimension size = getSize();
    DesignerGraphics.drawRect(myStyle, g, 0, 0, size.width, size.height);
  }
}