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
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Base class for components that should change their look over time.
 *
 * At a minimum, child classes should override {@link #updateData()} and {@link
 * #draw(Graphics2D)}, as well as pay attention to the field {@link #mFrameLength} as it controls
 * the behavior of timed animations.
 *
 */
public abstract class AnimatedComponent extends JComponent {

  protected long mLastRenderTime;

  protected boolean mDrawDebugInfo;

  protected final FontMetrics mDefaultFontMetrics;

  protected final AspectObserver myAspectObserver;

  /**
   * See {@link #setCursorSetter(BiFunction)}.
   */
  private BiFunction<Container, Cursor, Container> myCursorSetter;

  /**
   * Caches the container returned by {@link #myCursorSetter} so that {@link #setCursor(Cursor)}
   * doesn't execute {@link #myCursorSetter} in every call.
   */
  private Container myCursorSettingContainer;

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

  /**
   * Sets the cursor setter so that {@link #setCursor(Cursor)} has effect.
   *
   * Setting cursor on a Swing component that is not on the highest z-order hierarchy chain does
   * nothing. Call this with a setter that sets the cursor on the appropriate container and
   * {@link #setCursor(Cursor)} will call the setter when available.
   *
   * @param cursorSetter a lambda that finds the container of highest z-order to set the cursor on
   *                     and returns the found container, if any, so it can be cached.
   */
  public final void setCursorSetter(BiFunction<Container, Cursor, Container> cursorSetter) {
    this.myCursorSetter = cursorSetter;
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

  /**
   * Overrides default behavior, which does nothing when this component is not on the highest
   * z-roder hierarchy chain. This method now attempts to call the cursor setter first if set by
   * {@link #setCursorSetter(BiFunction)}.
   */
  @Override
  public void setCursor(Cursor cursor) {
    if (myCursorSettingContainer != null) {
      // The container to set cursor on is cached.
      myCursorSettingContainer.setCursor(cursor);
    }
    else if (myCursorSetter != null) {
      // Find the appropriate container and set the cursor on it.
      myCursorSettingContainer = myCursorSetter.apply(this, cursor);
    }
    else {
      super.setCursor(cursor);
    }
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
