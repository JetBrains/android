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

import com.android.tools.adtui.model.updater.Updatable;

import java.awt.*;
import java.awt.geom.Path2D;

/**
 * Base class for components that should change their look over time.
 *
 * At a minimum, child classes should override {@link #updateData()} and {@link
 * #draw(Graphics2D)}, as well as pay attention to the field {@link #mFrameLength} as it controls
 * the behavior of timed animations.
 *
 * Deprecated. See {@link AnimatedComponent}.
 */
@Deprecated
public abstract class LegacyAnimatedComponent extends AnimatedComponent implements Updatable {

  /**
   * The cached length of the last frame in seconds.
   */
  protected float mFrameLength;

  protected long mUpdateStartTime;

  protected long mUpdateEndTime;

  public LegacyAnimatedComponent() {
  }

  /**
   * First step of the animation, this is where the data is read and the current animation values
   * are fixed.
   */
  protected abstract void updateData();

  /**
   * Draws visual debug information.
   */
  @Override
  protected void debugDraw(Graphics2D g) {
    addDebugInfo("Update time: %.2fms", (mUpdateEndTime - mUpdateStartTime) / 1000000.f);
    addDebugInfo("Render time: %.2fms", (System.nanoTime() - mLastRenderTime) / 1000000.f);
    addDebugInfo("FPS: %.2f", (1.0f / mFrameLength));
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
  public void update(long elapsedNs) {
    mFrameLength = elapsedNs;

    mUpdateStartTime = System.nanoTime();
    this.updateData();
    mUpdateEndTime = System.nanoTime();
  }
}
