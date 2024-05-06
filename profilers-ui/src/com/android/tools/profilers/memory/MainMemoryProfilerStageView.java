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
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.adtui.stdui.ContextMenuItem;
import com.android.tools.adtui.stdui.DefaultContextMenuItem;
import com.android.tools.inspectors.common.ui.ContextMenuInstaller;
import com.android.tools.profilers.DismissibleMessage;
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.RecordingOptionsView;
import com.android.tools.profilers.StudioProfilersView;
import com.android.tools.profilers.SupportLevel;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.sessions.SessionAspect;
import com.android.tools.profilers.stacktrace.LoadingPanel;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Splitter;
import java.awt.BorderLayout;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MainMemoryProfilerStageView extends BaseStreamingMemoryProfilerStageView<MainMemoryProfilerStage> {
  private static Logger getLogger() {
    return Logger.getInstance(MainMemoryProfilerStageView.class);
  }
  private static final String SHOW_PROFILEABLE_MESSAGE = "profileable.memory.message";
  private final MemoryProfilerStageLayout myLayout;
  @Nullable private final MemoryTimelineComponent myTimelineComponent;
  @Nullable private CaptureObject myCaptureObject = null;
  @NotNull private final JButton myForceGarbageCollectionButton;
  @NotNull private final DefaultContextMenuItem myForceGarbageCollectionAction;
  @NotNull private final RecordingOptionsView myRecordingOptionsView;
  @NotNull private final LoadingPanel myHeapDumpLoadingPanel;
  @NotNull private final GarbageCollectionComponent myGarbageCollectionComponent;

  public MainMemoryProfilerStageView(@NotNull StudioProfilersView profilersView, @NotNull MainMemoryProfilerStage stage) {
    super(profilersView, stage);

    // Do not initialize the monitor UI if it only contains heap dump data.
    // In this case, myRangeSelectionComponent is null and we will not build the context menu.
    myTimelineComponent = getStage().isMemoryCaptureOnly() ?
                          null :
                          new MemoryTimelineComponent(this, buildTimeAxis(profilersView.getStudioProfilers()));
    myGarbageCollectionComponent = new GarbageCollectionComponent();
    CapturePanel capturePanel = new CapturePanel(getProfilersView(),
                                                 getStage().getCaptureSelection(),
                                                 getStage().isMemoryCaptureOnly() ? null : getSelectionTimeLabel(),
                                                 getStage().getRangeSelectionModel().getSelectionRange(),
                                                 getIdeComponents(),
                                                 getStage().getTimeline(),
                                                 false);

    myRecordingOptionsView = new RecordingOptionsView(stage.getRecordingOptionsModel());
    myLayout = new MemoryProfilerStageLayout(myTimelineComponent, capturePanel, myRecordingOptionsView, this::makeLoadingPanel);
    getComponent().add(myLayout.getComponent(), BorderLayout.CENTER);

    myHeapDumpLoadingPanel = getIdeComponents().createLoadingPanel(-1);
    myHeapDumpLoadingPanel.setLoadingText("Capturing heap dump");

    myForceGarbageCollectionButton = myGarbageCollectionComponent.makeGarbageCollectionButton(
      stage.getMemoryDataProvider(), profilersView.getStudioProfilers());

    myForceGarbageCollectionAction = myGarbageCollectionComponent.makeGarbageCollectionAction(stage.getStudioProfilers(),
                                                                                              myForceGarbageCollectionButton,
                                                                                              getComponent());
    myForceGarbageCollectionButton.setToolTipText(myForceGarbageCollectionAction.getDefaultToolTipText());

    getStage().getAspect().addDependency(this)
      .onChange(MemoryProfilerAspect.TRACKING_ENABLED, this::allocationTrackingChanged)
      .onChange(MemoryProfilerAspect.HEAP_DUMP_STARTED, this::showHeapDumpInProgress)
      .onChange(MemoryProfilerAspect.HEAP_DUMP_FINISHED, this::hideHeapDumpInProgress);
    getStage().getCaptureSelection().getAspect().addDependency(this)
      .onChange(CaptureSelectionAspect.CURRENT_LOADING_CAPTURE, this::captureObjectChanged)
      .onChange(CaptureSelectionAspect.CURRENT_LOADED_CAPTURE, this::captureObjectFinishedLoading)
      .onChange(CaptureSelectionAspect.CURRENT_CAPTURE_ELAPSED_TIME, this::updateCaptureElapsedTime);

    Runnable onSessionChanged = () -> {
      myRecordingOptionsView.setEnabled(getStage().getStudioProfilers().getSessionsManager().isSessionAlive());
      updateGcButton();
    };
    getStage().getStudioProfilers().getSessionsManager().addDependency(this)
      .onChange(SessionAspect.SELECTED_SESSION, onSessionChanged);

    captureObjectChanged();
    allocationTrackingChanged();
    buildContextMenu();
    onSessionChanged.run();
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
  RecordingOptionsView getRecordingOptionsView() {
    return myRecordingOptionsView;
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
    updateGcButton();
    if (getStage().isLiveAllocationTrackingSupported()) {
      if (getStage().isNativeAllocationSamplingEnabled()) {
        toolbar.add(getCaptureElapsedTimeLabel());
      }
    }
    else {
      toolbar.add(getCaptureElapsedTimeLabel());
    }
    if (getStage().getStudioProfilers().getSelectedSessionSupportLevel() == SupportLevel.PROFILEABLE) {
      // add banner to outer panel's center so it can stretch,
      // rather than the toolbar's layout, which makes it wrap to just enough width
      panel.add(DismissibleMessage.of(
        getStage().getStudioProfilers(),
        SHOW_PROFILEABLE_MESSAGE,
        "Some features are disabled for profileable processes.",
        SupportLevel.DOC_LINK));
    }
    return panel;
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
  JLabel getCaptureInfoMessage() {
    return myLayout.getCapturePanel().getCaptureInfoMessage();
  }

  private boolean isSelectedSessionDeviceX86OrX64() {
    String abi = getStage().getStudioProfilers().getSessionsManager().getSelectedSessionMetaData().getProcessAbi();
    return abi.equalsIgnoreCase("x86") || abi.equalsIgnoreCase("x86_64");
  }

  private void allocationTrackingChanged() {
    getCaptureElapsedTimeLabel().setText(getStage().isTrackingAllocations()
                                         ? TimeFormatter.getSemiSimplifiedClockString(0)
                                         : "");
  }

  private void updateCaptureElapsedTime() {
    if (getStage().isTrackingAllocations() &&
        (!getStage().isLiveAllocationTrackingReady() || getStage().isNativeAllocationSamplingEnabled())) {
      long elapsedTimeUs = TimeUnit.NANOSECONDS.toMicros(getStage().getAllocationTrackingElapsedTimeNs());
      getCaptureElapsedTimeLabel().setText(TimeFormatter.getSemiSimplifiedClockString(elapsedTimeUs));
    }
  }

  private void buildContextMenu() {
    if (myTimelineComponent == null) {
      return;
    }

    RangeSelectionComponent rangeSelectionComponent = myTimelineComponent.getRangeSelectionComponent();

    IdeProfilerComponents ideProfilerComponents = getIdeComponents();
    ContextMenuInstaller contextMenuInstaller = ideProfilerComponents.createContextMenuInstaller();

    DefaultContextMenuItem exportHeapDumpAction =
      new DefaultContextMenuItem.Builder("Export...").setIcon(AllIcons.ToolbarDecorator.Export).build();
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
    contextMenuInstaller.installGenericContextMenu(rangeSelectionComponent, myForceGarbageCollectionAction);
    contextMenuInstaller.installGenericContextMenu(rangeSelectionComponent, ContextMenuItem.SEPARATOR);
    getProfilersView().installCommonMenuItems(rangeSelectionComponent);
  }

  /**
   * Returns the memory capture object that intersects with the mouse X coordinate
   * within {@link #{myTimelineComponent.getRangeSelectionComponent()}}.
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
      myLayout.setShowingCaptureUi(false);
      return;
    }

    if (myCaptureObject.isDoneLoading()) {
      // If a capture is initiated on stage enter, we will not have gotten a chance to listen in on the capture done loading event.``
      captureObjectFinishedLoading();
    }
    else {
      myLayout.setLoadingUiVisible(true);
    }
  }

  private void captureObjectFinishedLoading() {
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

  private void updateGcButton() {
    myGarbageCollectionComponent.updateGcButton(getStage().getStudioProfilers(), myForceGarbageCollectionButton);
  }
}
