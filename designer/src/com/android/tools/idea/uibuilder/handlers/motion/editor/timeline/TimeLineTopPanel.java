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
package com.android.tools.idea.uibuilder.handlers.motion.editor.timeline;

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEUI;

import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * Draws the top row that has the tick marks
 */
public class TimeLineTopPanel extends JPanel {

  TickMarkCalculator mTickMarkCalculator = new TickMarkCalculator();
  int mXTickCount;
  TimelineStructure mTimelineStructure;

  TimeLineTopPanel(TimelineStructure timelineStructure) {
    setPreferredSize(new Dimension(MEUI.scale(100), MEUI.ourHeaderHeight));
    setBackground(MEUI.ourSecondaryPanelBackground);
    mTimelineStructure = timelineStructure;
    mTimelineStructure.mTimeLineInsetLeft = mTickMarkCalculator.ins_left;
    mTimelineStructure.mTimeLineInsetRight = mTickMarkCalculator.ins_right;
    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        resize();
      }
    });
  }

  @Override
  public void updateUI() {
    super.updateUI();
    if (mTickMarkCalculator !=  null) {
      setPreferredSize(new Dimension(MEUI.scale(100), MEUI.ourHeaderHeight));
      resize();
    }
  }

  private void resize() {
    int w = getWidth();
    int h = getHeight();
    mTickMarkCalculator.calcRangeTicks(w);
    int n = mTickMarkCalculator.getCount();

    if (mTimelineStructure.myXTicksPixels.length != n) {
      mTimelineStructure.myXTicksPixels = new int[n];
    }
    mTickMarkCalculator.calcTicks(mTimelineStructure.myXTicksPixels);
    int old = mTimelineStructure.mTimeLineWidth;
    mTimelineStructure.fireWidthChanged(old, mTimelineStructure.mTimeLineWidth = w);
    getParent().repaint();
  }

  public void setRange(float min, float max) {
    mTickMarkCalculator.setRange(min, max);
    mTimelineStructure.mTimeLineMinValue = min;
    mTimelineStructure.mTimeLineMaxValue = max;
  }

  @Override
  protected void paintComponent(Graphics g) {
    int w = getWidth();
    int h = getHeight();
    if (w == 0) {
      return;
    }
    Graphics2D g2d = (Graphics2D) g;
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
      RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(getBackground());
    g.fillRect(0, 0, w, h);
    g.setColor(MEUI.ourBorder);
    g.fillRect(0,0, 1, h);
    g.fillRect(0, h-1, w, 1);
    g.setColor(MEUI.myGridColor);
    int n = mTickMarkCalculator.getCount();
    if (mTimelineStructure.myXTicksPixels.length > 0) {
      mXTickCount = mTickMarkCalculator.paint(g2d, w, h, mTimelineStructure.myXTicksPixels);
    }
  }
}
