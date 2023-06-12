/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.heap;

import static com.android.tools.idea.diagnostics.heap.HeapSnapshotTraverse.HeapSnapshotPresentationConfig.SizePresentationStyle.BYTES;
import static com.android.tools.idea.diagnostics.heap.HeapSnapshotTraverse.HeapSnapshotPresentationConfig.SizePresentationStyle.OPTIMAL_UNITS;
import static com.android.tools.idea.util.StudioPathManager.isRunningFromSources;
import static com.google.wireless.android.sdk.stats.MemoryUsageReportEvent.MemoryUsageCollectionMetadata.StatusCode;

import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.util.StudioPathManager;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.MemoryUsageReportEvent;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.Alarm;
import com.intellij.util.system.CpuArch;
import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class HeapSnapshotTraverseService {

  private static final long REPORT_COLLECTION_DELAY_MILLISECONDS = Duration.ofMinutes(30).toMillis();
  // This is the name of the flag that is used for local E2E integration test runs and is used for enabling extended reports collection and
  // logging.
  private static final String COLLECT_AND_LOG_EXTENDED_MEMORY_REPORTS = "studio.collect.extended.memory.reports";
  private static final String DIAGNOSTICS_HEAP_NATIVE_PATH =
    "tools/adt/idea/android/src/com/android/tools/idea/diagnostics/heap/native";

  private static final String MEMORY_USAGE_REPORT_FAILURE_MESSAGE_PREFIX = "Memory usage report collection failed: ";
  private static final String JNI_OBJECT_TAGGER_LIB_NAME = "jni_object_tagger";
  private static final String RESOURCES_NATIVE_PATH = "plugins/android/resources/native";
  private static final Logger LOG = Logger.getInstance(HeapSnapshotTraverseService.class);

  @NotNull
  private final Alarm alarm;
  private boolean triedToLoadAgent = false;
  private boolean agentSuccessfullyLoaded = false;

  HeapSnapshotTraverseService() {
    alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, ApplicationManager.getApplication());
  }

  public void registerIntegrationTestCollectMemoryUsageStatisticsAction() {
    ActionManager.getInstance()
      .registerAction("IntegrationTestCollectMemoryUsageStatisticsAction", new IntegrationTestCollectMemoryUsageStatisticsAction());
  }

  @NotNull
  public static HeapSnapshotTraverseService getInstance() {
    return ApplicationManager.getApplication().getService(HeapSnapshotTraverseService.class);
  }

  void loadObjectTaggingAgent() {
    if (triedToLoadAgent) {
      return;
    }

    triedToLoadAgent = true;

    String vmName = ManagementFactory.getRuntimeMXBean().getName();
    String pid = vmName.substring(0, vmName.indexOf('@'));
    VirtualMachine vm = null;
    try {
      vm = VirtualMachine.attach(pid);
      Path libLocationPath = getLibLocation();
      if (libLocationPath == null) {
        sendMemoryCollectionFailureReport(StatusCode.AGENT_LOAD_FAILED);
        return;
      }
      vm.loadAgentPath(libLocationPath.toString());
      agentSuccessfullyLoaded = true;
    }
    catch (AttachNotSupportedException | AgentInitializationException | AgentLoadException |
           IOException e) {
      sendMemoryCollectionFailureReport(StatusCode.AGENT_LOAD_FAILED);
    }
    finally {
      if (vm != null) {
        try {
          vm.detach();
        }
        catch (IOException e) {
          LOG.warn("Failed to detach the VM after loading the object tagging agent", e);
        }
      }
    }
  }

  public void collectAndLogMemoryReport() {
    loadObjectTaggingAgent();
    if (!agentSuccessfullyLoaded) {
      return;
    }
    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(new HeapTraverseConfig(ComponentsSet.buildComponentSetForIntegrationTesting(),
      /*collectHistograms=*/true, /*collectDisposerTreeInfo=*/true));
    HeapSnapshotTraverse.collectAndWriteStats(LOG::info, stats,
                                              new HeapSnapshotTraverse.HeapSnapshotPresentationConfig(
                                                OPTIMAL_UNITS,
                                                /*shouldLogSharedClusters=*/true,
                                                /*shouldLogRetainedSizes=*/false));
  }

  /**
   * This method collects memory usage report and dumps it to memory_usage_report.log file. This method is used by the end2end integration
   * testing for collecting components/categories owned sizes that will be reported to perfgate afterwards.
   */
  public void collectMemoryReportAndDumpToMetricsFile() {
    try (Writer writer = new BufferedWriter(
      new FileWriter(new File(PathManager.getLogPath(), "memory_usage_report.log"), StandardCharsets.UTF_8))) {
      loadObjectTaggingAgent();
      if (!agentSuccessfullyLoaded) {
        // integration test is configured to fail in case of a line with this prefix in a log file.
        writer.append(MEMORY_USAGE_REPORT_FAILURE_MESSAGE_PREFIX).append("Failed to load agent").append("\n");
        return;
      }

      HeapSnapshotStatistics statistics = Boolean.getBoolean(COLLECT_AND_LOG_EXTENDED_MEMORY_REPORTS)
                                          ? new HeapSnapshotStatistics(
        new HeapTraverseConfig(ComponentsSet.buildComponentSetForIntegrationTesting(), /*collectHistograms=*/
                               true, /*collectDisposerTreeInfo=*/true))
                                          : new HeapSnapshotStatistics(ComponentsSet.buildComponentSetForIntegrationTesting());

      HeapSnapshotTraverse.collectAndWriteStats(
        (String s) -> {
          try {
            writer.append(s).append("\n");
          }
          catch (IOException e) {
            LOG.warn(String.format("%s Failed to write to the memory report file", MEMORY_USAGE_REPORT_FAILURE_MESSAGE_PREFIX), e);
          }
        }, statistics,
        new HeapSnapshotTraverse.HeapSnapshotPresentationConfig(
          BYTES,
          /*shouldLogSharedClusters=*/false,
          /*shouldLogRetainedSizes=*/false));
    }
    catch (IOException e) {
      LOG.error("Failed to write to the memory report file", e);
    }
  }

  private void lowerThreadPriorityAndCollectMemoryReport() {
    if (PowerSaveMode.isEnabled()) {
      sendMemoryCollectionFailureReport(StatusCode.POWER_SAVING_MODE_ENABLED);
      return;
    }
    Thread currentThread = Thread.currentThread();
    int oldThreadPriority = currentThread.getPriority();

    try {
      currentThread.setPriority(Thread.MIN_PRIORITY);
      loadObjectTaggingAgent();

      if (!agentSuccessfullyLoaded) {
        return;
      }

      HeapSnapshotStatistics stats = new HeapSnapshotStatistics(ComponentsSet.buildComponentSet());
      StatusCode statusCode = HeapSnapshotTraverse.collectMemoryReport(stats, HeapSnapshotTraverse.getLoadedClassesComputable);
      if (statusCode == StatusCode.NO_ERROR) {
        addMemoryReportCollectionRequest();
      }
    }
    finally {
      currentThread.setPriority(oldThreadPriority);
    }
  }

  public void addMemoryReportCollectionRequest() {
    alarm.addRequest(this::lowerThreadPriorityAndCollectMemoryReport,
                     REPORT_COLLECTION_DELAY_MILLISECONDS);
  }

  private static @NotNull String getLibName() {
    return System.mapLibraryName(JNI_OBJECT_TAGGER_LIB_NAME);
  }

  private static @NotNull String getPlatformName() {
    if (SystemInfo.isWindows) {
      return "win";
    }
    if (SystemInfo.isMac) {
      return CpuArch.isArm64() ? "mac_arm" : "mac";
    }
    if (SystemInfo.isLinux) {
      return "linux";
    }
    return "";
  }

  @Nullable
  private static Path getLibLocation() {
    String libName = getLibName();
    Path homePath = Paths.get(PathManager.getHomePath());
    // Installed Studio.
    Path libFile = homePath.resolve(RESOURCES_NATIVE_PATH).resolve(libName);
    if (Files.exists(libFile)) {
      return libFile;
    }

    if (isRunningFromSources()) {
      // Dev environment.
      libFile = StudioPathManager.resolvePathFromSourcesRoot(DIAGNOSTICS_HEAP_NATIVE_PATH)
        .resolve(getPlatformName()).resolve(libName);
      if (Files.exists(libFile)) {
        return libFile;
      }
    }
    return null;
  }

  private static void sendMemoryCollectionFailureReport(StatusCode statusCode) {
    UsageTracker.log(AndroidStudioEvent.newBuilder()
                       .setKind(AndroidStudioEvent.EventKind.MEMORY_USAGE_REPORT_EVENT)
                       .setMemoryUsageReportEvent(MemoryUsageReportEvent.newBuilder().setMetadata(
                         MemoryUsageReportEvent.MemoryUsageCollectionMetadata.newBuilder()
                           .setStatusCode(statusCode))));
  }
}
