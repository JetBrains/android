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

import com.android.ddmlib.*;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.monitor.ui.cpu.model.TraceDataStore;
import com.android.tools.datastore.profilerclient.DeviceProfilerService;
import com.android.tools.idea.monitor.ui.cpu.model.AppTrace;
import com.android.tools.idea.monitor.ui.cpu.model.TraceArt;
import com.android.tools.idea.monitor.ui.cpu.model.TraceSimplePerf;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class TraceRequestHandler {

  public enum Profiler {ART, SIMPLEPERF};
  public enum Mode {SAMPLING, INSTRUMENTING}

  private static final Logger LOG = Logger.getInstance(TraceRequestHandler.class);

  //  Folder name under which ART traces are stored when downloaded from the device.
  private static final String CAPTURES_DIRECTORY_NAME = "art_trace";

  @Nullable
  private final DeviceProfilerService mySelectedDeviceProfilerService;

  @NotNull
  private final DeviceContext myDeviceContext;

  @NotNull

  private final Project myProject;

  public TraceRequestHandler(
    @NotNull DeviceProfilerService selectedDeviceProfilerService,
    @NotNull DeviceContext deviceContext,
    @NotNull Project project) {
    mySelectedDeviceProfilerService = selectedDeviceProfilerService;
    myDeviceContext = deviceContext;
    myProject = project;
  }

  public void startTracing(Profiler profiler, Mode mode) {
    CpuServiceGrpc.CpuServiceBlockingStub cpuService = mySelectedDeviceProfilerService.getCpuService();
    String appPackageName = myDeviceContext.getSelectedClient().getClientData().getPackageName();

    CpuProfiler.CpuProfilingAppStartRequest.Builder requestBuilder =
      CpuProfiler.CpuProfilingAppStartRequest.newBuilder().setAppPkgName(appPackageName);

    if (profiler == Profiler.ART) {
      requestBuilder.setProfiler(CpuProfiler.CpuProfilingAppStartRequest.Profiler.ART);
    } else {
      requestBuilder.setProfiler(CpuProfiler.CpuProfilingAppStartRequest.Profiler.SIMPLE_PERF);
    }

    if (mode == Mode.SAMPLING) {
      requestBuilder.setMode(CpuProfiler.CpuProfilingAppStartRequest.Mode.SAMPLED);
    } else {
      requestBuilder.setMode(CpuProfiler.CpuProfilingAppStartRequest.Mode.INSTRUMENTED);
    }

    CpuProfiler.CpuProfilingAppStartResponse response = cpuService.startProfilingApp(requestBuilder.build());

    if (!response.getStatus().equals(CpuProfiler.CpuProfilingAppStartResponse.Status.SUCCESS)) {
      LOG.error("Unable to start tracing:" + response.getStatus());
      LOG.error(response.getErrorMessage());
      return;
    }
  }

  public void stopTracing(Profiler profiler) {
    CpuServiceGrpc.CpuServiceBlockingStub cpuService = mySelectedDeviceProfilerService.getCpuService();
    String appPackageName = myDeviceContext.getSelectedClient().getClientData().getPackageName();

    // Stop profiling.
    CpuProfiler.CpuProfilingAppStopRequest.Builder requestBuilder =
      CpuProfiler.CpuProfilingAppStopRequest.newBuilder().setAppPkgName(appPackageName);
    if (profiler == Profiler.ART) {
      requestBuilder.setProfiler(CpuProfiler.CpuProfilingAppStopRequest.Profiler.ART);
    } else {
      requestBuilder.setProfiler(CpuProfiler.CpuProfilingAppStopRequest.Profiler.SIMPLE_PERF);
    }
    CpuProfiler.CpuProfilingAppStopResponse response = cpuService.stopProfilingApp(requestBuilder.build());
    if (!response.getStatus().equals(CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS)) {
      LOG.error("Unable to stop tracing:" + response.getStatus());
      LOG.error(response.getErrorMessage());
      return;
    }

    // Save the trace data into a file, process it and store it in the datastore.
    final File[] dst = {null};
    try {
      ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<Object, IOException>() {
                                                           @Override
                                                           public Object compute() throws IOException {
                                                             dst[0] = createLocalFile(createATraceFileName());
                                                             Files.write(dst[0].toPath(), response.getTrace().toByteArray());
                                                             return null;
                                                           }
                                                         }
      );
      AppTrace trace;
      if (profiler == Profiler.ART) {
        trace = new TraceArt(dst[0]);
      } else {
        trace = new TraceSimplePerf(dst[0]);
      }
      trace.parse();
      TraceDataStore.getInstance().addTrace(myProject.getName(), trace);
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }

  // Create File to store the raw ART trace. This file is automatically deleted when AS stops.
  private File createLocalFile(String captureFileName) throws IOException {
    VirtualFile dir = createCapturesDirectory();
    File captureFile = new File(dir.createChildData(null, captureFileName).getPath());
    captureFile.deleteOnExit();
    return captureFile;
  }

  // Create capture directory where ART trace are downloaded from the device. This directory is not
  // deleted when AS stops.
  private VirtualFile createCapturesDirectory() throws IOException {
    assert myProject.getBasePath() != null;
    VirtualFile projectDir = LocalFileSystem.getInstance().findFileByPath(myProject.getBasePath());
    if (projectDir != null) {
      VirtualFile dir = projectDir.findChild(CAPTURES_DIRECTORY_NAME);
      if (dir == null) {
        dir = projectDir.createChildDirectory(null, CAPTURES_DIRECTORY_NAME);
      }
      return dir;
    }
    else {
      throw new IOException("Unable to create the captures directory: Project directory not found.");
    }
  }

  // Create a name for new CPU trace files that's unique during the lifetime of current process.
  private String createATraceFileName() {
    return String.format("t%d_%d.cpu_trace", java.lang.Thread.currentThread().getId(),
                         java.lang.System.nanoTime());
  }
}
