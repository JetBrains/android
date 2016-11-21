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

import com.android.tools.adtui.model.HNode;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profilers.AspectModel;
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioProfilers;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CpuProfilerStage extends Stage {

  private static final Logger LOG = Logger.getInstance(CpuProfilerStage.class);

  private AspectModel<CpuProfilerAspect> myAspect = new AspectModel<>();

  @Nullable
  private CpuCapture myCapture;

  /**
   * Whether there is a capture in progress.
   * TODO: Timeouts
   */
  private boolean myCapturing;

  /**
   * Id of the current selected thread.
   * If this variable has a valid thread id, {@link #myCaptureNode} should store the value of the {@link HNode} correspondent to the thread.
   */
  private int mySelectedThread;

  public CpuProfilerStage(@NotNull StudioProfilers profiler) {
    super(profiler);
  }

  @Override
  public void enter() {
  }

  @Override
  public void exit() {
  }

  public AspectModel<CpuProfilerAspect> getAspect() {
    return myAspect;
  }

  public void startCapturing() {
    CpuServiceGrpc.CpuServiceBlockingStub cpuService = getStudioProfilers().getClient().getCpuClient();

    CpuProfiler.CpuProfilingAppStartRequest request = CpuProfiler.CpuProfilingAppStartRequest.newBuilder()
      .setAppPkgName(getStudioProfilers().getProcess().getName()) // TODO: Investigate if this is the right way of choosing the app
      .setProfiler(CpuProfiler.CpuProfilingAppStartRequest.Profiler.ART) // TODO: support simpleperf
      .setMode(CpuProfiler.CpuProfilingAppStartRequest.Mode.SAMPLED) // TODO: support instrumented mode
      .build();

    CpuProfiler.CpuProfilingAppStartResponse response = cpuService.startProfilingApp(request);

    if (!response.getStatus().equals(CpuProfiler.CpuProfilingAppStartResponse.Status.SUCCESS)) {
      LOG.error("Unable to start tracing:" + response.getStatus());
      LOG.error(response.getErrorMessage());
      myCapturing = false;
    }
    else {
      myCapturing = true;
    }
    myAspect.changed(CpuProfilerAspect.CAPTURE);
  }

  public void stopCapturing() {
    CpuServiceGrpc.CpuServiceBlockingStub cpuService = getStudioProfilers().getClient().getCpuClient();

    CpuProfiler.CpuProfilingAppStopRequest request = CpuProfiler.CpuProfilingAppStopRequest.newBuilder()
      .setAppPkgName(getStudioProfilers().getProcess().getName()) // TODO: Investigate if this is the right way of choosing the app
      .setProfiler(CpuProfiler.CpuProfilingAppStopRequest.Profiler.ART) // TODO: support simpleperf
      .build();

    CpuProfiler.CpuProfilingAppStopResponse response = cpuService.stopProfilingApp(request);
    if (!response.getStatus().equals(CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS)) {
      LOG.error("Unable to stop tracing:" + response.getStatus());
      LOG.error(response.getErrorMessage());
      myCapture = null;
    }
    else {
      myCapture = new CpuCapture(response);
    }
    myAspect.changed(CpuProfilerAspect.CAPTURE);

    if (myCapture != null) {

      mySelectedThread = myCapture.getMainThreadId();
      myAspect.changed(CpuProfilerAspect.SELECTED_THREADS);

      ProfilerTimeline timeline = getStudioProfilers().getTimeline();
      timeline.setStreaming(false);
      timeline.getSelectionRange().set(myCapture.getRange());
    }
    myCapturing = false;
  }

  public void setSelectedThread(int id) {
    if (myCapture != null) {
      myCapture.setSelectedThread(id);
    }
    mySelectedThread = id;
    myAspect.changed(CpuProfilerAspect.SELECTED_THREADS);
  }

  public int getSelectedThread() {
    return mySelectedThread;
  }

  /**
   * The current capture of the cpu profiler, if null there is no capture to display otherwise we need to be in
   * a capture viewing mode.
   */
  @Nullable
  public CpuCapture getCapture() {
    return myCapture;
  }

  public boolean isCapturing() {
    return myCapturing;
  }
}
