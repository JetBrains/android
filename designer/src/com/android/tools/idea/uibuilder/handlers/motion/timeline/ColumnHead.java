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

import com.android.tools.adtui.common.StudioColorsKt;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * Draw the the top of the column
 */
class ColumnHead extends TimeLine implements Gantt.ChartElement {
  JLayeredPane myLayeredPane;
  Chart mChart;

  ColumnHead(Chart chart) {
    super(chart);
    mChart = chart;
    update(Reason.CONSTRUCTION);
    mChart.add(this);
    setBorder(JBUI.Borders.customLine(StudioColorsKt.getBorder(), 0, 0, 1, 0));
  }

  int[] mXPoints = new int[5];
  int[] mYPoints = new int[5];
  Rectangle2D myRect;
  FontMetrics myFontMetrics;

  //Fletching: The plastic vanes or feathers on an arrow. ...
  private void drawFletching(Graphics g, int framePosition, int x, int y) {
    String string = Integer.toString(framePosition);

    if (myFontMetrics == null) {
      myFontMetrics = g.getFontMetrics();
      myRect = myFontMetrics.getMaxCharBounds(g);
    }

    double w = myRect.getWidth() / 2;
    double h = myRect.getHeight() * 1.5;
    x++;
    mXPoints[0] = x;
    mYPoints[0] = (int)(y + h);
    mXPoints[1] = (int)(x - w);
    mYPoints[1] = (int)(y + h / 2);
    mXPoints[2] = (int)(x - w);
    mYPoints[2] = y;
    mXPoints[3] = (int)(x + w);
    mYPoints[3] = y;
    mXPoints[4] = (int)(x + w);
    mYPoints[4] = (int)(y + h / 2);

      g.setColor(Chart.getColorForPosition(framePosition));

    g.fillPolygon(mXPoints, mYPoints, 5);
    g.setColor(getBackground());
    g.drawString(string, x - myFontMetrics.stringWidth(string) / 2, y + myFontMetrics.getAscent());
  }

  @Override
  protected void paintBorder(Graphics g) {
    //Do Nothing here: The cursor line needs to be painted over the border so the call to
    //paintBorder needs to be done after the cursor painting.
  }

  @Override
  protected void paintComponent(Graphics g) {
    g.setColor(mChart.myGridColor);
    setForeground(mChart.myGridColor);
    super.paintComponent(g);
    super.paintBorder(g);

    if (!Float.isNaN(mChart.getTimeCursorMs())) {
      int h = getHeight();
      float time = mChart.getTimeCursorMs();
      int x = mChart.getCursorPosition();
      g.setColor(mChart.getColorForPosition(mChart.getFramePosition()));
      g.fillRect(x, h - 15, 1, h);
      drawFletching(g, mChart.getFramePosition(), x, 0);
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
        d.height = Gantt.HEADER_HEIGHT;
        setPreferredSize(d);
        repaint();
        break;
      default:
    }
    super.setInserts(mChart.myChartLeftInset, mChart.myChartRightInset);
  }
}
