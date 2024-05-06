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
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;

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
  private int myTitleHeight = MEUI.scale(20);
  private int myRowHeight = MEUI.scale(20);
  private int myGraphHeight = MEUI.scale(50);
  boolean mShowTitle = false;

  int[] mXPoint = new int[4];
  int[] mYPoint = new int[4];
  private boolean mHasGraph = true;
  private boolean mGraphOpen = false;
  GraphRender mGraph = new GraphRender();

  @Override
  public void updateUI() {
    super.updateUI();

    myTitleHeight = MEUI.scale(20);
    myRowHeight = MEUI.scale(20);
    myGraphHeight = MEUI.scale(50);

    if (mShowTitle) {
      setSize(MEUI.size(100, 40));
      setPreferredSize(MEUI.size(100, getNewHeight()));
    } else {
      setSize(MEUI.size(100, 20));
      setPreferredSize(MEUI.size(100, getNewHeight()));
    }
  }

  TimeLineRow(TimelineStructure timelineStructure) {
    setPreferredSize(MEUI.size(100, 20));
    mTimelineStructure = timelineStructure;
  }

  @Override
  public void paint(Graphics g) {
    int w = getWidth();
    int h = getHeight();
    int titley = MEUI.scale(20);
    Component parent = getParent();
    mCellHasFocus = parent != null && parent.hasFocus();

    //  Paint the title (if a title level)
    if (mShowTitle) {
      g.setColor(MEUI.ourPrimaryPanelBackground);
      g.fillRect(0, 0, w, titley);
      if (mSelected) {
        g.setColor(mCellHasFocus ? MEUI.ourMySelectedAndFocusedLineColor : MEUI.ourMySelectedLineColor);
        g.fillRect(0, titley, MEUI.ourLeftColumnWidth, myRowHeight);
        g.setColor(MEUI.ourSecondaryPanelBackground);
        g.fillRect(MEUI.ourLeftColumnWidth, titley, w - MEUI.ourLeftColumnWidth, myRowHeight);
        g.fillRect(0, titley + myRowHeight, w, h - (titley + myRowHeight));
      } else {
        g.setColor(MEUI.ourSecondaryPanelBackground);

        g.fillRect(0, titley, w, myRowHeight);
      }

    } else {
      if (mSelected) {
        g.setColor(mCellHasFocus ? MEUI.ourMySelectedAndFocusedLineColor : MEUI.ourMySelectedLineColor);
        g.fillRect(0, 0, MEUI.ourLeftColumnWidth, myRowHeight);
        g.setColor(MEUI.ourSecondaryPanelBackground);
        g.fillRect(MEUI.ourLeftColumnWidth, 0, w - MEUI.ourLeftColumnWidth, myRowHeight);
        g.fillRect(0, myRowHeight, w, h - myRowHeight);
      } else {
        g.setColor(MEUI.ourSecondaryPanelBackground);
        g.fillRect(0, 0, w, h);
      }
    }

    // draw line on the divider
    g.setColor(MEUI.ourBorder);
    g.fillRect(MEUI.ourLeftColumnWidth, 0, 1, h);

    FontMetrics metrics = g.getFontMetrics();
    int sx = MEUI.scale(2);
    int fontAscent = metrics.getAscent();
    int sy = 0;
    if (mShowTitle) {
      g.setColor(mSelected && mCellHasFocus ? MEUI.ourSelectedAndFocusedTextColor : MEUI.ourTextColor);
      g.setClip(0,0, MEUI.ourLeftColumnWidth, getHeight());
      g.drawString(mRow.mRef + ":" + mRow.mName, sx, fontAscent);
      g.setClip(null);
      sy += myTitleHeight;
    }
    ((Graphics2D) g)
      .setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    if (mHasGraph) {
      drawArrow(g, sy);
    }
    sy += fontAscent;
    g.setColor(mSelected && mCellHasFocus ? MEUI.ourSelectedAndFocusedTextColor : MEUI.ourTextColor);
    sx = MEUI.scale(22);
    int rowTypeEnd  = 0;
    if (mRow.mType != null) {
      g.setClip(0,0, MEUI.ourLeftColumnWidth, getHeight());
      g.drawString(mRow.mType, sx, sy);
      g.setClip(null);
      rowTypeEnd  =  sx + metrics.stringWidth(mRow.mType);
    }
    if (mRow.mKeyProp != null) {
      sx = MEUI.ourLeftColumnWidth - metrics.stringWidth(mRow.mKeyProp) - 3;
      if (sx < rowTypeEnd){
        g.setClip(0,0,MEUI.ourLeftColumnWidth ,getHeight());
        g.drawString(mRow.mKeyProp, rowTypeEnd, sy);
        g.setClip(null);
      } else {
        g.drawString(mRow.mKeyProp, sx, sy);
      }
    }
    sy -= metrics.getAscent();

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

      int ypos = sy + myRowHeight / 2;
      int x = mTimelineStructure.floatToPosition(pos) + MEUI.ourLeftColumnWidth;
      drawDiamond(g, x, ypos);
      if (keyFrame == mSelectedKeyFrame && mSelected) {
        g.setColor(mCellHasFocus ? MEUI.ourSelectedAndFocusedTextColor : MEUI.ourMySelectedTextColor);
      }
    }
    if (mHasGraph && mGraphOpen) {
      int gy = myRowHeight + ((mShowTitle) ? myTitleHeight : 0);
      mGraph.draw(g, mTimelineStructure, MEUI.ourLeftColumnWidth, gy, w - MEUI.ourLeftColumnWidth, myGraphHeight);
    }
  }

  public void drawArrow(Graphics g, int y) {

    int x = 2;
    int size = myRowHeight / 3;
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
    drawTicks(g, mTimelineStructure, h, 0);
  }

  public static void drawTicks(Graphics g, TimelineStructure mTimelineStructure, int h, int y) {
    for (int i = 0; i < mTimelineStructure.myXTicksPixels.length; i++) {
      int x = mTimelineStructure.myXTicksPixels[i] + MEUI.ourLeftColumnWidth;
      g.fillRect(x, y, 1, h);
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
    updateUI();
  }

  private int getNewHeight() {
    int ret = myRowHeight;
    if (mShowTitle) {
      ret += myTitleHeight;
    }
    if (mGraphOpen && mHasGraph) {
      ret += myGraphHeight;
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
