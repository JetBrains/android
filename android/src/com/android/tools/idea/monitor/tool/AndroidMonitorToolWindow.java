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
import com.android.tools.idea.monitor.datastore.SeriesDataStoreImpl;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.idea.monitor.ui.TimeAxisSegment;
import com.android.tools.idea.monitor.ui.cpu.view.CpuUsageSegment;
import com.android.tools.idea.monitor.ui.memory.view.MemorySegment;
import com.android.tools.idea.monitor.ui.network.view.NetworkSegment;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AndroidMonitorToolWindow implements Disposable {

  private static final int CHOREOGRAPHER_FPS = 60;

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

  public AndroidMonitorToolWindow(@NotNull final Project project) {
    myProject = project;
    myComponent = new JPanel(new BorderLayout());
    myDataStore = new SeriesDataStoreImpl();
    myChoreographer = new Choreographer(CHOREOGRAPHER_FPS, myComponent);
    myChoreographer.register(createComponentsList());
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
    AccordionLayout accordionLayout = new AccordionLayout(mySegmentsContainer, AccordionLayout.Orientation.VERTICAL);
    mySegmentsContainer.setLayout(accordionLayout);
    mySelection = new SelectionComponent(mySegmentsContainer, myTimeAxis, xSelectionRange, xGlobalRange, myXRange);

    return Arrays.asList(accordionLayout, animatedTimeRange, mySelection, myScrollbar, myTimeAxis, myXRange, xGlobalRange, xSelectionRange);
  }

  private void populateUi() {
    DeviceContext deviceContext = new DeviceContext();
    DevicePanel devicePanel = new DevicePanel(myProject, deviceContext);
    myComponent.add(devicePanel.getComponent(), BorderLayout.NORTH);

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
    gridBagPanel.add(new Box.Filler(rightSpacer, rightSpacer, rightSpacer), gbc);
    myComponent.add(gridBagPanel);
  }

  public JComponent getComponent() {
    return myComponent;
  }

  private BaseSegment createSegment(BaseSegment.SegmentType type, int minHeight, int preferredHeight, int maxHeight) {
    BaseSegment segment;
    switch (type) {
      case TIME:
        segment = new TimeAxisSegment(myXRange, myTimeAxis);
        break;
      case CPU:
        segment = new CpuUsageSegment(myXRange, myDataStore);
        break;
      case MEMORY:
        segment = new MemorySegment(myXRange, myDataStore);
        break;
      default:
        // TODO create corresponding segments based on type (e.g. GPU, events).
        segment = new NetworkSegment(myXRange, myDataStore);
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