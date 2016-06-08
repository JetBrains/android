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
package com.android.tools.idea.monitor.tool;

import com.android.tools.adtui.*;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.common.formatter.TimeAxisFormatter;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.ddms.DevicePanel;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.datastore.SeriesDataStoreImpl;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.idea.monitor.ui.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.TimeAxisSegment;
import com.android.tools.idea.monitor.ui.cpu.view.CpuUsageSegment;
import com.android.tools.idea.monitor.ui.memory.view.MemorySegment;
import com.android.tools.idea.monitor.ui.network.view.NetworkSegment;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AndroidMonitorToolWindow implements Disposable {

  private static final int CHOREOGRAPHER_FPS = 60;

  private static final int TOOLBAR_HORIZONTAL_GAP = JBUI.scale(5);

  // Segment dimensions.
  private static final int MONITOR_MAX_HEIGHT = JBUI.scale(Short.MAX_VALUE);

  private static final int MONITOR_PREFERRED_HEIGHT = JBUI.scale(200);

  private static final int TIME_AXIS_HEIGHT = JBUI.scale(20);

  @NotNull
  private final Project myProject;

  @NotNull
  private final JPanel myComponent;

  @NotNull
  private final Choreographer myChoreographer;

  @NotNull
  private SeriesDataStore myDataStore;

  private SelectionComponent mySelection;

  private Range myXRange;

  private AxisComponent myTimeAxis;

  private RangeScrollbar myScrollbar;

  private JPanel mySegmentsContainer;

  private EventDispatcher<ProfilerEventListener> myEventDispatcher;

  private AccordionLayout mySegmentsLayout;

  private JButton myCollapseSegmentsButton;

  public AndroidMonitorToolWindow(@NotNull final Project project) {
    myProject = project;
    myComponent = new JPanel(new BorderLayout());
    myDataStore = new SeriesDataStoreImpl();
    myChoreographer = new Choreographer(CHOREOGRAPHER_FPS, myComponent);
    myChoreographer.register(createComponentsList());
    myEventDispatcher = EventDispatcher.create(ProfilerEventListener.class);
    populateUi();
  }

  @Override
  public void dispose() {
  }

  private List<Animatable> createComponentsList() {
    Range xGlobalRange = new Range();
    AnimatedTimeRange animatedTimeRange = new AnimatedTimeRange(xGlobalRange, System.currentTimeMillis());
    Range xSelectionRange = new Range();

    myXRange = new Range();
    myScrollbar = new RangeScrollbar(xGlobalRange, myXRange);

    // add horizontal time axis
    myTimeAxis = new AxisComponent(myXRange, xGlobalRange, "TIME", AxisComponent.AxisOrientation.BOTTOM, 0, 0, false,
                                   TimeAxisFormatter.DEFAULT);

    mySegmentsContainer = new JBPanel();
    mySegmentsLayout = new AccordionLayout(mySegmentsContainer, AccordionLayout.Orientation.VERTICAL);
    mySegmentsContainer.setLayout(mySegmentsLayout);
    mySelection = new SelectionComponent(mySegmentsContainer, myTimeAxis, xSelectionRange, xGlobalRange, myXRange);

    return Arrays.asList(
      mySegmentsLayout, animatedTimeRange, mySelection, myScrollbar, myTimeAxis, myXRange, xGlobalRange, xSelectionRange);
  }

  // TODO: refactor to use ActionToolbar, as we're going to have more actions in the toolbar
  private void createToolbarComponent() {
    JBPanel toolbar = new JBPanel(new HorizontalLayout(TOOLBAR_HORIZONTAL_GAP));
    DeviceContext deviceContext = new DeviceContext();
    DevicePanel devicePanel = new DevicePanel(myProject, deviceContext);
    myCollapseSegmentsButton = new JButton();
    // TODO: use proper icon
    myCollapseSegmentsButton.setIcon(AllIcons.Actions.Back);
    myCollapseSegmentsButton.addActionListener(event -> myEventDispatcher.getMulticaster().profilersReset());
    myCollapseSegmentsButton.setVisible(false);

    toolbar.add(HorizontalLayout.RIGHT, devicePanel.getComponent());
    toolbar.add(HorizontalLayout.LEFT, myCollapseSegmentsButton);
    myComponent.add(toolbar, BorderLayout.NORTH);
  }

  private void populateUi() {
    createToolbarComponent();

    GridBagLayout gbl = new GridBagLayout();
    GridBagConstraints gbc = new GridBagConstraints();
    JBPanel gridBagPanel = new JBPanel();
    gridBagPanel.setBorder(BorderFactory.createLineBorder(AdtUiUtils.DEFAULT_BORDER_COLOR, 1));
    gridBagPanel.setLayout(gbl);

    // Add Selection Overlay
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridx = 2;
    gbc.gridy = 0;
    gbc.gridwidth = 1;
    gbc.weightx = 1;
    gbc.weighty = 1;
    gridBagPanel.add(mySelection, gbc);

    // Add Accordion Control
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 4;
    gbc.weightx = 0;
    gbc.weighty = 0;
    gridBagPanel.add(mySegmentsContainer, gbc);

    // Add Scrollbar
    gbc.gridy = 1;
    gridBagPanel.add(myScrollbar, gbc);

    // TODO: add events segment

    // Monitor segments
    BaseSegment networkSegment = createMonitorSegment(BaseSegment.SegmentType.NETWORK);
    BaseSegment memorySegment = createMonitorSegment(BaseSegment.SegmentType.MEMORY);
    BaseSegment cpuSegment = createMonitorSegment(BaseSegment.SegmentType.CPU);
    // Timeline segment
    BaseSegment timeSegment = createSegment(BaseSegment.SegmentType.TIME, TIME_AXIS_HEIGHT, TIME_AXIS_HEIGHT, TIME_AXIS_HEIGHT);

    mySegmentsContainer.add(networkSegment);
    mySegmentsContainer.add(memorySegment);
    mySegmentsContainer.add(cpuSegment);
    mySegmentsContainer.add(timeSegment);

    // Add left spacer
    Dimension leftSpacer = new Dimension(BaseSegment.getSpacerWidth() + networkSegment.getLabelColumnWidth(), 0);
    gbc.gridy = 0;
    gbc.gridx = 0;
    gbc.gridwidth = 2;
    gbc.weightx = 0;
    gbc.weighty = 0;
    gridBagPanel.add(new Box.Filler(leftSpacer, leftSpacer, leftSpacer), gbc);

    // Add right spacer
    Dimension rightSpacer = new Dimension(BaseSegment.getSpacerWidth(), 0);
    gbc.gridy = 0;
    gbc.gridx = 3;
    gbc.gridwidth = 1;
    gbc.weightx = 0;
    gbc.weighty = 0;
    Component rightSpacerFiller = new Box.Filler(rightSpacer, rightSpacer, rightSpacer);
    gridBagPanel.add(rightSpacerFiller, gbc);
    myComponent.add(gridBagPanel);


    // TODO construct/destroy Levels 2 and 3 extra segment/elements as we expand/collapse segments
    myEventDispatcher.addListener(new ProfilerEventListener() {
      @Override
      public void profilerExpanded(@NotNull BaseSegment.SegmentType segmentType) {
        switch (segmentType) {
          case NETWORK:
            mySegmentsLayout.setState(networkSegment, AccordionLayout.AccordionState.MAXIMIZE);
            break;
          case CPU:
            mySegmentsLayout.setState(cpuSegment, AccordionLayout.AccordionState.MAXIMIZE);
            break;
          case MEMORY:
            mySegmentsLayout.setState(memorySegment, AccordionLayout.AccordionState.MAXIMIZE);
            break;
          default:
        }
        rightSpacerFiller.setVisible(true);
        myCollapseSegmentsButton.setVisible(true);
      }

      @Override
      public void profilersReset() {
        // Sets all the components back to their preferred states.
        mySegmentsLayout.resetComponents();
        rightSpacerFiller.setVisible(false);
        myCollapseSegmentsButton.setVisible(false);
      }
    });
  }

  public JComponent getComponent() {
    return myComponent;
  }

  private BaseSegment createSegment(BaseSegment.SegmentType type, int minHeight, int preferredHeight, int maxHeight) {
    BaseSegment segment;
    switch (type) {
      case TIME:
        segment = new TimeAxisSegment(myXRange, myTimeAxis, myEventDispatcher);
        break;
      case CPU:
        segment = new CpuUsageSegment(myXRange, myDataStore, myEventDispatcher);
        break;
      case MEMORY:
        segment = new MemorySegment(myXRange, myDataStore, myEventDispatcher);
        break;
      default:
        // TODO create corresponding segments based on type (e.g. GPU, events).
        segment = new NetworkSegment(myXRange, myDataStore, myEventDispatcher);
    }

    segment.setMinimumSize(new Dimension(0, minHeight));
    segment.setPreferredSize(new Dimension(0, preferredHeight));
    segment.setMaximumSize(new Dimension(0, maxHeight));
    segment.setBorder(BorderFactory.createLineBorder(JBColor.GRAY));

    List<Animatable> segmentAnimatables = new ArrayList<>();
    segment.createComponentsList(segmentAnimatables);

    // LineChart needs to animate before y ranges so add them to the Choreographer in order.
    myChoreographer.register(segmentAnimatables);
    segment.initializeComponents();

    return segment;
  }

  private BaseSegment createMonitorSegment(BaseSegment.SegmentType type) {
    return createSegment(type, 0, MONITOR_PREFERRED_HEIGHT, MONITOR_MAX_HEIGHT);
  }
}