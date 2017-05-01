/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.AspectObserver;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Base class for components that should change their look over time.
 *
 * At a minimum, child classes should override {@link #updateData()} and {@link
 * #draw(Graphics2D)}, as well as pay attention to the field {@link #mFrameLength} as it controls
 * the behavior of timed animations.
 */
public abstract class AnimatedComponent extends JComponent {

  protected long mLastRenderTime;

  protected boolean mDrawDebugInfo;

  protected final FontMetrics mDefaultFontMetrics;

  protected final AspectObserver myAspectObserver;

  @NotNull
  private final List<String> mDebugInfo;

  private int mDrawCount;

  public AnimatedComponent() {
    mDebugInfo = new LinkedList<>();
    mDefaultFontMetrics = getFontMetrics(AdtUiUtils.DEFAULT_FONT);
    myAspectObserver = new AspectObserver();
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
    draw(g2d, getSize());
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

    addDebugInfo("Draw Count: %d", mDrawCount);

    g.setFont(AdtUiUtils.DEFAULT_FONT);
    g.setColor(AdtUiUtils.DEFAULT_FONT_COLOR);
    int i = 0;
    for (String s : mDebugInfo) {
      g.drawString(s, getSize().width - 150, getSize().height - 10 * i++ - 5);
    }
    mDebugInfo.clear();
  }

  /**
   * Renders the data constructed in the update phase to the given graphics context.
   */
  protected abstract void draw(Graphics2D g, Dimension size);

  protected void debugDraw(Graphics2D g) {}

  protected void opaqueRepaint() {
    // TODO: In theory swing should handle transparent repaints correctly, but
    // for now this works-around the issue of multiple repaints.
    Container c = this;
    while (c.getParent() != null && !c.isOpaque()) {
      c = c.getParent();
    }
    c.repaint();
  }
}
