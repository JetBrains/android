/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.intellij.ui.JBColor;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.util.Arrays;

public class DrawMotionPath implements DrawCommand {
  private static final boolean WHITE_PATH = true;
  private final float[] mKeyFramesPos;
  private final int[] mKeyFramesType;
  private float[] mPath;
  private GeneralPath ourPath = new GeneralPath();
  private AffineTransform at = new AffineTransform();
  private static final Color myGridColor = JBColor.namedColor("UIDesigner.motion.Grid.lineSeparatorColor", 0xffCACACA, 0xffCACACA);
  private static final Color myAxisColor = JBColor.namedColor("UIDesigner.motion.Axis.lineSeparatorColor", 0xff979797, 0xff979797);
  private static final Color myPointLabelBackground = JBColor.namedColor("UIDesigner.motion.label.background", 0xfff7f7f7, 0xff4b4d4d);
  private static final Color myPointLabelShadow = JBColor.namedColor("UIDesigner.motion.label.shadowColor", 0x33000000, 0x33000000);
  private static final Color myPointLabelText = JBColor.namedColor("UIDesigner.motion.label.textColor", 0xFF000000, 0xffbdbdbd);

  private static BasicStroke ourBasicStroke = new BasicStroke(1f);
  private static BasicStroke ourShadowStroke = new BasicStroke(1f);
  private static BasicStroke ourAxis = new BasicStroke(2f);
  private static BasicStroke ourMajorLines = new BasicStroke(1f);
  private float mViewX;
  private float mViewY;
  private float mViewWidth;
  private float mViewHeight;
  int[] xpath = new int[4];
  int[] ypath = new int[4];
  boolean mSelected = false;
  int mSelectedKey;
  private GeneralPath mGridAxis = new GeneralPath();
  private GeneralPath mGridMajor = new GeneralPath();
  public static final int TYPE_SCREEN = 2;
  public static final int TYPE_PATH = 1;
  public static final int TYPE_CARTESIAN = 0;
  private float drawXaxisX;
  private float drawXaxisY;
  private float drawYaxisX;
  private float drawYaxisY;
  String mPercent;
  DecimalFormat myPercentFormat = new DecimalFormat("#.000");
  JBColor myPointBackground = new JBColor(new Color(0x884322ff, true), new Color(0x884322ff, true));
  private Color myLineColor = new JBColor(0xFFFF00FF, 0xFFFF00FF);

  DrawMotionPath(boolean selected,
                 int selected_key,
                 int key_pos_type,
                 float[] path,
                 int pathSize,
                 int[] keyFramesType,
                 float[] keyFramePos,
                 int keyFramesSize,
                 float vx,
                 float vy,
                 float vw,
                 float vh,
                 float cbx,
                 float cby,
                 float cbw,
                 float cbh,
                 float percentX,
                 float percentY) {
    mSelectedKey = selected_key;
    mPath = Arrays.copyOf(path, pathSize);
    ourPath.reset();
    mViewX = vx;
    mViewY = vy;
    mViewWidth = vw;
    mViewHeight = vh;
    mSelected = selected;
    mGridAxis.reset();
    mGridMajor.reset();

    if (!Float.isNaN(percentX)) {
      mPercent = "(" + myPercentFormat.format(percentX) + "," + myPercentFormat.format(percentY) + ")";
    }
    else {
      mPercent = "";
    }
    drawXaxisX = Float.NaN;
    if (pathSize >= 4 && selected_key >= 0) {
      switch (key_pos_type) {
        case TYPE_CARTESIAN:
        default:
          graphDeltaXY();
          break;
        case TYPE_PATH:
          graphPathRelative();
          break;
        case TYPE_SCREEN:
          graphDeltaParent(cbx, cby, cbw, cbh);
          break;
      }
    }

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

  private void drawArrow(float tipx, float tipy, float dirX, float dirY) {
    float px, py;
    float hyp = (float)(1 / Math.hypot(dirX, dirY));
    dirX *= hyp;
    dirY *= hyp;
    float arrowSize = 10;
    mGridAxis.moveTo(tipx, tipy);
    px = tipx + arrowSize * (dirY - dirX);
    py = tipy + arrowSize * (-dirX - dirY);
    mGridAxis.lineTo(px, py);
    mGridAxis.moveTo(tipx, tipy);
    px = tipx + arrowSize * (-dirY - dirX);
    py = tipy + arrowSize * (dirX - dirY);
    mGridAxis.lineTo(px, py);
  }

  private void graphDeltaXY() {
    float mStartX, mStartY;
    float mEndX, mEndY;
    mStartX = mPath[0];
    mStartY = mPath[1];
    mEndX = mPath[mPath.length - 2];
    mEndY = mPath[mPath.length - 1];
    for (int i = 0; i <= 100; i += 10) {
      float x = mStartX + (mEndX - mStartX) * i / 100f;
      float y = mStartY + (mEndY - mStartY) * i / 100f;
      if (i == 0) {
        mGridAxis.moveTo(x, mStartY);
        mGridAxis.lineTo(x, mEndY);
        mGridAxis.moveTo(mStartX, y);
        mGridAxis.lineTo(mEndX, y);
      }
      else {
        mGridMajor.moveTo(x, mStartY);
        mGridMajor.lineTo(x, mEndY);
        mGridMajor.moveTo(mStartX, y);
        mGridMajor.lineTo(mEndX, y);
      }
    }

    drawArrow(mStartX, mEndY, 0, mEndY - mStartY);
    drawArrow(mEndX, mStartY, mEndX - mStartX, 0);
    float xoffsetY = (mStartY > mEndY) ? 30 : -30;
    float yoffsetX = (mStartX > mEndX) ? 30 : -30;
    float char_width = 10;
    float char_assent = 10;
    drawXaxisX = mEndX - char_width;
    drawXaxisY = mStartY + xoffsetY;
    drawYaxisX = mStartX + yoffsetX;
    drawYaxisY = mEndY + char_assent;
  }

  private void graphPathRelative() {
    float mStartX, mStartY;
    float mEndX, mEndY;
    mStartX = mPath[0];
    mStartY = mPath[1];
    mEndX = mPath[mPath.length - 2];
    mEndY = mPath[mPath.length - 1];
    for (int x = 0; x <= 100; x += 10) {
      float y = 0;
      float xp1 = mStartX + (mEndX - mStartX) * x / 100f;
      float yp1 = mStartY + (mEndY - mStartY) * x / 100f;
      y = 100;
      float xp2 = xp1 + (mEndY - mStartY) * y / 100f;
      float yp2 = yp1 - (mEndX - mStartX) * y / 100f;

      y = -100;
      float xp3 = xp1 + (mEndY - mStartY) * y / 100f;
      float yp3 = yp1 - (mEndX - mStartX) * y / 100f;
      if (x == 0 || x == 100) {
        mGridAxis.moveTo(xp2, yp2);
        mGridAxis.lineTo(xp3, yp3);
      }
      else {
        mGridMajor.moveTo(xp2, yp2);
        mGridMajor.lineTo(xp3, yp3);
      }
    }
    for (int y = -100; y <= 100; y += 10) {

      float xp1 = mStartX + (mEndY - mStartY) * y / 100f;
      float yp1 = mStartY - (mEndX - mStartX) * y / 100f;
      float x = 100;

      float xp2 = xp1 + (mEndX - mStartX) * x / 100f;
      float yp2 = yp1 + (mEndY - mStartY) * x / 100f;
      if (y == -100 || y == 0 || y == 100) {
        mGridAxis.moveTo(xp1, yp1);
        mGridAxis.lineTo(xp2, yp2);
      }
      else {
        mGridMajor.moveTo(xp1, yp1);
        mGridMajor.lineTo(xp2, yp2);
      }
    }

    float xdir_dx = (mEndX - mStartX);
    float xdir_dy = (mEndY - mStartY);
    float hypot = (float)(1 / Math.hypot(xdir_dx, xdir_dy));
    xdir_dx *= hypot;
    xdir_dy *= hypot;
    float ydir_dx = (mEndY - mStartY);
    float ydir_dy = -(mEndX - mStartX);
    hypot = (float)(1 / Math.hypot(ydir_dx, ydir_dy));
    ydir_dx *= hypot;
    ydir_dy *= hypot;
    float distanceToCenter = 22;
    float x_centerToDrawX = -10;
    float y_centerToDrawX = -8;
    float centerToDrawY = +2;
    drawXaxisX = mEndX + xdir_dx * distanceToCenter + x_centerToDrawX;
    drawXaxisY = mEndY + xdir_dy * distanceToCenter + centerToDrawY;
    drawArrow(mEndX, mEndY, mEndX - mStartX, mEndY - mStartY);
    float xp1 = mStartX - (mEndY - mStartY) * 100 / 100f;
    float yp1 = mStartY + (mEndX - mStartX) * 100 / 100f;

    drawYaxisX = xp1 - xdir_dx * distanceToCenter + y_centerToDrawX;
    drawYaxisY = yp1 - xdir_dy * distanceToCenter + centerToDrawY;
    drawArrow(xp1, yp1, -(mEndY - mStartY), +(mEndX - mStartX));
  }

  private void graphDeltaParent(float vx,
                                float vy,
                                float vw,
                                float vh) {

    for (int i = 0; i <= 100; i += 10) {
      float x = vx + (vw) * i / 100f;
      float y = vy + (vh) * i / 100f;
      if (i == 0) {
        mGridAxis.moveTo(x, vy);
        mGridAxis.lineTo(x, vy + vh);
        mGridAxis.moveTo(vx, y);
        mGridAxis.lineTo(vx + vw, y);
      }
      else {
        mGridMajor.moveTo(x, vy);
        mGridMajor.lineTo(x, vy + vh);
        mGridMajor.moveTo(vx, y);
        mGridMajor.lineTo(vx + vw, y);
      }
    }
    drawArrow(vx + vw, vy, 1, 0);
    drawArrow(vx, vy + vh, 0, 1);

    float xoffsetY = -10;
    float yoffsetX = -30;
    float char_width = 10;
    float char_assent = 10;
    drawXaxisX = vx + vw - char_width;
    drawXaxisY = vy + xoffsetY;
    drawYaxisX = vx + yoffsetX;
    drawYaxisY = vy + vh;
  }


  @Override
  public int getLevel() {
    return DrawCommand.CONNECTION_LEVEL;
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
    g2.setFont(g2.getFont().deriveFont(12 / (float)scale));
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    float lineWidth = (2f / (float)scale);
    g2.transform(at);

    if (Math.abs(ourShadowStroke.getLineWidth() - lineWidth) > 0.01) {
      ourShadowStroke = new BasicStroke(lineWidth);
      ourBasicStroke = new BasicStroke(lineWidth / 2);
    }
    // shadow
    if (WHITE_PATH) {
      g2.setStroke(ourShadowStroke);
      g2.setColor(Color.BLACK);
      g2.draw(ourPath);
    }

    // grid lines
    g2.setColor(myGridColor);
    g2.setStroke(ourMajorLines);
    g2.draw(mGridMajor);

    // draw axis
    g2.setColor(myAxisColor);
    g2.setStroke(ourAxis);
    g2.draw(mGridAxis);
    if (!Float.isNaN(drawXaxisX)) {
      g2.drawString("x", drawXaxisX, drawXaxisY);
      g2.drawString("y", drawYaxisX, drawYaxisY);
    }

    // main path
    g2.setStroke(ourBasicStroke);
    g2.setColor((WHITE_PATH) ? myLineColor : Color.WHITE);
    g2.draw(ourPath);

    int diamond = (int)(1 + 5 / scale);
    // Only draw keyframes if the component is selected
    if (mKeyFramesPos != null && mSelected) {
      for (int i = 0; i < mKeyFramesPos.length; i += 2) {
        int type = mKeyFramesType[i / 2];
        if (type / 1000 != 2) {
          // For now, only draw keyframes positions
          continue;
        }
        int posx = (int)mKeyFramesPos[i];
        int posy = (int)mKeyFramesPos[i + 1];
        xpath[0] = posx;
        xpath[1] = posx + diamond + 1;
        xpath[2] = posx;
        xpath[3] = posx - diamond - 1;

        ypath[0] = posy - diamond - 1;
        ypath[1] = posy;
        ypath[2] = posy + diamond + 1;
        ypath[3] = posy;
        int stringOffset = 10;
        g2.setColor(Color.BLACK);
        g2.fillPolygon(xpath, ypath, 4);

        if (mSelectedKey == i / 2 && mPercent.length() > 0) {
          FontMetrics fm = g2.getFontMetrics();
          Rectangle2D bounds = fm.getStringBounds(mPercent, g2);
          g2.setColor(myPointLabelShadow);
          int border = 4;
          g2.fillRect(stringOffset + posx - border / 2, posy - fm.getAscent() - border / 2, (int)bounds.getWidth() + border * 2,
                      (int)bounds.getHeight() + border * 2);
          g2.setColor(myPointLabelBackground);
          border = 6;
          g2.fillRect(stringOffset + posx - border, posy - fm.getAscent() - border, (int)bounds.getWidth() + border * 2,
                      (int)bounds.getHeight() + border * 2);
        }
        xpath[1]--;
        ypath[2]--;
        xpath[3]++;
        ypath[0]++;

        if (mSelectedKey == i / 2) {
          g2.setColor(myPointLabelText);
          g2.drawString(mPercent, 10 + posx, posy);
          g2.setColor(Color.BLUE);
        }
        else {
          g2.setColor(Color.WHITE);
        }
        g2.fillPolygon(xpath, ypath, 4);
      }
    }
  }

  @Override
  public String serialize() {
    return null;
  }

  public static void buildDisplayList(boolean selected, DisplayList list,
                                      int selected_key,
                                      int key_pos_type,
                                      float[] path,
                                      int pathSize,
                                      int[] keyFrameType,
                                      float[] keyFramePos,
                                      int keyFrameSize,
                                      float x,
                                      float y,
                                      float w,
                                      float h,
                                      float cbx,
                                      float cby,
                                      float cbw,
                                      float cbh,
                                      float percentX,
                                      float percentY) {

    list.add(
      new DrawMotionPath(selected, selected_key, key_pos_type, path, pathSize, keyFrameType, keyFramePos, keyFrameSize, x, y, w, h, cbx,
                         cby, cbw, cbh, percentX, percentY));
  }
}
