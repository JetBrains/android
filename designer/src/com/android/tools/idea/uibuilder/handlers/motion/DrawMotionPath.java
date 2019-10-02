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

public class DrawMotionPath implements DrawCommand {
  private final float[] mkeyFramesPos;
  private float[] mPath;
  private GeneralPath ourPath = new GeneralPath();
  private AffineTransform at = new AffineTransform();
  private static BasicStroke ourBasicStroke = new BasicStroke(1f);
  private static BasicStroke ourShadowStroke = new BasicStroke(1f);
  private int mViewX;
  private int mViewY;
  private int mViewWidth;
  private int mViewHeight;

  DrawMotionPath(float[] path, int pathSize, int[] keyFramesType, float[] keyFramePos, int keyFramesSize, int vx, int vy, int vw, int vh) {
    mPath = Arrays.copyOf(path, pathSize);
    ourPath.reset();
    mViewX = vx;
    mViewY = vy;
    mViewWidth = vw;
    mViewHeight = vh;

    for (int i = 0; i < pathSize; i += 2) {
      float x = mPath[i];
      float y = mPath[i + 1];
      if ((i & 2) == 0) {
        ourPath.moveTo(x, y);
      }
      else {
        ourPath.lineTo(x, y);
      }
    }

    if (keyFramesSize > 0) {
      mkeyFramesPos = Arrays.copyOf(keyFramePos, keyFramesSize * 2);
    }
    else {
      mkeyFramesPos = null;
    }
  }


  @Override
  public int getLevel() {
    return CONNECTION_LEVEL;
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    double scale = sceneContext.getScale();
    double vx = sceneContext.getSwingXDip(mViewX);
    double vy = sceneContext.getSwingYDip(mViewY);
    Graphics2D g2 = (Graphics2D)g.create();
    at.setToIdentity();
    at.translate(vx, vy);
    at.scale(scale, scale);

    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    float lineWidth = (2f / (float)scale);
    g2.transform(at);

    if (Math.abs(ourShadowStroke.getLineWidth() - lineWidth) > 0.01) {
      ourShadowStroke = new BasicStroke(lineWidth);
      ourBasicStroke = new BasicStroke(lineWidth / 2);
    }
    g2.setStroke(ourShadowStroke);
    g2.setColor(Color.BLACK);
    g2.draw(ourPath);
    int diamond = (int)(1 + 5 / scale);
    g2.setStroke(ourBasicStroke);
    g2.setColor(Color.white);
    g2.draw(ourPath);
    if (mkeyFramesPos != null) {
      for (int i = 0; i < mkeyFramesPos.length; i += 2) {
        int posx = (int)mkeyFramesPos[i];
        int posy = (int)mkeyFramesPos[i + 1];
        int[] xpath = new int[]{posx, posx + diamond, posx, posx - diamond};
        int[] ypath = new int[]{posy - diamond, posy, posy + diamond, posy};
        g2.fillPolygon(xpath, ypath, 4);
      }
    }
  }

  @Override
  public String serialize() {
    return null;
  }

  public static void buildDisplayList(DisplayList list,
                                      float[] path,
                                      int pathSize,
                                      int[] keyFrameType,
                                      float[] keyFramePos,
                                      int keyFrameSize,
                                      int x,
                                      int y,
                                      int w,
                                      int h) {

    list.add(new DrawMotionPath(path, pathSize, keyFrameType, keyFramePos, keyFrameSize, x, y, w, h));
  }
}
