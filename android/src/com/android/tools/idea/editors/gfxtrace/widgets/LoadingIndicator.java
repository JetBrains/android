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
package com.android.tools.idea.editors.gfxtrace.widgets;

import com.android.tools.idea.editors.gfxtrace.renderers.RenderUtils;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.util.ConcurrencyUtil;

import javax.swing.*;
import java.awt.*;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LoadingIndicator {
  private static final Icon[] LOADING_ICONS =
    {AllIcons.Process.Big.Step_1, AllIcons.Process.Big.Step_2, AllIcons.Process.Big.Step_3, AllIcons.Process.Big.Step_4,
      AllIcons.Process.Big.Step_5, AllIcons.Process.Big.Step_6, AllIcons.Process.Big.Step_7, AllIcons.Process.Big.Step_8,
      AllIcons.Process.Big.Step_9, AllIcons.Process.Big.Step_10, AllIcons.Process.Big.Step_11, AllIcons.Process.Big.Step_12};
  private static final long MS_PER_FRAME = 60;
  private static final long CYCLE_LENGTH = LOADING_ICONS.length * MS_PER_FRAME;
  private static final int MIN_SIZE = 3 * LOADING_ICONS[0].getIconWidth() / 2;

  private static final Set<Repaintable> componentsToRedraw = Sets.newIdentityHashSet();
  private static final ScheduledExecutorService tickerScheduler =
    ConcurrencyUtil.newSingleScheduledThreadExecutor("LoadingAnimation");

  public static void paint(Component c, Graphics g, int x, int y, int w, int h) {
    long elapsed = System.currentTimeMillis() % CYCLE_LENGTH;
    if (Math.min(w, h) < MIN_SIZE) {
      Graphics2D child = (Graphics2D)g.create(x, y, w, h);
      child.scale(0.5, 0.5);
      RenderUtils.drawIcon(c, child, LOADING_ICONS[(int)((elapsed * LOADING_ICONS.length) / CYCLE_LENGTH)], 0, 0, w * 2, h * 2);
      child.dispose();
    } else {
      RenderUtils.drawIcon(c, g, LOADING_ICONS[(int)((elapsed * LOADING_ICONS.length) / CYCLE_LENGTH)], x, y, w, h);
    }
  }

  public static Dimension getMinimumSize() {
    return new Dimension(LOADING_ICONS[0].getIconWidth(), LOADING_ICONS[0].getIconHeight());
  }

  public static void scheduleForRedraw(Repaintable c) {
    synchronized (componentsToRedraw) {
      if (componentsToRedraw.add(c) && componentsToRedraw.size() == 1) {
        tickerScheduler.schedule(new Runnable() {
          @Override
          public void run() {
            redrawAll();
          }
        }, MS_PER_FRAME, TimeUnit.MILLISECONDS);
      }
    }
  }

  private static void redrawAll() {
    Repaintable[] components;
    synchronized (componentsToRedraw) {
      components = componentsToRedraw.toArray(new Repaintable[componentsToRedraw.size()]);
      componentsToRedraw.clear();
    }
    for (Repaintable c : components) {
      c.repaint();
    }
  }
}
