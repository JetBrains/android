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
  private final float[] mKeyFramesPos;
  private final int[] mKeyFramesType;
  private float[] mPath;
  private GeneralPath ourPath = new GeneralPath();
  private AffineTransform at = new AffineTransform();
  private static BasicStroke ourBasicStroke = new BasicStroke(1f);
  private static BasicStroke ourShadowStroke = new BasicStroke(1f);
  private int mViewX;
  private int mViewY;
  private int mViewWidth;
  private int mViewHeight;
  int[] xpath = new int[4];
  int[] ypath = new int[4];
  boolean mSelected = false;

  DrawMotionPath(boolean selected,
                 float[] path,
                 int pathSize,
                 int[] keyFramesType,
                 float[] keyFramePos,
                 int keyFramesSize,
                 int vx,
                 int vy,
                 int vw,
                 int vh) {
    mPath = Arrays.copyOf(path, pathSize);
    ourPath.reset();
    mViewX = vx;
    mViewY = vy;
    mViewWidth = vw;
    mViewHeight = vh;
    mSelected = selected;

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
      mKeyFramesPos = Arrays.copyOf(keyFramePos, keyFramesSize * 2);
      mKeyFramesType = Arrays.copyOf(keyFramesType, keyFramesSize);
    }
    else {
      mKeyFramesPos = null;
      mKeyFramesType = null;
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
    // Only draw keyframes if the component is selected
    if (mKeyFramesPos != null && mSelected) {
      for (int i = 0; i < mKeyFramesPos.length; i += 2) {
        int type = mKeyFramesType[i/2];
        if (type / 1000 != 2) {
          // For now, only draw keyframes positions
          continue;
        }
        int posx = (int)mKeyFramesPos[i];
        int posy = (int)mKeyFramesPos[i + 1];
        xpath[0] = posx;
        xpath[1] = posx + diamond+1;
        xpath[2] = posx;
        xpath[3] = posx - diamond-1;

        ypath[0] = posy - diamond-1;
        ypath[1] = posy;
        ypath[2] = posy + diamond+1;
        ypath[3] = posy;

        g2.setColor(Color.BLACK);
        g2.fillPolygon(xpath, ypath, 4);
        xpath[1]--;
        ypath[2]--;
        xpath[3]++;
        ypath[0]++;
        g2.setColor(Color.WHITE);
        g2.fillPolygon(xpath, ypath, 4);
      }
    }
  }

  @Override
  public String serialize() {
    return null;
  }

  public static void buildDisplayList(boolean selected, DisplayList list,
                                      float[] path,
                                      int pathSize,
                                      int[] keyFrameType,
                                      float[] keyFramePos,
                                      int keyFrameSize,
                                      int x,
                                      int y,
                                      int w,
                                      int h) {

    list.add(new DrawMotionPath(selected, path, pathSize, keyFrameType, keyFramePos, keyFrameSize, x, y, w, h));
  }
}
