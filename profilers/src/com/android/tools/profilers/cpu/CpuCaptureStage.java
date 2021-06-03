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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.BoxSelectionListener;
import com.android.tools.adtui.model.BoxSelectionModel;
import com.android.tools.adtui.model.DefaultTimeline;
import com.android.tools.adtui.model.MultiSelectionModel;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.Timeline;
import com.android.tools.adtui.model.event.EventModel;
import com.android.tools.adtui.model.event.LifecycleEventModel;
import com.android.tools.adtui.model.event.UserEvent;
import com.android.tools.adtui.model.trackgroup.TrackGroupActionListener;
import com.android.tools.adtui.model.trackgroup.TrackGroupModel;
import com.android.tools.adtui.model.trackgroup.TrackModel;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.idea.transport.EventStreamServer;
import com.android.tools.profiler.perfetto.proto.TraceProcessor;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profilers.NullMonitorStage;
import com.android.tools.profilers.ProfilerTrackRendererType;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.cpu.analysis.CpuAnalysisModel;
import com.android.tools.profilers.cpu.analysis.CpuAnalyzable;
import com.android.tools.profilers.cpu.analysis.CpuFullTraceAnalysisModel;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration;
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameEventTooltip;
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameEventTrackModel;
import com.android.tools.profilers.cpu.systemtrace.BufferQueueTooltip;
import com.android.tools.profilers.cpu.systemtrace.BufferQueueTrackModel;
import com.android.tools.profilers.cpu.systemtrace.CpuCoreTrackModel;
import com.android.tools.profilers.cpu.systemtrace.CpuFrameTooltip;
import com.android.tools.profilers.cpu.systemtrace.CpuFramesModel;
import com.android.tools.profilers.cpu.systemtrace.CpuFrequencyTooltip;
import com.android.tools.profilers.cpu.systemtrace.CpuFrequencyTrackModel;
import com.android.tools.profilers.cpu.systemtrace.CpuKernelTooltip;
import com.android.tools.profilers.cpu.systemtrace.CpuSystemTraceData;
import com.android.tools.profilers.cpu.systemtrace.CpuThreadSliceInfo;
import com.android.tools.profilers.cpu.systemtrace.RssMemoryTooltip;
import com.android.tools.profilers.cpu.systemtrace.RssMemoryTrackModel;
import com.android.tools.profilers.cpu.systemtrace.SurfaceflingerTooltip;
import com.android.tools.profilers.cpu.systemtrace.SurfaceflingerTrackModel;
import com.android.tools.profilers.cpu.systemtrace.SystemTraceCpuCapture;
import com.android.tools.profilers.cpu.systemtrace.SystemTraceFrame;
import com.android.tools.profilers.cpu.systemtrace.VsyncTooltip;
import com.android.tools.profilers.cpu.systemtrace.VsyncTrackModel;
import com.android.tools.profilers.event.LifecycleEventDataSeries;
import com.android.tools.profilers.event.LifecycleTooltip;
import com.android.tools.profilers.event.UserEventDataSeries;
import com.android.tools.profilers.event.UserEventTooltip;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class holds the models and capture data for the {@code com.android.tools.profilers.cpu.CpuCaptureStageView}.
 * This stage is set when a capture is selected from the {@link CpuProfilerStage}, or when a capture is imported.
 */
public class CpuCaptureStage extends Stage<Timeline> {
  public enum Aspect {
    /**
     * Triggered when the stage changes state from parsing to analyzing. This can also be viewed as capture parsing completed.
     * If the capture parsing fails the stage will transfer back to the {@link CpuProfilerStage}
     */
    STATE,
    /**
     * Triggered when a new analysis model is added / removed.
     */
    ANALYSIS_MODEL_UPDATED,
  }

  public enum State {
    /**
     * Initial state set when creating this stage. Parsing happens when {@link #enter} is called
     */
    PARSING,
    /**
     * When parsing has completed the state transitions to analyzing, we remain in this state while viewing data of the capture.
     */
    ANALYZING,
  }

  /**
   * Helper function to save trace data to disk. The file is put in to the users temp directory with the format cpu_trace_[traceid].trace.
   * If the file exists FileUtil will append numbers to the end making it unique.
   */
  @NotNull
  public static File saveCapture(long traceId, ByteString data) {
    try {
      File trace = FileUtil.createTempFile(String.format(Locale.US, "cpu_trace_%d", traceId), ".trace", true);
      try (FileOutputStream out = new FileOutputStream(trace)) {
        out.write(data.toByteArray());
      }
      return trace;
    }
    catch (IOException io) {
      throw new IllegalStateException("Unable to save trace to disk");
    }
  }

  @Nullable
  private static File getAndSaveCapture(@NotNull StudioProfilers profilers, long traceId) {
    Transport.BytesRequest traceRequest = Transport.BytesRequest.newBuilder()
      .setStreamId(profilers.getSession().getStreamId())
      .setId(String.valueOf(traceId))
      .build();
    Transport.BytesResponse traceResponse = profilers.getClient().getTransportClient().getBytes(traceRequest);
    if (!traceResponse.getContents().isEmpty()) {
      return saveCapture(traceId, traceResponse.getContents());
    }
    return null;
  }

  /**
   * Responsible for parsing trace files into {@link CpuCapture}.
   * Parsed captures should be obtained from this object.
   */
  private final CpuCaptureHandler myCpuCaptureHandler;
  private final AspectModel<Aspect> myAspect = new AspectModel<>();
  private final List<CpuAnalysisModel> myAnalysisModels = new ArrayList<>();
  private final List<TrackGroupModel> myTrackGroupModels = new ArrayList<>();
  private final MultiSelectionModel<CpuAnalyzable> myMultiSelectionModel = new MultiSelectionModel<>();

  private CpuCaptureMinimapModel myMinimapModel;
  private State myState = State.PARSING;

  // Accessible only when in state analyzing
  private CpuCapture myCapture;

  /**
   * The track groups share a timeline based on the minimap selection.
   * <p>
   * Data range: capture range;
   * View range: minimap selection;
   * Tooltip range: all track groups share the same mouse-over range, different from the minimap or profilers;
   * Selection range: union of all selected trace events;
   */
  private final Timeline myTrackGroupTimeline = new DefaultTimeline();

  /**
   * Create a capture stage that loads a given trace id. If a trace id is not found null will be returned.
   */
  @Nullable
  public static CpuCaptureStage create(@NotNull StudioProfilers profilers, @NotNull ProfilingConfiguration configuration, long traceId) {
    File captureFile = getAndSaveCapture(profilers, traceId);
    if (captureFile == null) {
      return null;
    }
    String captureProcessNameHint = CpuProfiler.getTraceInfoFromId(profilers, traceId).getConfiguration().getAppName();
    return new CpuCaptureStage(profilers, configuration, captureFile, traceId, captureProcessNameHint, profilers.getSession().getPid());
  }

  /**
   * Create a capture stage based on a file, this is used for importing traces. In the absence of a trace ID, the session ID is used.
   */
  @NotNull
  public static CpuCaptureStage create(@NotNull StudioProfilers profilers,
                                       @NotNull ProfilingConfiguration configuration,
                                       @NotNull File captureFile,
                                       long sessionId) {
    return new CpuCaptureStage(profilers, configuration, captureFile, sessionId, null, 0);
  }

  /**
   * Create a capture stage that loads a given file.
   */
  @VisibleForTesting
  public CpuCaptureStage(@NotNull StudioProfilers profilers,
                  @NotNull ProfilingConfiguration configuration,
                  @NotNull File captureFile,
                  long traceId,
                  @Nullable String captureProcessNameHint,
                  int captureProcessIdHint) {
    super(profilers);
    myCpuCaptureHandler = new CpuCaptureHandler(
      profilers.getIdeServices(), captureFile, traceId, configuration, captureProcessNameHint, captureProcessIdHint);
  }

  public State getState() {
    return myState;
  }

  @NotNull
  public AspectModel<Aspect> getAspect() {
    return myAspect;
  }

  @NotNull
  public CpuCaptureHandler getCaptureHandler() {
    return myCpuCaptureHandler;
  }

  @NotNull
  public List<TrackGroupModel> getTrackGroupModels() {
    return myTrackGroupModels;
  }

  @NotNull
  public MultiSelectionModel<CpuAnalyzable> getMultiSelectionModel() {
    return myMultiSelectionModel;
  }

  @NotNull
  public CpuCaptureMinimapModel getMinimapModel() {
    assert myState == State.ANALYZING;
    return myMinimapModel;
  }

  @NotNull
  public List<CpuAnalysisModel> getAnalysisModels() {
    return myAnalysisModels;
  }

  /**
   * @return the capture timeline, different from the profiler's session timeline.
   */
  @NotNull
  public Timeline getCaptureTimeline() {
    return getCapture().getTimeline();
  }

  /**
   * @return the track group timeline specific to the capture stage.
   */
  @NotNull
  @Override
  public Timeline getTimeline() {
    return myTrackGroupTimeline;
  }

  private void setState(State state) {
    myState = state;
    myAspect.changed(Aspect.STATE);
  }

  @NotNull
  public CpuCapture getCapture() {
    assert myState == State.ANALYZING;
    return myCapture;
  }

  @Override
  public void enter() {
    getStudioProfilers().getUpdater().register(myCpuCaptureHandler);
    getStudioProfilers().getIdeServices().getFeatureTracker().trackEnterStage(getStageType());
    myCpuCaptureHandler.parse(capture -> {
      try {
        if (capture == null) {
          // Generic catch all for capture failing to load, this happens for both import and live captures.
          if (getStudioProfilers().getSessionsManager().isSessionAlive()) {
            // User will get a notification then sent back to the CpuProfilerStage
            getStudioProfilers().getIdeServices().getMainExecutor()
              .execute(() -> getStudioProfilers().setStage(getParentStage()));
          }
          else {
            // If the user was importing a trace the user will be sent to the null stage with an error + notification.
            getStudioProfilers().getIdeServices().getMainExecutor().execute(
              () -> getStudioProfilers().setStage(new NullMonitorStage(
                getStudioProfilers(),
                "The profiler was unable to parse the trace file. Please make sure the file selected is a valid trace.")));
          }
        }
        else {
          myCapture = capture;
          onCaptureParsed(capture);
          setState(State.ANALYZING);
        }
      }
      catch (Exception ex) {
        // Logging if an exception happens since setState may trigger various callbacks.
        Logger.getInstance(CpuCaptureStage.class).error(ex);
      }
    });
  }

  @Override
  public void exit() {
    getStudioProfilers().getUpdater().unregister(myCpuCaptureHandler);
  }

  @Override
  public AndroidProfilerEvent.Stage getStageType() {
    return AndroidProfilerEvent.Stage.CPU_CAPTURE_STAGE;
  }

  public void addCpuAnalysisModel(@NotNull CpuAnalysisModel model) {
    myAnalysisModels.add(model);
    myAspect.changed(Aspect.ANALYSIS_MODEL_UPDATED);
  }

  @NotNull
  @Override
  public Stage<?> getParentStage() {
    return new CpuProfilerStage(getStudioProfilers());
  }

  @NotNull
  @Override
  public Class<? extends Stage<?>> getHomeStageClass() {
    return CpuProfilerStage.class;
  }

  public void removeCpuAnalysisModel(int index) {
    myAnalysisModels.remove(index);
    myAspect.changed(Aspect.ANALYSIS_MODEL_UPDATED);
  }

  private void onCaptureParsed(@NotNull CpuCapture capture) {
    myTrackGroupTimeline.getDataRange().set(capture.getRange());
    myMinimapModel = new CpuCaptureMinimapModel(getStudioProfilers(), capture, getTimeline().getViewRange());
    initTrackGroupList(capture);
    addCpuAnalysisModel(new CpuFullTraceAnalysisModel(capture, getTimeline().getViewRange()));
    if (getStudioProfilers().getSession().getPid() == 0) {
      // For an imported traces we need to insert a CPU_TRACE event into the database. This is used by the Sessions' panel to display the
      // correct trace type associated with the imported file.
      insertImportedTraceEvent(capture);
    }
  }

  private void insertImportedTraceEvent(@NotNull CpuCapture capture) {
    Cpu.CpuTraceInfo importedTraceInfo = Cpu.CpuTraceInfo.newBuilder()
      // Use session ID as trace ID for imported traces.
      .setTraceId(getStudioProfilers().getSession().getSessionId())
      .setFromTimestamp(TimeUnit.MICROSECONDS.toNanos((long)capture.getRange().getMin()))
      .setToTimestamp(TimeUnit.MICROSECONDS.toNanos((long)capture.getRange().getMax()))
      .setConfiguration(
        Cpu.CpuTraceConfiguration
          .newBuilder()
          .setUserOptions(Cpu.CpuTraceConfiguration.UserOptions.newBuilder().setTraceType(capture.getType())))
      .build();
    // TODO(b/141560550): add test when we can mock TransportService#registerStreamServer.
    EventStreamServer streamServer =
      getStudioProfilers().getSessionsManager().getEventStreamServer(getStudioProfilers().getSession().getStreamId());
    if (streamServer != null) {
      streamServer.getEventDeque().offer(
        Common.Event.newBuilder()
          .setGroupId(importedTraceInfo.getTraceId())
          .setTimestamp(importedTraceInfo.getToTimestamp())
          .setIsEnded(true)
          .setKind(Common.Event.Kind.CPU_TRACE)
          .setCpuTrace(
            Cpu.CpuTraceData.newBuilder().setTraceEnded(Cpu.CpuTraceData.TraceEnded.newBuilder().setTraceInfo(importedTraceInfo)))
          .build());
    }
  }

  /**
   * The order of track groups dictates their default order in the UI.
   */
  private void initTrackGroupList(@NotNull CpuCapture capture) {
    myTrackGroupModels.clear();

    FeatureTracker featureTracker = getStudioProfilers().getIdeServices().getFeatureTracker();
    // Interaction events, e.g. user interaction, app lifecycle. Recorded trace only.
    if (getStudioProfilers().getSession().getPid() != 0) {
      myTrackGroupModels.add(createInteractionTrackGroup(getStudioProfilers(), getTimeline()));
    }

    if (capture.getSystemTraceData() != null) {
      // Display pipeline events, e.g. frames, surfaceflinger. Systrace only.
      myTrackGroupModels.add(createDisplayTrackGroup(capture.getMainThreadId(), capture.getSystemTraceData(), getTimeline()));
      // Android frame lifecycle. Systrace only.
      if (!capture.getSystemTraceData().getAndroidFrameLayers().isEmpty()) {
        capture.getSystemTraceData().getAndroidFrameLayers().forEach(
          layer -> myTrackGroupModels.add(createFrameLifecycleTrackGroup(layer, getTimeline()))
        );
      }
      // CPU per-core usage and event etc. Systrace only.
      myTrackGroupModels.add(createCpuCoresTrackGroup(capture.getMainThreadId(), capture.getSystemTraceData(), getTimeline()));
      // RSS memory counters.
      myTrackGroupModels.add(createRssMemoryTrackGroup(capture.getSystemTraceData(), getTimeline()));
    }

    // Thread states and trace events.
    myTrackGroupModels.add(createThreadsTrackGroup(capture, getTimeline(), getMultiSelectionModel(), featureTracker));

    // Add action listener for tracking all track group actions.
    myTrackGroupModels.forEach(model -> model.addActionListener(new TrackGroupActionListener() {
      @Override
      public void onGroupMovedUp(@NotNull String title) {
        featureTracker.trackMoveTrackGroupUp(title);
      }

      @Override
      public void onGroupMovedDown(@NotNull String title) {
        featureTracker.trackMoveTrackGroupDown(title);
      }

      @Override
      public void onGroupCollapsed(@NotNull String title) {
        featureTracker.trackCollapseTrackGroup(title);
      }

      @Override
      public void onGroupExpanded(@NotNull String title) {
        featureTracker.trackExpandTrackGroup(title);
      }
    }));
  }

  private static TrackGroupModel createInteractionTrackGroup(@NotNull StudioProfilers studioProfilers, @NotNull Timeline timeline) {
    TrackGroupModel interaction = TrackGroupModel.newBuilder().setTitle("Interaction").build();
    EventModel<UserEvent> userEventEventModel =
      new EventModel<>(new RangedSeries<>(timeline.getViewRange(), new UserEventDataSeries(studioProfilers)));
    LifecycleEventModel lifecycleEventModel =
      new LifecycleEventModel(
        new RangedSeries<>(timeline.getViewRange(), new LifecycleEventDataSeries(studioProfilers, false)),
        new RangedSeries<>(timeline.getViewRange(), new LifecycleEventDataSeries(studioProfilers, true)));
    interaction.addTrackModel(
      TrackModel.newBuilder(userEventEventModel, ProfilerTrackRendererType.USER_INTERACTION, "User")
        .setDefaultTooltipModel(new UserEventTooltip(timeline, userEventEventModel)));
    interaction.addTrackModel(
      TrackModel.newBuilder(lifecycleEventModel, ProfilerTrackRendererType.APP_LIFECYCLE, "Lifecycle")
        .setDefaultTooltipModel(new LifecycleTooltip(timeline, lifecycleEventModel)));
    return interaction;
  }

  private static TrackGroupModel createDisplayTrackGroup(
    int mainThreadId, @NotNull CpuSystemTraceData systemTraceData, @NotNull Timeline timeline) {

    TrackGroupModel display = TrackGroupModel.newBuilder()
      .setTitle("Display")
      .setTitleHelpText("This section contains display info. " +
                        "<p><b>Frames</b>: when a frame is being drawn. Long frames are colored red.</p>" +
                        "<p><b>SurfaceFlinger</b>: system process responsible for sending buffers to display.</p>" +
                        "<p><b>VSYNC</b>: a signal that synchronizes the display pipeline.</p>" +
                        "<p><b>BufferQueue</b>: how many frame buffers are queued up, waiting for SurfaceFlinger to consume.</p>")
      .setTitleHelpLink("Learn more", "https://source.android.com/devices/graphics")
      .build();

    // Frame
    CpuFramesModel.FrameState mainFrames = new CpuFramesModel.FrameState(
      "Main", mainThreadId, SystemTraceFrame.FrameThread.MAIN,
      systemTraceData, timeline.getViewRange());
    CpuFrameTooltip mainFrameTooltip = new CpuFrameTooltip(timeline);
    mainFrameTooltip.setFrameSeries(mainFrames.getSeries());
    display.addTrackModel(
      TrackModel.newBuilder(mainFrames, ProfilerTrackRendererType.FRAMES, "Frames").setDefaultTooltipModel(mainFrameTooltip));

    // Surfaceflinger
    SurfaceflingerTrackModel sfModel = new SurfaceflingerTrackModel(systemTraceData, timeline.getViewRange());
    SurfaceflingerTooltip sfTooltip = new SurfaceflingerTooltip(timeline, sfModel.getSurfaceflingerEvents());
    display.addTrackModel(
      TrackModel.newBuilder(sfModel, ProfilerTrackRendererType.SURFACEFLINGER, "SurfaceFlinger").setDefaultTooltipModel(sfTooltip));

    // VSYNC
    VsyncTrackModel vsyncModel = new VsyncTrackModel(systemTraceData, timeline.getViewRange());
    VsyncTooltip vsyncTooltip = new VsyncTooltip(timeline, vsyncModel.getVsyncCounterSeries());
    display.addTrackModel(TrackModel.newBuilder(vsyncModel, ProfilerTrackRendererType.VSYNC, "VSYNC").setDefaultTooltipModel(vsyncTooltip));

    // Buffer Queue
    BufferQueueTrackModel bufferQueueTrackModel = new BufferQueueTrackModel(systemTraceData, timeline.getViewRange());
    BufferQueueTooltip bufferQueueTooltip = new BufferQueueTooltip(timeline, bufferQueueTrackModel.getBufferQueueSeries());
    display.addTrackModel(TrackModel.newBuilder(bufferQueueTrackModel, ProfilerTrackRendererType.BUFFER_QUEUE, "BufferQueue")
                            .setDefaultTooltipModel(bufferQueueTooltip));
    return display;
  }

  private static TrackGroupModel createFrameLifecycleTrackGroup(@NotNull TraceProcessor.AndroidFrameEventsResult.Layer layer,
                                                                @NotNull Timeline timeline) {
    TrackGroupModel frameLayer = TrackGroupModel.newBuilder()
      .setTitle("Frame Lifecycle (" + layer.getLayerName() + ")")
      .setTitleHelpText(AndroidFrameEventTrackModel.getTitleHelpText())
      .build();

    layer.getPhaseList().stream().sorted(AndroidFrameEventTrackModel.getTrackComparator()).forEach(
      phase -> {
        AndroidFrameEventTrackModel phaseTrack = new AndroidFrameEventTrackModel(phase.getFrameEventList(), timeline.getViewRange());
        AndroidFrameEventTooltip tooltip = new AndroidFrameEventTooltip(timeline, phaseTrack.getSeries().get(0));
        String trackTitle = AndroidFrameEventTrackModel.getDisplayName(phase.getPhaseName());
        frameLayer.addTrackModel(TrackModel.newBuilder(phaseTrack, ProfilerTrackRendererType.ANDROID_FRAME_EVENT, trackTitle)
                                   .setDefaultTooltipModel(tooltip));
      }
    );
    return frameLayer;
  }

  private static TrackGroupModel createThreadsTrackGroup(@NotNull CpuCapture capture,
                                                         @NotNull Timeline timeline,
                                                         @NotNull MultiSelectionModel<CpuAnalyzable> multiSelectionModel,
                                                         @NotNull FeatureTracker featureTracker) {
    // Collapse threads for ART and SimplePerf traces.
    boolean collapseThreads = !(capture instanceof SystemTraceCpuCapture);
    List<CpuThreadInfo> threadInfos =
      capture.getThreads().stream().sorted(new CaptureThreadComparator(capture)).collect(Collectors.toList());
    String threadsTitle = String.format(Locale.getDefault(), "Threads (%d)", threadInfos.size());
    BoxSelectionModel boxSelectionModel = new BoxSelectionModel(timeline.getSelectionRange(), timeline.getViewRange());
    boxSelectionModel.addBoxSelectionListener(new BoxSelectionListener() {
      @Override
      public void boxSelectionCreated(long durationUs, int trackCount) {
        featureTracker.trackSelectBox(durationUs, trackCount);
      }
    });
    TrackGroupModel threads = TrackGroupModel.newBuilder()
      .setTitle(threadsTitle)
      .setTitleHelpText("This section contains thread info. Double-click on the thread name to expand/collapse. " +
                        "Shift+click to select multiple threads.")
      .setTrackSelectable(true)
      // For box selection
      .setBoxSelectionModel(boxSelectionModel)
      .build();
    for (CpuThreadInfo threadInfo : threadInfos) {
      String title = threadInfo.getName();
      // Since thread tracks display multiple elements with different tooltip we don't set a default tooltip model here but defer to the
      // track renderer to switch between its various tooltip models.
      threads.addTrackModel(
        TrackModel.newBuilder(
          new CpuThreadTrackModel(capture, threadInfo, timeline, multiSelectionModel), ProfilerTrackRendererType.CPU_THREAD, title)
          .setCollapsible(true)
          .setCollapsed(collapseThreads));
    }
    return threads;
  }

  private static TrackGroupModel createCpuCoresTrackGroup(int mainThreadId,
                                                          @NotNull CpuSystemTraceData systemTraceData,
                                                          @NotNull Timeline timeline) {
    int cpuCount = systemTraceData.getCpuCount();
    String coresTitle = String.format(Locale.getDefault(), "CPU cores (%d)", cpuCount);
    TrackGroupModel cores = TrackGroupModel.newBuilder().setTitle(coresTitle).setCollapsedInitially(true).build();
    for (int cpuId = 0; cpuId < cpuCount; ++cpuId) {
      // CPU Core scheduling.
      final int coreId = cpuId;
      LazyDataSeries<CpuThreadSliceInfo> coreSchedSeries = new LazyDataSeries<>(() -> systemTraceData.getCpuThreadSliceInfoStates(coreId));
      CpuKernelTooltip kernelTooltip = new CpuKernelTooltip(timeline, mainThreadId);
      kernelTooltip.setCpuSeries(cpuId, coreSchedSeries);
      cores.addTrackModel(TrackModel.newBuilder(new CpuCoreTrackModel(coreSchedSeries, timeline.getViewRange(), mainThreadId),
                                                ProfilerTrackRendererType.CPU_CORE, "CPU " + cpuId).setDefaultTooltipModel(kernelTooltip));

      // CPU Core frequency.
      String cpuFrequencyTitle = "CPU " + cpuId + " Frequency";
      List<SeriesData<Long>> cpuFreqCounters = systemTraceData.getCpuCounters().get(cpuId).getOrDefault("cpufreq", Collections.emptyList());
      CpuFrequencyTrackModel cpuFreqTrackModel = new CpuFrequencyTrackModel(cpuFreqCounters, timeline.getViewRange());
      CpuFrequencyTooltip cpuFreqTooltip = new CpuFrequencyTooltip(timeline, cpuId, cpuFreqTrackModel.getCpuFrequencySeries());
      cores.addTrackModel(TrackModel.newBuilder(cpuFreqTrackModel, ProfilerTrackRendererType.CPU_FREQUENCY, cpuFrequencyTitle)
                            .setDefaultTooltipModel(cpuFreqTooltip));
    }
    return cores;
  }

  private static TrackGroupModel createRssMemoryTrackGroup(@NotNull CpuSystemTraceData systemTraceData, @NotNull Timeline timeline) {
    TrackGroupModel memory = TrackGroupModel.newBuilder()
      .setTitle("Process Memory (RSS)")
      .setTitleHelpText("This section shows the memory footprint of the app." +
                        "<p><b>Resident Set Size (RSS)</b> is the portion of memory the app occupies in RAM, " +
                        "combining both shared and non-shared pages.</p>")
      .setTitleHelpLink("Learn more", "https://d.android.com/r/studio-ui/profiler/rss-memory")
      .setCollapsedInitially(true)
      .build();
    RssMemoryTrackModel.Companion.getIncludedCountersNameMap().forEach(
      (counterName, displayName) -> {
        if (systemTraceData.getMemoryCounters().containsKey(counterName)) {
          RssMemoryTrackModel trackModel =
            new RssMemoryTrackModel(systemTraceData.getMemoryCounters().get(counterName), timeline.getViewRange());
          RssMemoryTooltip tooltip = new RssMemoryTooltip(timeline, counterName, trackModel.getMemoryCounterSeries());
          memory.addTrackModel(
            TrackModel.newBuilder(trackModel, ProfilerTrackRendererType.RSS_MEMORY, displayName).setDefaultTooltipModel(tooltip));
        }
      }
    );
    return memory;
  }
}
