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
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MeModel;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Debug;

import javax.swing.JPanel;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * This represents a single row of a timeline
 * Its may contain a Header, a KeyFrame line, and a Graph
 */
public class TimeLineRow extends JPanel {

  private static final boolean DEBUG = false;
  TimeLineRowData mRow;
  int mRowNumber = 0;
  private boolean mSelected;
  private boolean mCellHasFocus;
  TimelineStructure mTimelineStructure;
  MTag mSelectedKeyFrame;
  private static final int TITLE_HEIGHT = MEUI.scale(20);
  private static final int ROW_HEIGHT = MEUI.scale(20);
  private static final int GRAPH_HEIGHT = MEUI.scale(50);
  boolean mShowTitle = false;
  static final int iconOffset = MEUI.scale(10);
  static final int refOffset = MEUI.scale(30);
  static final int nameOffset = MEUI.scale(50);
  static final int typeOffset = MEUI.scale(90);
  int[] mXPoint = new int[4];
  int[] mYPoint = new int[4];
  private boolean mHasGraph = true;
  private boolean mGraphOpen = false;
  GraphRender mGraph = new GraphRender();

  TimeLineRow(TimelineStructure timelineStructure) {
    setPreferredSize(MEUI.size(100, 20));
    mTimelineStructure = timelineStructure;
  }

  @Override
  public void paint(Graphics g) {
    int w = getWidth();
    int h = getHeight();
    int titley = MEUI.scale(20);

    //  Paint the title (if a title level)
    if (mShowTitle) {
      g.setColor(MEUI.ourPrimaryPanelBackground);
      g.fillRect(0, 0, w, titley);
      if (mSelected) {
        g.setColor(MEUI.ourMySelectedLineColor);
        g.fillRect(0, titley, w, ROW_HEIGHT);
        g.setColor(MEUI.ourSecondaryPanelBackground);
        g.fillRect(0, titley + ROW_HEIGHT, w, h - (titley + ROW_HEIGHT));
      } else {
        g.setColor(MEUI.ourSecondaryPanelBackground);

        g.fillRect(0, titley, w, ROW_HEIGHT);
      }

    } else {
      if (mSelected) {
        g.setColor(MEUI.ourMySelectedLineColor);
        g.fillRect(0, 0, w, ROW_HEIGHT);
        g.setColor(MEUI.ourSecondaryPanelBackground);
        g.fillRect(0, ROW_HEIGHT, w, h - ROW_HEIGHT);
      } else {
        g.setColor(MEUI.ourSecondaryPanelBackground);
        g.fillRect(0, 0, w, h);
      }
    }

    // draw line on the divider
    g.setColor(MEUI.ourBorder);
    g.fillRect(MEUI.ourLeftColumnWidth, 0, 1, h);

    // g.drawString(mRow.mKey, 2, g.getFontMetrics().getAscent());
    FontMetrics metrics = g.getFontMetrics();

    int refWidth = Math.max(metrics.stringWidth("Id:"), metrics.stringWidth("Tg:"));
    int sx = MEUI.scale(2);
    int fontAscent = metrics.getAscent();
    int sy = 0;
    if (mShowTitle) {
      g.setColor(MEUI.ourTextColor);
      g.drawString(mRow.mRef + ":" + mRow.mName, sx, fontAscent);
      sy += TITLE_HEIGHT;
    }
    ((Graphics2D) g)
      .setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    if (mHasGraph) {
      drawArrow(g, sy);
    }
    sy += fontAscent;
    g.setColor((mSelected) ? MEUI.ourMySelectedTextColor : MEUI.ourTextColor);
    sx = MEUI.scale(22);
    if (mRow.mType != null) {
      g.drawString(mRow.mType, sx, sy);
    }
    if (mRow.mKeyProp != null) {
      sx = MEUI.ourLeftColumnWidth - metrics.stringWidth(mRow.mKeyProp) - 3;
      g.drawString(mRow.mKeyProp, sx, sy);
    }
    sy -= metrics.getAscent();
    int rad = 4;
    int diameter = rad * 2;

    for (MTag keyFrame : mRow.mKeyFrames) {
      String posString = keyFrame.getAttributeValue("framePosition");
      if (posString == null) {
        continue;
      }

      int pos = Integer.parseInt(posString);

      if (keyFrame == mSelectedKeyFrame && mSelected) {
        g.setColor(MEUI.ourMySelectedKeyColor);
      } else {
        g.setColor(MEUI.ourTextColor);
        if (DEBUG) {
          if (mSelectedKeyFrame != null && pos == Integer.parseInt(mSelectedKeyFrame.getAttributeValue("framePosition"))) {
            Debug.log(mSelectedKeyFrame.toFormalXmlString(">"));
            Debug.log(keyFrame.toFormalXmlString("<"));
          }

        }
      }

      int ypos = sy + ROW_HEIGHT / 2;
      int x = mTimelineStructure.floatToPosition(pos) + MEUI.ourLeftColumnWidth;
      drawDiamond(g, x, ypos);
      if (keyFrame == mSelectedKeyFrame && mSelected) {
        g.setColor(MEUI.ourTextColor);
      }
    }
    if (mHasGraph && mGraphOpen) {
      int gy = ROW_HEIGHT + ((mShowTitle) ? TITLE_HEIGHT : 0);
      mGraph.draw(g, mTimelineStructure, MEUI.ourLeftColumnWidth, gy, w - MEUI.ourLeftColumnWidth, GRAPH_HEIGHT);
    }
    drawTicks(g, mTimelineStructure, h);
  }

  public void drawArrow(Graphics g, int y) {

    int x = 2;
    int size = ROW_HEIGHT / 3;
    y += size;
    if (mGraphOpen) {
      mXPoint[0] = x;
      mXPoint[1] = x + size;
      mXPoint[2] = x + size / 2;

      mYPoint[0] = y;
      mYPoint[1] = y;
      mYPoint[2] = y + size;
    } else {
      mXPoint[0] = x;
      mXPoint[1] = x;
      mXPoint[2] = x + size;

      mYPoint[0] = y;
      mYPoint[1] = y + size;
      mYPoint[2] = y + size / 2;
    }
    g.fillPolygon(mXPoint, mYPoint, 3);
  }

  public static void drawTicks(Graphics g, TimelineStructure mTimelineStructure, int h) {
    for (int i = 0; i < mTimelineStructure.myXTicksPixels.length; i++) {
      int x = mTimelineStructure.myXTicksPixels[i] + MEUI.ourLeftColumnWidth;
      g.fillRect(x, 0, 1, h);
    }
  }

  private void drawDiamond(Graphics g, int x, int y) {
    int size = 4;
    mXPoint[0] = x;
    mXPoint[1] = x + size;
    mXPoint[2] = x;
    mXPoint[3] = x - size;
    mYPoint[0] = y - size;
    mYPoint[1] = y;
    mYPoint[2] = y + size;
    mYPoint[3] = y;
    g.fillPolygon(mXPoint, mYPoint, 4);
  }

  void setRowData(MeModel model, TimeLineRowData row, int row_number, boolean selection, boolean cellHasFocus, MTag selectedKeyFrame, boolean showTitle) {
    mRow = row;
    mRowNumber = row_number;
    mSelected = selection;
    mCellHasFocus = cellHasFocus;
    mSelectedKeyFrame = selectedKeyFrame;
    mShowTitle = showTitle;
    mHasGraph = mGraph.setUp(model, row);
    if (mShowTitle) {
      setSize(MEUI.size(100, 40));
      setPreferredSize(MEUI.size(100, getNewHeight()));
      return;
    }
    setSize(MEUI.size(100, 20));
    setPreferredSize(MEUI.size(100, getNewHeight()));
    revalidate();

  }

  private int getNewHeight() {
    int ret = ROW_HEIGHT;
    if (mShowTitle) {
      ret += TITLE_HEIGHT;
    }
    if (mGraphOpen & mHasGraph) {
      ret += GRAPH_HEIGHT;
    }
    return ret;
  }

  public void setSelectedKeyFrame(MTag selectedKeyFrame) {
    mSelectedKeyFrame = selectedKeyFrame;
    repaint();
  }

  public void setSelected(boolean b) {
    mSelected = b;
  }

  public void toggleGraph() {
    if (!mHasGraph) {
      return;
    }
    mGraphOpen = !mGraphOpen;
    setPreferredSize(MEUI.size(100, getNewHeight()));
    revalidate();
  }
}
