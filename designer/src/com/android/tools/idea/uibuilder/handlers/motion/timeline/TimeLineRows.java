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

import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.scale.JBUIScale;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;

import static com.intellij.openapi.ui.VerticalFlowLayout.TOP;

/**
 * The make chart that displays the Keyframes in time
 */
public class TimeLineRows extends JPanel implements Gantt.ChartElement {
  private static final boolean DEBUG = false;
  Color myBackground = Chart.ourAvgBackground;
  Chart myChart;
  int[] myXPoints = new int[10]; // so that the memory is not allocated on paint
  int[] myYPoints = new int[10]; // so that the memory is not allocated on paint
  ArrayList<ViewRow> myViewRows = new ArrayList<>();
  public static int ourDiamondSize = JBUIScale.scale(10);
  private boolean myInStateChange;
  private boolean myDisplayInstructions = true;
  static int ourBaseSelected = (Chart.ourMySelectedLineColor.getRGB() & 0xFFFFFF)|0x77000000;
  static Color ourTransparent = new Color(ourBaseSelected & 0xFFFFFF, true);
  static Color ourLightColor = new Color(ourBaseSelected, true);
  ;

  // a super light spacer to fill the bottom of the table
  JComponent mySpacer = new JComponent() {
  };

  TimeLineRows(Chart chart) {
    VerticalFlowLayout layout = new VerticalFlowLayout(TOP, 0, 0, true, false);
    setLayout(layout);

    layout.setHorizontalFill(true);
    layout.setVerticalFill(false);

    myChart = chart;
    update(Reason.CONSTRUCTION);
    myChart.add(this);
  }

  @Override
  public void update(Reason reason) {
    if (DEBUG) {
      StackTraceElement[] st = new Throwable().getStackTrace();
      System.out.println("update ..... " + reason.name() + "   " + st[2].getFileName() + ":" + st[2].getLineNumber());
      for (int i = 3; i < 10; i++) {
        StackTraceElement element = st[i];
        System.out.println(" " + reason.name() + "   " + st[i].toString());
      }
      System.out.println(" " + reason.name() + "   " + st[5].getFileName() + ":" + st[5].getLineNumber());
    }
    if (reason == Reason.SELECTION_CHANGED) {
      return;
    }
    if (reason == Reason.CURSOR_POSITION_CHANGED) {
      repaint();
      return;
    }
    if (reason == Reason.RESIZE || reason == Reason.ZOOM || reason == Reason.ADDVIEW) {
      Dimension d = getPreferredSize();
      d.width = myChart.getGraphWidth();
      if (myChart.getmNumberOfViews() > 0) {
        Gantt.ViewElement v = myChart.myViewElements.get(myChart.getmNumberOfViews() - 1);
        d.height = v.myYStart + v.myHeight + 1;
      }

      if (reason != Reason.ADDVIEW && myViewRows.size() == myChart.myViewElements.size()) {
        int chartWidth = myChart.getGraphWidth();
        for (ViewRow row : myViewRows) {
          int pos = row.myRow;
          Gantt.ViewElement v = myChart.myViewElements.get(pos);
          Dimension dimension = row.getPreferredSize();
          if (dimension.width == chartWidth && dimension.height == v.myHeight) {
          }
          else {
            row.setPreferredSize(new Dimension(chartWidth, v.myHeight));
          }
        }
        revalidate();
        repaint();
      }
      else {
        // remove old rows
        for (ViewRow row : myViewRows) {
          remove(row);
        }
        myViewRows.clear();
        remove(mySpacer);

        int chartWidth = myChart.getGraphWidth();
        for (int i = 0; i < myChart.myViewElements.size(); i++) {
          Gantt.ViewElement v = myChart.myViewElements.get(i);
          ViewRow vr = new ViewRow(v, i);
          myViewRows.add(vr);
          vr.setPreferredSize(new Dimension(chartWidth, v.myHeight));
          add(vr);
        }
        myDisplayInstructions = false;
        if (myChart != null
            && myChart.myModel != null
            && (myChart.myModel.getStartConstraintSet().myConstraintViews.isEmpty() ||
                myChart.myModel.getEndConstraintSet().myConstraintViews.isEmpty())) {
          myDisplayInstructions = true;
        }
        revalidate();
        repaint();
      }
    }
  }

  @Override
  public void paint(Graphics g) {
    super.paint(g);
    if (!Float.isNaN(myChart.getTimeCursorMs())) {
      int x = myChart.getCursorPosition();
      g.setColor(myChart.getColorForPosition(myChart.getFramePosition()));
      g.fillRect(x, 0, 1, getHeight());
    }
    if (myDisplayInstructions) {
      g.setColor(Chart.myGridColor);
      int w = getWidth();
      int h = getHeight();
       String str = "Please add Constraints at 0% and 100%";
      Rectangle2D b = g.getFontMetrics().getStringBounds(str, g);
      g.drawString(str , (w-(int)b.getWidth())/2, (h-(int)b.getHeight())/2);
    }
  }

  private void paintBorder(Graphics g, int width, int height) {
    g.setColor(myBackground);
    g.fillRect(0, 0, width, height);
  }

  @Override
  protected void paintComponent(Graphics g) {
    int width = getWidth();
    int height = getHeight();
    paintBorder(g, width, height);
  }

  // ==============================LocationTable======================================= //
  static class LocationTable {
    int max = 10;
    int[] location = new int[max * 2];
    MotionSceneModel.KeyFrame[] keyFrames = new MotionSceneModel.KeyFrame[max];
    int addPoint = 0;

    public void clear() {
      addPoint = 0;
    }

    void add(int x, int y, MotionSceneModel.KeyFrame keyFrame) {
      if (max == addPoint) {
        max *= 2;
        location = Arrays.copyOf(location, max * 2);
        keyFrames = Arrays.copyOf(keyFrames, max);
      }
      location[addPoint * 2] = x;
      location[addPoint * 2 + 1] = y;
      keyFrames[addPoint] = keyFrame;
      addPoint++;
    }

    MotionSceneModel.KeyFrame find(int x, int y, int max) {
      int closeSq = Integer.MAX_VALUE;
      MotionSceneModel.KeyFrame keyFrame = null;
      int maxSq = max * max;
      for (int i = 0; i < keyFrames.length; i++) {
        int kf_x = location[i * 2];
        int kf_y = location[i * 2 + 1];
        int dx = Math.abs(kf_x - x);
        dx *= dx;
        if (dx > maxSq) continue;
        int dy = Math.abs(kf_y - y);
        dy *= dy;
        if (dy > maxSq) continue;
        if (closeSq > dy + dx) {
          keyFrame = keyFrames[i];
          closeSq = dy + dx;
        }
      }
      return keyFrame;
    }
  }

  /* =============================ViewRow===================================== */
  class ViewRow extends JPanel {
    final Gantt.ViewElement myViewElement;
    LocationTable myLocationTable = new LocationTable();
    int myRow;
    boolean myRowHasMarks = false;

    public ViewRow(Gantt.ViewElement v, int row) {
      myViewElement = v;
      myRow = row;
      MouseAdapter ml = new MouseAdapter() {

        @Override
        public void mouseClicked(MouseEvent e) {
          select(e.getX(), e.getY());
        }
      };
      addMouseMotionListener(ml);
      addMouseListener(ml);
    }

    private void select(int x, int y) {
      MotionSceneModel.KeyFrame keyFrame = myLocationTable.find(x, y, 20);
      if (keyFrame != myChart.mySelectedKeyFrame) {
        myChart.mySelectedKeyFrame = keyFrame;
        myChart.mySelectedKeyView = keyFrame.target;
        myChart.mySelection = Chart.Selection.KEY;
        myChart.update(Reason.SELECTION_CHANGED);
        if (keyFrame != null) {
          float position = keyFrame.getFramePosition() / 100f;
          myChart.setCursorPosition(position);
        }
      }
      else {
        int width = getWidth() - myChart.myChartLeftInset - myChart.myChartRightInset;
        int fp = ((x - myChart.myChartLeftInset) * 100) / width;
        if (fp < 0) {
          fp = 0;
        }
        else if (fp > 100) {
          fp = 100;
        }
        myChart.setCursorPosition(fp / 100f);
      }
    }

    public void drawBowtie(Graphics g, boolean selected, int x, int pos) {
      int half = ourDiamondSize / 2;
      myXPoints[0] = x;
      myYPoints[0] = pos + half - 1;
      myXPoints[1] = x + half;
      myYPoints[1] = pos;
      myXPoints[2] = x + half;
      myYPoints[2] = pos + ourDiamondSize;
      myXPoints[3] = x;
      myYPoints[3] = pos + half + 1;
      myXPoints[4] = x - half;
      myYPoints[4] = pos + ourDiamondSize;
      myXPoints[5] = x - half;
      myYPoints[5] = pos;
      if (selected) {
        g.drawPolygon(myXPoints, myYPoints, 6);

        GradientPaint paint = new GradientPaint(0, 0, ourTransparent, x, 0, ourLightColor);
        ((Graphics2D)g).setPaint(paint);
        myXPoints[0] = 0;
        myYPoints[0] = pos;
        myXPoints[1] = 0;
        myYPoints[1] = pos + ourDiamondSize;
        myXPoints[2] = x - half;
        myYPoints[2] = pos + ourDiamondSize;
        myXPoints[3] = x - half;
        myYPoints[3] = pos;
        g.fillPolygon(myXPoints, myYPoints, 4);
      }
      else {
        g.fillPolygon(myXPoints, myYPoints, 6);
      }
    }

    public void drawDiamond(Graphics g, boolean selected, int x, int pos) {
      int half = ourDiamondSize / 2;
      myXPoints[0] = x;
      myYPoints[0] = pos;
      myXPoints[1] = x + half;
      myYPoints[1] = pos + half;
      myXPoints[2] = x;
      myYPoints[2] = pos + ourDiamondSize;
      myXPoints[3] = x - half;
      myYPoints[3] = pos + half;
      if (selected) {
        g.drawPolygon(myXPoints, myYPoints, 4);
      }
      else {
        g.fillPolygon(myXPoints, myYPoints, 4);
      }
    }

    public void drawCircle(Graphics g, boolean selected, int x, int pos) {
      int half = ourDiamondSize / 2;
      if (selected) {
        g.drawRoundRect(x - half, pos, ourDiamondSize, ourDiamondSize, ourDiamondSize, ourDiamondSize);
      }
      else {
        g.fillRoundRect(x - half, pos, ourDiamondSize, ourDiamondSize, ourDiamondSize, ourDiamondSize);
      }
    }

    public void drawSquare(Graphics g, boolean selected, int x, int pos) {
      int half = ourDiamondSize / 2;
      myXPoints[0] = x;
      myYPoints[0] = pos;
      myXPoints[1] = x + half;
      myYPoints[1] = pos + half;
      myXPoints[2] = x;
      myYPoints[2] = pos + ourDiamondSize;
      myXPoints[3] = x - half;
      myYPoints[3] = pos + half;
      if (selected) {
        g.drawRoundRect(x - half, pos - half, ourDiamondSize, ourDiamondSize, 2, 2);
      }
      else {
        g.fillRoundRect(x - half, pos - half, ourDiamondSize, ourDiamondSize, 2, 2);
      }
    }

    @Override
    protected void paintComponent(Graphics g) {

      if (myRow % 2 == 0) {
        g.setColor(Chart.ourPrimaryPanelBackground);
      }
      else {
        g.setColor(Chart.ourSecondaryPanelBackground);
      }

      myLocationTable.clear();
      int panelWidth = getWidth();
      int panelHeight = getHeight();

      g.fillRect(0, 0, getWidth(), getHeight());
      if (myRow % 2 == 1) {
        g.setColor(Chart.ourPrimaryPanelBackground);
      }
      else {
        g.setColor(Chart.ourSecondaryPanelBackground);
      }
      // Draw vertical lines
      g.setColor(Chart.ourBorder);
      for (int i = 0; i < myChart.myXTickCount; i++) {
        int xLines = myChart.myXTicksPixels[i];
        g.fillRect(xLines, 0, 1, panelHeight);
      }
      final boolean DRAW_RECTS = false;
      // Draw bounding rectangles
      g.setColor(Color.GRAY);
      int y = 0;
      myRowHasMarks = false;
      if (DRAW_RECTS) {
        g.drawRect(0, y, panelWidth, myViewElement.myHeightView);
      }
      y += myViewElement.myHeightView;
      if (myViewElement.myHeightPosition > 0) {
        if (DRAW_RECTS) {
          g.drawRect(0, y, panelWidth, myViewElement.myHeightPosition);
        }
        y += myViewElement.myHeightPosition;
      }
      if (myViewElement.myHeightAttribute > 0) {
        if (DRAW_RECTS) {
          g.drawRect(0, y, panelWidth, myViewElement.myHeightAttribute);
        }
        y += myViewElement.myHeightAttribute;
      }
      if (myViewElement.myHeightCycle > 0) {
        if (DRAW_RECTS) {
          g.drawRect(0, y, panelWidth, myViewElement.myHeightCycle);
        }
        y += myViewElement.myHeightCycle;
      }
      Graphics2D g2d = (Graphics2D)g;
      Stroke stroke = g2d.getStroke();
      g2d.setStroke(new BasicStroke(2));
      int pos = 2;
      if (myChart != null) {
        int width = getWidth() - myChart.myChartLeftInset - myChart.myChartRightInset;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Chart.myUnSelectedLineColor);
        if (myChart.myModel != null
            && myChart.myModel.getStartConstraintSet() != null
            && myChart.myModel.getStartConstraintSet().myConstraintViews != null
            && myChart.myModel.getStartConstraintSet().myConstraintViews.get(myViewElement.myName) != null) {
          int xpos = myChart.myChartLeftInset + (int)((0 * width) / 100);
          drawSquare(g, false, xpos, pos + ourDiamondSize);
          myRowHasMarks = true;
        }
        if (myChart.myModel != null
            && myChart.myModel.getEndConstraintSet() != null &&
            myChart.myModel.getEndConstraintSet().myConstraintViews != null &&
            myChart.myModel.getEndConstraintSet().myConstraintViews.get(myViewElement.myName) != null) {
          int xpos = myChart.myChartLeftInset + (int)((100 * width) / 100);
          drawSquare(g, false, xpos, pos + ourDiamondSize);
          myRowHasMarks = true;
        }
        myRowHasMarks |= !myViewElement.mKeyFrames.myKeyAttributes.isEmpty();
        myRowHasMarks |= !myViewElement.mKeyFrames.myKeyCycles.isEmpty();
        myRowHasMarks |= !myViewElement.mKeyFrames.myKeyPositions.isEmpty();

        if (myViewElement.myHeightPosition > 0) {
          pos = myViewElement.myHeightView + (myViewElement.myHeightPosition - ourDiamondSize) / 2;
        }
        else {
          pos = (myViewElement.myHeightView - ourDiamondSize) / 2;
        }
        // put diamonds for positions
        for (MotionSceneModel.KeyPos key : myViewElement.mKeyFrames.myKeyPositions) {
          int x = myChart.myChartLeftInset + (int)((key.framePosition * width) / 100);
          if (key == myChart.mySelectedKeyFrame) {
            g.setColor(Chart.ourMySelectedLineColor);
            drawDiamond(g, true, x, pos);
            g.setColor(Chart.myUnSelectedLineColor);
          }
          else {
            drawDiamond(g, false, x, pos);
          }

          myLocationTable.add(x, pos, key);
        }
        int delta_y = (getHeight() - ourDiamondSize) / 4;

        if (myViewElement.myHeightAttribute > 0) {
          pos = myViewElement.myHeightView + myViewElement.myHeightPosition;
          pos += (myViewElement.myHeightAttribute - ourDiamondSize) / 2;
        }
        else {
          pos += delta_y;
        }
        // put circles for attributes
        for (MotionSceneModel.KeyAttributes key : myViewElement.mKeyFrames.myKeyAttributes) {
          int x = myChart.myChartLeftInset + (int)((key.framePosition * width) / 100);

          if (key == myChart.mySelectedKeyFrame) {
            g.setColor(Chart.ourMySelectedLineColor);
            drawCircle(g, true, x, pos);
            g.setColor(Chart.myUnSelectedLineColor);
          }
          else {
            drawCircle(g, false, x, pos);
          }
          myLocationTable.add(x, pos, key);
        }
        if (myViewElement.myHeightCycle > 0) {
          pos = myViewElement.myHeightView + myViewElement.myHeightPosition + myViewElement.myHeightAttribute;
          pos += (myViewElement.myHeightCycle - ourDiamondSize) / 2;
        }
        else {
          pos += delta_y;
        }
        // put diamonds for cycles
        for (MotionSceneModel.KeyCycle key : myViewElement.mKeyFrames.myKeyCycles) {
          int x = myChart.myChartLeftInset + (int)((key.framePosition * width) / 100);
          if (key == myChart.mySelectedKeyFrame) {
            g.setColor(Chart.ourMySelectedLineColor);
            drawBowtie(g, true, x, pos);
            g.setColor(Chart.myUnSelectedLineColor);
          }
          else {
            drawBowtie(g, false, x, pos);
          }
          myLocationTable.add(x, pos, key);
        }

        int x = myChart.getCursorPosition();
        g2d.setStroke(stroke);
        g.setColor(myChart.getColorForPosition(myChart.getFramePosition()));
        g.fillRect(x, 0, 1, panelHeight);
      }
    }
  }
}
