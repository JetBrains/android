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
import com.android.tools.perflib.vmtrace.ThreadInfo;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profilers.AspectModel;
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioProfilers;
import com.intellij.openapi.diagnostic.Logger;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class CpuProfilerStage extends Stage {

  private static final Logger LOG = Logger.getInstance(CpuProfilerStage.class);

  private  AspectModel<CpuProfilerAspect> myAspect = new AspectModel<>();

  @Nullable
  private HNode<MethodModel> myCapture;

  private Map<ThreadInfo, HNode<MethodModel>> myCaptureTrees;

  private CpuCaptureState myCaptureState = CpuCaptureState.NONE;

  /**
   * Id of the current selected thread.
   * If this variable has a valid thread id, {@link #myCapture} should store the value of the {@link HNode} correspondent to the thread.
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
    myCaptureState = CpuCaptureState.CAPTURING;
    myAspect.changed(CpuProfilerAspect.CAPTURED_TREE);
    startTracing();
  }

  public void startTracing() {
    CpuServiceGrpc.CpuServiceBlockingStub cpuService = getStudioProfilers().getClient().getCpuClient();
    // TODO: handle the [UNKNOWN] case. Also, we're using getDescription() from ddmlib.ClientData, instead of getPackageName().
    // Check if that's what we really want as the name might have another format ("$applicationId:$processName") for multi-process app
    String appName = getStudioProfilers().getProcess().getName();

    CpuProfiler.CpuProfilingAppStartRequest.Builder request = CpuProfiler.CpuProfilingAppStartRequest.newBuilder();

    request.setAppPkgName(appName);

    // TODO: support simpleperf
    request.setProfiler(CpuProfiler.CpuProfilingAppStartRequest.Profiler.ART);
    // TODO: support instrumented mode
    request.setMode(CpuProfiler.CpuProfilingAppStartRequest.Mode.SAMPLED);

    CpuProfiler.CpuProfilingAppStartResponse response = cpuService.startProfilingApp(request.build());

    if (!response.getStatus().equals(CpuProfiler.CpuProfilingAppStartResponse.Status.SUCCESS)) {
      LOG.error("Unable to start tracing:" + response.getStatus());
      LOG.error(response.getErrorMessage());
    }
  }

  public void stopCapturing() {
    try {
    stopTracing();
    } catch (StatusRuntimeException e) {
      LOG.error(e);
    }
    myCaptureState = CpuCaptureState.CAPTURED;
    myAspect.changed(CpuProfilerAspect.CAPTURED_TREE);
  }

  private void stopTracing() {
    CpuServiceGrpc.CpuServiceBlockingStub cpuService = getStudioProfilers().getClient().getCpuClient();
    // TODO: handle the [UNKNOWN] case. Also, we're using getDescription() from ddmlib.ClientData, instead of getPackageName().
    // Check if that's what we really want as the name might have another format ("$applicationId:$processName") for multi-process app
    String appName = getStudioProfilers().getProcess().getName();

    // Stop profiling.
    CpuProfiler.CpuProfilingAppStopRequest.Builder request = CpuProfiler.CpuProfilingAppStopRequest.newBuilder().setAppPkgName(appName);

    // TODO: support simpleperf
    request.setProfiler(CpuProfiler.CpuProfilingAppStopRequest.Profiler.ART);

    CpuProfiler.CpuProfilingAppStopResponse response = cpuService.stopProfilingApp(request.build());
    if (!response.getStatus().equals(CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS)) {
      LOG.error("Unable to stop tracing:" + response.getStatus());
      LOG.error(response.getErrorMessage());
      return;
    }

    CpuTraceArt traceArt = new CpuTraceArt();
    traceArt.trace(response.getTrace().toByteArray());
    myCaptureTrees = traceArt.getThreadsGraph();
    // Select thread with the most information.
    myCapture = null;
    for (Map.Entry<ThreadInfo, HNode<MethodModel>> captureEntry : myCaptureTrees.entrySet()) {
      if (myCapture == null || myCapture.duration() < captureEntry.getValue().duration()) {
        myCapture = captureEntry.getValue();
        mySelectedThread = captureEntry.getKey().getId();
      }
    }
    // Fire the selected threads aspect to update the UI accordingly
    myAspect.changed(CpuProfilerAspect.SELECTED_THREADS);
    assert myCapture != null;

    ProfilerTimeline timeline = getStudioProfilers().getTimeline();
    timeline.setStreaming(false);
    // TODO: Investigate why the captured ranges seem to be tiny, cannot really change the view to that.
    // timeline.getViewRange().set(myCapture.getStart(), myCapture.getEnd());
    timeline.getSelectionRange().set(myCapture.getStart(), myCapture.getEnd());
  }

  @Nullable
  public HNode<MethodModel> getCaptureTree() {
    return myCapture;
  }

  public CpuCaptureState getCaptureState() {
    return myCaptureState;
  }

  public void setSelectedThread(int threadId) {
    if (myCaptureState != CpuCaptureState.CAPTURED) {
      // If the capture tree should not be displayed yet, return early.
      return;
    }
    mySelectedThread = threadId;
    myCapture = null;
    for (Map.Entry<ThreadInfo, HNode<MethodModel>> entry : myCaptureTrees.entrySet()) {
      if (entry.getKey().getId() == threadId) {
        myCapture = entry.getValue();
      }
    }
    myAspect.changed(CpuProfilerAspect.SELECTED_THREADS);
  }

  public int getSelectedThread() {
    return mySelectedThread;
  }
}
