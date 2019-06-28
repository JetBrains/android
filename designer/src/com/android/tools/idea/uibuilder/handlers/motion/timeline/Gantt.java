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

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.text.DecimalFormat;

/**
 * Provides the TimeLine panel
 */
public class Gantt extends JPanel implements GanttCommands {

  public static final int HEADER_HEIGHT = JBUIScale.scale(30);
  private static final JBDimension SLIDER_DIMENSION = JBUI.size(140, 16);

  GanttController myGanttController = new GanttController();
  private Chart myChart = new Chart(this);
  private ViewList myViewList = new ViewList(myChart);
  private ColumnHead myColumnHead = new ColumnHead(myChart);
  private TimeLineRows myRowGraphc = new TimeLineRows(myChart);
  private JScrollPane myScrollPane = new JBScrollPane(myRowGraphc);
  TrackControls myTrackControls = new TrackControls(myChart);
  JTextField myDuration;
  private DecimalFormat myFormat = new DecimalFormat("####.00");

  private MotionSceneModel myMotionSceneModel;
  private JLabel myTitleLabel;

  public Gantt(GanttEventListener listener) {
    super(new BorderLayout());
    add(buildTitleBar(), BorderLayout.NORTH);
    add(myScrollPane, BorderLayout.CENTER);
    myScrollPane.setColumnHeaderView(myColumnHead);
    myScrollPane.setRowHeaderView(myViewList);
    myScrollPane.setCorner(ScrollPaneConstants.UPPER_LEFT_CORNER, myTrackControls);
    myScrollPane.setBorder(JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0));
    JViewport viewPort = myScrollPane.getViewport();
    viewPort.setScrollMode(JViewport.BLIT_SCROLL_MODE);
    viewPort.setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE);
    viewPort.setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
    myRowGraphc.getAutoscrolls();
    myChart.myContainerHeight = myScrollPane.getViewport().getHeight();
    myChart.myContainerWidth = myScrollPane.getViewport().getWidth();
    setBackground(Chart.ourSecondaryPanelBackground);
    setup();
    if (listener != null) {
      listener.onInit(this);
    }
    myGanttController.setListener(listener);
    myTrackControls.setGantt(myGanttController);
    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        myChart.myContainerHeight = myScrollPane.getViewport().getHeight();
        myChart.myContainerWidth = myScrollPane.getViewport().getWidth();
        myChart.update(ChartElement.Reason.RESIZE);
      }
    });

    MouseAdapter timeLineMouse = new MouseAdapter() {

      @Override
      public void mousePressed(MouseEvent e) {
        repaint();
      }

      private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        int pos = e.getX();
        float timeCursorMs = (pos - myChart.myChartLeftInset) / myChart.myPixelsPerMs;
        myChart.setTimeCursorMs(clamp(timeCursorMs, 0, myChart.myAnimationTotalTimeMs));
        float percent = myChart.getTimeCursorMs() / myChart.myAnimationTotalTimeMs;

        JViewport viewPort = (JViewport)SwingUtilities.getAncestorOfClass(JViewport.class, myRowGraphc);
        Rectangle rec = myRowGraphc.getVisibleRect();
        myGanttController.framePosition(percent);
        int dx = 0;
        if (pos + 1 < rec.x) {
          dx = pos - rec.x;
        }
        if (pos > rec.x + rec.width) {
          dx = pos - (rec.x + rec.width);
        }
        if (dx != 0) {
          rec.x += dx / 2; // TODO fix this is a simple hack to prevent ugly double paint
          myRowGraphc.scrollRectToVisible(rec);
        }
        repaint();
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        // quantize on mouse up
        float pos = ((int)(100 * myChart.getTimeCursorMs() / myChart.getAnimationTotalTimeMs())) / 100f;
        float timeCursorMs  = pos * myChart.getAnimationTotalTimeMs();
        myChart.setTimeCursorMs(clamp(timeCursorMs, 0, myChart.myAnimationTotalTimeMs));
        float percent = myChart.getTimeCursorMs() / myChart.myAnimationTotalTimeMs;
        myGanttController.framePosition(percent);
        repaint();
      }
    };

    myColumnHead.addMouseMotionListener(timeLineMouse);
    myColumnHead.addMouseWheelListener(timeLineMouse);
    myColumnHead.addMouseListener(timeLineMouse);

    // Require some extra vertical space such that we initially show a few lines in the chart.
    Dimension min = getMinimumSize();
    setMinimumSize(new Dimension(min.width, 2 * min.height));
  }

  void selectionChanged() {
    myGanttController.myListener.selectionEvent();
  }

  public void setZoom(float percent) {
    myChart.setZoom(percent);
  }

  public MotionSceneModel.KeyFrame getSelectedKey(String id) {
    return myChart.mySelectedKeyFrame;
  }

  public void clearSelectedKey() {
    myChart.mySelectedKeyFrame = null;
  }

  public MotionSceneModel.TransitionTag getTransitionTag() {
    if (myChart.myModel == null) {
      return null;
    }
    return myChart.myModel.getTransitionTag(0);
  }

  public MotionSceneModel.OnSwipeTag getOnSwipeTag() {
    return myChart.myModel.getOnSwipeTag();
  }

  static class IntFilter extends DocumentFilter {
    @Override
    public void insertString(FilterBypass fb, int offset, String string,
                             AttributeSet attr) throws BadLocationException {

      Document doc = fb.getDocument();
      StringBuilder sb = new StringBuilder();
      sb.append(doc.getText(0, doc.getLength()));
      sb.insert(offset, string);

      if (test(sb.toString())) {
        super.insertString(fb, offset, string, attr);
      }
      else {
        // TODO warn the user and don't allow the insert
      }
    }

    private boolean test(String text) {
      try {
        Integer.parseInt(text);
        return true;
      }
      catch (NumberFormatException e) {
        return false;
      }
    }

    @Override
    public void replace(FilterBypass fb, int offset, int length, String text,
                        AttributeSet attrs) throws BadLocationException {

      Document doc = fb.getDocument();
      StringBuilder sb = new StringBuilder();
      sb.append(doc.getText(0, doc.getLength()));
      sb.replace(offset, offset + length, text);

      if (test(sb.toString())) {
        super.replace(fb, offset, length, text, attrs);
      }
      else {
        // TODO warn the user and don't allow the insert
      }
    }

    @Override
    public void remove(FilterBypass fb, int offset, int length)
      throws BadLocationException {
      Document doc = fb.getDocument();
      StringBuilder sb = new StringBuilder();
      sb.append(doc.getText(0, doc.getLength()));
      sb.delete(offset, offset + length);

      if (test(sb.toString())) {
        super.remove(fb, offset, length);
      }
      else {
        // TODO warn the user and don't allow the insert
      }
    }
  }

  // ==================================TITLE BAR code================================
  private JPanel buildTitleBar() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(JBUI.Borders.empty(0, JBUIScale.scale(4)));
    myTitleLabel = new JLabel("Timeline");
    panel.add(myTitleLabel, BorderLayout.WEST);
    JPanel right = new JPanel();
    right.add(new JLabel("Duration(ms)"));
    myDuration = new JTextField();
    myDuration.setText("XXXXX"); // This sets the Preferred size to the size needed to fit 5 X characters
    myDuration.setPreferredSize(myDuration.getPreferredSize());
    myDuration.setText("600");
    ((PlainDocument)myDuration.getDocument()).setDocumentFilter(new IntFilter());
    myDuration.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        JTextField c = (JTextField)e.getSource();
        ActionListener[] listeners = c.getActionListeners();
        ActionEvent event = new ActionEvent(c, ActionEvent.ACTION_PERFORMED, null);
        for (int i = 0; i < listeners.length; i++) {
          listeners[i].actionPerformed(event);
        }
      }
    });
    myDuration.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myGanttController.myListener != null) {
          int duration = Integer.parseInt(myDuration.getText());
          myGanttController.myListener.transitionDuration(duration);
          myChart.setAnimationTotalTimeMs(duration);
        }
      }
    });

    myDuration.setAlignmentX(Component.LEFT_ALIGNMENT);
    right.add(myDuration);
    right.add(new JLabel(StudioIcons.LayoutEditor.Motion.MIN_SCALE));
    right.add(createZoomSlider());
    right.add(new JLabel(StudioIcons.LayoutEditor.Motion.MAX_SCALE));
    panel.add(right, BorderLayout.EAST);
    return panel;
  }

  @NotNull
  private JSlider createZoomSlider() {
    JSlider slider = new JSlider();
    slider.setPreferredSize(SLIDER_DIMENSION);
    slider.setValue(0);
    slider.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        setZoom((1 + ((JSlider)e.getSource()).getValue() / 25f));
      }
    });
    return slider;
  }

  public void setDurationMs(int time) {
    myDuration.setText(Integer.toString(time));
  }

  void updateLabel() {
    String s;
    switch (myChart.getMode()) {
      case START:
        s = "Start Constraint " + myFormat.format(myChart.getTimeCursorMs()) + "ms";
        break;
      case END:
        s = "End Constraint " + myFormat.format(myChart.getTimeCursorMs()) + "ms";
        break;
      default:
        s = "Timeline " + myFormat.format(myChart.getTimeCursorMs()) + "ms";
    }
    myTitleLabel.setText(s);
  }

  @Override
  public void setBackground(Color bg) {
    super.setBackground(bg);
    if (myRowGraphc == null) {
      return;
    }
    myRowGraphc.setBackground(bg);
    myViewList.setBackground(bg);
    myTrackControls.setBackground(bg);
    myScrollPane.setBackground(bg);
    myColumnHead.setBackground(bg);
  }

  public void setup() {
  }

  public Chart getChart() { return myChart; }

  public MotionSceneModel getMotionScene() {
    return myMotionSceneModel;
  }

  public void setMotionScene(MotionSceneModel motionScene) {
    myMotionSceneModel = motionScene;
    if (motionScene.myTransition != null && !motionScene.myTransition.isEmpty()) {
      myDuration.setText(Integer.toString(motionScene.myTransition.get(0).duration));
    }
    myChart.clear();
    for (String s : motionScene.mySceneViews.keySet()) {
      myChart.addView(new ViewElement(s, motionScene.mySceneViews.get(s)));
    }
    myChart.update(Gantt.ChartElement.Reason.ADDVIEW);
    myChart.setMotionSceneModel(myMotionSceneModel);
    validate();
    repaint();
  }

  @Override
  public void setProgress(float percent) {
    myChart.setCursorPosition(percent);
  }

  @Override
  public void setMode(Mode mode) {
    myChart.setMode(mode);
  }

  @Override
  public Mode getMode() {
    return myChart.getMode();
  }

  @Override
  public void update() {
  }

  /* ================================= ViewElement ========================= */
  static class ViewElement {

    String myName;
    int myYStart;
    int myHeight;
    boolean myHasAttribute = false;
    boolean myHasCycle = false;
    boolean myHasPosition = false;
    int myHeightView = 0;
    int myHeightAttribute = 0;
    int myHeightCycle = 0;
    int myHeightPosition = 0;
    MotionSceneModel.MotionSceneView mKeyFrames;

    public ViewElement(String name, MotionSceneModel.MotionSceneView keyFrames) {
      myName = name;
      mKeyFrames = keyFrames;
    }

    @Override
    public String toString() {
      return myName;
    }

    public Icon getIcon() {
      return mKeyFrames.getIcon();
    }

    ViewElement(String name) {
    }
  }

  interface ChartElement {
    enum Reason {
      RESIZE, // this is to be called when the tree is expanding
      ADDVIEW,
      SELECTION_CHANGED,
      ZOOM, // the time is being expanded
      CONSTRUCTION,
      MODE_CHANGE,
      CURSOR_POSITION_CHANGED,
      GRAPH_SELECTED, OTHER
    }

    void update(Reason reason);

    void repaint();

    void invalidate();
  }
}
