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

import static com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.KeyTypeAttribute;
import static com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.KeyTypeCycle;
import static com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.KeyTypePosition;
import static com.intellij.openapi.ui.VerticalFlowLayout.TOP;

import com.android.tools.idea.uibuilder.handlers.motion.Debug;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;

/**
 * The make chart that displays the Keyframes in time
 */
public class TimeLineRows extends JPanel implements Gantt.ChartElement {
  private static final boolean DEBUG = false;
  private static final boolean SIMPLE = false;
  Color myBackground = Chart.ourAvgBackground;
  Chart myChart;
  int[] myXPoints = new int[10]; // so that the memory is not allocated on paint
  int[] myYPoints = new int[10]; // so that the memory is not allocated on paint
  ArrayList<ViewRow> myViewRows = new ArrayList<>();
  public static int ourDiamondSize = JBUI.scale(10);
  private boolean myInStateChange;
  private boolean myDisplayInstructions = true;
  static int ourBaseSelected = (Chart.ourMySelectedLineColor.getRGB() & 0xFFFFFF) | 0x77000000;
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
      repaint();
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
            && (myChart.myModel.getStartConstraintSet() == null ||
                myChart.myModel.getEndConstraintSet() == null ||
                myChart.myModel.getStartConstraintSet().myConstraintViews.isEmpty() ||
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
      g.drawString(str, (w - (int)b.getWidth()) / 2, (h - (int)b.getHeight()) / 2);
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

  // ==============================KeyFrameTable======================================= //
  static class BaseTable<T extends MotionSceneModel.TagAccess> {
    int max = 5;
    int maxSq = max * max;

    int[] location = new int[max * 2];
    ArrayList<T> keyFrames = new ArrayList<>(max);
    int addPoint = 0;

    public void clear() {
      addPoint = 0;
      keyFrames.clear();
    }

    void add(int x, int y, T keyFrame) {
      if (max == addPoint) {
        max *= 2;
        location = Arrays.copyOf(location, max * 2);
      }
      location[addPoint * 2] = x;
      location[addPoint * 2 + 1] = y;
      keyFrames.add(addPoint, keyFrame);
      addPoint++;
    }

    T find(int x, int y, int max, SmartPsiElementPointer<XmlTag> previousKeyFrameTag) {
      int closeSq = Integer.MAX_VALUE;
      List<T> possibleFrames = new ArrayList<>();

      for (int i = 0; i < keyFrames.size(); i++) {
        int kf_x = location[i * 2];
        int kf_y = location[i * 2 + 1];
        int dx = Math.abs(kf_x - x);
        dx *= dx;
        if (dx > maxSq) continue;
        int dy = Math.abs(kf_y - y);
        dy *= dy;
        if (dy > maxSq) continue;
        if (closeSq >= dy + dx && !possibleFrames.contains(keyFrames.get(i))) {
          possibleFrames.add(keyFrames.get(i));
          closeSq = dy + dx;
        }
      }
      if (possibleFrames.isEmpty()) {
        return null;
      }
      int foundIndex = -1;
      for (int i = 0; i < possibleFrames.size(); i++) {
        if (possibleFrames.get(i).getTag() == previousKeyFrameTag) {
          foundIndex = i;
          break;
        }
      }
      int nextIndex = (foundIndex + 1) % possibleFrames.size();
      return possibleFrames.get(nextIndex);
    }
  }

  // ==============================KeyFrameTable======================================= //
  static class KeyFrameTable extends BaseTable<MotionSceneModel.KeyFrame> {
  }

  // ==============================KeyFrameTable======================================= //
  static class ConstraintTable extends BaseTable<MotionSceneModel.ConstraintView> {
  }

  static class CreateConstraint implements MotionSceneModel.TagAccess {
    String myName;
    boolean myStart;

    static CreateConstraint get(String name, boolean start) {
      return new CreateConstraint(name, start);
    }

    private CreateConstraint(String name, boolean start) {
      myName = name;
      myStart = start;
    }

    String getName() { return myName;}

    boolean isStart() {return myStart; }

    @Override
    public SmartPsiElementPointer<XmlTag> getTag() {
      return null;
    }
  }

  static class LayoutsSelectionTable extends BaseTable<CreateConstraint> {
    ArrayList<CreateConstraint> pool = new ArrayList<>();
    ArrayList<CreateConstraint> inUse = new ArrayList<>();

    @Override
    public void clear() {
      super.clear();
      pool.addAll(inUse);
      inUse.clear();
    }

    public CreateConstraint get(String name, boolean isStart) {
      CreateConstraint ret = pool.remove(0);
      ret.myName = name;
      ret.myStart = isStart;
      return ret;
    }

    void add(int x, int y, String name) {
      CreateConstraint create = (pool.isEmpty()) ? new CreateConstraint(name, false) : get(name, false);
      inUse.add(create);
      super.add(x, y, create);
    }
  }

  static class CreateConstraintTable extends BaseTable<CreateConstraint> {
    ArrayList<CreateConstraint> pool = new ArrayList<>();
    ArrayList<CreateConstraint> inUse = new ArrayList<>();

    @Override
    public void clear() {
      super.clear();
      pool.addAll(inUse);
      inUse.clear();
    }

    public CreateConstraint get(String name, boolean isStart) {
      CreateConstraint ret = pool.remove(0);
      ret.myName = name;
      ret.myStart = isStart;
      return ret;
    }

    void add(int x, int y, String name, boolean isStart) {
      CreateConstraint create = (pool.isEmpty()) ? new CreateConstraint(name, isStart) : get(name, isStart);
      inUse.add(create);
      super.add(x, y, create);
    }
  }

  /* =============================ViewRow===================================== */
  class ViewRow extends JPanel {
    final Gantt.ViewElement myViewElement;
    KeyFrameTable myKeyFrameTable = new KeyFrameTable();
    ConstraintTable myConstraintTable = new ConstraintTable();
    CreateConstraintTable myCreateConstraintTable = new CreateConstraintTable();
    LayoutsSelectionTable myLayoutsSelectionTable = new LayoutsSelectionTable();

    int myRow;
    boolean myRowHasMarks = false;

    public ViewRow(Gantt.ViewElement v, int row) {
      myViewElement = v;
      myRow = row;
      MouseAdapter ml = new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          if (e.isPopupTrigger()) {
            pop(e);
          }
        }

        @Override
        public void mouseClicked(MouseEvent e) {
          mouseClick(e);
        }
      };
      addMouseMotionListener(ml);
      addMouseListener(ml);
    }

    public void pop(MouseEvent e) {
      ArrayList<String> list = new ArrayList<>();
      final MotionSceneModel.KeyFrame keyFrame = myKeyFrameTable.find(e.getX(), e.getY(), 20, myChart.mySelectedKeyFrameTag);
      final int location = e.getX();
      int tmpPos = -1;
      int tmpAtt = -1;
      int tmpCyc = -1;
      if (keyFrame == null) {
        tmpPos = list.size();
        list.add("Create KeyPosition");
        tmpAtt = list.size();
        list.add("Create KeyAttribute");
        tmpCyc = list.size();
        list.add("Create KeyCycle");
      }
      else {
        switch (keyFrame.mType) {
          case KeyTypePosition:
            tmpPos = list.size();
            list.add("Create KeyAttribute");
            tmpCyc = list.size();
            list.add("Create KeyCycle");
            break;
          case KeyTypeAttribute:
            tmpPos = list.size();
            list.add("Create KeyPosition");
            tmpCyc = list.size();
            list.add("Create Cycle");
            break;
          case KeyTypeCycle:
            tmpPos = list.size();
            list.add("Create KeyPosition");
            tmpAtt = list.size();
            list.add("Create KeyAttribute");
            break;
        }
      }

      final int keyPositionIndex = tmpPos;
      final int keyCycleIndex = tmpCyc;
      final int keyAttributeIndex = tmpAtt;

      final int copyIndex = (keyFrame != null) ? list.size() : -1;
      final int cutIndex = (keyFrame != null) ? list.size() + 1 : -1;
      if (keyFrame != null) {
        list.add("copy");
        list.add("cut");
      }
      final int pasteIndex = (myChart.myCopyBuffer != null) ? list.size() : -1;
      if (myChart.myCopyBuffer != null) {
        list.add("paste");
      }
      final int deleteIndex = (keyFrame != null) ? list.size() : -1;
      if (keyFrame != null) {
        list.add("delete");
      }

      boolean noStartConstraints =
        myChart.myModel.getStartConstraintSet() == null || myChart.myModel.getStartConstraintSet().myConstraintViews.isEmpty();
      boolean noEndConstraints =
        myChart.myModel.getEndConstraintSet() == null || myChart.myModel.getEndConstraintSet().myConstraintViews.isEmpty();

      final JList<String> displayedList = new JBList<String>(list);
      JBPopupListener listener = new JBPopupListener.Adapter() {

        @Override
        public void onClosed(LightweightWindowEvent event) {
          JBPopup popup = event.asPopup();
          System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>.. " + event.isOk());
          if (!event.isOk()) {
            return;
          }
          int index = displayedList.getSelectedIndex();
          System.out.println("displayedList.getSelectedIndex() " + index);

          if (index == pasteIndex) {
            paste(location);
          }
          else if (index == deleteIndex) {
            delete(keyFrame);
          }
          else if (index == cutIndex) {
            copy(keyFrame);
            delete(keyFrame);
          }
          else if (index == copyIndex) {
            copy(keyFrame);
          }
          else if (index == keyPositionIndex) {
            createKeyFrame(KeyTypePosition, location);
          }
          else if (index == keyAttributeIndex) {
            createKeyFrame(KeyTypeAttribute, location);
          }
          else if (index == keyCycleIndex) {
            createKeyFrame(KeyTypeCycle, location);
          }
        }
      };
      JBPopup popup =
        JBPopupFactory.getInstance()
          .createListPopupBuilder(displayedList)
          .setTitle("KeyFrame")
          .addListener(listener).createPopup();
      JComponent component = ((JComponent)e.getSource());

      popup.show(new RelativePoint(component, new Point(0, 0)));
    }

    /**
     * @param key
     */
    public void copy(MotionSceneModel.KeyFrame key) {
      myChart.myCopyBuffer = key.cloneKeyFrame();
    }

    /**
     * @param key
     */
    public void delete(MotionSceneModel.KeyFrame key) {
      key.deleteTag("delete tag");
    }

    /**
     * @param location
     */
    public void paste(int location) {
      MotionSceneModel.KeyFrame key = myChart.myCopyBuffer.cloneKeyFrame();
      int width = getWidth() - myChart.myChartLeftInset - myChart.myChartRightInset;
      int framePosition = ((location - myChart.myChartLeftInset) * 100) / width;
      myViewElement.mKeyFrames.myModel.createKeyFrame(key.mType, framePosition, myViewElement.myName, key);
    }

    /**
     * @param frameType
     */
    void createKeyFrame(String frameType, int location) {

      String name = myViewElement.myName;
      MotionSceneModel model = myViewElement.mKeyFrames.myModel;
      int width = getWidth() - myChart.myChartLeftInset - myChart.myChartRightInset;
      int fpos = ((location - myChart.myChartLeftInset) * 100) / width;
      if (fpos == 0) {
        fpos = 1;
      }
      else if (fpos == 100) {
        fpos = 99;
      }

      myChart.mySelectedKeyFrameTag = myViewElement.mKeyFrames.myModel.createKeyFrame(frameType, fpos, name);
      myChart.mySelectedKeyView = name;
    }

    private void mouseClick(MouseEvent e) {

      select(e.getX(), e.getY());
    }

    private void select(int x, int y) {
      MotionSceneModel.KeyFrame keyFrame = myKeyFrameTable.find(x, y, 20, myChart.mySelectedKeyFrameTag);
      MotionSceneModel.ConstraintView constraint = myConstraintTable.find(x, y, 20, myChart.mySelectedConstraintTag);
      CreateConstraint create = myCreateConstraintTable.find(x, y, 20, myChart.mySelectedConstraintTag);
      CreateConstraint layout = myLayoutsSelectionTable.find(x, y, 20, myChart.mySelectedConstraintTag);
      int width = getWidth() - myChart.myChartLeftInset - myChart.myChartRightInset;
      int framePosition = ((x - myChart.myChartLeftInset) * 100) / width;
      framePosition = Math.max(0, Math.min(100, framePosition));

      if (constraint != myChart.mySelectedConstraint) { // selected a constraint
        myChart.select(constraint);
        if (constraint != null) {
          if (DEBUG) {
            Debug.println(" Constraint hit " + framePosition + "    " + constraint.getTag().getElement().getText() + " " + constraint.mId);
          }
          myChart.update(Reason.SELECTION_CHANGED);
          myChart.setCursorPosition(framePosition / 100f);
          return;
        }
      }
      if (keyFrame != myChart.mySelectedKeyFrame) { // selected a keyframe
        myChart.select(keyFrame);

        if (keyFrame != null) {
          if (DEBUG) {
            Debug.println(" keyFrame hit " + framePosition);
          }

          myChart.update(Reason.SELECTION_CHANGED);
          myChart.setCursorPosition(keyFrame.framePosition / 100f);
          return;
        }
      }
      else {
        if (keyFrame != null) {
          myChart.setCursorPosition(keyFrame.framePosition / 100f);
        }
      }
      if (create != null) { // selected to create a
        if (DEBUG) {
          Debug.println(" Create hit ");
        }
        myChart.myModel.createConstraint(create.getName(), create.isStart());
        myChart.setCursorPosition(framePosition / 100f);
        return;
      }

      if (layout != null) { // selected to create a
        if (DEBUG) {
          Debug.println(" layout show ");
        }

        myChart.selectView(layout.getName());
        myChart.update(Reason.SELECTION_CHANGED);
        myChart.update(Reason.CURSOR_POSITION_CHANGED);// Todo fix update system
        return;
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
      g.drawPolygon(myXPoints, myYPoints, 6);
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

    public void drawTriangle(Graphics g, boolean selected, int x, int pos) {
      int half = ourDiamondSize / 2;
      myXPoints[0] = x;
      myYPoints[0] = pos;
      myXPoints[1] = x + half;
      myYPoints[1] = pos + ourDiamondSize;
      myXPoints[2] = x - half;
      myYPoints[2] = pos + ourDiamondSize;
      if (selected) {
        g.drawPolygon(myXPoints, myYPoints, 3);
      }
      else {
        g.fillPolygon(myXPoints, myYPoints, 3);
      }
    }

    public void drawBase(Graphics g, boolean selected, int x, int pos) {
      int half = ourDiamondSize / 2;
      Color orig = g.getColor();
      if (selected) {
        g.setColor(Chart.ourMySelectedLineColor);
        g.drawRoundRect(x - half, pos - half, ourDiamondSize, ourDiamondSize, ourDiamondSize, ourDiamondSize);
      }
      else {
        g.drawRoundRect(x - half, pos - half, ourDiamondSize, ourDiamondSize, ourDiamondSize, ourDiamondSize);
      }
      g.setColor(orig);
    }

    public void drawGroup(Graphics g, boolean selected, int x, int pos) {
      int half = ourDiamondSize / 2;
      myXPoints[0] = x;
      myYPoints[0] = pos;
      myXPoints[1] = x + half;
      myYPoints[1] = pos + ourDiamondSize;
      myXPoints[2] = x - half;
      myYPoints[2] = pos + ourDiamondSize;
      if (selected) {
        g.drawPolygon(myXPoints, myYPoints, 3);
      }
      else {
        g.fillPolygon(myXPoints, myYPoints, 3);
      }
    }

    public void drawPentagon(Graphics g, boolean selected, int x, int pos, int lines) {
      int half = ourDiamondSize / 2;
      int sixth = ourDiamondSize / 5;
      int quarter = ourDiamondSize / 4;

      myXPoints[0] = x;
      myYPoints[0] = pos - half;

      myXPoints[1] = x + half;
      myYPoints[1] = pos;

      myXPoints[2] = x + sixth;
      myYPoints[2] = pos + half;

      myXPoints[3] = x - sixth;
      myYPoints[3] = pos + half;

      myXPoints[4] = x - half;
      myYPoints[4] = pos;

      if (selected) {
        g.fillPolygon(myXPoints, myYPoints, 5);
      }
      else {
        g.fillPolygon(myXPoints, myYPoints, 5);
      }
      Color c = g.getColor();
      g.setColor(Color.GRAY);
      if ((lines & 1) != 0) {
        g.drawLine(x - quarter + 2, pos - half + quarter + 1, x + quarter - 3, pos - half + quarter + 1);
      }
      if ((lines & 2) != 0) {
        g.drawLine(x - half + 2, pos, x + half - 3, pos);
      }
      if ((lines & 4) != 0) {
        g.drawLine(x - quarter + 2, pos + half - quarter - 1, x + quarter - 3, pos + half - quarter - 1);
      }
      g.setColor(c);
    }

    public void drawStartConstraint(Graphics g, Color selected, int x, int pos) {
      int half = ourDiamondSize / 2;
      int inset = ourDiamondSize / 3;
      myXPoints[0] = x - half;
      myYPoints[0] = pos - half;
      myXPoints[1] = x;
      myYPoints[1] = pos - half;
      myXPoints[2] = x + half;
      myYPoints[2] = pos;
      myXPoints[3] = x + half;
      myYPoints[3] = pos + half;
      myXPoints[4] = x - half;
      myYPoints[4] = pos + half;

      g.fillPolygon(myXPoints, myYPoints, 5);

      Color c = g.getColor();

      if (selected != null) {
        g.setColor(selected);
        g.fillPolygon(myXPoints, myYPoints, 5);
      }
      myXPoints[0] = x - half + inset;
      myYPoints[0] = pos - half + inset;
      myXPoints[1] = x + half - inset;
      myYPoints[1] = pos;
      myXPoints[2] = x - half + inset;
      myYPoints[2] = pos + half - inset;
      g.setColor(Color.WHITE);
      g.fillPolygon(myXPoints, myYPoints, 3);
      g.setColor(c);
    }

    public void drawAddConstraint(Graphics g, int x, int pos) {
      Color c = g.getColor();
      int half = ourDiamondSize / 2;
      int inset = ourDiamondSize / 8;
      g.setColor(Chart.ourAddConstraintColor);
      g.fillRoundRect(x - half, pos - half, ourDiamondSize, ourDiamondSize, ourDiamondSize, ourDiamondSize);
      g.setColor(Chart.ourAddConstraintPlus);
      g.fillRect(x - inset, pos - half + inset, inset * 2, ourDiamondSize - inset * 2); // vertical
      g.fillRect(x - half + inset, pos - inset, ourDiamondSize - inset * 2, inset * 2); // horizontal
      g.setColor(c);
    }

    public void drawEndConstraint(Graphics g, Color selected, int x, int pos) {
      int half = ourDiamondSize / 2;
      int inset = ourDiamondSize / 3;

      myXPoints[0] = x - half;
      myYPoints[0] = pos;

      myXPoints[1] = x;
      myYPoints[1] = pos - half;

      myXPoints[2] = x + half;
      myYPoints[2] = pos - half;

      myXPoints[3] = x + half;
      myYPoints[3] = pos + half;
      myXPoints[4] = x - half;
      myYPoints[4] = pos + half;

      g.fillPolygon(myXPoints, myYPoints, 5);

      Color c = g.getColor();

      if (selected != null) {
        g.setColor(selected);
        g.fillPolygon(myXPoints, myYPoints, 5);
      }

      myXPoints[0] = x - half + inset;
      myYPoints[0] = pos - half + inset;
      myXPoints[1] = x + half - inset;
      myYPoints[1] = myYPoints[0];
      myXPoints[2] = myXPoints[1];
      myYPoints[2] = pos + half - inset;
      myXPoints[3] = myXPoints[0];
      myYPoints[3] = myYPoints[2];
      if (selected != null) {

        g.setColor(selected);
        g.drawRoundRect(x - half, pos - half, ourDiamondSize, ourDiamondSize, ourDiamondSize, ourDiamondSize);
      }
      g.setColor(Color.WHITE);
      g.fillPolygon(myXPoints, myYPoints, 4);
      g.setColor(c);
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

    /**
     * Provides the bulk of the gantt chart graphics
     *
     * @param g
     */
    @Override
    protected void paintComponent(Graphics g) {

      g.setColor((myRow % 2 == 0) ? Chart.ourPrimaryPanelBackground : Chart.ourSecondaryPanelBackground);
      myKeyFrameTable.clear();
      myConstraintTable.clear();
      myCreateConstraintTable.clear();
      myLayoutsSelectionTable.clear();

      int panelWidth = getWidth();
      int panelHeight = getHeight();

      g.fillRect(0, 0, getWidth(), getHeight());

      g.setColor((myRow % 2 == 1) ? Chart.ourPrimaryPanelBackground : Chart.ourSecondaryPanelBackground);

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
      g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      int half = ourDiamondSize / 2;
      int pos = 2;
      if (myChart != null) {
        int width = getWidth() - myChart.myChartLeftInset - myChart.myChartRightInset;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int xp = JBUI.scale(10);
        int yp = pos + ourDiamondSize;
        myLayoutsSelectionTable.add(xp, yp, myViewElement.myName);
        boolean viewSelected = (myChart.mySelection == Chart.Selection.VIEW && myViewElement.myName.equals(myChart.mySelectedKeyView));
        drawBase(g, viewSelected, xp, yp);
        g.setColor(Chart.myUnSelectedLineColor);

        // draw constraint at the start
        if (myChart.myModel != null
            && myChart.myModel.getStartConstraintSet() != null
            && myChart.myModel.getStartConstraintSet().myConstraintViews != null) {
          MotionSceneModel.ConstraintView constraint = myChart.myModel.getStartConstraintSet().myConstraintViews.get(myViewElement.myName);
          int xpos = myChart.myChartLeftInset + (int)((0 * width) / 100);
          if (constraint != null) {
            boolean selected = myChart.mySelectedConstraint == constraint;
            myConstraintTable.add(xpos - ourDiamondSize, pos + ourDiamondSize, constraint);
            drawStartConstraint(g, selected ? Chart.ourMySelectedLineColor : null, xpos - ourDiamondSize, pos + ourDiamondSize);
            myRowHasMarks = true;
          }
          else {
            drawAddConstraint(g, xpos - ourDiamondSize, pos + ourDiamondSize);
            myCreateConstraintTable.add(xpos - ourDiamondSize, pos + ourDiamondSize, myViewElement.myName, true);
          }
        }

        // draw constraint at the end
        if (myChart.myModel != null
            && myChart.myModel.getEndConstraintSet() != null &&
            myChart.myModel.getEndConstraintSet().myConstraintViews != null) {
          MotionSceneModel.ConstraintView constraint = myChart.myModel.getEndConstraintSet().myConstraintViews.get(myViewElement.myName);
          int xpos = myChart.myChartLeftInset + (int)((100 * width) / 100);
          if (constraint != null) {
            myConstraintTable.add(xpos + ourDiamondSize, pos + ourDiamondSize, constraint);
            boolean selected = myChart.mySelectedConstraint == constraint;
            drawEndConstraint(g, selected ? Chart.ourMySelectedLineColor : null, xpos + ourDiamondSize, pos + ourDiamondSize);
            myRowHasMarks = true;
          }
          else {
            drawAddConstraint(g, xpos + ourDiamondSize, pos + ourDiamondSize);
            myCreateConstraintTable.add(xpos + ourDiamondSize, pos + ourDiamondSize, myViewElement.myName, false);
          }
        }
        myRowHasMarks |= !myViewElement.mKeyFrames.myKeyAttributes.isEmpty();
        myRowHasMarks |= !myViewElement.mKeyFrames.myKeyCycles.isEmpty();
        myRowHasMarks |= !myViewElement.mKeyFrames.myKeyPositions.isEmpty();
        if (myViewElement.myHeightPosition == 0 && myViewElement.myHeightCycle == 0 && myViewElement.myHeightAttribute == 0) {
          int[] marked = new int[101];
          for (MotionSceneModel.KeyPos key : myViewElement.mKeyFrames.myKeyPositions) {
            marked[key.framePosition] = 1;
          }
          for (MotionSceneModel.KeyAttributes key : myViewElement.mKeyFrames.myKeyAttributes) {
            marked[key.framePosition] |= 2;
          }
          for (MotionSceneModel.KeyCycle key : myViewElement.mKeyFrames.myKeyCycles) {
            marked[key.framePosition] |= 4;
          }
          pos = (myViewElement.myHeightView - ourDiamondSize) / 2 + 3;
          for (int i = 0; i < marked.length; i++) {
            int mark = marked[i];
            if (mark == 0) continue;
            int x = myChart.myChartLeftInset + (int)((i * width) / 100);
            drawPentagon(g, true, x, pos, mark);
          }
          return;
        }
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

          myKeyFrameTable.add(x, pos + half, key);
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
            if (SIMPLE) {
              drawDiamond(g, true, x, pos);
            }
            else {
              drawTriangle(g, true, x, pos);
            }
            g.setColor(Chart.myUnSelectedLineColor);
          }
          else {

            if (SIMPLE) {
              drawDiamond(g, false, x, pos);
            }
            else {
              drawTriangle(g, false, x, pos);
            }
          }
          myKeyFrameTable.add(x, pos + half, key);
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
            if (SIMPLE) {
              drawDiamond(g, true, x, pos);
            }
            else {
              drawBowtie(g, true, x, pos);
            }
            g.setColor(Chart.myUnSelectedLineColor);
          }
          else {
            if (SIMPLE) {
              drawDiamond(g, false, x, pos);
            }
            else {
              drawBowtie(g, false, x, pos);
            }
          }
          myKeyFrameTable.add(x, pos + half, key);
        }
      }
    }
  }
}
