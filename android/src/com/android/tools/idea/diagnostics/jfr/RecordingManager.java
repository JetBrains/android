/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.jfr;

import com.android.tools.idea.diagnostics.jfr.reports.JfrFreezeReports;
import com.android.tools.idea.diagnostics.jfr.reports.JfrTypingLatencyReports;
import com.android.tools.idea.diagnostics.report.JfrBasedReport;
import com.android.tools.idea.diagnostics.report.DiagnosticReport;
import com.android.tools.idea.diagnostics.report.DiagnosticReportProperties;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.serverflags.ServerFlagService;
import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.messages.MessageBusConnection;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import jdk.jfr.Event;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.jetbrains.annotations.NotNull;

public class RecordingManager {

  private static final Logger LOG = Logger.getInstance(RecordingManager.class);

  public static final int JFR_RECORDING_DURATION_SECONDS = 30;
  private static final int MAX_CAPTURE_DURATION_SECONDS = 300;
  private static final String JFR_SERVER_FLAG_NAME = "diagnostics/jfr";

  private static final List<JfrReportGenerator.Capture> pendingCaptures = new ArrayList<>();
  private static Instant previousRecordingEnd = Instant.MIN;

  private static final RecordingBuffer recordings = new RecordingBuffer();
  private static final Object jfrLock = new Object();
  private static LowMemoryWatcher lowMemoryWatcher;
  private static Consumer<DiagnosticReport> reportCallback;

  public static void init(Consumer<DiagnosticReport> callback) {
    ServerFlagService serverFlagService = ServerFlagService.Companion.getInstance();
    // TODO(b/257594096): disabled on Mac due to crashes in the JVM during sampling
    if (!SystemInfo.isMac && serverFlagService.getBoolean(JFR_SERVER_FLAG_NAME, false)) {
      reportCallback = callback;
      setupActionEvents();
      setupLowMemoryEvents();

      JobScheduler.getScheduler().scheduleWithFixedDelay(new Runnable() {
        @Override
        public void run() {
          Instant recordingEnd = Instant.now();
          synchronized (jfrLock) {
            try {
              Recording rec = recordings.swapBuffers();
              if (rec != null) {
                boolean hasActiveCaptures = false;
                for (JfrReportGenerator.Capture c : pendingCaptures) {
                  // don't need to check if the capture's end is before the start of the previous recording,
                  // since it would have been deleted by the previous call to purgeCompletedCaptures.
                  if (c.getStart().isBefore(previousRecordingEnd)) {
                    hasActiveCaptures = true;
                    break;
                  }
                }
                if (hasActiveCaptures) {
                  Path recPath = new File(FileUtil.getTempDirectory(), "recording.jfr").toPath();
                  rec.dump(recPath);
                  rec.close();
                  readAndDispatchRecordingEvents(recPath);
                  Files.deleteIfExists(recPath);
                }
              }
            } catch (IOException e) {
              LOG.warn(e);
            }
            purgeCompletedCaptures();
            previousRecordingEnd = recordingEnd;
          }
        }
      }, 0, JFR_RECORDING_DURATION_SECONDS, TimeUnit.SECONDS);
      createReportManagers(serverFlagService);
    }
  }

  private static void createReportManagers(ServerFlagService serverFlagService) {
    JfrFreezeReports.Companion.createFreezeReportManager();

    if (StudioFlags.JFR_TYPING_LATENCY_ENABLED.get()) {
      JfrTypingLatencyReports.Companion.createReportManager(serverFlagService);
    }
  }

  static void startCapture(JfrReportGenerator.Capture capture) {
    synchronized (jfrLock) {
      pendingCaptures.add(capture);
    }
  }

  private static void readAndDispatchRecordingEvents(Path recPath) throws IOException {
    try (RecordingFile recordingFile = new RecordingFile(recPath)) {
      while (recordingFile.hasMoreEvents()) {
        RecordedEvent e = recordingFile.readEvent();
        for (JfrReportGenerator.Capture capture : pendingCaptures) {
          if (capture.containsInstant(e.getStartTime()) && capture.getGenerator().getEventFilter().accept(e)) {
            capture.getGenerator().accept(e, capture);
          }
        }
      }
    }
  }

  private static void purgeCompletedCaptures() {
    for (int i = pendingCaptures.size() - 1; i >= 0; i--) {
      JfrReportGenerator.Capture capture = pendingCaptures.get(i);
      if (capture.getEnd() != null && capture.getEnd().isBefore(previousRecordingEnd)) {
        JfrReportGenerator generator = capture.getGenerator();
        generator.captureCompleted(capture);
        if (generator.isFinished()) {
          generateReport(generator);
        }
        pendingCaptures.remove(i);
      }
    }
  }

  private static void generateReport(JfrReportGenerator gen) {
    try {
      Map<String, String> report = gen.generateReport();
      if (!report.isEmpty()) {
        reportCallback.accept(new JfrBasedReport(gen.getReportType(), gen.generateReport(), new DiagnosticReportProperties()));
      }
    } catch (Exception e) {
      LOG.warn(e);
    }
  }

  private static void setupActionEvents() {
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    IdentityHashMap<AnActionEvent, Event> jfrEventMap = new IdentityHashMap<>();
    connection.subscribe(AnActionListener.TOPIC, new AnActionListener() {
      @Override
      public void beforeActionPerformed(@NotNull AnAction action,
                                        @NotNull DataContext dataContext,
                                        @NotNull AnActionEvent event) {
        Action a = new Action();
        a.actionId = ActionManager.getInstance().getId(action);
        a.begin();
        jfrEventMap.put(event, a);
      }

      @Override
      public void afterActionPerformed(@NotNull AnAction action,
                                       @NotNull DataContext dataContext,
                                       @NotNull AnActionEvent event) {
        Action a = (Action) jfrEventMap.get(event);
        if (a != null) {
          jfrEventMap.remove(event);
          a.commit();
        }
      }
    });
  }

  private static void setupLowMemoryEvents() {
    lowMemoryWatcher = LowMemoryWatcher.register(() -> {
      new LowMemory().commit();
    });
  }

  public static Path dumpJfrTo(Path directory) {
    synchronized (jfrLock) {
      return recordings.dumpJfrTo(directory);
    }
  }

  public static class DumpJfrAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      dumpJfrTo(new File(PathManager.getLogPath()).toPath());
    }
  }
}
