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
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.StringMTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Track;
import com.android.tools.idea.uibuilder.handlers.motion.editor.timeline.TimeLineTopLeft.TimelineCommands;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MTagActionListener;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MeModel;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector.TimeLineCmd;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector.TimeLineListener;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.Utils;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.Timer;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.LayerUI;

/**
 * The panel that displays the timeline
 */
public class TimeLinePanel extends JPanel {
  private static int PLAY_TIMEOUT = 60 * 60 * 1000;
  private static int MS_PER_FRAME = 15;
  private static float TIMELINE_MIN = 0.0f;
  private static float TIMELINE_MAX = 100.0f;
  private static float[] ourSpeedsMultipliers = {0.25f, 0.5f, 1f, 2f, 4f};
  MotionEditorSelector mMotionEditorSelector;
  private TimelineStructure mTimelineStructure = new TimelineStructure();
  private TimeLineTopPanel myTimeLineTopPanel = new TimeLineTopPanel(mTimelineStructure);
  private MTag mSelectedKeyFrame;
  private boolean mMouseDown = false;
  private METimeLine mTimeLine = new METimeLine();
  private JScrollPane myScrollPane = new MEScrollPane(mTimeLine);
  private TimeLineTopLeft mTimeLineTopLeft = new TimeLineTopLeft();
  private MeModel mMeModel;
  private MTag mTransitionTag;
  private float mMotionProgress; // from 0 .. 1;
  private Timer myTimer;
  private Timer myPlayLimiter;
  private int myYoyo = 0;
  private int mDuration = 1000;
  private float myProgressPerMillisecond = 1 / (float)mDuration;
  private long last_time;
  private int mCurrentSpeed = 2;
  private boolean mIsPlaying = false;
  private ArrayList<TimeLineListener> mTimeLineListeners = new ArrayList<>();
  private int mDirection = 1;
  int[] myXPoints = new int[5];
  int[] myYPoints = new int[5];
  private MTagActionListener mListener;
  Timer myMouseDownTimer;
  JPopupMenu myPlaybackSpeedPopupMenu = new JPopupMenu();

  @Override
  public void updateUI() {
    super.updateUI();
    if (mTimeLineTopLeft != null) {
      mTimeLineTopLeft.updateUI();
    }
    if (mTimeLine != null) {
      mTimeLine.updateUI();
    }
  }
  public TimeLinePanel() {
    super(new BorderLayout());

    for (int i = 0; i < ourSpeedsMultipliers.length; i++) {
      myPlaybackSpeedPopupMenu.add(ourSpeedsMultipliers[i] + "x").addActionListener(createPlaybackSpeedPopupMenuActionListener(i));
    }

    JPanel top = new JPanel(new BorderLayout());
    top.add(myTimeLineTopPanel, BorderLayout.CENTER);
    top.add(mTimeLineTopLeft, BorderLayout.WEST);
    myScrollPane.setColumnHeaderView(top);
    myScrollPane.setBorder(BorderFactory.createEmptyBorder());
    int flags = 0;

    mTimeLine.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        int code = e.getExtendedKeyCode();

        switch (code) {


          case KeyEvent.VK_V:
            if (!e.isControlDown()) {
              break;
            }
            // Fallthrough
          case KeyEvent.VK_PASTE:
            paste();
            break;

            //////////////////////////////////////////////////////////////
          case KeyEvent.VK_C:
            if (!e.isControlDown()) {
              break;
            }
            // Fallthrough copy
          case KeyEvent.VK_COPY:
            if (e.isControlDown()) {
              if (mSelectedKeyFrame != null) {
                MEUI.copy(mSelectedKeyFrame);
              }
            }
            break;
            /////////////////////////////////////////////////////////
          case KeyEvent.VK_X:
            if (!e.isControlDown()) {
              break;
            }
            // Fallthrough cut
          case KeyEvent.VK_CUT:
            if (e.isControlDown()) {
              if (mSelectedKeyFrame != null) {
                MEUI.cut(mSelectedKeyFrame);
              }
            }
            break;
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
          case KeyEvent.VK_DELETE:
          case KeyEvent.VK_BACK_SPACE:
            if (mListener != null) {
              if (mSelectedKeyFrame != null) {
                mListener.delete(new MTag[]{mSelectedKeyFrame}, 0);
              }
            }
            break;
          case KeyEvent.VK_LEFT: {
            TimeLineRowData rowData = mTimeLine.getSelectedValue();
            int numOfKeyFrames = rowData.mKeyFrames.size();
            int indexInRow = -1;
            for (int i = 0; i < numOfKeyFrames; i++) {
              if (rowData.mKeyFrames.get(i).equals(mSelectedKeyFrame)) {
                indexInRow = i;
                break;
              }
            }

            int selIndex = (indexInRow - 1 + numOfKeyFrames) % numOfKeyFrames;
            mSelectedKeyFrame = rowData.mKeyFrames.get(selIndex);

            mTimeLine.getTimeLineRow(mTimeLine.getSelectedIndex()).setSelectedKeyFrame(mSelectedKeyFrame);
            Track.timelineTableSelect(mMeModel.myTrack);
            mMotionEditorSelector.notifyListeners(MotionEditorSelector.Type.KEY_FRAME, new MTag[]{mSelectedKeyFrame}, flags);
            if (mListener != null && mSelectedKeyFrame != null) {
              mListener.select(mSelectedKeyFrame, 0);
            }
          }
          break;
          case KeyEvent.VK_RIGHT: {
            TimeLineRowData rowData = mTimeLine.getSelectedValue();
            int numOfKeyFrames = rowData.mKeyFrames.size();
            int indexInRow = -1;
            for (int i = 0; i < numOfKeyFrames; i++) {
              if (rowData.mKeyFrames.get(i).equals(mSelectedKeyFrame)) {
                indexInRow = i;
                break;
              }
            }
            int selIndex = (indexInRow + 1) % numOfKeyFrames;
            mSelectedKeyFrame = rowData.mKeyFrames.get(selIndex);
            mTimeLine.getTimeLineRow(mTimeLine.getSelectedIndex()).setSelectedKeyFrame(mSelectedKeyFrame);
            Track.timelineTableSelect(mMeModel.myTrack);
            mMotionEditorSelector.notifyListeners(MotionEditorSelector.Type.KEY_FRAME, new MTag[]{mSelectedKeyFrame}, flags);
            if (mListener != null && mSelectedKeyFrame != null) {
              mListener.select(mSelectedKeyFrame, 0);
            }
          }
          break;
        }
      }
    });

    mTimeLine.setFocusable(true);
    mTimeLine.setRequestFocusEnabled(true);


    mTimeLineTopLeft.addControlsListener((e, mode) -> {
      performCommand(e, mode);
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
        JLayer jlayer = (JLayer)c;
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
      if (!e.getValueIsAdjusting()) {
        groupSelected();
      }
    });
  }

  private void paste() {
    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

    try {
      String buff = (String)(clipboard.getContents(this).getTransferData(DataFlavor.stringFlavor));
      StringMTag pastedTag = StringMTag.parse(buff);
      HashMap<String, MTag.Attribute> attr = pastedTag.getAttrList();
      if (mSelectedKeyFrame == null) {
        TimeLineRowData rowData = mTimeLine.getSelectedValue();
        if (rowData.mKeyFrames.isEmpty()) {
          return;
        }
        mSelectedKeyFrame = rowData.mKeyFrames.get(0);
      }

      MTag keyFrameSet = mSelectedKeyFrame.getParent();
      MTag.TagWriter writer = keyFrameSet.getChildTagWriter(pastedTag.getTagName());
      for (String s : attr.keySet()) {
        MTag.Attribute a = attr.get(s);
        if (a == null || a.mAttribute.equals("framePosition")) {
          writer.setAttribute(a.mNamespace, a.mAttribute, Integer.toString((int)(mMotionProgress * 100 + 0.5)));
        }
        else {

          writer.setAttribute(a.mNamespace, a.mAttribute, a.mValue);
        }
      }

      MTag[] children = pastedTag.getChildTags();
      for (int i = 0; i < children.length; i++) {
        MTag child = children[i];
        MTag.TagWriter cw = writer.getChildTagWriter(child.getTagName());
        HashMap<String, MTag.Attribute> cwAttrMap = child.getAttrList();
        for (String cwAttrStr : cwAttrMap.keySet()) {
          MTag.Attribute cwAttr = cwAttrMap.get(cwAttrStr);
          cw.setAttribute(cwAttr.mNamespace, cwAttr.mAttribute, cwAttr.mValue);
        }
      }
      mSelectedKeyFrame = writer.commit("paste");
    }
    catch (UnsupportedFlavorException e) {
      e.printStackTrace();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void setActionListener(MTagActionListener l) {
    mListener = l;
  }

  public void stopAnimation() {
    performCommand(TimelineCommands.PAUSE, 0);
    resetMotionProgress();
  }

  private static String buildKey(MTag keyFrame) {
    String targetKey;
    String target = keyFrame.getAttributeValue("motionTarget");

    targetKey = target;
    if (target != null && target.startsWith("@")) {
      targetKey = "Id:" + Utils.stripID(target);
    }
    else {
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

  public void clearSelection() {
    mSelectedKeyFrame = null;
    int n = mTimeLine.getComponentCount();
    if (n == 0 || mTimeLine.mSelectedIndex >= n) {
      return;
    }
    Component component = mTimeLine.getComponent(mTimeLine.mSelectedIndex);
    if (component instanceof TimeLineRow) {
      TimeLineRow child = ((TimeLineRow)component);
      child.setSelected(true);
      repaint();
    }
  }

  public void addTimeLineListener(TimeLineListener timeLineListener) {
    mTimeLineListeners.add(timeLineListener);
  }

  public void notifyTimeLineListeners(TimeLineCmd cmd, Float value) {
    for (TimeLineListener listener : mTimeLineListeners) {
      listener.command(cmd, value);
    }
  }

  private void refreshCycling(boolean cycle) {
    MTag[] kef = mTransitionTag.getChildTags(MotionSceneAttrs.Tags.KEY_FRAME_SET);
    if (kef == null || kef.length == 0) {
      cycle = false;
    } else  {
      MTag[] timeCycle = kef[0].getChildTags(MotionSceneAttrs.Tags.KEY_TIME_CYCLE);
      if (timeCycle == null || timeCycle.length == 0) {
        cycle = false;
      }
    }

    if (cycle) {
      if (myMouseDownTimer != null) {
        myMouseDownTimer.stop();
        myMouseDownTimer = null;
      }
      myMouseDownTimer = new Timer(MS_PER_FRAME, e -> {
        notifyTimeLineListeners(TimeLineCmd.MOTION_PROGRESS, mMotionProgress);
      });
      myMouseDownTimer.start();
    } else {
      if(myMouseDownTimer != null) {
        myMouseDownTimer.stop();
        myMouseDownTimer = null;
      }
    }
  }
  private void createTimer() {
    if (myTimer == null) {
      myTimer = new Timer(MS_PER_FRAME, e -> {
        progress();
      });
      myTimer.setRepeats(true);
      myTimer.start();
    }
    if (myPlayLimiter == null) {
      myPlayLimiter = new Timer(PLAY_TIMEOUT, e -> {
        destroyTimer();
      });
      myPlayLimiter.setRepeats(false);
      myPlayLimiter.start();
    }
  }

  private void watchdog() {
    if (myPlayLimiter != null) {
      myPlayLimiter.restart();
    }
  }

  private void destroyTimer() {
    if (myTimer != null) {
      myTimer.stop();
      myTimer = null;
    }
    if (myPlayLimiter != null) {
      myPlayLimiter.stop();
      myPlayLimiter = null;
    }
  }

  private ActionListener createPlaybackSpeedPopupMenuActionListener(int playbackSpeedIndex) {
    return new ActionListener() {
      int speedIndex = playbackSpeedIndex;
      @Override
      public void actionPerformed(ActionEvent e) {
        Track.animationSpeed(mMeModel.myTrack);
        mCurrentSpeed = speedIndex;
        myProgressPerMillisecond = ourSpeedsMultipliers[mCurrentSpeed] / (float)mDuration;
        mTimeLineTopLeft.mSlow.setToolTipText(mDuration + " x " + ourSpeedsMultipliers[mCurrentSpeed]);
        mTimeLineTopLeft.mSlow.setText(ourSpeedsMultipliers[mCurrentSpeed] + "x");
      }
    };
  }

  private void showPlaybackSpeedPopup() {
    myPlaybackSpeedPopupMenu.show(mTimeLineTopLeft.mSlow, 0, 20);
  }

  public boolean isPlaying() {
    return mIsPlaying;
  }

  public void resetMotionProgress() {
    mMotionProgress = 0;
    notifyTimeLineListeners(TimeLineCmd.MOTION_PROGRESS, mMotionProgress);
    notifyTimeLineListeners(TimeLineCmd.MOTION_STOP, mMotionProgress);
    repaint();
  }

  private void performCommand(TimelineCommands e, int mode) {
    watchdog();
    switch (e) {
      case PLAY:
        last_time = System.nanoTime();
        Track.playAnimation(mMeModel.myTrack);
        createTimer();
        notifyTimeLineListeners(TimeLineCmd.MOTION_PLAY, mMotionProgress);
        mIsPlaying = true;
        break;
      case SPEED:
        showPlaybackSpeedPopup();
        break;
      case LOOP:
        Track.animationDirectionToggle(mMeModel.myTrack);
        myYoyo = mode;
        break;
      case PAUSE:
        mIsPlaying = false;
        destroyTimer();
        notifyTimeLineListeners(TimeLineCmd.MOTION_STOP, mMotionProgress);
        mTimeLineTopLeft.displayPlay();
        break;
      case END:
        Track.animationEnd(mMeModel.myTrack);
        mIsPlaying = false;
        destroyTimer();
        mMotionProgress = 1;
        notifyTimeLineListeners(TimeLineCmd.MOTION_PROGRESS, mMotionProgress);
        notifyTimeLineListeners(TimeLineCmd.MOTION_STOP, mMotionProgress);
        break;
      case START:
        Track.animationStart(mMeModel.myTrack);
        mIsPlaying = false;
        destroyTimer();
        mMotionProgress = 0;
        notifyTimeLineListeners(TimeLineCmd.MOTION_PROGRESS, mMotionProgress);
        notifyTimeLineListeners(TimeLineCmd.MOTION_STOP, mMotionProgress);
        break;
    }
  }

  private void progress() {
    if (!mIsPlaying || mMouseDown) {
      return;
    }
    long time = System.nanoTime();

    switch (myYoyo) {
      case 0:
        mMotionProgress += myProgressPerMillisecond * ((time - last_time) * 1E-6f);
        if (mMotionProgress > 1f) {
          notifyTimeLineListeners(TimeLineCmd.MOTION_PROGRESS, 1f);

          mMotionProgress = mMotionProgress - 1;
        }
        break;
      case 1:
        mMotionProgress -= myProgressPerMillisecond * ((time - last_time) * 1E-6f);
        if (mMotionProgress < 0f) {
          notifyTimeLineListeners(TimeLineCmd.MOTION_PROGRESS, 0f);
          mMotionProgress = 1 + mMotionProgress;
        }
        break;
      case 2:
        mMotionProgress += (mDirection) * myProgressPerMillisecond * ((time - last_time) * 1E-6f);
        if (mMotionProgress < 0f) {
          notifyTimeLineListeners(TimeLineCmd.MOTION_PROGRESS, 0f);
          mDirection = +1;
          mMotionProgress = -mMotionProgress;
        }
        else if (mMotionProgress > 1f) {
          notifyTimeLineListeners(TimeLineCmd.MOTION_PROGRESS, 1f);
          mDirection = -1;
          mMotionProgress = 1 - (mMotionProgress - 1);
        }
        break;
    }
    last_time = time;

    notifyTimeLineListeners(TimeLineCmd.MOTION_PROGRESS, mMotionProgress);
    repaint();
  }

  private void groupSelected() {
    if (mTimeLine == null || mTimeLine.getSelectedValue() == null
        || mTimeLine.getSelectedValue().mKeyFrames == null) {
      if (mTransitionTag != null) {
        mMotionEditorSelector
          .notifyListeners(MotionEditorSelector.Type.TRANSITION, new MTag[]{mTransitionTag}, 0);
      }
      return;
    }

    mMotionEditorSelector.notifyListeners(MotionEditorSelector.Type.KEY_FRAME_GROUP,
                                          mTimeLine.getSelectedValue().mKeyFrames.toArray(new MTag[0]), 0);
  }

  public void setMTag(MTag transitionTag, MeModel model) {
    MTag newSelection = findSelectedKeyFrameInNewModel(model);

    mTransitionTag = transitionTag;
    if (mTransitionTag != null) {
      String duration = mTransitionTag.getAttributeValue("duration");
      if (duration != null) {
        try {
          int durationInt = Integer.parseInt(duration);
          mDuration = durationInt;
          if (mDuration == 0) {
            mDuration = 1000;
          }
          myProgressPerMillisecond = ourSpeedsMultipliers[mCurrentSpeed] / (float)mDuration;
        }
        catch (NumberFormatException e) {
        }
      }
    }
    else {
      mDuration = 1000;
      mCurrentSpeed = 2;
      myProgressPerMillisecond = 1 / (float)mDuration;
    }

    if (mTimeLineTopLeft != null && mTimeLineTopLeft.mSlow != null) {
      mTimeLineTopLeft.mSlow.setToolTipText(mDuration + " x " + ourSpeedsMultipliers[mCurrentSpeed]);
    }

    mSelectedKeyFrame = null;
    mMeModel = model;
    List<TimeLineRowData> list = transitionTag != null ? buildTransitionList() : Collections.emptyList();
    mTimeLine.setListData(list, model);
    if (transitionTag != null) {
      mMotionEditorSelector.notifyListeners(MotionEditorSelector.Type.TRANSITION, new MTag[]{transitionTag}, 0);
    }

    if (newSelection != null) {
      int index = findKeyFrameInRows(list, newSelection);
      if (index >= 0) {
        mTimeLine.setSelectedIndex(index);
        mMotionEditorSelector.notifyListeners(MotionEditorSelector.Type.KEY_FRAME, new MTag[]{newSelection}, 0);
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
    Graphics2D g2 = (Graphics2D)g;

    int w = c.getWidth();
    int h = c.getHeight();
    int timeStart = MEUI.ourLeftColumnWidth + mTimelineStructure.myXTicksPixels[0];
    int timeWidth = mTimelineStructure.myXTicksPixels[mTimelineStructure.myXTicksPixels.length - 1]
                    - mTimelineStructure.myXTicksPixels[0];
    int y = 0;
    int x = timeStart + (int)(mMotionProgress * (timeWidth));
    Color lineColor = MEUI.myTimeCursorColor;
    int inset = 2;
    int d = (int)(mMotionProgress * 100);
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
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(lineColor);
    Rectangle2D bounds = fm.getStringBounds(digits, g2);
    int xStart = (int)(x - bounds.getWidth() / 2 - inset);
    int halfWidth = (2 * inset + (int)bounds.getWidth()) / 2;
    int yHeight = 2 * inset + (int)bounds.getHeight() + 2;
    if (mMouseDown) {
      myXPoints[0] = xStart;
      myYPoints[0] = 0;
      myXPoints[1] = xStart;
      myYPoints[1] = yHeight;
      myXPoints[2] = xStart + halfWidth;
      myYPoints[2] = yHeight + 10;
      myXPoints[3] = xStart + halfWidth * 2;
      myYPoints[3] = yHeight;
      myXPoints[4] = xStart + halfWidth * 2;
      myYPoints[4] = 0;

      g2.fillPolygon(myXPoints, myYPoints, 5);
      g.setColor(MEUI.Graph.ourCursorTextColor);
      g2.drawString(digits, (int)(x - bounds.getWidth() / 2), (int)(fm.getAscent() + inset));
    }
    else {
      myXPoints[0] = xStart;
      myYPoints[0] = yHeight;
      myXPoints[1] = xStart + halfWidth;
      myYPoints[1] = yHeight + 10;
      myXPoints[2] = xStart + halfWidth * 2;
      myYPoints[2] = yHeight;
      g2.fillPolygon(myXPoints, myYPoints, 3);
    }

    g2.setColor(lineColor);
    g2.drawLine(xStart + halfWidth, yHeight, xStart + halfWidth, h);
  }

  /**
   * This is being called by the layer pain to implement the timeline cursor
   *
   * @param e
   */
  private void processMouseDrag(MouseEvent e) {
    if (mTimelineStructure == null) {
      return;
    }
    int timeStart = MEUI.ourLeftColumnWidth + mTimelineStructure.myXTicksPixels[0];
    int timeWidth =
      mTimelineStructure.myXTicksPixels[mTimelineStructure.myXTicksPixels.length - 1]
      - mTimelineStructure.myXTicksPixels[0];
    float progress = (e.getX() - timeStart) / (float)(timeWidth);
    float error = (float)(2 / timeWidth);
    boolean inRange = progress > -error && progress < 1 + error;
    switch (e.getID()) {
      case MouseEvent.MOUSE_CLICKED: {
        mTimeLine.requestFocus();
        if (inRange) {
          MTag oldSelection = mSelectedKeyFrame;
          selectKeyFrame(progress);
          int index = mTimeLine.getSelectedIndex();
          mTimeLine.setSelectedIndex(index);
          TimeLineRow row = mTimeLine.getTimeLineRow(index);
          if (row == null) {
            return;
          }
          Track.timelineTableSelect(mMeModel.myTrack);
          row.setSelectedKeyFrame(mSelectedKeyFrame);
          if (mSelectedKeyFrame != null && oldSelection != mSelectedKeyFrame) {

          }
        }
        if (e.getX() < mTimeLineTopLeft.getWidth() && e.getY() > mTimeLineTopLeft.getHeight()) {
          int index = mTimeLine.getSelectedIndex();

          TimeLineRow row = mTimeLine.getTimeLineRow(index);
          if (row != null) { // TODO: Check why this is being hit
            row.toggleGraph();
          }
        }
      }
      break;
      case MouseEvent.MOUSE_PRESSED: {
        mMouseDown = (progress >= 0.0f && progress <= 1.0f);
        if (mMouseDown) {
          notifyTimeLineListeners(TimeLineCmd.MOTION_SCRUB, mMotionProgress);
        }
        repaint();
      }
      break;
      case MouseEvent.MOUSE_RELEASED: {
        if (mMouseDown) {
          notifyTimeLineListeners(TimeLineCmd.MOTION_STOP, mMotionProgress);
        }
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
      }
      else if (progress <= 0.0f && mMotionProgress != 0.0f) {
        mMotionProgress = 0.0f;
        notifyTimeLineListeners(TimeLineCmd.MOTION_PROGRESS, progress);
        repaint();
      }
      else if (progress >= 1.0f && mMotionProgress != 1.0f) {
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
      mMotionEditorSelector.notifyListeners(MotionEditorSelector.Type.KEY_FRAME, new MTag[]{minTag}, 0);
      mSelectedKeyFrame = minTag;
      repaint();
    }
  }

  public void setListeners(MotionEditorSelector listeners) {
    mMotionEditorSelector = listeners;
  }

  public int getYoyoMode() {
    return myYoyo;
  }

  public float getSpeedMultiplier() {
    return ourSpeedsMultipliers[mCurrentSpeed];
  }

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
        }
        else {
          if (parent_height - y <= 0) {
            c.setBounds(0, y, parent_width, 0);
            c.setVisible(false);
          }
          else {
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
        g.setColor(MEUI.myGridColor);
        TimeLineRow.drawTicks(g, mTimelineStructure, getHeight());
        // draw line on the divider
        g.setColor(MEUI.ourBorder);
        g.fillRect(MEUI.ourLeftColumnWidth, 0, 1, getHeight());
      }
    };
    int mSelectedIndex = 0;
    ArrayList<ListSelectionListener> listeners = new ArrayList<>();

    METimeLine() {
      super(new VertLayout());
      add(pad);
      pad.setBackground(MEUI.ourAvgBackground);
      MouseAdapter mouseAdapter = new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
            refreshCycling(true);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
          refreshCycling(false);
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

    void addListSelectionListener(ListSelectionListener l) {
      listeners.add(l);
    }

    public TimeLineRow getTimeLineRow(int index) {
      if (getComponent(index) instanceof TimeLineRow) {
        return (TimeLineRow)getComponent(index);
      }
      return null;
    }

    public TimeLineRowData getSelectedValue() {
      if (getComponent(mSelectedIndex) instanceof TimeLineRow) {
        return ((TimeLineRow)getComponent(mSelectedIndex)).mRow;
      }
      return null;
    }

    public void setListData(List<TimeLineRowData> list, MeModel model) {
      Component[] children = getComponents();
      removeAll();
      String lastName = null;
      int n = Math.min(children.length - 1, list.size());
      for (int i = 0; i < n; i++) {
        TimeLineRow child = (TimeLineRow)children[i];
        TimeLineRowData data = list.get(i);
        boolean showTitle = !(data.mName != null && data.mName.equals(lastName));
        child.setRowData(model, data, i, false, false, mSelectedKeyFrame, showTitle);
        lastName = data.mName;
        add(child);
      }
      if (n >= 0 && list.size() > n) {
        for (int i = n; i < list.size(); i++) {
          TimeLineRow child = new TimeLineRow(mTimelineStructure);
          TimeLineRowData data = list.get(i);
          boolean showTitle = !(data.mName != null && data.mName.equals(lastName));
          if (data == null || data.mName == null) {
            showTitle = false;
          } else {
            lastName = data.mName;
          }
          child.setRowData(model, data, i, false, false, mSelectedKeyFrame, showTitle);

          add(child);
        }
      }
      add(pad);
      revalidate();
    }

    public int getSelectedIndex() {
      return mSelectedIndex;
    }

    public void setSelectedIndex(int index) {
      if (index < 0) {
        return;
      }

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
        TimeLineRow child = ((TimeLineRow)comp);

        child.setSelected(false);
        child.repaint();
      }
      TimeLineRow child = ((TimeLineRow)getComponent(mSelectedIndex));
      child.setSelected(true);
      child.repaint();
    }

    private void notifySelectionListener(int index) {
      ListSelectionEvent event = new ListSelectionEvent(this, index, index, false);

      for (ListSelectionListener listener : listeners) {
        listener.valueChanged(event);
      }
    }
  }


}
