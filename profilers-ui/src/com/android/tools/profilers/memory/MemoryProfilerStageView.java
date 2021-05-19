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
package com.android.tools.profilers.memory;

import static com.android.tools.profilers.ProfilerLayout.createToolbarLayout;

import com.android.tools.adtui.RangeSelectionComponent;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.flat.FlatSeparator;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.profilers.ContextMenuInstaller;
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.JComboBoxView;
import com.android.tools.profilers.ProfilerAction;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerCombobox;
import com.android.tools.profilers.ProfilerComboboxCellRenderer;
import com.android.tools.profilers.ProfilerFonts;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.StudioProfilersView;
import com.android.tools.profilers.event.LifecycleTooltip;
import com.android.tools.profilers.event.LifecycleTooltipView;
import com.android.tools.profilers.event.UserEventTooltip;
import com.android.tools.profilers.event.UserEventTooltipView;
import com.android.tools.profilers.memory.MemoryProfilerStage.LiveAllocationSamplingMode;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.sessions.SessionAspect;
import com.android.tools.profilers.stacktrace.ContextMenuItem;
import com.android.tools.profilers.stacktrace.LoadingPanel;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBUI;
import icons.StudioIcons;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.swing.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MemoryProfilerStageView extends BaseMemoryProfilerStageView<MemoryProfilerStage> {
  private static Logger getLogger() {
    return Logger.getInstance(MemoryProfilerStageView.class);
  }

  private static final String RECORD_TEXT = "Record";
  private static final String STOP_TEXT = "Stop";
  private static final String LIVE_ALLOCATION_TRACKING_NOT_READY_TOOLTIP =
    "Allocation tracking isn't ready. Please wait.";
  @VisibleForTesting
  static final String RECORD_NATIVE_TEXT = "Record native allocations";
  @VisibleForTesting
  static final String X86_RECORD_NATIVE_TOOLTIP = "Native memory recording is unavailable on x86 or x86_64 devices";
  @VisibleForTesting
  static final String STOP_NATIVE_TEXT = "Stop recording";

  private final MemoryProfilerStageLayout myLayout;

  @Nullable private final MemoryTimelineComponent myTimelineComponent;
  @Nullable private CaptureObject myCaptureObject = null;

  @NotNull private JButton myForceGarbageCollectionButton;
  @NotNull private JButton myHeapDumpButton;
  @NotNull private JButton myAllocationButton;
  @NotNull private final JButton myNativeAllocationButton;
  @NotNull private JComboBox myAllocationSamplingRateDropDown;
  @NotNull private ProfilerAction myForceGarbageCollectionAction;
  @NotNull private ProfilerAction myHeapDumpAction;
  @NotNull private ProfilerAction myAllocationAction;
  @NotNull private ProfilerAction myStopAllocationAction;
  @NotNull private ProfilerAction myNativeAllocationAction;
  @NotNull private ProfilerAction myStopNativeAllocationAction;
  @NotNull private final JLabel myCaptureElapsedTime;
  @NotNull private final JLabel myAllocationSamplingRateLabel;
  @NotNull private final LoadingPanel myHeapDumpLoadingPanel;

  public MemoryProfilerStageView(@NotNull StudioProfilersView profilersView, @NotNull MemoryProfilerStage stage) {
    super(profilersView, stage);

    // Turns on the auto-capture selection functionality - this would select the latest user-triggered heap dump/allocation tracking
    // capture object if an existing one has not been selected.
    getStage().enableSelectLatestCapture(true, SwingUtilities::invokeLater);

    getTooltipBinder().bind(MemoryUsageTooltip.class, MemoryUsageTooltipView::new);
    getTooltipBinder().bind(LifecycleTooltip.class, (stageView, tooltip) -> new LifecycleTooltipView(stageView.getComponent(), tooltip));
    getTooltipBinder().bind(UserEventTooltip.class, (stageView, tooltip) -> new UserEventTooltipView(stageView.getComponent(), tooltip));

    // Do not initialize the monitor UI if it only contains heap dump data.
    // In this case, myRangeSelectionComponent is null and we will not build the context menu.
    myTimelineComponent = getStage().isMemoryCaptureOnly() ?
                          null :
                          new MemoryTimelineComponent(this, buildTimeAxis(profilersView.getStudioProfilers()));
    CapturePanel capturePanel = new CapturePanel(getProfilersView(),
                                                 getStage().getCaptureSelection(),
                                                 getStage().isMemoryCaptureOnly() ? null : getSelectionTimeLabel(),
                                                 getStage().getRangeSelectionModel().getSelectionRange(),
                                                 getIdeComponents(),
                                                 getStage().getTimeline(),
                                                 false);

    myLayout = new MemoryProfilerStageLayout(myTimelineComponent, capturePanel, this::makeLoadingPanel);
    getComponent().add(myLayout.getComponent(), BorderLayout.CENTER);

    myHeapDumpLoadingPanel = getIdeComponents().createLoadingPanel(-1);
    myHeapDumpLoadingPanel.setLoadingText("Capturing heap dump");

    myForceGarbageCollectionButton = new CommonButton(StudioIcons.Profiler.Toolbar.FORCE_GARBAGE_COLLECTION);
    myForceGarbageCollectionButton.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.FORCE_GARBAGE_COLLECTION));
    myForceGarbageCollectionButton.addActionListener(e -> {
      getStage().forceGarbageCollection();
      getStage().getStudioProfilers().getIdeServices().getFeatureTracker().trackForceGc();
    });
    myForceGarbageCollectionAction =
      new ProfilerAction.Builder("Force garbage collection")
        .setContainerComponent(getComponent())
        .setIcon(myForceGarbageCollectionButton.getIcon())
        .setActionRunnable(() -> myForceGarbageCollectionButton.doClick(0))
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_G, AdtUiUtils.getActionMask())).build();
    myForceGarbageCollectionButton.setToolTipText(myForceGarbageCollectionAction.getDefaultToolTipText());


    myHeapDumpButton = new CommonButton(StudioIcons.Profiler.Toolbar.HEAP_DUMP);
    myHeapDumpButton.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.HEAP_DUMP));
    myHeapDumpButton.addActionListener(e -> {
      getStage().requestHeapDump();
      getStage().getStudioProfilers().getIdeServices().getFeatureTracker().trackDumpHeap();
    });
    myHeapDumpAction =
      new ProfilerAction.Builder("Dump Java heap")
        .setContainerComponent(getComponent())
        .setIcon(myHeapDumpButton.getIcon())
        .setActionRunnable(() -> myHeapDumpButton.doClick(0))
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_D, AdtUiUtils.getActionMask())).build();
    myHeapDumpButton.setToolTipText(myHeapDumpAction.getDefaultToolTipText());

    myCaptureElapsedTime = new JLabel("");
    myCaptureElapsedTime.setFont(ProfilerFonts.STANDARD_FONT);
    myCaptureElapsedTime.setBorder(JBUI.Borders.emptyLeft(5));
    myCaptureElapsedTime.setForeground(ProfilerColors.CPU_CAPTURE_STATUS);

    // Set to the longest text this button will show as to initialize the persistent size properly.
    // Call setPreferredSize to avoid the initialized size being overwritten.
    // TODO: b/80546414 Use common button instead.
    myAllocationButton = new JButton(RECORD_TEXT);
    myAllocationButton.setPreferredSize(myAllocationButton.getPreferredSize());

    myAllocationButton
      .addActionListener(e -> {
        if (getStage().isTrackingAllocations()) {
          getStage().getStudioProfilers().getIdeServices().getFeatureTracker().trackRecordAllocations();
        }
        getStage().trackAllocations(!getStage().isTrackingAllocations());
      });
    myAllocationButton.setVisible(!getStage().isLiveAllocationTrackingSupported());
    myAllocationAction =
      new ProfilerAction.Builder("Record allocations")
        .setIcon(StudioIcons.Profiler.Toolbar.RECORD)
        .setContainerComponent(getComponent())
        .setEnableBooleanSupplier(() -> !getStage().isTrackingAllocations())
        .setActionRunnable(() -> myAllocationButton.doClick(0)).
        setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_R, AdtUiUtils.getActionMask())).build();
    myStopAllocationAction =
      new ProfilerAction.Builder("Stop recording")
        .setIcon(StudioIcons.Profiler.Toolbar.STOP_RECORDING)
        .setContainerComponent(getComponent())
        .setEnableBooleanSupplier(() -> getStage().isTrackingAllocations())
        .setActionRunnable(() -> myAllocationButton.doClick(0)).
        setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_S, AdtUiUtils.getActionMask())).build();

    myNativeAllocationButton = new JButton(RECORD_NATIVE_TEXT);
    myNativeAllocationButton.setPreferredSize(myNativeAllocationButton.getPreferredSize());
    myNativeAllocationButton
      .addActionListener(e -> {
        getStage().toggleNativeAllocationTracking();
        disableRecordingButtons();
      });
    myNativeAllocationButton.setVisible(getStage().isNativeAllocationSamplingEnabled());
    myNativeAllocationAction =
      new ProfilerAction.Builder(RECORD_NATIVE_TEXT)
        .setIcon(StudioIcons.Profiler.Toolbar.RECORD)
        .setContainerComponent(getComponent())
        .setEnableBooleanSupplier(() -> !getStage().isTrackingAllocations())
        .setActionRunnable(() -> myNativeAllocationButton.doClick(0)).
        setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_R, AdtUiUtils.getActionMask())).build();
    myStopNativeAllocationAction =
      new ProfilerAction.Builder(STOP_NATIVE_TEXT)
        .setIcon(StudioIcons.Profiler.Toolbar.STOP_RECORDING)
        .setContainerComponent(getComponent())
        .setEnableBooleanSupplier(() -> getStage().isTrackingAllocations())
        .setActionRunnable(() -> myNativeAllocationButton.doClick(0)).
        setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_S, AdtUiUtils.getActionMask())).build();

    myAllocationSamplingRateLabel = new JLabel("Allocation Tracking");
    myAllocationSamplingRateLabel.setBorder(JBUI.Borders.empty(0, 8));
    myAllocationSamplingRateDropDown = new ProfilerCombobox();

    getStage().getAspect().addDependency(this)
      .onChange(MemoryProfilerAspect.TRACKING_ENABLED, this::allocationTrackingChanged)
      .onChange(MemoryProfilerAspect.HEAP_DUMP_STARTED, this::showHeapDumpInProgress)
      .onChange(MemoryProfilerAspect.HEAP_DUMP_FINISHED, this::hideHeapDumpInProgress);
    getStage().getCaptureSelection().getAspect().addDependency(this)
      .onChange(CaptureSelectionAspect.CURRENT_LOADING_CAPTURE, this::captureObjectChanged)
      .onChange(CaptureSelectionAspect.CURRENT_LOADED_CAPTURE, this::captureObjectFinishedLoading)

      .onChange(CaptureSelectionAspect.CURRENT_CAPTURE_ELAPSED_TIME, this::updateCaptureElapsedTime);

    captureObjectChanged();
    allocationTrackingChanged();
    buildContextMenu();
  }

  @Override
  public boolean isToolbarVisible() {
    return !getStage().isMemoryCaptureOnly();
  }

  @VisibleForTesting
  MemoryProfilerStageLayout getLayout() {
    return myLayout;
  }

  @VisibleForTesting
  JButton getGarbageCollectionButtion() {
    return myForceGarbageCollectionButton;
  }

  @VisibleForTesting
  @NotNull
  JButton getHeapDumpButton() {
    return myHeapDumpButton;
  }

  @VisibleForTesting
  @NotNull
  JButton getAllocationButton() {
    return myAllocationButton;
  }

  @VisibleForTesting
  @NotNull
  JButton getNativeAllocationButton() {
    return myNativeAllocationButton;
  }

  @VisibleForTesting
  JLabel getAllocationCaptureElaspedTimeLabel() {
    return myCaptureElapsedTime;
  }

  @VisibleForTesting
  @NotNull
  JComboBox getAllocationSamplingRateDropDown() {
    return myAllocationSamplingRateDropDown;
  }

  @VisibleForTesting
  @NotNull
  JLabel getAllocationSamplingRateLabel() {
    return myAllocationSamplingRateLabel;
  }

  @VisibleForTesting
  @Nullable
  MemoryTimelineComponent getTimelineComponent() {
    return myTimelineComponent;
  }

  @Override
  public JComponent getToolbar() {
    JPanel panel = new JPanel(new BorderLayout());
    JPanel toolbar = new JPanel(createToolbarLayout());
    panel.add(toolbar, BorderLayout.WEST);
    toolbar.removeAll();
    toolbar.add(myForceGarbageCollectionButton);
    toolbar.add(myHeapDumpButton);
    if (getStage().isLiveAllocationTrackingSupported() &&
        getStage().getStudioProfilers().getIdeServices().getFeatureConfig().isLiveAllocationsSamplingEnabled()) {
      toolbar.add(myAllocationSamplingRateLabel);
      toolbar.add(myAllocationSamplingRateDropDown);
      if (getStage().isNativeAllocationSamplingEnabled()) {
        toolbar.add(new FlatSeparator());
        toolbar.add(myNativeAllocationButton);
        toolbar.add(myCaptureElapsedTime);
      }

      JComboBoxView<LiveAllocationSamplingMode, MemoryProfilerAspect> sampleRateComboView =
        new JComboBoxView<>(myAllocationSamplingRateDropDown,
                            getStage().getAspect(),
                            MemoryProfilerAspect.LIVE_ALLOCATION_SAMPLING_MODE,
                            getStage()::getSupportedLiveAllocationSamplingMode,
                            getStage()::getLiveAllocationSamplingMode,
                            getStage()::requestLiveAllocationSamplingModeUpdate);
      sampleRateComboView.bind();
      myAllocationSamplingRateDropDown.setRenderer(new LiveAllocationSamplingModeRenderer());
    }
    else {
      // useLiveAllocationTracking can return false if the user previously set live allocation tracking to "None". In this case
      // no live allocation tracking events are returned and false is returned. As such we enter this case and we still need to check
      // if we should be using native allocation tracking or not.
      // TODO (b/150651682): Remove this condition when useLiveAllocationTracking bug is fixed.
      if (getStage().isNativeAllocationSamplingEnabled()) {
        toolbar.add(new FlatSeparator());
        toolbar.add(myNativeAllocationButton);
      }
      else {
        toolbar.add(myAllocationButton);
      }
      toolbar.add(myCaptureElapsedTime);
    }

    StudioProfilers profilers = getStage().getStudioProfilers();
    Runnable toggleButtons = () -> {
      resetRecordingButtons();
      liveAllocationStatusChanged();  // update myAllocationSamplingRateLabel and myAllocationSamplingRateDropDown
    };
    profilers.getSessionsManager().addDependency(this).onChange(SessionAspect.SELECTED_SESSION, toggleButtons);
    getStage().getAspect().addDependency(this).onChange(MemoryProfilerAspect.LIVE_ALLOCATION_STATUS,
                                                        this::liveAllocationStatusChanged);
    toggleButtons.run();
    return panel;
  }

  private void resetRecordingButtons() {
    boolean isAlive = getStage().getStudioProfilers().getSessionsManager().isSessionAlive();
    myForceGarbageCollectionButton.setEnabled(isAlive);
    myHeapDumpButton.setEnabled(isAlive && !getStage().isTrackingAllocations());
    myAllocationButton.setEnabled(isAlive);
    myNativeAllocationButton.setEnabled(isAlive && !isSelectedSessionDeviceX86OrX64());
  }

  private void disableRecordingButtons() {
    myAllocationButton.setEnabled(false);
    myNativeAllocationButton.setEnabled(false);
    myHeapDumpButton.setEnabled(false);
  }

  @VisibleForTesting
  @NotNull
  public Splitter getMainSplitter() {
    return myLayout.getMainSplitter();
  }

  @VisibleForTesting
  @NotNull
  public Splitter getChartCaptureSplitter() {
    return myLayout.getChartCaptureSplitter();
  }

  @VisibleForTesting
  @NotNull
  public JPanel getCapturePanel() {
    return myLayout.getCapturePanel().getComponent();
  }

  @VisibleForTesting
  @NotNull
  MemoryCaptureView getCaptureView() {
    return myLayout.getCapturePanel().getCaptureView();
  }

  @VisibleForTesting
  @NotNull
  MemoryHeapView getHeapView() {
    return myLayout.getCapturePanel().getHeapView();
  }

  @VisibleForTesting
  @NotNull
  MemoryClassGrouping getClassGrouping() {
    return myLayout.getCapturePanel().getClassGrouping();
  }

  @VisibleForTesting
  @NotNull
  MemoryClassifierView getClassifierView() {
    return myLayout.getCapturePanel().getClassifierView();
  }

  @VisibleForTesting
  @NotNull
  MemoryClassSetView getClassSetView() {
    return myLayout.getCapturePanel().getClassSetView();
  }

  @VisibleForTesting
  @NotNull
  MemoryInstanceDetailsView getInstanceDetailsView() {
    return myLayout.getCapturePanel().getInstanceDetailsView();
  }

  @VisibleForTesting
  @NotNull
  JLabel getCaptureElapsedTimeLabel() {
    return myCaptureElapsedTime;
  }

  @VisibleForTesting
  @NotNull
  JLabel getCaptureInfoMessage() {
    return myLayout.getCapturePanel().getCaptureInfoMessage();
  }

  private void liveAllocationStatusChanged() {
    boolean isAlive = getStage().getStudioProfilers().getSessionsManager().isSessionAlive();
    boolean isReady = getStage().isLiveAllocationTrackingReady();
    if (isAlive) {
      if (isReady) {
        myAllocationSamplingRateLabel.setEnabled(true);
        myAllocationSamplingRateDropDown.setEnabled(true);
        myAllocationSamplingRateLabel.setToolTipText(null);
        myAllocationSamplingRateDropDown.setToolTipText(null);
      }
      else {
        myAllocationSamplingRateLabel.setEnabled(false);
        myAllocationSamplingRateDropDown.setEnabled(false);
        myAllocationSamplingRateLabel.setToolTipText(LIVE_ALLOCATION_TRACKING_NOT_READY_TOOLTIP);
        myAllocationSamplingRateDropDown.setToolTipText(LIVE_ALLOCATION_TRACKING_NOT_READY_TOOLTIP);
      }
    }
    else {
      myAllocationSamplingRateLabel.setEnabled(false);
      myAllocationSamplingRateDropDown.setEnabled(false);
    }
  }

  private boolean isSelectedSessionDeviceX86OrX64() {
    String abi = getStage().getStudioProfilers().getSessionsManager().getSelectedSessionMetaData().getProcessAbi();
    return abi.equalsIgnoreCase("x86") || abi.equalsIgnoreCase("x86_64");
  }

  private void allocationTrackingChanged() {
    if (getStage().isTrackingAllocations()) {
      myAllocationButton.setText(STOP_TEXT);
      myAllocationButton.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.STOP_RECORDING));
      myAllocationButton.setToolTipText("Stop recording");
      myNativeAllocationButton.setText(STOP_NATIVE_TEXT);
      myNativeAllocationButton.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.STOP_RECORDING));
      myNativeAllocationButton.setToolTipText(STOP_NATIVE_TEXT);
      myCaptureElapsedTime.setText(TimeFormatter.getSemiSimplifiedClockString(0));
    }
    else {
      myCaptureElapsedTime.setText("");
      myAllocationButton.setText(RECORD_TEXT);
      myAllocationButton.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.RECORD));
      myAllocationButton.setToolTipText("Record memory allocations");
      myNativeAllocationButton.setText(RECORD_NATIVE_TEXT);
      myNativeAllocationButton.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.RECORD));
      myNativeAllocationButton.setToolTipText(isSelectedSessionDeviceX86OrX64() ? X86_RECORD_NATIVE_TOOLTIP : RECORD_NATIVE_TEXT);
    }
    resetRecordingButtons();
  }

  private void updateCaptureElapsedTime() {
    if (getStage().isTrackingAllocations() &&
        (!getStage().isLiveAllocationTrackingReady() || getStage().isNativeAllocationSamplingEnabled())) {
      long elapsedTimeUs = TimeUnit.NANOSECONDS.toMicros(getStage().getAllocationTrackingElapsedTimeNs());
      myCaptureElapsedTime.setText(TimeFormatter.getSemiSimplifiedClockString(elapsedTimeUs));
    }
  }

  private void buildContextMenu() {
    if (myTimelineComponent == null) {
      return;
    }

    RangeSelectionComponent rangeSelectionComponent = myTimelineComponent.getRangeSelectionComponent();

    IdeProfilerComponents ideProfilerComponents = getIdeComponents();
    ContextMenuInstaller contextMenuInstaller = ideProfilerComponents.createContextMenuInstaller();

    ProfilerAction exportHeapDumpAction = new ProfilerAction.Builder("Export...").setIcon(AllIcons.ToolbarDecorator.Export).build();
    contextMenuInstaller.installGenericContextMenu(
      rangeSelectionComponent, exportHeapDumpAction,
      x -> getCaptureIntersectingWithMouseX(x) != null &&
           getCaptureIntersectingWithMouseX(x).isExportable(),
      x -> getIdeComponents().createExportDialog().open(
        () -> "Export capture to file",
        () -> MemoryProfiler.generateCaptureFileName(),
        () -> getCaptureIntersectingWithMouseX(x).getExportableExtension(),
        file -> getStage().getStudioProfilers().getIdeServices().saveFile(
          file,
          (output) -> {
            try {
              getCaptureIntersectingWithMouseX(x).saveToFile(output);
            }
            catch (IOException e) {
              getLogger().warn(e);
            }
          }, null)));
    contextMenuInstaller.installGenericContextMenu(rangeSelectionComponent, ContextMenuItem.SEPARATOR);

    if (!getStage().isLiveAllocationTrackingReady()) {
      contextMenuInstaller.installGenericContextMenu(rangeSelectionComponent, myAllocationAction);
      contextMenuInstaller.installGenericContextMenu(rangeSelectionComponent, myStopAllocationAction);
    }
    if (getStage().isNativeAllocationSamplingEnabled()) {
      contextMenuInstaller.installGenericContextMenu(rangeSelectionComponent, myNativeAllocationAction);
      contextMenuInstaller.installGenericContextMenu(rangeSelectionComponent, myStopNativeAllocationAction);
    }
    contextMenuInstaller.installGenericContextMenu(rangeSelectionComponent, myForceGarbageCollectionAction);
    contextMenuInstaller.installGenericContextMenu(rangeSelectionComponent, ContextMenuItem.SEPARATOR);
    contextMenuInstaller.installGenericContextMenu(rangeSelectionComponent, myHeapDumpAction);
    contextMenuInstaller.installGenericContextMenu(rangeSelectionComponent, ContextMenuItem.SEPARATOR);

    getProfilersView().installCommonMenuItems(rangeSelectionComponent);
  }

  /**
   * Returns the memory capture object that intersects with the mouse X coordinate within {@link #myRangeSelectionComponent}.
   */
  @Nullable
  private CaptureObject getCaptureIntersectingWithMouseX(int mouseXLocation) {
    assert myTimelineComponent != null;
    Range range = getStage().getTimeline().getViewRange();
    double pos = mouseXLocation / myTimelineComponent.getRangeSelectionComponent().getSize().getWidth() * range.getLength() + range.getMin();
    CaptureDurationData<? extends CaptureObject> durationData = getStage().getIntersectingCaptureDuration(new Range(pos, pos));
    return durationData == null ? null : durationData.getCaptureEntry().getCaptureObject();
  }

  private void captureObjectChanged() {
    // Forcefully ends the previous loading operation if it is still ongoing.
    stopLoadingUi();
    myCaptureObject = getStage().getCaptureSelection().getSelectedCapture();
    if (myCaptureObject == null) {
      resetRecordingButtons();
      myLayout.setShowingCaptureUi(false);
      return;
    }

    if (myCaptureObject.isDoneLoading()) {
      // If a capture is initiated on stage enter, we will not have gotten a chance to listen in on the capture done loading event.``
      captureObjectFinishedLoading();
    }
    else {
      disableRecordingButtons();
      myLayout.setLoadingUiVisible(true);
    }
  }

  private void captureObjectFinishedLoading() {
    resetRecordingButtons();
    // If the capture is an imported file, myRangeSelectionComponent is null.
    // If it is part of a profiler session, myRangeSelectionComponent is not null and should obtain the focus.
    if (myTimelineComponent != null) {
      myTimelineComponent.getRangeSelectionComponent().requestFocus();
    }

    if (myCaptureObject != getStage().getCaptureSelection().getSelectedCapture() || myCaptureObject == null) {
      return;
    }

    myLayout.setShowingCaptureUi(true);
  }

  private void stopLoadingUi() {
    if (myCaptureObject != null && myLayout.isLoadingUiVisible()) {
      myLayout.setLoadingUiVisible(false);
    }
  }

  private void showHeapDumpInProgress() {
    getComponent().removeAll();
    myHeapDumpLoadingPanel.setChildComponent(myLayout.getComponent());
    getComponent().add(myHeapDumpLoadingPanel.getComponent(), BorderLayout.CENTER);
    myHeapDumpLoadingPanel.startLoading();
  }

  private void hideHeapDumpInProgress() {
    myHeapDumpLoadingPanel.stopLoading();
    getComponent().removeAll();
    myHeapDumpLoadingPanel.setChildComponent(null);
    getComponent().add(myLayout.getComponent());
  }

  @VisibleForTesting
  static class LiveAllocationSamplingModeRenderer extends ProfilerComboboxCellRenderer<LiveAllocationSamplingMode> {
    @Override
    protected void customizeCellRenderer(@NotNull JList list,
                                         LiveAllocationSamplingMode value,
                                         int index,
                                         boolean selected,
                                         boolean hasFocus) {
      append(value.getDisplayName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }
}
