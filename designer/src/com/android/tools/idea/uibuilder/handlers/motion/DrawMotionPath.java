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
package com.android.tools.idea.uibuilder.handlers.motion;

import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawCommand;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

public class DrawMotionPath implements DrawCommand {
  private float[] mValues;
  private GeneralPath ourPath = new GeneralPath();
  private AffineTransform at = new AffineTransform();
  private static BasicStroke ourBasicStroke = new BasicStroke(0);

  DrawMotionPath(float[] values, int size) {
    mValues = Arrays.copyOf(values, values.length);
    ourPath.reset();
    for (int i = 0; i < size; i += 2) {
      float x = values[i];
      float y = values[i + 1];
      if ((i & 2) == 0) {
        ourPath.moveTo(x, y);
      }
      else {
        ourPath.lineTo(x, y);
      }
    }
  }

  @Override
  public int getLevel() {
    return 0;
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    double scale = sceneContext.getScale();
    double dx = sceneContext.getSwingX(0);
    double dy = sceneContext.getSwingY(0);
    Graphics2D g2 = (Graphics2D)g.create();
    at.setToIdentity();
    at.translate(dx, dy);
    at.scale(scale, scale);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setStroke(ourBasicStroke);
    g2.transform(at);
    g2.setColor(Color.white);
    g2.draw(ourPath);
  }

  @Override
  public String serialize() {
    return null;
  }

  @Override
  public int compareTo(@NotNull Object o) {
    return 0;
  }


  public static void buildDisplayList(DisplayList list, float[] values, int size) {
    list.add(new DrawMotionPath(values, size));
  }
}
