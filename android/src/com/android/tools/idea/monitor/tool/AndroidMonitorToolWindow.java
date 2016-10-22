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

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.adtui.*;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.common.formatter.TimeAxisFormatter;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.ddms.DevicePanel;
import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.ddms.adb.AdbService;
import com.android.tools.idea.logcat.AndroidLogcatService;
import com.android.tools.idea.monitor.datastore.Poller;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.datastore.SeriesDataStoreImpl;
import com.android.tools.idea.monitor.profilerclient.DeviceProfilerService;
import com.android.tools.idea.monitor.profilerclient.ProfilerService;
import com.android.tools.idea.monitor.ui.BaseProfilerUiManager;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.idea.monitor.ui.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.TimeAxisSegment;
import com.android.tools.idea.monitor.ui.cpu.view.CpuProfilerUiManager;
import com.android.tools.idea.monitor.ui.energy.view.EnergyProfilerUiManager;
import com.android.tools.idea.monitor.ui.events.view.EventProfilerUiManager;
import com.android.tools.idea.monitor.ui.memory.view.MemoryProfilerUiManager;
import com.android.tools.idea.monitor.ui.network.view.NetworkProfilerUiManager;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.JBUI;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static com.android.tools.idea.run.editor.ProfilerState.ENABLE_ENERGY_PROFILER;

public class AndroidMonitorToolWindow implements Disposable {

  private static final Logger LOG = Logger.getInstance(AndroidMonitorToolWindow.class);

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

  @NotNull
  private final Splitter mySplitter;

  @Nullable
  private Choreographer myChoreographer;

  @Nullable
  private SeriesDataStore myDataStore;

  private SelectionComponent mySelection;

  private Range myTimeCurrentRangeUs;

  // Range currently selected in the GUI.
  private Range myTimeSelectionRangeUs;

  private AxisComponent myTimeAxis;

  private RangeScrollbar myScrollbar;

  private JPanel mySegmentsContainer;

  private JPanel myDetailedViewContainer;

  private EventDispatcher<ProfilerEventListener> myEventDispatcher;

  private JButton myCollapseSegmentsButton;

  private TreeMap<BaseProfilerUiManager.ProfilerType, BaseProfilerUiManager> myProfilerManagers;

  private BaseProfilerUiManager.ProfilerType myExpandedProfiler;

  private boolean myProfilersInitialized;

  private JPanel myProfilerToolbar;

  public AndroidMonitorToolWindow(@NotNull final Project project) {
    myProject = project;
    myComponent = new JPanel(new BorderLayout());
    mySplitter = new Splitter(true, 1f);
    myDeviceContext = new DeviceContext();
    myProfilerManagers = new TreeMap<>();

    createToolbarComponent();
    setupDevice();
  }

  @Override
  public void dispose() {
  }

  private List<Animatable> createCommonAnimatables() {
    assert myDataStore != null;
    final long deviceStartTimeUs = myDataStore.getLatestTimeUs();
    Range timeGlobalRangeUs = new Range(deviceStartTimeUs - RangeScrollbar.DEFAULT_VIEW_LENGTH_US, deviceStartTimeUs);
    myTimeSelectionRangeUs = new Range();
    myTimeCurrentRangeUs = new Range();

    myScrollbar = new RangeScrollbar(timeGlobalRangeUs, myTimeCurrentRangeUs);

    AxisComponent.Builder builder = new AxisComponent.Builder(myTimeCurrentRangeUs, TimeAxisFormatter.DEFAULT, AxisComponent.AxisOrientation.BOTTOM)
      .setGlobalRange(timeGlobalRangeUs).setOffset(deviceStartTimeUs);
    myTimeAxis = builder.build();

    myDetailedViewContainer = new JBPanel(new BorderLayout());
    mySegmentsContainer = new JBPanel();
    AccordionLayout accordion = new AccordionLayout(mySegmentsContainer, AccordionLayout.Orientation.VERTICAL);
    mySegmentsContainer.setLayout(accordion);
    accordion.setLerpFraction(1f);

    mySelection = new SelectionComponent(mySegmentsContainer, myTimeAxis, myTimeSelectionRangeUs, timeGlobalRangeUs, myTimeCurrentRangeUs);

    return Arrays.asList(accordion,
                         frameLength -> {
                           long maxTimeBufferUs = TimeUnit.NANOSECONDS.toMicros(Poller.POLLING_DELAY_NS);
                           long currentTimeUs = myDataStore.getLatestTimeUs();
                           // Once elapsedTime is greater than DEFAULT_VIEW_LENGTH_US, set global min to 0 so that user can
                           // not scroll back to negative time.
                           timeGlobalRangeUs.setMinTarget(Math.min(currentTimeUs - RangeScrollbar.DEFAULT_VIEW_LENGTH_US, deviceStartTimeUs));
                           // Updates the global range's max to match the device's current time.
                           timeGlobalRangeUs.setMaxTarget(currentTimeUs - maxTimeBufferUs);
                         },
                         mySelection,
                         myScrollbar,
                         myTimeAxis,
                         myTimeCurrentRangeUs,
                         timeGlobalRangeUs,
                         myTimeSelectionRangeUs);
  }

  private void setupDevice() {
    mySelectedDevice = myDeviceContext.getSelectedDevice();
    connectToDevice();

    AndroidDebugBridge.addDeviceChangeListener(AndroidLogcatService.getInstance());

    myDeviceContext.addListener(new DeviceContext.DeviceSelectionListener() {
      @Override
      public void deviceSelected(@Nullable IDevice device) {
        // Early return if the DeviceProfilerService for the selected device is already running.
        if (mySelectedDeviceProfilerService != null && mySelectedDeviceProfilerService.getDevice() == device) {
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

    // TODO the logic below is copied partially from the old Android Monitor. Refactor/reimplement to show proper statuses in the UI.
    final File adb = AndroidSdkUtils.getAdb(myProject);
    if (adb == null) {
      return;
    }
    ListenableFuture<AndroidDebugBridge> future = AdbService.getInstance().getDebugBridge(adb);
    Futures.addCallback(future, new FutureCallback<AndroidDebugBridge>() {
      @Override
      public void onSuccess(@Nullable AndroidDebugBridge bridge) {
        LOG.info("Successfully obtained debug bridge");
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        LOG.info("Unable to obtain debug bridge", t);
      }
    }, EdtExecutor.INSTANCE);
  }

  // TODO: refactor to use ActionToolbar, as we're going to have more actions in the toolbar
  private void createToolbarComponent() {
    myProfilerToolbar = new JBPanel(new HorizontalLayout(TOOLBAR_HORIZONTAL_GAP));
    DevicePanel devicePanel = new DevicePanel(myProject, myDeviceContext);
    myCollapseSegmentsButton = new JButton();
    // TODO: use proper icon
    myCollapseSegmentsButton.setIcon(AllIcons.Actions.Back);
    myCollapseSegmentsButton.addActionListener(event -> myEventDispatcher.getMulticaster().profilersReset());
    myCollapseSegmentsButton.setVisible(false);

    myProfilerToolbar.add(HorizontalLayout.RIGHT, devicePanel.getComponent());
    myProfilerToolbar.add(HorizontalLayout.LEFT, myCollapseSegmentsButton);
    myComponent.add(myProfilerToolbar, BorderLayout.NORTH);
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
      manager.setupOverviewUi(myProfilerToolbar, mySegmentsContainer);
    }

    // Timeline segment
    TimeAxisSegment timeSegment = new TimeAxisSegment(myTimeCurrentRangeUs, myTimeAxis, myEventDispatcher);
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

    mySplitter.setFirstComponent(gridBagPanel);
    myComponent.add(mySplitter);

    myEventDispatcher.addListener(new ProfilerEventListener() {
      @Override
      public void profilerExpanded(@NotNull BaseProfilerUiManager.ProfilerType profilerType) {
        // No other profiler should request expansion if a profiler is already expanded
        assert (myExpandedProfiler == null || myExpandedProfiler == profilerType);

        boolean firstExpansion = myExpandedProfiler == null; // Whether the profiler is expanded for the first time. e.g. L1 -> L2
        for (Map.Entry<BaseProfilerUiManager.ProfilerType, BaseProfilerUiManager> entry : myProfilerManagers.entrySet()) {
          BaseProfilerUiManager manager = entry.getValue();
          if (entry.getKey() == profilerType) {
            if (firstExpansion) {
              // The profiler is expanded for the first time. e.g. L1 -> L2
              manager.setupExtendedOverviewUi(myProfilerToolbar, mySegmentsContainer);
              myExpandedProfiler = profilerType;
            }
            else if (mySplitter.getSecondComponent() == null) {
              // The profiler is expanded for the second time. e.g. L2 -> L3
              // Note that subsequent expansion call should not trigger this code block.
              manager.setupDetailedViewUi(myProfilerToolbar, myDetailedViewContainer);
              mySplitter.setOrientation(manager.isDetailedViewVerticallySplit());
              mySplitter.setSecondComponent(myDetailedViewContainer);
              mySplitter.setProportion(0.75f);
            }
          }
          else if (entry.getKey() == BaseProfilerUiManager.ProfilerType.EVENT && firstExpansion) {
            // Special handle to expand the Event monitor from L1 -> L2 during the first expansion.
            manager.setupExtendedOverviewUi(myProfilerToolbar, mySegmentsContainer);
          }
          else {
            // TODO disable polling data from device.
          }
        }

        if (firstExpansion) {
          timeSegment.toggleView(true);
          rightSpacerFiller.setVisible(true);
          myCollapseSegmentsButton.setVisible(true);
        }
      }

      @Override
      public void profilersReset() {
        myProfilerManagers.get(myExpandedProfiler).resetProfiler(myProfilerToolbar, mySegmentsContainer, myDetailedViewContainer);
        // Also reset event segment to avoid it taking the whole panel after reset.
        myProfilerManagers.get(BaseProfilerUiManager.ProfilerType.EVENT)
          .resetProfiler(myProfilerToolbar, mySegmentsContainer, myDetailedViewContainer);

        mySplitter.setSecondComponent(null);
        mySplitter.setProportion(1f);

        timeSegment.toggleView(false);
        rightSpacerFiller.setVisible(false);
        myCollapseSegmentsButton.setVisible(false);
        myExpandedProfiler = null;
      }

      @Override
      public void profilerServerDisconnected() {
        LOG.info("Attempt to communicate with Device Profiler Service failed. Disconnecting...");
        // TODO Right now this callback can be called from many pollers which in-turn would
        // call disconnectFromDevice() all at once, which is potentially not thread safe.
        // We should add some logic to guard against this case.
        disconnectFromDevice();
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
      LOG.info("Successfully disconnected from Device Profiler Service.");
    }
  }

  private void connectToDevice() {
    if (mySelectedDevice == null) {
      return;
    }

    mySelectedDeviceProfilerService = ProfilerService.getInstance().connect(this, mySelectedDevice);
    LOG.info(mySelectedDeviceProfilerService == null ?
             "Attempt to connect to Device Profiler Service failed." : "Successfully connected to Device Profiler Service.");
  }

  private void initializeProfilers() {
    if (mySelectedDeviceProfilerService == null || mySelectedClient == null) {
      return;
    }

    myEventDispatcher = EventDispatcher.create(ProfilerEventListener.class);
    myDataStore = new SeriesDataStoreImpl(mySelectedDeviceProfilerService, myEventDispatcher);
    myChoreographer = new Choreographer(CHOREOGRAPHER_FPS, myComponent);
    myChoreographer.register(createCommonAnimatables());

    // TODO: add event manager to myProfilerManagers
    myProfilerManagers.put(BaseProfilerUiManager.ProfilerType.EVENT,
                           new EventProfilerUiManager(myTimeCurrentRangeUs, myChoreographer, myDataStore, myEventDispatcher));
    myProfilerManagers.put(BaseProfilerUiManager.ProfilerType.NETWORK,
                           new NetworkProfilerUiManager(myTimeCurrentRangeUs, myChoreographer, myDataStore, myEventDispatcher));
    myProfilerManagers.put(BaseProfilerUiManager.ProfilerType.MEMORY,
                           new MemoryProfilerUiManager(myTimeCurrentRangeUs, myChoreographer, myDataStore, myEventDispatcher));
    myProfilerManagers.put(BaseProfilerUiManager.ProfilerType.CPU,
                           new CpuProfilerUiManager(myTimeCurrentRangeUs, myTimeSelectionRangeUs, myChoreographer, myDataStore, myEventDispatcher,
                                                    mySelectedDeviceProfilerService, myDeviceContext, myProject));
    if (System.getProperty(ENABLE_ENERGY_PROFILER) != null ) {
      myProfilerManagers.put(BaseProfilerUiManager.ProfilerType.ENERGY,
                             new EnergyProfilerUiManager(myTimeCurrentRangeUs, myChoreographer, myDataStore, myEventDispatcher));
    }
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
        manager.resetProfiler(myProfilerToolbar, mySegmentsContainer, myDetailedViewContainer);
      }
      myProfilerManagers.clear();

      if (myDataStore != null) {
        myDataStore.stop();
        myDataStore = null;
      }
      myChoreographer = null;
      myEventDispatcher = null;
      mySelectedClient = null;
      myProfilersInitialized = false;
      myExpandedProfiler = null;
    }
    // Hides the reset button
    if (myCollapseSegmentsButton != null) {
      myCollapseSegmentsButton.setVisible(false);
    }
  }
}