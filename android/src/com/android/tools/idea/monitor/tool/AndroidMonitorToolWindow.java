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

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.adtui.*;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.common.formatter.TimeAxisFormatter;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.ddms.DevicePanel;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.datastore.SeriesDataStoreImpl;
import com.android.tools.idea.monitor.profilerclient.DeviceProfilerService;
import com.android.tools.idea.monitor.profilerclient.ProfilerService;
import com.android.tools.idea.monitor.ui.BaseProfilerUiManager;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.idea.monitor.ui.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.TimeAxisSegment;
import com.android.tools.idea.monitor.ui.cpu.view.CpuProfilerUiManager;
import com.android.tools.idea.monitor.ui.memory.view.MemoryProfilerUiManager;
import com.android.tools.idea.monitor.ui.network.view.NetworkProfilerUiManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class AndroidMonitorToolWindow implements Disposable {

  private static final int CHOREOGRAPHER_FPS = 60;

  private static final int TOOLBAR_HORIZONTAL_GAP = JBUI.scale(5);

  private static final int TIME_AXIS_HEIGHT = JBUI.scale(20);

  @NotNull
  private final Project myProject;

  @NotNull
  private DeviceContext myDeviceContext;

  @Nullable
  private IDevice mySelectedDevice;

  @Nullable
  private Client mySelectedClient;

  @Nullable
  private DeviceProfilerService mySelectedDeviceProfilerService;

  @NotNull
  private final JPanel myComponent;

  @Nullable
  private Choreographer myChoreographer;

  @Nullable
  private SeriesDataStore myDataStore;

  private SelectionComponent mySelection;

  private Range myXRange;

  private AxisComponent myTimeAxis;

  private RangeScrollbar myScrollbar;

  private JPanel mySegmentsContainer;

  private JPanel myDetailedViewContainer;

  private EventDispatcher<ProfilerEventListener> myEventDispatcher;

  private JButton myCollapseSegmentsButton;

  private TreeMap<BaseProfilerUiManager.ProfilerType, BaseProfilerUiManager> myProfilerManagers;

  private BaseProfilerUiManager.ProfilerType myExpandedProfiler;

  private boolean myProfilersInitialized;

  public AndroidMonitorToolWindow(@NotNull final Project project) {
    myProject = project;
    myComponent = new JPanel(new BorderLayout());
    myDeviceContext = new DeviceContext();
    myProfilerManagers = new TreeMap<>();

    setupDevice();
    createToolbarComponent();
  }

  @Override
  public void dispose() {
  }

  private List<Animatable> createCommonAnimatables() {
    Range xGlobalRange = new Range(-RangeScrollbar.DEFAULT_VIEW_LENGTH_MS, 0);
    Range xSelectionRange = new Range();
    myXRange = new Range();

    myScrollbar = new RangeScrollbar(xGlobalRange, myXRange);

    myTimeAxis = new AxisComponent(myXRange, xGlobalRange, "TIME", AxisComponent.AxisOrientation.BOTTOM, 0, 0, false,
                                   TimeAxisFormatter.DEFAULT);
    myTimeAxis.setLabelVisible(false);

    myDetailedViewContainer = new JBPanel();
    mySegmentsContainer = new JBPanel();
    AccordionLayout accordion = new AccordionLayout(mySegmentsContainer, AccordionLayout.Orientation.VERTICAL);
    mySegmentsContainer.setLayout(accordion);
    accordion.setLerpFraction(1f);

    mySelection = new SelectionComponent(mySegmentsContainer, myTimeAxis, xSelectionRange, xGlobalRange, myXRange);

    assert myDataStore != null;
    return Arrays.asList(accordion,
                         frameLength -> {
                           // Updates the global range's max to match the device's current time.
                           xGlobalRange.setMaxTarget(myDataStore.getLatestTime());
                         },
                         mySelection,
                         myScrollbar,
                         myTimeAxis,
                         myXRange,
                         xGlobalRange,
                         xSelectionRange);
  }

  private void setupDevice() {
    mySelectedDevice = myDeviceContext.getSelectedDevice();
    if (mySelectedDevice != null) {
      connectToDevice();
    }

    myDeviceContext.addListener(new DeviceContext.DeviceSelectionListener() {
      @Override
      public void deviceSelected(@Nullable IDevice device) {
        // Return early if selecting the same device selected previously
        // We shouldn't return early, however, if there's no connection with the device.
        // In this case, we want to make sure that connectToDevice() is called.
        if (device == mySelectedDevice && mySelectedDeviceProfilerService != null) {
          return;
        }

        disconnectFromDevice();
        mySelectedDevice = device;
        connectToDevice();
      }

      @Override
      public void deviceChanged(@NotNull IDevice device, int changeMask) {
        if ((changeMask & IDevice.CHANGE_STATE) > 0) {
          if (device.isOnline() && mySelectedDeviceProfilerService == null) {
            connectToDevice();
          }
          else if (device.isOffline() || device.getState() == IDevice.DeviceState.DISCONNECTED) {
            disconnectFromDevice();
          }
        }
      }

      /**
       * If a valid Client is selected, (re)initialize the UI/datastore and everything along with it.
       *
       * TODO for now, the UI/datastore is destroyed if the Client is null. We should come up with a better approach
       *      to keep the existing information present and have some visuals to indicate a "disconnected" status.
       */
      @Override
      public void clientSelected(@Nullable Client c) {
        if (mySelectedClient != c) {
          deinitializeProfilers();
          mySelectedClient = c;
        }

        if (!myProfilersInitialized) {
          // Make sure the device is connected before initializing the profilers.
          if (mySelectedDeviceProfilerService == null) {
            connectToDevice();
          }
          initializeProfilers();
        }
      }
    }, this);
  }

  // TODO: refactor to use ActionToolbar, as we're going to have more actions in the toolbar
  private void createToolbarComponent() {
    JBPanel toolbar = new JBPanel(new HorizontalLayout(TOOLBAR_HORIZONTAL_GAP));
    DevicePanel devicePanel = new DevicePanel(myProject, myDeviceContext);
    myCollapseSegmentsButton = new JButton();
    // TODO: use proper icon
    myCollapseSegmentsButton.setIcon(AllIcons.Actions.Back);
    myCollapseSegmentsButton.addActionListener(event -> myEventDispatcher.getMulticaster().profilersReset());
    myCollapseSegmentsButton.setVisible(false);

    toolbar.add(HorizontalLayout.RIGHT, devicePanel.getComponent());
    toolbar.add(HorizontalLayout.LEFT, myCollapseSegmentsButton);
    myComponent.add(toolbar, BorderLayout.NORTH);
  }

  private void populateProfilerUi() {
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

    // Setup profiler segments
    for (BaseProfilerUiManager manager : myProfilerManagers.values()) {
      manager.setupOverviewUi(mySegmentsContainer);
    }

    // Timeline segment
    TimeAxisSegment timeSegment = new TimeAxisSegment(myXRange, myTimeAxis, myEventDispatcher);
    timeSegment.setMinimumSize(new Dimension(0, TIME_AXIS_HEIGHT));
    timeSegment.setPreferredSize(new Dimension(0, TIME_AXIS_HEIGHT));
    timeSegment.setMaximumSize(new Dimension(0, TIME_AXIS_HEIGHT));
    timeSegment.setBorder(BorderFactory.createLineBorder(JBColor.GRAY));
    timeSegment.initializeComponents();
    mySegmentsContainer.add(timeSegment);

    // Add left spacer
    Dimension leftSpacer = new Dimension(BaseSegment.getSpacerWidth() + timeSegment.getLabelColumnWidth(), 0);
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
    rightSpacerFiller.setVisible(false);

    myComponent.add(gridBagPanel);

    myEventDispatcher.addListener(new ProfilerEventListener() {
      @Override
      public void profilerExpanded(@NotNull BaseProfilerUiManager.ProfilerType profilerType) {
        // No other profiler should request expansion if a profiler is already expanded
        assert (myExpandedProfiler == null || myExpandedProfiler == profilerType);

        for (Map.Entry<BaseProfilerUiManager.ProfilerType, BaseProfilerUiManager> entry : myProfilerManagers.entrySet()) {
          if (entry.getKey() == profilerType) {
            if (myExpandedProfiler == null) {
              entry.getValue().setupExtendedOverviewUi(mySegmentsContainer);
              myExpandedProfiler = profilerType;
            }
            else {
              entry.getValue().setupDetailedViewUi(myDetailedViewContainer);
            }
          }
          else if (entry.getKey() == BaseProfilerUiManager.ProfilerType.EVENT) {
            // Special case for Events profiler, as it is always visible.
            entry.getValue().setupExtendedOverviewUi(mySegmentsContainer);
          }
          else {
            // TODO disable polling data from device.
          }
        }

        timeSegment.toggleView(true);
        rightSpacerFiller.setVisible(true);
        myCollapseSegmentsButton.setVisible(true);
      }

      @Override
      public void profilersReset() {
        myProfilerManagers.get(myExpandedProfiler).resetProfiler(mySegmentsContainer, myDetailedViewContainer);
        timeSegment.toggleView(false);
        rightSpacerFiller.setVisible(false);
        myCollapseSegmentsButton.setVisible(false);
        myExpandedProfiler = null;
      }
    });
  }

  public JComponent getComponent() {
    return myComponent;
  }

  private void disconnectFromDevice() {
    if (mySelectedDeviceProfilerService != null) {
      deinitializeProfilers();
      ProfilerService.getInstance().disconnect(this, mySelectedDeviceProfilerService);
      mySelectedDeviceProfilerService = null;
    }
  }

  private void connectToDevice() {
    if (mySelectedDevice == null) {
      return;
    }

    if (!mySelectedDevice.isOnline()) {
      return;
    }

    mySelectedDeviceProfilerService = ProfilerService.getInstance().connect(this, mySelectedDevice);
  }

  private void initializeProfilers() {
    if (mySelectedDeviceProfilerService == null || mySelectedClient == null) {
      return;
    }
    myDataStore = new SeriesDataStoreImpl(mySelectedDeviceProfilerService);
    myChoreographer = new Choreographer(CHOREOGRAPHER_FPS, myComponent);
    myChoreographer.register(createCommonAnimatables());
    myEventDispatcher = EventDispatcher.create(ProfilerEventListener.class);

    // TODO: add event manager to myProfilerManagers
    //myProfilerManagers.put(BaseProfilerUiManager.ProfilerType.EVENT,
    //                       new EventProfilerUiManager(myXRange, myChoreographer, myDataStore, myEventDispatcher));
    myProfilerManagers.put(BaseProfilerUiManager.ProfilerType.NETWORK,
                           new NetworkProfilerUiManager(myXRange, myChoreographer, myDataStore, myEventDispatcher));
    myProfilerManagers.put(BaseProfilerUiManager.ProfilerType.MEMORY,
                           new MemoryProfilerUiManager(myXRange, myChoreographer, myDataStore, myEventDispatcher));
    myProfilerManagers.put(BaseProfilerUiManager.ProfilerType.CPU,
                           new CpuProfilerUiManager(myXRange, myChoreographer, myDataStore, myEventDispatcher));
    for (BaseProfilerUiManager manager : myProfilerManagers.values()) {
      manager.startMonitoring(mySelectedClient.getClientData().getPid());
    }

    populateProfilerUi();
    myProfilersInitialized = true;
  }

  private void deinitializeProfilers() {
    if (mySelectedClient != null) {
      synchronized (myComponent.getTreeLock()) {
        // Empties the entire UI except the toolbar.
        for (int i = myComponent.getComponentCount() - 1; i > 0; i--) {
          myComponent.remove(i);
        }
      }

      for (BaseProfilerUiManager manager : myProfilerManagers.values()) {
        manager.stopMonitoring();
      }
      myProfilerManagers.clear();

      if (myDataStore != null) {
        myDataStore.reset();
        myDataStore = null;
      }
      myChoreographer = null;
      myEventDispatcher = null;
      mySelectedClient = null;
      myProfilersInitialized = false;
    }
  }
}