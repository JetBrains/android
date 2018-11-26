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

import static com.android.tools.idea.uibuilder.handlers.motion.timeline.TimeLineIcons.END_CONSTRAINT;
import static com.android.tools.idea.uibuilder.handlers.motion.timeline.TimeLineIcons.START_CONSTRAINT;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.uibuilder.handlers.motion.Debug;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import icons.StudioIcons;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import javax.swing.text.PlainDocument;
import org.jetbrains.annotations.NotNull;

/**
 * Provides the TimeLine panel
 */
public class Gantt extends JPanel implements GanttCommands {
  private static final boolean DEBUG = false;
  public static final int HEADER_HEIGHT = JBUI.scale(30);
  private static final JBDimension SLIDER_DIMENSION = JBUI.size(140, 16);

  GanttController myGanttController = new GanttController();
  private Chart myChart = new Chart(this);
  private ViewList myViewList = new ViewList(myChart);
  private ColumnHead myColumnHead = new ColumnHead(myChart);
  private TimeLineRows myRowGraphc = new TimeLineRows(myChart);
  private JScrollPane myScrollPane = new JBScrollPane(myRowGraphc);
  TrackControls myTrackControls = new TrackControls(myChart);
  TitleBar myTitleBar;
  JTextField myDuration;
  private DecimalFormat myFormat = new DecimalFormat("####.00");

  private MotionSceneModel myMotionSceneModel;
  private JLabel myTitleLabel;

  public Gantt(GanttEventListener listener) {
    super(new BorderLayout());
    myTitleBar = new TitleBar(myChart);
    add(myTitleBar, BorderLayout.NORTH);
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
        float timeCursorMs = pos * myChart.getAnimationTotalTimeMs();
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

  public MotionSceneModel.ConstraintView getSelectedConstraintView(String id) {
    return myChart.mySelectedConstraint;
  }

  public void clearSelectedKey() {
    myChart.mySelectedKeyView = null;
    myChart.mySelectedKeyFrame = null;
    myChart.mySelectedKeyFrameTag = null;
    myChart.mySelectedConstraintTag = null;
  }

  public MotionSceneModel.TransitionTag getTransitionTag() {
    if (myChart.myModel == null) {
      return null;
    }
    return myChart.myModel.getTransitionTag(0);
  }

  public MotionSceneModel.OnSwipeTag getOnSwipeTag() {
    if (myChart.myModel == null) {
      return null;
    }
    return myChart.myModel.getOnSwipeTag();
  }

  public void handleSelectionChanged(@NotNull SelectionModel model, @NotNull List<NlComponent> selection) {
    List<NlComponent> views = myMotionSceneModel != null ? myMotionSceneModel.getViews() : Collections.emptyList();
    if (views.isEmpty()) {
      return;
    }
    NlComponent motion = views.get(0).getParent();
    if (motion == null) {
      return;
    }
    List<String> ids = new ArrayList<>(selection.size());
    for (NlComponent selected : selection) {
      if (selected.getParent() == motion) {
        String id = selected.getId();
        if (id != null) {
          ids.add(id);
        }
      }
    }
    myViewList.handleSelection(ids);
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

  static class MotionSceneUI extends JPanel {
    PropPanel mPropPanel;
    JBTabbedPane myPropTabb;
    OnPanelCreate myCreate;
    OnSwipePanel mySwipePanel;
    OnClickPanel[] onClickPanel = new OnClickPanel[0];
    ComboBox<String> myTransition;
    final Dimension mDimensions;
    Chart myChart;
    Gantt myGantt;

    boolean mRebuildingUI = false;

    MotionSceneUI(Gantt gantt, Chart chart) {
      setLayout(new BorderLayout());
      myGantt = gantt;
      myChart = chart;
      JPanel topControlls = new JPanel(new BorderLayout());
      JPanel transitions = new JPanel(new BorderLayout());
      topControlls.add(transitions);
      JPanel transitionList = new JPanel();
      transitions.add(transitionList, BorderLayout.WEST);
      JLabel label = new JLabel("Transition");
      transitionList.add(label);
      myTransition = new ComboBox<>(new String[]{"(start - end)", "new..."});
      transitionList.add(myTransition);
      myTransition.addActionListener(l -> selectTransition());

      myPropTabb = new JBTabbedPane();
      mPropPanel = new PropPanel();
      mDimensions = mPropPanel.getPreferredSize();
      myPropTabb.add(mPropPanel, "Properties");
      myCreate = new OnPanelCreate(this);
      myPropTabb.add(myCreate, "+");
      myPropTabb.addChangeListener(c -> plusHit(c));
      add(topControlls, BorderLayout.NORTH);
      add(myPropTabb, BorderLayout.CENTER);
    }

    private void selectTransition() {
      if (mRebuildingUI) {
        return;
      }
      int items = myTransition.getItemCount();
      int selectedIndex = myTransition.getSelectedIndex();
      if (selectedIndex == items - 1) {
        myTransition.insertItemAt(" - ", items - 1);
        myTransition.setSelectedIndex(items - 1);
      }
      else {
        myChart.myModel.setCurrentTransition(selectedIndex);
        myChart.update(ChartElement.Reason.SELECTION_CHANGED);
      }
      myGantt.setMotionScene(myChart.myModel);
      myChart.update(ChartElement.Reason.ADDVIEW);
    }

    @Override
    public void setBackground(Color color) {
      super.setBackground(color);
    }

    void addOnClickPanel() {
      if (DEBUG) {
        Debug.println("addOnClickPanel");
      }
      MotionSceneModel.OnClickTag onClickTag = myGantt.myMotionSceneModel.createOnClick();
      OnClickPanel clickPanel = new OnClickPanel(mDimensions);
      int index = myPropTabb.indexOfComponent(myCreate);
      myPropTabb.insertTab("onClick", null, clickPanel, "Support swiping", index);
      myPropTabb.setSelectedIndex(index);
      clickPanel.setOnClick(onClickTag);
    }

    private void addOnSwipePanel() {
      if (DEBUG) {
        Debug.println(2, "addOnSwipePanel");
      }
      MotionSceneModel.OnSwipeTag onSwipeTag = myGantt.myMotionSceneModel.createOnSwipe();
      mySwipePanel = new OnSwipePanel(mDimensions);
      int index = myPropTabb.indexOfComponent(myCreate);

      myPropTabb.insertTab("onSwipe", null, mySwipePanel, "Support swiping", index);
      myPropTabb.setSelectedIndex(index);
      mySwipePanel.setOnSwipeTag(onSwipeTag);
    }

    private void plusHit(ChangeEvent c) {
      if (DEBUG) {
        Debug.println("plusHit +");
      }
    }

    public void updateOnClicks(ArrayList<MotionSceneModel.OnClickTag> onClickTags) {
      if (DEBUG) {
        Debug.println("  updateOnClicks " + onClickTags.size());
      }
      if (onClickTags == null) {
        for (OnClickPanel ui : onClickPanel) {
          myPropTabb.remove(ui);
        }
        return;
      }
      if (onClickPanel.length != onClickTags.size()) {
        if (DEBUG) {
          Debug.println("updateOnClicks resize ");
        }

        for (OnClickPanel ui : onClickPanel) {
          myPropTabb.remove(ui);
        }

        int oldLen = onClickPanel.length;
        onClickPanel = Arrays.copyOf(onClickPanel, onClickTags.size());
        for (int i = oldLen; i < onClickPanel.length; i++) {
          onClickPanel[i] = new OnClickPanel(mDimensions);
        }
        int index = myPropTabb.indexOfComponent(myCreate);
        for (int i = 0; i < onClickPanel.length; i++) {
          myPropTabb.insertTab("onClick" + (i + 1), null, onClickPanel[i], "Support onClick", index + i);
        }
      }

      for (int i = 0; i < onClickPanel.length; i++) {
        onClickPanel[i].setOnClick(onClickTags.get(i));
      }
    }

    public void rebuildTransitionName(String[] names) {
      mRebuildingUI = true;
      String current = (String)myTransition.getSelectedItem();
      int show = 0;
      myTransition.removeAllItems();
      for (int i = 0; i < names.length; i++) {
        String name = names[i];
        if (name.equals(current)) {
          show = i;
        }
        myTransition.insertItemAt(name, i);
      }
      myTransition.insertItemAt("New...", names.length);
      myTransition.setSelectedIndex(show);
      mRebuildingUI = false;
      updateTransitionProperties();
    }

    static String trimId(String fullId) {
      if (fullId == null) {
        return null;
      }
      int slashPos = fullId.indexOf('/');
      if (slashPos == -1) {
        return fullId;
      }
      return fullId.substring(fullId.indexOf('/') + 1);
    }

    private void updateTransitionProperties() {
      String end = trimId(myChart.myModel.getEndId());
      String start = trimId(myChart.myModel.getStartId());
      int endPos = 0;
      int startPos = 0;

      String[] csets = myChart.myModel.getKnownConstraintSetID();
      mPropPanel.myEndConstraint.removeAllItems();
      mPropPanel.myStartConstraint.removeAllItems();
      for (int i = 0; i < csets.length; i++) {
        String cset = csets[i];
        String setIdName = trimId(cset);
        mPropPanel.myEndConstraint.addItem(cset);
        mPropPanel.myStartConstraint.addItem(cset);
        if (end != null && end.equals(setIdName)) {
          endPos = i;
        }
        if (start != null && start.equals(setIdName)) {
          startPos = i;
        }
      }
      mPropPanel.myEndConstraint.addItem("New...");
      mPropPanel.myStartConstraint.addItem("New...");

      if (end != null) {
        mPropPanel.myEndConstraint.setSelectedIndex(endPos);
      }
      if (start != null) {
        mPropPanel.myStartConstraint.setSelectedIndex(startPos);
      }
    }

    static class OnPanelCreate extends JPanel {
      MotionSceneUI myMotionSceneUI;

      OnPanelCreate(MotionSceneUI ui) {
        myMotionSceneUI = ui;
        JButton button0nSwipe = new JButton("0nSwipe");
        add(button0nSwipe);
        JButton button0nClick = new JButton("0nClick");
        add(button0nClick);

        button0nSwipe.addActionListener(e -> create0nSwipe(e));
        button0nClick.addActionListener(e -> create0nClick(e));
      }

      private void create0nClick(ActionEvent e) {
        myMotionSceneUI.addOnClickPanel();
      }

      private void create0nSwipe(ActionEvent e) {
        myMotionSceneUI.addOnSwipePanel();
      }
    }

    static class PropPanel extends JPanel {
      JTextField myDuration;
      ComboBox<String> myStartConstraint;
      ComboBox<String> myEndConstraint;
      JTextField myStaggered;

      PropPanel() {
        super(new GridBagLayout());
        setBackground(Chart.ourSecondaryPanelBackground);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weighty = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(10, 10, 0, 0);
        JLabel label = new JLabel("Start", START_CONSTRAINT, JLabel.TRAILING);
        add(label, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        myStartConstraint = new ComboBox<>(new String[]{"@+id/start", "new..."});
        add(myStartConstraint, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        label = new JLabel("End", END_CONSTRAINT, JLabel.TRAILING);
        add(label, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        myEndConstraint = new ComboBox<>(new String[]{"@+id/start", "new..."});
        add(myEndConstraint, gbc);

        label = new JLabel("Duration(ms)");
        myDuration = new JTextField();
        myDuration.setText("XXXXX"); // This sets the Preferred size to the size needed to fit 5 X characters
        myDuration.setPreferredSize(myDuration.getPreferredSize());
        myDuration.setText("600");

        gbc.gridx = 3;
        gbc.gridy = 0;
        gbc.weightx = 0;
        add(label, gbc);
        gbc.gridx = 4;
        gbc.weightx = 1;

        add(myDuration, gbc);

        gbc.gridx = 3;
        gbc.gridy = 1;
        gbc.weightx = 0;
        label = new JLabel("Staggered");
        add(label, gbc);
        gbc.gridx = 4;
        gbc.weightx = 1;
        myStaggered = new JTextField("0");
        add(myStaggered, gbc);
      }
    }
  }

  // ==================================TITLE BAR code================================
  class TitleBar extends JPanel implements Gantt.ChartElement {
    MotionSceneUI motionSceneUI;
    Chart myChart;

    TitleBar(Chart chart) {
      super(new BorderLayout());
      myChart = chart;
      chart.add(this);
      motionSceneUI = new MotionSceneUI(Gantt.this, chart);
      add(motionSceneUI, BorderLayout.NORTH);
      setBorder(JBUI.Borders.empty(0, JBUI.scale(4)));
      myTitleLabel = new JLabel("Motion");
      add(myTitleLabel, BorderLayout.WEST);
      JPanel right = new JPanel();
      myDuration = motionSceneUI.mPropPanel.myDuration;

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
      right.add(new JLabel(StudioIcons.LayoutEditor.Motion.MIN_SCALE));
      right.add(createZoomSlider());
      right.add(new JLabel(StudioIcons.LayoutEditor.Motion.MAX_SCALE));
      add(right, BorderLayout.EAST);
    }

    @Override
    public void update(Reason reason) {
      MotionSceneModel.OnSwipeTag onSwipeTag = null;
      ArrayList<MotionSceneModel.OnClickTag> onClickTags = null;
      String[] transitionNames = null;
      if (myChart != null && myChart.myModel != null) {
        onSwipeTag = myChart.myModel.getOnSwipeTag();
        onClickTags = myChart.myModel.getOnClickTags();
        transitionNames = myChart.myModel.getTransitionsNames();
      }
      if (motionSceneUI.mySwipePanel == null) {
        if (onSwipeTag == null) {
          return;
        }
        else {
          motionSceneUI.addOnSwipePanel();
          motionSceneUI.mySwipePanel.setOnSwipeTag(onSwipeTag);
        }
      }
      else {
        if (onSwipeTag == null) {
          motionSceneUI.myPropTabb.remove(motionSceneUI.mySwipePanel);
          motionSceneUI.mySwipePanel = null;
        }
        else {
          motionSceneUI.mySwipePanel.setOnSwipeTag(onSwipeTag);
        }
        motionSceneUI.updateOnClicks(onClickTags);
      }
      if (transitionNames != null) {
        motionSceneUI.rebuildTransitionName(transitionNames);
      }
    }
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
    for (String s : motionScene.getSceneViewsNames()) {
      myChart.addView(new ViewElement(s, motionScene.getSceneView(s)));
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
      if (mKeyFrames == null) {
        return null;
      }
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
