/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.adtui;

import com.android.tools.adtui.common.AdtUIUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.LinkedList;
import java.util.List;

/**
 * Base class for components that should change their look over time.
 *
 * At a minimum, child classes should override {@link #updateData()} and {@link
 * #draw(Graphics2D)}, as well as pay attention to the field {@link #mFrameLength} as it controls
 * the behavior of timed animations.
 */
public abstract class AnimatedComponent extends JComponent implements Animatable {

  /**
   * The cached length of the last frame in seconds.
   */
  protected float mFrameLength;

  protected long mLastRenderTime;

  protected long mUpdateStartTime;

  protected long mUpdateEndTime;

  protected boolean mDrawDebugInfo;

  protected final FontMetrics mDefaultFontMetrics;

  @NotNull
  private final List<String> mDebugInfo;

  private int mDrawCount;

  private int mMultiDrawNumFrames;

  public AnimatedComponent() {
    mDebugInfo = new LinkedList<>();
    mDefaultFontMetrics = getFontMetrics(AdtUIUtils.DEFAULT_FONT);
  }

  public final boolean isDrawDebugInfo() {
    return mDrawDebugInfo;
  }

  public final void setDrawDebugInfo(boolean drawDebugInfo) {
    mDrawDebugInfo = drawDebugInfo;
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    mLastRenderTime = System.nanoTime();
    Graphics2D g2d = (Graphics2D)g.create();
    draw(g2d);
    mDrawCount++;

    if (mDrawDebugInfo) {
      doDebugDraw(g2d);
    }
    g2d.dispose();
  }

  protected final void addDebugInfo(String format, Object... values) {
    if (mDrawDebugInfo) {
      mDebugInfo.add(String.format(format, values));
    }
  }

  private void doDebugDraw(Graphics2D g) {
    debugDraw(g);

    addDebugInfo("Multi-draw Frame Count: %d", mMultiDrawNumFrames);
    addDebugInfo("Draw Count: %d", mDrawCount);
    addDebugInfo("Update time: %.2fms", (mUpdateEndTime - mUpdateStartTime) / 1000000.f);
    addDebugInfo("Render time: %.2fms", (System.nanoTime() - mLastRenderTime) / 1000000.f);
    addDebugInfo("FPS: %.2f", (1.0f / mFrameLength));
    g.setFont(AdtUIUtils.DEFAULT_FONT);
    g.setColor(AdtUIUtils.DEFAULT_FONT_COLOR);
    int i = 0;
    for (String s : mDebugInfo) {
      g.drawString(s, getSize().width - 150, getSize().height - 10 * i++ - 5);
    }
    mDebugInfo.clear();
  }

  /**
   * First step of the animation, this is where the data is read and the current animation values
   * are fixed.
   */
  protected abstract void updateData();

  /**
   * Renders the data constructed in the update phase to the given graphics context.
   */
  protected abstract void draw(Graphics2D g);

  /**
   * Draws visual debug information.
   */
  protected void debugDraw(Graphics2D g) {
  }

  protected static void drawArrow(Graphics2D g, float x, float y, float dx, float dy, float len,
                                  Color color) {
    Path2D.Float path = new Path2D.Float();
    path.moveTo(x, y);
    path.lineTo(x + dx * len, y + dy * len);
    path.lineTo(x + dx * (len - 10) + dy * 10, y + dy * (len - 10) - dx * 10);
    path.lineTo(x + dx * (len - 10) - dy * 10, y + dy * (len - 10) + dx * 10);
    g.setColor(color);
    g.draw(path);
  }

  protected static void drawMarker(Graphics2D g, float x, float y, Color color) {
    Path2D.Float path = new Path2D.Float();
    path.moveTo(x - 10, y);
    path.lineTo(x + 10, y);
    path.moveTo(x, y - 10);
    path.lineTo(x, y + 10);
    g.setColor(color);
    g.draw(path);
  }

  @Override
  public void animate(float frameLength) {
    if (mDrawCount > 1) {
      // draw is expected to be triggered once per component per animation cycle.
      // Otherwise, we are potentially wasting cycles repainting the same data. e.g. This can
      // happen if there are overlapping translucent components requesting repaints.
      //
      // Note - there are circumstances where multiple draws in a cycle is normal,
      // such as when the panel resizes, or anything that triggers repaint in the swing
      // rendering system. This code does not distinguish against those cases at the moment.
      mMultiDrawNumFrames++;
    }
    mDrawCount = 0;

    mFrameLength = frameLength;

    mUpdateStartTime = System.nanoTime();
    this.updateData();
    mUpdateEndTime = System.nanoTime();
  }

  @Override
  public void reset() {
    mMultiDrawNumFrames = 0;
  }
}
