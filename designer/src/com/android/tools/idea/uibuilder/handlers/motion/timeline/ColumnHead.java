/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.timeline;

import javax.swing.*;
import java.awt.*;

/**
 * Draw the the top of the column
 */
class ColumnHead extends TimeLine implements Gantt.ChartElement {
  JLayeredPane myLayeredPane;
  int rowHeight = 20;
  Chart mChart;

  ColumnHead(Chart chart) {
    super(chart);
    mChart = chart;
    update(Reason.CONSTRUCTION);
    mChart.add(this);
  }

  int[] mXPoints = new int[5];
  int[] mYPoints = new int[5];

  //Fletching: The plastic vanes or feathers on an arrow. ...
  private void drawFletching(Graphics g, int x, int y) {
    x++;
    mXPoints[0] = x;
    mYPoints[0] = y + 10;
    mXPoints[1] = x - 5;
    mYPoints[1] = y + 6;
    mXPoints[2] = x - 5;
    mYPoints[2] = y;
    mXPoints[3] = x + 4;
    mYPoints[3] = y;
    mXPoints[4] = x + 4;
    mYPoints[4] = y + 6;
    g.fillPolygon(mXPoints, mYPoints, 5);
  }

  @Override
  protected void paintComponent(Graphics g) {
    g.setColor(mChart.myGridColor);
    setForeground(mChart.myGridColor);
    super.paintComponent(g);

    if (!Float.isNaN(mChart.getTimeCursorMs())) {
      int h = getHeight();
      float time = mChart.getTimeCursorMs();
      int x = mChart.getCursorPosition();
      g.setColor(mChart.myTimeCursorColor);
      drawFletching(g, x, 0);
      g.fillRect(x, h - 15, 1, h);
    }
  }

  @Override
  public void update(Reason reason) {
    setGraphWidth(mChart.getGraphWidth());
    switch (reason) {
      case CURSOR_POSITION_CHANGED: {
        if (mChart.myGantt.myGanttController != null) {
          mChart.myGantt.myGanttController.framePosition(mChart.getTimeCursorMs() / mChart.myAnimationTotalTimeMs);
          repaint();
        }
      }
      break;
      case RESIZE:
      case ZOOM:
        Dimension d = getPreferredSize();
        d.width = mChart.getGraphWidth();
        d.height = rowHeight;
        setPreferredSize(d);
        repaint();
        break;
        default:
    }
    super.setInserts(mChart.myChartLeftInset, mChart.myChartRightInset);
  }
}
