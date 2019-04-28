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

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Cpu.CpuTraceInfo;
import com.android.tools.profiler.proto.Cpu.CpuTraceType;
import com.android.tools.profiler.proto.CpuProfiler.CpuStartRequest;
import com.android.tools.profiler.proto.CpuProfiler.CpuStopRequest;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.StudioProfiler;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.cpu.atrace.AtraceExporter;
import com.android.tools.profilers.sessions.SessionsManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CpuProfiler extends StudioProfiler {

  /**
   * Maps a {@link Common.Session} ID to the trace {@link File} (to be) imported into it as a {@link CpuCapture}.
   */
  @NotNull
  private final Map<Long, File> mySessionTraceFiles;

  public CpuProfiler(@NotNull StudioProfilers profilers) {
    super(profilers);
    mySessionTraceFiles = new HashMap<>();
    if (profilers.getIdeServices().getFeatureConfig().isImportCpuTraceEnabled()) {
      // Only enable handling *.trace files if the import CPU traces flag is enabled.
      registerImportedSessionListener();
      registerTraceImportHandler();
    }
  }

  private static Logger getLogger() {
    return Logger.getInstance(CpuProfiler.class);
  }

  /**
   * Registers a listener that will open a {@link CpuProfilerStage} in import trace mode when the {@link Common.Session} selected is a
   * {@link Common.SessionMetaData.SessionType#CPU_CAPTURE}.
   */
  private void registerImportedSessionListener() {
    myProfilers.registerSessionChangeListener(Common.SessionMetaData.SessionType.CPU_CAPTURE, () -> {
      // Open a CpuProfilerStage in import mode when selecting an imported session.
      long sessionId = myProfilers.getSession().getSessionId();
      assert mySessionTraceFiles.containsKey(sessionId);
      CpuProfilerStage stage = new CpuProfilerStage(myProfilers, mySessionTraceFiles.get(sessionId));
      myProfilers.setStage(stage);
    });
  }

  /**
   * Registers a handler for importing *.trace files. It will create a {@link Common.SessionMetaData.SessionType#CPU_CAPTURE}
   * {@link Common.Session} and select it.
   */
  private void registerTraceImportHandler() {
    SessionsManager sessionsManager = myProfilers.getSessionsManager();
    sessionsManager.registerImportHandler("trace", file -> {
      long startTimestampEpochMs = System.currentTimeMillis();
      long startTimestampNs = getImportedSessionStartTimestampNs(file, startTimestampEpochMs);
      // The end timestamp is going to be updated once the capture is parsed. When starting the session (before parsing a trace), set it to
      // be one minute from the begin time, as it is a reasonable length for a "default" timeline that can be displayed if parsing fails
      // and before the parsing happens.
      long endTimestampNs = startTimestampNs + TimeUnit.MINUTES.toNanos(1);

      Common.Session importedSession = sessionsManager.createImportedSession(file.getName(),
                                                                             Common.SessionMetaData.SessionType.CPU_CAPTURE,
                                                                             startTimestampNs,
                                                                             endTimestampNs,
                                                                             startTimestampEpochMs);
      // Associate the trace file with the session so we can retrieve it later.
      mySessionTraceFiles.put(importedSession.getSessionId(), file);
      // Select the imported session
      sessionsManager.update();
      // TODO(b/76206865): add usage tracking for creating sessions by importing CPU trace files.
      sessionsManager.setSession(importedSession);

      // Make sure the timeline is paused when the stage is opened for the first time, and its bounds are
      // [sessionStartTimestamp, sessionEndTimestamp].
      ProfilerTimeline timeline = myProfilers.getTimeline();
      timeline.reset(myProfilers.getSession().getStartTimestamp(), myProfilers.getSession().getEndTimestamp());
      timeline.setIsPaused(true);

      myProfilers.getIdeServices().getFeatureTracker().trackCreateSession(Common.SessionMetaData.SessionType.CPU_CAPTURE,
                                                                          SessionsManager.SessionCreationSource.MANUAL);
    });
  }

  /**
   * Returns the start timestamp, in nanoseconds, of the imported trace session. First, we try to get the trace file creation time.
   * If there is an error to obtain it, we fallback to the session start time.
   */
  private static long getImportedSessionStartTimestampNs(File trace, long sessionStartTimestampEpochMs) {
    Path tracePath = Paths.get(trace.getPath());
    try {
      BasicFileAttributes attributes = Files.readAttributes(tracePath, BasicFileAttributes.class);
      return attributes.creationTime().to(TimeUnit.NANOSECONDS);
    }
    catch (IOException e) {
      getLogger().warn("Trace file creation time could not be read. Falling back to session start time.");
    }

    // If the file creation time can not be obtained, use the session epoch start time as a fallback
    return TimeUnit.MICROSECONDS.toNanos(sessionStartTimestampEpochMs);
  }

  @Nullable
  public File getTraceFile(Common.Session session) {
    return mySessionTraceFiles.get(session.getSessionId());
  }

  @Override
  public ProfilerMonitor newMonitor() {
    return new CpuMonitor(myProfilers);
  }

  @Override
  public void startProfiling(Common.Session session) {
    // TODO: handle different status of the response
    myProfilers.getClient().getCpuClient().startMonitoringApp(CpuStartRequest.newBuilder().setSession(session).build());
  }

  @Override
  public void stopProfiling(Common.Session session) {
    // TODO: handle different status of the response
    myProfilers.getClient().getCpuClient().stopMonitoringApp(CpuStopRequest.newBuilder().setSession(session).build());
  }

  /**
   * Copies the content of the trace file corresponding to a {@link TraceInfo} to a given {@link FileOutputStream}.
   */
  static void saveCaptureToFile(@NotNull CpuTraceInfo info, @NotNull OutputStream outputStream) {
    // Copy temp trace file to the output stream.
    try {
      // Atrace Format = [HEADER|ZlibData][HEADER|ZlibData]
      // Systrace Expected format = [HEADER|ZlipData]
      // As such exporting the file raw Systrace will only read the first header/data chunk.
      // Atrace captures come over as several parts combined into one file. As such we need an exporter
      // to handle converting the format to a format that Systrace can support. The reason for the multi-part file
      // is because Atrace dumps a compressed data file every X interval and this file represents the concatenation of all
      // the individual dumps.
      if (info.getTraceType() == CpuTraceType.ATRACE) {
        AtraceExporter.export(new File(info.getTraceFilePath()), outputStream);
      }
      else {
        FileUtil.copy(new FileInputStream(info.getTraceFilePath()), outputStream);
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
  static String generateCaptureFileName(@NotNull CpuTraceType profilerType) {
    StringBuilder traceName = new StringBuilder(String.format("cpu-%s-", StringUtil.toLowerCase(profilerType.name())));
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    traceName.append(LocalDateTime.now().format(formatter));
    return traceName.toString();
  }
}
