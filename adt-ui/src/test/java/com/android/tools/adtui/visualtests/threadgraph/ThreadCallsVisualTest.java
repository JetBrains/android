/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.adtui.visualtests.threadgraph;

import com.android.tools.adtui.*;
import com.android.tools.adtui.model.*;
import com.android.tools.adtui.chart.hchart.HTreeChart;
import com.android.tools.adtui.chart.hchart.JavaMethodHRenderer;
import com.android.tools.adtui.chart.hchart.Method;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.adtui.visualtests.VisualTest;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.components.JBPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class ThreadCallsVisualTest extends VisualTest implements ActionListener {

  private static final String ACTION_START_RECORDING = "start_recording";
  private static final String ACTION_STOP_RECORDING = "stop_recording";
  private static final String ACTION_SAVE_RECORDING = "save_recording";
  private static final String ACTION_LOAD_RECORDING = "load_recording";
  private static final String ACTION_THREAD_SELECTED = "thread_selected";

  private HTreeChart mChart;
  private HashMap<String, DefaultHNode<Method>> forest;
  private JButton mRecordButton;
  private JButton mSaveButton;
  private JButton mLoadButton;
  private JComboBox mComboBox;
  private Sampler mSampler;
  private DefaultHNode<Method> mtree;

  private Range mTimeSelectionRangeUs;
  private Range mTimeGlobalRangeUs;

  private SelectionComponent mSelector;
  private AxisComponent mAxis;

  @NotNull
  private JScrollBar mScrollBar;
  private final AxisComponentModel mAxisModel;

  public ThreadCallsVisualTest() {
    this.mTimeGlobalRangeUs = new Range(0, 0);

    this.mAxisModel = new AxisComponentModel(mTimeGlobalRangeUs, TimeAxisFormatter.DEFAULT);
    this.mAxis = new AxisComponent(mAxisModel, AxisComponent.AxisOrientation.BOTTOM);

    this.mTimeSelectionRangeUs = new Range(0, 0);

    this.mChart = new HTreeChart<DefaultHNode<Method>>(null, mTimeSelectionRangeUs, HTreeChart.Orientation.BOTTOM_UP);
    this.mChart.setHRenderer(new JavaMethodHRenderer());

    SelectionModel model = new SelectionModel(mTimeSelectionRangeUs);
    mSelector = new SelectionComponent(model, mTimeGlobalRangeUs);
  }

  @Override
  public String getName() {
    return "Thread stacks";
  }

  @Override
  protected List<Updatable> createModelList() {
    List<Updatable> list = new ArrayList<>();
    list.add(mAxisModel);
    return list;
  }

  @Override
  protected List<AnimatedComponent> getDebugInfoComponents() {
    return Arrays.asList(mChart, mSelector, mAxis);
  }

  @Override
  protected void populateUi(@NotNull JPanel mainPanel) {
    mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

    JBPanel controlPanel = new JBPanel();
    controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.X_AXIS));

    JBPanel buttonsPanel = new JBPanel();
    buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.Y_AXIS));
    mRecordButton = new JButton("Record");
    mRecordButton.setActionCommand(ACTION_START_RECORDING);
    mRecordButton.addActionListener(this);
    buttonsPanel.add(mRecordButton);

    mLoadButton = new JButton("Load");
    mLoadButton.setActionCommand(ACTION_LOAD_RECORDING);
    mLoadButton.addActionListener(this);
    buttonsPanel.add(mLoadButton);

    mSaveButton = new JButton("Save");
    mSaveButton.setActionCommand(ACTION_SAVE_RECORDING);
    mSaveButton.addActionListener(this);
    buttonsPanel.add(mSaveButton);

    mComboBox = new JComboBox<String>(new String[0]) {
      @Override
      public Dimension getMaximumSize() {
        Dimension max = super.getMaximumSize();
        max.height = getPreferredSize().height;
        return max;
      }
    };
    mComboBox.addActionListener(this);
    mComboBox.setActionCommand(ACTION_THREAD_SELECTED);

    JBPanel viewControlPanel = new JBPanel();
    viewControlPanel.setLayout(new BoxLayout(viewControlPanel, BoxLayout.Y_AXIS));
    JLayeredPane mockTimelinePane = createMockTimeline();
    viewControlPanel.add(mockTimelinePane);
    viewControlPanel.add(mComboBox);

    controlPanel.add(buttonsPanel);
    controlPanel.add(viewControlPanel);

    mainPanel.add(controlPanel);

    JBPanel viewPanel = new JBPanel();
    viewPanel.setLayout(new BoxLayout(viewPanel, BoxLayout.X_AXIS));
    viewPanel.add(mChart);
    mScrollBar = new JScrollBar(JScrollBar.VERTICAL);
    mScrollBar.addAdjustmentListener(e -> {
      Range yRange = mChart.getYRange();
      int yOffset = e.getValue();
      yRange.setMin(yOffset);
    });

    viewPanel.add(mScrollBar);
    mainPanel.add(viewPanel);
  }

  /**
   * Invoked when an action occurs.
   */
  @Override
  public void actionPerformed(ActionEvent e) {
    if (ACTION_START_RECORDING.equals(e.getActionCommand())) {
      if (mSampler == null) {
        mSampler = new Sampler();
      }
      mSampler.startSampling();
      mRecordButton.setActionCommand(ACTION_STOP_RECORDING);
      mRecordButton.setText("Stop Recording");
    }
    else if (ACTION_STOP_RECORDING.equals(e.getActionCommand())) {
      mSampler.stopSampling();
      mRecordButton.setActionCommand(ACTION_START_RECORDING);
      mRecordButton.setText("Record");
      setData(mSampler.getData());

    }
    else if (ACTION_SAVE_RECORDING.equals(e.getActionCommand())) {

    }
    else if (ACTION_LOAD_RECORDING.equals(e.getActionCommand())) {

    }
    else if (ACTION_THREAD_SELECTED.equals(e.getActionCommand())) {
      int selected = mComboBox.getSelectedIndex();
      if (selected >= 0 && selected < mComboBox.getItemCount()) {
        String threadName = (String)mComboBox.getSelectedItem();
        mtree = forest.get(threadName);
        mChart.setHTree(mtree);
        double start = mtree.getFirstChild().getStart();
        double end = mtree.getLastChild().getEnd();

        mTimeGlobalRangeUs.setMin(start);
        mTimeGlobalRangeUs.setMax(end);
        mTimeSelectionRangeUs.setMin(start);
        mTimeSelectionRangeUs.setMax(end);

        mScrollBar.setValues(0, mChart.getHeight(), 0, mChart.getMaximumHeight());
      }
    }
  }

  public void setData(HashMap<String, DefaultHNode<Method>> forest) {
    this.forest = forest;
    mComboBox.removeAllItems();
    for (String threadName : forest.keySet()) {
      mComboBox.addItem(threadName);
    }
  }

  private JLayeredPane createMockTimeline() {
    JBLayeredPane timelinePane = new JBLayeredPane() {
      @Override
      public Dimension getMaximumSize() {
        Dimension max = super.getMaximumSize();
        max.height = getPreferredSize().height;
        return max;
      }
    };
    timelinePane.add(mAxis);
    timelinePane.add(mSelector);
    timelinePane.setPreferredSize(new Dimension(Integer.MAX_VALUE, 100));

    return timelinePane;
  }
}
