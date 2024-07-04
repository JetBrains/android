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
package com.android.tools.profilers.cpu;

import static com.android.tools.profilers.ImportedSessionUtils.importFile;
import static com.android.tools.profilers.ImportedSessionUtils.importFileWithArtifactEvent;
import static com.android.tools.profilers.ImportedSessionUtils.makeEndedEvent;
import static com.android.tools.profilers.cpu.CpuCaptureParserUtil.getFileTraceType;

import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Trace;
import com.android.tools.profiler.proto.Trace.TraceInfo;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profilers.NullMonitorStage;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.StudioProfiler;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.TraceConfigOptionsUtils;
import com.android.tools.profilers.cpu.config.ImportedConfiguration;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType;
import com.android.tools.profilers.cpu.systemtrace.AtraceExporter;
import com.android.tools.profilers.sessions.SessionsManager;
import com.android.tools.profilers.transporteventutils.TransportUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CpuProfiler implements StudioProfiler {
  @NotNull
  private final StudioProfilers profilers;

  public CpuProfiler(@NotNull StudioProfilers profilers) {
    this.profilers = profilers;

    registerImportedSessionListener();
    registerTraceImportHandler();
  }

  private void onImportSessionSelected() {
    long traceId = profilers.getSession().getStartTimestamp();
    profilers.getIdeServices().runAsync(
      () -> CpuCaptureStage.create(profilers, new ImportedConfiguration(), CpuCaptureMetadata.CpuProfilerEntryPoint.UNKNOWN,
                                   traceId),
      captureStage -> {
        if (captureStage != null) {
          profilers.getIdeServices().getMainExecutor().execute(() -> profilers.setStage(captureStage));
        }
        else {
          profilers.getIdeServices().showNotification(CpuProfilerNotifications.IMPORT_TRACE_PARSING_FAILURE);
        }
      }
    );
  }

  private static Logger getLogger() {
    return Logger.getInstance(CpuProfiler.class);
  }

  /**
   * Registers a listener that will switch to the {@link CpuCaptureStage} when the {@link Common.Session} selected is a
   * {@link Common.SessionMetaData.SessionType#CPU_CAPTURE}.
   */
  private void registerImportedSessionListener() {
    profilers.registerSessionChangeListener(Common.SessionMetaData.SessionType.CPU_CAPTURE, this::onImportSessionSelected);
  }

  /**
   * Registers a handler for importing *.trace files. It will create a {@link Common.SessionMetaData.SessionType#CPU_CAPTURE}
   * {@link Common.Session} and select it.
   */
  private void registerTraceImportHandler() {
    SessionsManager sessionsManager = profilers.getSessionsManager();
    sessionsManager.registerImportHandler("trace", this::loadCapture);
    sessionsManager.registerImportHandler("pftrace", this::loadCapture);
    sessionsManager.registerImportHandler("perfetto-trace", this::loadCapture);
  }

  private void loadCapture(File file) throws IllegalStateException {
    boolean isTaskBasedUxEnabled = profilers.getIdeServices().getFeatureConfig().isTaskBasedUxEnabled();

    TraceType traceType = getFileTraceType(file, TraceType.UNSPECIFIED);
    if (isTaskBasedUxEnabled) {
      if (traceType == null || traceType == TraceType.UNSPECIFIED) {
        throw new IllegalStateException("Cannot import trace with type:\n" + traceType);
      }
    }

    if (isTaskBasedUxEnabled) {
      Function2<Long, Long, Common.Event> makeEvent = (start, end) -> {
        Trace.TraceInfo.Builder importedTraceInfo = Trace.TraceInfo.newBuilder()
          .setTraceId(start)
          .setFromTimestamp(start)
          .setToTimestamp(end);

        Trace.TraceConfiguration.Builder config = Trace.TraceConfiguration.newBuilder();
        TraceConfigOptionsUtils.addDefaultTraceOptions(config, traceType);
        importedTraceInfo.setConfiguration(config);

        return makeEndedEvent(start, end, Common.Event.Kind.CPU_TRACE, builder -> {
          builder.setTraceData(
            Trace.TraceData.newBuilder().setTraceEnded(Trace.TraceData.TraceEnded.newBuilder().setTraceInfo(importedTraceInfo)));
          return Unit.INSTANCE;
        });
      };

      importFileWithArtifactEvent(profilers.getSessionsManager(), file, Common.SessionData.SessionStarted.SessionType.CPU_CAPTURE,
                          makeEvent);
    }
    else {
      importFile(profilers.getSessionsManager(), file, Common.SessionData.SessionStarted.SessionType.CPU_CAPTURE);
    }

    profilers.getIdeServices().getFeatureTracker().trackCreateSession(Common.SessionMetaData.SessionType.CPU_CAPTURE,
                                                                      SessionsManager.SessionCreationSource.MANUAL);
  }

  @Override
  public @NotNull ProfilerMonitor newMonitor() {
    return new CpuMonitor(profilers);
  }

  @Override
  public void startProfiling(@NotNull Common.Session session) { }

  @Override
  public void stopProfiling(@NotNull Common.Session session) {
    List<TraceInfo> traces = getTraceInfoFromSession(profilers.getClient(), session);

    TraceInfo mostRecentTrace = traces.isEmpty() ? null : traces.get(traces.size() - 1);
    if (mostRecentTrace != null && mostRecentTrace.getToTimestamp() == -1) {
      stopTracing(profilers,
                  session,
                  mostRecentTrace.getConfiguration(),
                  null,
                  null);
    }
  }

  /**
   * Copies the content of the trace file corresponding to a {@link CpuTraceInfo} to a given {@link FileOutputStream}.
   */
  static void saveCaptureToFile(@NotNull StudioProfilers profilers,
                                @NotNull Common.Session session,
                                @NotNull TraceInfo info,
                                @NotNull OutputStream outputStream) {

    try {
      Transport.BytesRequest traceRequest = Transport.BytesRequest.newBuilder()
        .setStreamId(session.getStreamId())
        .setId(String.valueOf(info.getTraceId()))
        .build();
      Transport.BytesResponse traceResponse = profilers.getClient().getTransportClient().getBytes(traceRequest);

      // Atrace Format = [HEADER|ZlibData][HEADER|ZlibData]
      // Systrace Expected format = [HEADER|ZlipData]
      // As such exporting the file raw Systrace will only read the first header/data chunk.
      // Atrace captures come over as several parts combined into one file. As such we need an exporter
      // to handle converting the format to a format that Systrace can support. The reason for the multi-part file
      // is because Atrace dumps a compressed data file every X interval and this file represents the concatenation of all
      // the individual dumps.
      if (TraceType.from(info.getConfiguration()) == TraceType.ATRACE) {
        File trace = FileUtil.createTempFile(String.format("cpu_trace_%d", info.getTraceId()), ".trace", true);
        try (FileOutputStream out = new FileOutputStream(trace)) {
          out.write(traceResponse.getContents().toByteArray());
        }
        AtraceExporter.export(trace, outputStream);
      }
      else {
        FileUtil.copy(new ByteArrayInputStream(traceResponse.getContents().toByteArray()), outputStream);
        if (TraceType.from(info.getConfiguration()) == TraceType.PERFETTO) {
          // TODO (b/184681183): Uncomment this when we know what we want the user experience to be.
          //PerfettoTrace.Trace trace = PerfettoTrace.Trace.newBuilder()
          //  .addPacket(PerfettoTrace.TracePacket.newBuilder()
          //               .setUiState(PerfettoTrace.UiState.newBuilder()
          //               .setHighlightProcess(PerfettoTrace.UiState.HighlightProcess
          //                                      .newBuilder()
          //                                      .setPid(profilers.getSession().getPid())
          //                                      .build())
          //                             .build())
          //               .build())
          //  .build();
          //outputStream.write(trace.toByteArray());
        }
      }
    }
    catch (IOException exception) {
      getLogger().warn("Failed to export CPU trace file:\n" + exception);
    }
  }

  /**
   * Generate a default name for a trace to be exported. The name suggested is based on the current timestamp and the capture type.
   */
  @NotNull
  static String generateCaptureFileName(@NotNull TraceType profilerType) {
    StringBuilder traceName = new StringBuilder(String.format("cpu-%s-", StringUtil.toLowerCase(profilerType.name())));
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    traceName.append(LocalDateTime.now().format(formatter));
    return traceName.toString();
  }

  /**
   * Returns the list of {@link CpuTraceInfo} that intersect with the given range.
   *
   * @param rangeUs note that this is a microsecond range. If the range min/max value is Long.MINVALUE/Long.MAXVALUE
   *                respectively, the MIN/MAX values will be used for the query. Otherwise conversion to nanoseconds result
   *                in overflows.
   */
  @NotNull
  public static List<TraceInfo> getTraceInfoFromRange(@NotNull ProfilerClient client,
                                                         @NotNull Common.Session session,
                                                         @NotNull Range rangeUs) {
    // Converts the range to nanoseconds before calling the service.
    long rangeMinNs = rangeUs.getMin() == Long.MIN_VALUE ? Long.MIN_VALUE : TimeUnit.MICROSECONDS.toNanos((long)rangeUs.getMin());
    long rangeMaxNs = rangeUs.getMax() == Long.MAX_VALUE ? Long.MAX_VALUE : TimeUnit.MICROSECONDS.toNanos((long)rangeUs.getMax());

    Transport.GetEventGroupsResponse response = client.getTransportClient().getEventGroups(
      Transport.GetEventGroupsRequest.newBuilder()
        .setStreamId(session.getStreamId())
        .setPid(session.getPid())
        .setKind(Common.Event.Kind.CPU_TRACE)
        .setFromTimestamp(rangeMinNs)
        .setToTimestamp(rangeMaxNs)
        .build());
    return response.getGroupsList().stream()
      .map(group -> {
        // We only care about the CpuTraceInfo stored in the very last event in the group.
        Common.Event event = group.getEvents(group.getEventsCount() - 1);
        TraceInfo info = event.getTraceData().hasTraceStarted() ?
                            event.getTraceData().getTraceStarted().getTraceInfo() : event.getTraceData().getTraceEnded().getTraceInfo();
        if (info.equals(TraceInfo.getDefaultInstance())) {
          // A default instance means that we have a generically ended group due to device disconnect.
          // In those case, we look for the start event and use its CpuTraceInfo instead.
          assert group.getEventsCount() > 1;
          info = group.getEvents(0).getTraceData().getTraceStarted().getTraceInfo();
          if (info.getToTimestamp() == -1) {
            info = info.toBuilder()
              .setToTimestamp(session.getEndTimestamp())
              .setStopStatus(Trace.TraceStopStatus.newBuilder().setStatus(Trace.TraceStopStatus.Status.APP_PROCESS_DIED))
              .build();
          }
        }
        return info;
      })
      .sorted(Comparator.comparingLong(TraceInfo::getFromTimestamp))
      .collect(Collectors.toList());
  }

  /**
   * Gets the trace info for a given trace id.
   * This function only uses the unified pipeline.
   */
  @NotNull
  public static TraceInfo getTraceInfoFromId(@NotNull StudioProfilers profilers, long traceId) {
    Transport.GetEventGroupsResponse response = profilers.getClient().getTransportClient().getEventGroups(
      Transport.GetEventGroupsRequest.newBuilder()
        .setStreamId(profilers.getSession().getStreamId())
        .setKind(Common.Event.Kind.CPU_TRACE)
        .setGroupId(traceId)
        .build());
    if (response.getGroupsCount() == 0) {
      return TraceInfo.getDefaultInstance();
    }
    Trace.TraceData data = response.getGroups(0).getEvents(response.getGroups(0).getEventsCount() - 1).getTraceData();
    if (data.hasTraceStarted()) {
      return data.getTraceStarted().getTraceInfo();
    }
    return data.getTraceEnded().getTraceInfo();
  }

  /**
   * Returns the list of all {@link CpuTraceInfo} for a given session.
   */
  @NotNull
  public static List<TraceInfo> getTraceInfoFromSession(@NotNull ProfilerClient client,
                                                           @NotNull Common.Session session) {
    return getTraceInfoFromRange(client, session, new Range(Long.MIN_VALUE, Long.MAX_VALUE));
  }

  /**
   * Gets the trace status for a given trace id.
   * This function only uses the unified pipeline.
   */
  @NotNull
  public static Common.Event getTraceStatusEventFromId(@NotNull StudioProfilers profilers, long traceId) {
    Transport.GetEventGroupsResponse response = profilers.getClient().getTransportClient().getEventGroups(
      Transport.GetEventGroupsRequest.newBuilder()
        .setStreamId(profilers.getSession().getStreamId())
        .setKind(Common.Event.Kind.TRACE_STATUS)
        .setGroupId(traceId)
        .build());
    if (response.getGroupsCount() == 0) {
      return Common.Event.getDefaultInstance();
    }
    return response.getGroups(0).getEvents(response.getGroups(0).getEventsCount() - 1);
  }

  /**
   * Executes a start trace command for cpu-based tracing and registers
   * a listener for the expected response event.
   */
  public static void startTracing(@NotNull StudioProfilers profilers,
                                  @NotNull Common.Session session,
                                  @NotNull Trace.TraceConfiguration configuration,
                                  @NotNull Consumer<Trace.TraceStartStatus> statusResponseHandler,
                                  @Nullable Consumer<Trace.TraceInfo> cpuTraceResponseHandler) {
    Executor poolExecutor = profilers.getIdeServices().getPoolExecutor();
    Commands.Command startCommand = Commands.Command.newBuilder()
      .setStreamId(session.getStreamId())
      .setPid(session.getPid())
      .setType(Commands.Command.CommandType.START_TRACE)
      .setStartTrace(Trace.StartTrace.newBuilder()
                       .setProfilerType(Trace.ProfilerType.CPU)
                       .setConfiguration(configuration)
                       .build())
      .build();

    profilers.getClient().executeAsync(startCommand, poolExecutor)
      .thenAcceptAsync(response -> {
        Function<Common.Event, Boolean> traceStatusEventCallback = event -> {
          statusResponseHandler.accept(event.getTraceStatus().getTraceStartStatus());
          // unregisters the listener.
          return true;
        };
        TransportUtils.registerListener(profilers, Common.Event.Kind.TRACE_STATUS,
                                        session.getStreamId(), session.getPid(), response.getCommandId(), traceStatusEventCallback);
        if (cpuTraceResponseHandler != null) {
          Function<Common.Event, Boolean> cpuTraceEventCallback = event -> {
            if (event.getTraceData().hasTraceStarted()) {
              cpuTraceResponseHandler.accept(event.getTraceData().getTraceStarted().getTraceInfo());
            }
            // unregisters the listener.
            return true;
          };
          TransportUtils.registerListener(profilers, Common.Event.Kind.CPU_TRACE,
                                          session.getStreamId(), session.getPid(), response.getCommandId(), cpuTraceEventCallback);
        }
      }, poolExecutor);
  }

  public static void stopTracing(@NotNull StudioProfilers profilers,
                                 @NotNull Common.Session session,
                                 @NotNull Trace.TraceConfiguration configuration,
                                 @Nullable Consumer<Trace.TraceStopStatus> statusResponseHandler,
                                 @Nullable Consumer<Trace.TraceInfo> cpuTraceResponseHandler) {
    Executor poolExecutor = profilers.getIdeServices().getPoolExecutor();
    Commands.Command stopCommand = Commands.Command.newBuilder()
      .setStreamId(session.getStreamId())
      .setPid(session.getPid())
      .setSessionId(session.getSessionId())
      .setType(Commands.Command.CommandType.STOP_TRACE)
      .setStopTrace(Trace.StopTrace.newBuilder()
                      .setProfilerType(Trace.ProfilerType.CPU)
                      .setConfiguration(configuration)
                      .setNeedTraceResponse(statusResponseHandler != null))
      .build();

    profilers.getClient().executeAsync(stopCommand, poolExecutor)
      .thenAcceptAsync(response -> {
        if (statusResponseHandler != null) {
          Function<Common.Event, Boolean> traceStatusEventCallback = event -> {
            statusResponseHandler.accept(event.getTraceStatus().getTraceStopStatus());
            // unregisters the listener.
            return true;
          };
          TransportUtils.registerListener(profilers, Common.Event.Kind.TRACE_STATUS,
                                          session.getStreamId(), session.getPid(), response.getCommandId(), traceStatusEventCallback);
        }
        if (cpuTraceResponseHandler != null) {
          Function<Common.Event, Boolean> cpuTraceEventCallback = event -> {
            if (event.getTraceData().hasTraceEnded()) {
              cpuTraceResponseHandler.accept(event.getTraceData().getTraceEnded().getTraceInfo());
            }
            // unregisters the listener.
            return true;
          };
          TransportUtils.registerListener(profilers, Common.Event.Kind.CPU_TRACE,
                                          session.getStreamId(), session.getPid(), response.getCommandId(), cpuTraceEventCallback);
        }
      }, poolExecutor);
  }
}
