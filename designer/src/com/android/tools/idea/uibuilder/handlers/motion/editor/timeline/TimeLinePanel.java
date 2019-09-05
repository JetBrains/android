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

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Annotations.NotNull;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEScrollPane;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEUI;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionLayoutAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.timeline.TimeLineTopLeft.TimelineCommands;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MeModel;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector.TimeLineCmd;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector.TimeLineListener;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.Utils;

import java.util.Collections;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.LayerUI;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * The panel that displays the timeline
 */
public class TimeLinePanel extends JPanel {

  private static float TIMELINE_MIN = 0.0f;
  private static float TIMELINE_MAX = 100.0f;
  private TimelineStructure mTimelineStructure = new TimelineStructure();
  private TimeLineTopPanel myTimeLineTopPanel = new TimeLineTopPanel(mTimelineStructure);
  MotionEditorSelector mMotionEditorSelector;
  private MTag mSelectedKeyFrame;
  private boolean mMouseDown = false;

  /**
   * This is a very simple vertical flow layout with special handling of the last Component
   */
  static class VertLayout implements LayoutManager {
    Dimension dimension = new Dimension();

    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    @Override
    public void removeLayoutComponent(Component comp) {
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      int y = 0;
      int width = 0;

      int n = parent.getComponentCount();
      for (int i = 0; i < n; i++) {
        Component c = parent.getComponent(i);
        Dimension size = c.getPreferredSize();
        width = Math.max(width, size.width);
        y += size.height;
      }
      dimension.height = y;
      dimension.width = width;
      return dimension;
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
      int y = 0;
      int width = 0;

      int n = parent.getComponentCount();
      for (int i = 0; i < n; i++) {
        Component c = parent.getComponent(i);
        Dimension size = c.getMinimumSize();
        width = Math.max(width, size.width);
        y += size.height;
      }
      dimension.height = y;
      dimension.width = width;
      return dimension;
    }

    @Override
    public void layoutContainer(Container parent) {
      int y = 0;
      int n = parent.getComponentCount();
      int parent_height = parent.getHeight();
      int parent_width = parent.getWidth();
      for (int i = 0; i < n; i++) {
        Component c = parent.getComponent(i);
        Dimension size = c.getPreferredSize();
        if (i < n - 1) {
          c.setBounds(0, y, parent_width, size.height);
        } else {
          if (parent_height - y <= 0) {
            c.setBounds(0, y, parent_width, 0);
            c.setVisible(false);
          } else {
            c.setBounds(0, y, parent_width, parent_height - y);
          }
        }
        y += size.height;
      }

    }
  }

  public class METimeLine extends JPanel {
    JPanel pad = new JPanel() {
      @Override
      public void paintComponent(Graphics g) {
        g.setColor(MEUI.ourAvgBackground);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(MEUI.ourTextColor);
        TimeLineRow.drawTicks(g, mTimelineStructure, getHeight());
      }
    };
    int mSelectedIndex = 0;

    METimeLine() {
      super(new VertLayout());
      add(pad);
      pad.setBackground(MEUI.ourAvgBackground);
      MouseAdapter mouseAdapter = new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
        }

        @Override
        public void mouseReleased(MouseEvent e) {
          int n = getComponentCount() - 1;
          int y = e.getY();
          for (int i = 0; i < n; i++) {
            Component c = getComponent(i);
            if (y < c.getY() + c.getHeight()) {
              setSelectedIndex(i);
              break;
            }
          }
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
        }

        @Override
        public void mouseDragged(MouseEvent e) {
        }
      };
      addMouseListener(mouseAdapter);
    }

    ArrayList<ListSelectionListener> listeners = new ArrayList<>();

    void addListSelectionListener(ListSelectionListener l) {
      listeners.add(l);
    }

    public TimeLineRow getTimeLineRow(int index) {
      if (getComponent(index) instanceof TimeLineRow) {
        return (TimeLineRow) getComponent(index);
      }
      return null;
    }

    public TimeLineRowData getSelectedValue() {
      if (getComponent(mSelectedIndex) instanceof TimeLineRow) {
        return ((TimeLineRow) getComponent(mSelectedIndex)).mRow;
      }
      return null;
    }

    public void setListData(List<TimeLineRowData> list, MeModel model) {
      Component[] children = getComponents();
      removeAll();
      String lastName = null;
      int n = Math.min(children.length - 1, list.size());
      for (int i = 0; i < n; i++) {
        TimeLineRow child = (TimeLineRow) children[i];
        TimeLineRowData data = list.get(i);
        boolean showTitle = !(data.mName != null && data.mName.equals(lastName));
        child.setRowData(model, data, i, false, false, mSelectedKeyFrame, showTitle);
        lastName = data.mName;
        add(child);
      }
      if (list.size() > n) {
        for (int i = n; i < list.size(); i++) {
          TimeLineRow child = new TimeLineRow(mTimelineStructure);
          TimeLineRowData data = list.get(i);
          boolean showTitle = !(data.mName.equals(lastName));
          child.setRowData(model, data, i, false, false, mSelectedKeyFrame, showTitle);
          lastName = data.mName;

          add(child);
        }
      }
      add(pad);
      revalidate();
    }

    public int getSelectedIndex() {
      return mSelectedIndex;
    }

    private void notifySelectionListener(int index) {
      ListSelectionEvent event = new ListSelectionEvent(this, index, index, false);

      for (ListSelectionListener listener : listeners) {
        listener.valueChanged(event);
      }
    }

    public void setSelectedIndex(int index) {
      int prev = mSelectedIndex;
      mSelectedIndex = index;
      if (mSelectedKeyFrame == null) {
        notifySelectionListener(index);
      }
      if (getComponentCount() > prev) {
        Component comp = getComponent(prev);
        if (!(comp instanceof TimeLineRow)) {
          return;
        }
        TimeLineRow child = ((TimeLineRow) comp);

        child.setSelected(false);
        child.repaint();

      }
      TimeLineRow child = ((TimeLineRow) getComponent(mSelectedIndex));
      child.setSelected(true);
      child.repaint();
    }
  }

  private METimeLine mTimeLine = new METimeLine();

  private JScrollPane myScrollPane = new MEScrollPane(mTimeLine);
  private TimeLineTopLeft mTimeLineTopLeft = new TimeLineTopLeft();
  private MeModel mMeModel;
  private MTag mTransitionTag;
  private float mMotionProgress; // from 0 .. 1;
  private Timer myTimer;
  private boolean myYoyo = false;
  private boolean myForwardDirection = true;
  private float myProgressPerFrame = 0.01f;
  private static float[] ourSpeeds = {0.01f, 0.02f, 0.04f};
  private int mCurrentSpeed = 0;
  private boolean mIsPlaying = false;
  private ArrayList<TimeLineListener> mTimeLineListeners = new ArrayList<>();

  public TimeLinePanel() {
    super(new BorderLayout());
    JPanel top = new JPanel(new BorderLayout());
    top.add(myTimeLineTopPanel, BorderLayout.CENTER);
    top.add(mTimeLineTopLeft, BorderLayout.WEST);
    myScrollPane.setColumnHeaderView(top);
    myScrollPane.setBorder(BorderFactory.createEmptyBorder());
    mTimeLine.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        int code = e.getExtendedKeyCode();
        switch (code) {
          case KeyEvent.VK_UP:
            int index = mTimeLine.getSelectedIndex() - 1;
            if (index < 0) {
              index = mTimeLine.getComponentCount() - 2;
            }
            mSelectedKeyFrame = null;
            mTimeLine.setSelectedIndex(index);
            groupSelected();

            break;
          case KeyEvent.VK_DOWN:
            index = mTimeLine.getSelectedIndex() + 1;
            if (index > mTimeLine.getComponentCount() - 2) {
              index = 0;
            }
            mSelectedKeyFrame = null;
            mTimeLine.setSelectedIndex(index);
            groupSelected();
            break;
        }
      }
    });

    mTimeLine.setFocusable(true);

    mTimeLineTopLeft.addControlsListener(e -> {
      performCommand(e);
    });

    JLayer<JComponent> jlayer = new JLayer<JComponent>(myScrollPane, new LayerUI<JComponent>() {
      @Override
      public void paint(Graphics g, JComponent c) {
        super.paint(g, c);
        paintCursor(g, c);
      }

      @Override
      public void installUI(JComponent c) {
        super.installUI(c);
        JLayer jlayer = (JLayer) c;
        jlayer.setLayerEventMask(
          AWTEvent.MOUSE_EVENT_MASK |
            AWTEvent.MOUSE_MOTION_EVENT_MASK
        );
      }

      @Override
      protected void processMouseMotionEvent(MouseEvent e, JLayer l) {
        processMouseDrag(e);
      }

      @Override
      protected void processMouseEvent(MouseEvent e, JLayer l) {
        processMouseDrag(e);
      }
    });

    add(jlayer);
    mTimelineStructure.addWidthChangedListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        mTimeLine.repaint();
      }
    });
    mTimeLine.setBackground(MEUI.ourAvgBackground);
    myTimeLineTopPanel.setRange(TIMELINE_MIN, TIMELINE_MAX);
    mTimeLine.addListSelectionListener(e -> {
      mTimeLine.requestFocus();
      if (!e.getValueIsAdjusting()) {
        groupSelected();
      }
    });
    createTimer();
  }

  public void addTimeLineListener(TimeLineListener timeLineListener) {
    mTimeLineListeners.add(timeLineListener);
  }

  public void notifyTimeLineListeners(TimeLineCmd cmd, Float value) {
    for (TimeLineListener listener : mTimeLineListeners) {
      listener.command(cmd, value);
    }
  }
  private void createTimer() {
    if (myTimer == null) {
      myTimer = new Timer(30, e -> {
        progress();
      });
    myTimer.setRepeats(true);
    }
  }
  private void destroyTimer() {
    if (myTimer != null) {
      myTimer.stop();
      myTimer = null;
    }
  }
  private void performCommand(TimelineCommands e) {
    switch (e) {
      case PLAY:
        myTimer.setRepeats(true);
        notifyTimeLineListeners(TimeLineCmd.MOTION_PLAY, mMotionProgress);
        myTimer.start();
        mIsPlaying = true;
        break;
      case SPEED:
        mCurrentSpeed = (mCurrentSpeed + 1) % ourSpeeds.length;
        myProgressPerFrame = ourSpeeds[mCurrentSpeed];
        break;
      case LOOP:
        myYoyo = !myYoyo;
        break;
      case PAUSE:
        mIsPlaying = false;
        myTimer.stop();
        notifyTimeLineListeners(TimeLineCmd.MOTION_STOP, mMotionProgress);
        break;
      case END:
        mIsPlaying = false;
        myTimer.stop();
        notifyTimeLineListeners(TimeLineCmd.MOTION_STOP, mMotionProgress);
        mMotionProgress = 1;
        break;
      case START:
        mIsPlaying = false;
        myTimer.stop();
        notifyTimeLineListeners(TimeLineCmd.MOTION_STOP, mMotionProgress);
        mMotionProgress = 0;
        break;
    }
  }

  private void progress() {
    if (!mIsPlaying) {
      return;
    }
    if (myForwardDirection) {
      mMotionProgress = mMotionProgress + myProgressPerFrame;
      if (mMotionProgress > 1f) {
        if (myYoyo) {
          myForwardDirection = false;
          mMotionProgress = 1 - (mMotionProgress - 1);
        } else {
          mMotionProgress = 0;
        }
      }
    } else { // backward
      mMotionProgress = mMotionProgress - myProgressPerFrame;

      if (mMotionProgress < 0) {
        if (myYoyo) {
          myForwardDirection = true;
          mMotionProgress = -mMotionProgress;
        } else {
          mMotionProgress = 1 + mMotionProgress;
        }
      }
    }
    notifyTimeLineListeners(TimeLineCmd.MOTION_PROGRESS, mMotionProgress);
    repaint();
  }

  private void groupSelected() {
    if (mTimeLine == null || mTimeLine.getSelectedValue() == null
      || mTimeLine.getSelectedValue().mKeyFrames == null) {
      if (mTransitionTag != null) {
        mMotionEditorSelector
          .notifyListeners(MotionEditorSelector.Type.TRANSITION, new MTag[]{mTransitionTag});
      }
      return;
    }

    mMotionEditorSelector.notifyListeners(MotionEditorSelector.Type.KEY_FRAME_GROUP,
      mTimeLine.getSelectedValue().mKeyFrames.toArray(new MTag[0]));
  }

  public void setMTag(MTag transitionTag, MeModel model) {
    MTag newSelection = findSelectedKeyFrameInNewModel(model);

    mTransitionTag = transitionTag;
    mSelectedKeyFrame = null;
    mMeModel = model;
    List<TimeLineRowData> list = transitionTag != null ? buildTransitionList() : Collections.emptyList();
    mTimeLine.setListData(list, model);

    if (newSelection != null) {
      int index = findKeyFrameInRows(list, newSelection);
      if (index >= 0) {
        mTimeLine.setSelectedIndex(index);
        mMotionEditorSelector.notifyListeners(MotionEditorSelector.Type.KEY_FRAME, new MTag[]{newSelection});
        mSelectedKeyFrame = newSelection;
      }
    }
  }

  private MTag findSelectedKeyFrameInNewModel(@NotNull MeModel newModel) {
    if (mSelectedKeyFrame == null) {
      return null;
    }
    MTag oldKeyFrameSet = mSelectedKeyFrame.getParent();
    if (oldKeyFrameSet == null) {
      return null;
    }
    MTag oldTransition = oldKeyFrameSet.getParent();
    if (oldTransition == null) {
      return null;
    }
    MTag transition = newModel.motionScene.getChildTagWithTreeId(MotionSceneAttrs.Tags.TRANSITION, oldTransition.getTreeId());
    if (transition == null) {
      return null;
    }
    for (MTag kfSet : transition.getChildTags(MotionSceneAttrs.Tags.KEY_FRAME_SET)) {
      MTag keyFrame = kfSet.getChildTagWithTreeId(mSelectedKeyFrame.getTagName(), mSelectedKeyFrame.getTreeId());
      if (keyFrame != null) {
        return keyFrame;
      }
    }
    return null;
  }

  private int findKeyFrameInRows(@NotNull List<TimeLineRowData> rows, @NotNull MTag keyFrame) {
    for (int index = 0; index < rows.size(); index++) {
      if (rows.get(index).mKeyFrames.contains(keyFrame)) {
        return index;
      }
    }
    return -1;
  }

  private List<TimeLineRowData> buildTransitionList() {
    List<TimeLineRowData> views = new ArrayList<>();
    TreeMap<String, ArrayList<MTag>> keyMap = new TreeMap<>();

    MTag[] keyFrameSets = mTransitionTag.getChildTags("KeyFrameSet");
    for (int i = 0; i < keyFrameSets.length; i++) {
      MTag keyFrameSet = keyFrameSets[i];
      MTag[] keyFrames = keyFrameSet.getChildTags();

      for (int j = 0; j < keyFrames.length; j++) {
        MTag keyFrame = keyFrames[j];
        String targetKey = buildKey(keyFrame);

        if (!keyMap.containsKey(targetKey)) {
          ArrayList<MTag> list = new ArrayList<>();
          keyMap.put(targetKey, list);
        }
        keyMap.get(targetKey).add(keyFrame);
      }
    }
    for (String id : keyMap.keySet()) {
      TimeLineRowData row = new TimeLineRowData();
      row.mKeyFrames = keyMap.get(id);
      row.buildKey(row.mKeyFrames.get(0));
      row.buildTargetStrings(row.mKeyFrames.get(0));
      views.add(row);
    }

    return views;
  }

  private static String buildKey(MTag keyFrame) {
    String targetKey;
    String target = keyFrame.getAttributeValue("motionTarget");

    targetKey = target;
    if (target != null && target.startsWith("@")) {
      targetKey = "Id:" + Utils.stripID(target);
    } else {
      targetKey = "Tag:" + target;
    }
    String name = keyFrame.getTagName();
    targetKey += name;
    String[] keys = new String[0];
    switch (name) {
      case "KeyPosition":
        keys = MotionLayoutAttrs.KeyPositionKey;
        break;
      case "KeyAttribute":
        keys = MotionLayoutAttrs.KeyAttributesKey;
        break;
      case "KeyCycle":
        keys = MotionLayoutAttrs.KeyCycleKey;
        break;
      case "KeyTimeCycle":
        keys = MotionLayoutAttrs.KeyTimeCycleKey;
        break;
      case "KeyTrigger":
        keys = MotionLayoutAttrs.KeyTriggerKey;
        break;
    }
    for (String key : keys) {
      targetKey += get(keyFrame, key);
    }
    return targetKey;
  }

  private static String get(MTag tag, String attr) {
    String s = tag.getAttributeValue(attr);
    return (s == null) ? "" : attr;
  }

  boolean matches(String target, TimeLineRowData view) {
    if (target == null) {
      return false;
    }
    if (target.startsWith("@id") || target.startsWith("@+id")) {
      return Utils.stripID(target).equals(view.mKey);
    }
    String tag = view.mStartConstraintSet.getAttributeValue("layout_constraintTag");
    if (tag == null) { // TODO walk derived constraints
      System.err.println(
        view.mKey + " " + view.mLayoutView + " id = " + ((view.mLayoutView == null)
          ? view.mLayoutView.getAttributeValue("id") : ""));
      tag = view.mLayoutView.getAttributeValue("layout_constraintTag");
    }
    if (tag.matches(target)) {
      return true;
    }
    return false;
  }

  /**
   * Get and create if does not exist.
   */
  TimeLineRowData addRow(List<TimeLineRowData> views, String viewId) {
    for (TimeLineRowData view : views) {
      if (view.mKey.equals(viewId)) {
        return view;
      }
    }
    TimeLineRowData view = new TimeLineRowData();
    view.mKey = viewId;
    views.add(view);
    return view;
  }

  TimeLineRowData get(List<TimeLineRowData> views, String viewId) {
    for (TimeLineRowData view : views) {
      if (view.mKey.equals(viewId)) {
        return view;
      }
    }
    return null;
  }

  /**
   * Draws the cursor
   *
   * @param g
   * @param c
   */
  private void paintCursor(Graphics g, JComponent c) {
    if (mTimelineStructure.myXTicksPixels == null
      || mTimelineStructure.myXTicksPixels.length == 0) {
      return;
    }
    Graphics2D g2 = (Graphics2D) g;

    int w = c.getWidth();
    int h = c.getHeight();
    int timeStart = MEUI.ourLeftColumnWidth + mTimelineStructure.myXTicksPixels[0];
    int timeWidth = mTimelineStructure.myXTicksPixels[mTimelineStructure.myXTicksPixels.length - 1]
      - mTimelineStructure.myXTicksPixels[0];
    int y = 0;
    int x = timeStart + (int) (mMotionProgress * (timeWidth));
    Color lineColor = MEUI.myTimeCursorColor;
    if (mMouseDown) {
      lineColor = lineColor.brighter();
      int inset = 2;
      int d = (int) (mMotionProgress * 100);
      String digits = Integer.toString(d);
      switch (digits.length()) {
        case 1:
          digits = ".0" + digits;
          break;
        case 2:
          digits = "." + digits;
          break;
        case 3:
          digits = "1.0";
          break;
      }
      FontMetrics fm = g2.getFontMetrics();
      Color orig = g.getColor();
      g.setColor(lineColor);
      Rectangle2D bounds = fm.getStringBounds(digits, g2);
      g2.fillRoundRect((int) (x - bounds.getWidth() / 2 - inset), 0, 2 * inset + (int) bounds.getWidth(),
        2 * inset + (int) bounds.getHeight() + 2, 5, 5);
      g.setColor(orig);
      g2.drawString(digits, (int) (x - bounds.getWidth() / 2), (int) (fm.getAscent() + inset));
      y = (int) (inset * 2 + bounds.getHeight());
    }
    g2.setColor(lineColor);
    g2.drawLine(x, y, x, h);
  }

  /**
   * This is being called by the layer pain to implement the timeline cursor
   *
   * @param e
   */
  private void processMouseDrag(MouseEvent e) {
    int timeStart = MEUI.ourLeftColumnWidth + mTimelineStructure.myXTicksPixels[0];
    int timeWidth =
      mTimelineStructure.myXTicksPixels[mTimelineStructure.myXTicksPixels.length - 1]
        - mTimelineStructure.myXTicksPixels[0];
    float progress = (e.getX() - timeStart) / (float) (timeWidth);
    float error = (float) (2 / timeWidth);
    boolean inRange = progress > -error && progress < 1 + error;
    switch (e.getID()) {
      case MouseEvent.MOUSE_CLICKED: {
        if (inRange) {
          MTag oldSelection = mSelectedKeyFrame;
          selectKeyFrame(progress);
          int index = mTimeLine.getSelectedIndex();
          mTimeLine.setSelectedIndex(index);
          TimeLineRow row = mTimeLine.getTimeLineRow(index);
          if (row == null) {
            return;
          }
          row.setSelectedKeyFrame(mSelectedKeyFrame);
          if (mSelectedKeyFrame != null && oldSelection != mSelectedKeyFrame) {

          }
        }
        if (e.getX() < 20) {
          int index = mTimeLine.getSelectedIndex();

          TimeLineRow row = mTimeLine.getTimeLineRow(index);
          row.toggleGraph();
        }
      }
      break;
      case MouseEvent.MOUSE_PRESSED: {

        mMouseDown = (progress >= 0.0f && progress <= 1.0f);

        repaint();
      }
      break;
      case MouseEvent.MOUSE_RELEASED: {
        mMouseDown = false;
        repaint();
      }
    }

    if (!mMouseDown) {
      return;
    }

    if ((e.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) != 0) {

      if (progress >= 0.0f && progress <= 1.0f) {
        mMotionProgress = progress;
        notifyTimeLineListeners(TimeLineCmd.MOTION_PROGRESS, progress);
        repaint();
      } else if (progress <= 0.0f && mMotionProgress != 0.0f) {
        mMotionProgress = 0.0f;
        notifyTimeLineListeners(TimeLineCmd.MOTION_PROGRESS, progress);
        repaint();
      } else if (progress >= 1.0f && mMotionProgress != 1.0f) {
        mMotionProgress = 1.0f;
        notifyTimeLineListeners(TimeLineCmd.MOTION_PROGRESS, progress);
        repaint();
      }
    }
  }

  private void selectKeyFrame(float progress) {
    if (mTimeLine == null || mTimeLine.getSelectedValue() == null) {
      return;
    }
    mSelectedKeyFrame = null;
    ArrayList<MTag> f = mTimeLine.getSelectedValue().mKeyFrames;
    float minDist = Float.MAX_VALUE;
    MTag minTag = null;
    for (MTag tag : f) {
      String posString = tag.getAttributeValue("framePosition");
      if (posString != null) {
        float dist = Math.abs(progress - Integer.parseInt(posString) / 100f);

        if (dist < minDist) {
          minTag = tag;
          minDist = dist;
        }
      }
    }
    if (minDist < 0.1f) {
      mMotionEditorSelector.notifyListeners(MotionEditorSelector.Type.KEY_FRAME, new MTag[]{minTag});
      mSelectedKeyFrame = minTag;
      repaint();
    }
  }

  public void setListeners(MotionEditorSelector listeners) {
    mMotionEditorSelector = listeners;
  }
}
