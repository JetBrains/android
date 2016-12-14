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

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.DataSeries;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsResponse;
import com.android.tools.profiler.proto.MemoryServiceGrpc.MemoryServiceBlockingStub;
import com.android.tools.profilers.ProfilerMode;
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.memory.adapters.*;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class MemoryProfilerStage extends Stage {
  private final int myProcessId;

  @NotNull
  private AspectModel<MemoryProfilerAspect> myAspect = new AspectModel<>();

  @NotNull
  private final MemoryServiceBlockingStub myClient;

  @NotNull
  private final HeapDumpSampleDataSeries myHeapDumpSampleDataSeries;

  @NotNull
  private final AllocationInfosDataSeries myAllocationInfosDataSeries;

  @NotNull
  private final CaptureObjectLoader myLoader;

  @NotNull
  private MemoryProfilerSelection mySelection;

  private boolean myAllocationStatus;

  public MemoryProfilerStage(@NotNull StudioProfilers profilers) {
    this(profilers, new CaptureObjectLoader());
  }

  @VisibleForTesting
  MemoryProfilerStage(@NotNull StudioProfilers profilers, @NotNull CaptureObjectLoader loader) {
    super(profilers);
    myProcessId = profilers.getProcessId();
    myClient = profilers.getClient().getMemoryClient();
    myHeapDumpSampleDataSeries = new HeapDumpSampleDataSeries(profilers.getClient().getMemoryClient(), profilers.getProcessId());
    myAllocationInfosDataSeries = new AllocationInfosDataSeries(profilers.getClient().getMemoryClient(), profilers.getProcessId());
    myLoader = loader;
    mySelection = new MemoryProfilerSelection(this);
    myAllocationStatus = false;
  }

  @Override
  public void enter() {
    super.enter();

    myLoader.start();
  }

  @Override
  public void exit() {
    super.exit();

    selectCapture(null, null);
    myLoader.stop();
  }

  @Override
  public ProfilerMode getProfilerMode() {
    return mySelection.getCaptureObject() == null ? ProfilerMode.NORMAL : ProfilerMode.EXPANDED;
  }

  @NotNull
  public AspectModel<MemoryProfilerAspect> getAspect() {
    return myAspect;
  }

  public void requestHeapDump() {
    myClient.triggerHeapDump(MemoryProfiler.TriggerHeapDumpRequest.newBuilder().setAppId(myProcessId).build());
  }

  public DataSeries<CaptureDurationData<HeapDumpCaptureObject>> getHeapDumpSampleDurations() {
    return myHeapDumpSampleDataSeries;
  }

  /**
   * @return the actual status, which may be different from the input
   */
  public void trackAllocations(boolean enabled) {
    TrackAllocationsResponse response = myClient.trackAllocations(
      MemoryProfiler.TrackAllocationsRequest.newBuilder().setAppId(myProcessId).setEnabled(enabled).build());
    myAllocationStatus = enabled && (
      response.getStatus() == TrackAllocationsResponse.Status.SUCCESS ||
      response.getStatus() == TrackAllocationsResponse.Status.IN_PROGRESS);
    myAspect.changed(MemoryProfilerAspect.LEGACY_ALLOCATION);
  }

  public boolean isTrackingAllocations() {
    return myAllocationStatus;
  }

  @NotNull
  public DataSeries<CaptureDurationData<AllocationsCaptureObject>> getAllocationInfosDurations() {
    return myAllocationInfosDataSeries;
  }

  public void selectInstance(@Nullable InstanceObject instanceObject) {
    mySelection.setInstanceObject(instanceObject);
  }

  @Nullable
  public InstanceObject getSelectedInstance() {
    return mySelection.getInstanceObject();
  }

  public void selectClass(@Nullable ClassObject classObject) {
    mySelection.setClassObject(classObject);
  }

  @Nullable
  public ClassObject getSelectedClass() {
    return mySelection.getClassObject();
  }

  public void selectHeap(@Nullable HeapObject heapObject) {
    mySelection.setHeapObject(heapObject);
  }

  @Nullable
  public HeapObject getSelectedHeap() {
    return mySelection.getHeapObject();
  }

  public void selectCapture(@Nullable CaptureObject captureObject, @Nullable Executor joiner) {
    if (!mySelection.setCaptureObject(captureObject)) {
      return;
    }

    getStudioProfilers().modeChanged();

    ProfilerTimeline timeline = getStudioProfilers().getTimeline();
    if (captureObject != null) {
      timeline.getSelectionRange().set(TimeUnit.NANOSECONDS.toMicros(captureObject.getStartTimeNs()),
                                       TimeUnit.NANOSECONDS.toMicros(captureObject.getEndTimeNs()));
      ListenableFuture<CaptureObject> future = myLoader.loadCapture(captureObject);
      future.addListener(() -> {
                           try {
                             CaptureObject loadedCaptureObject = future.get();
                             if (loadedCaptureObject != null && loadedCaptureObject == mySelection.getCaptureObject()) {
                               myAspect.changed(MemoryProfilerAspect.CURRENT_LOADED_CAPTURE);
                             }
                           }
                           catch (InterruptedException ignored) {
                             Thread.currentThread().interrupt();
                           }
                           catch (ExecutionException | CancellationException ignored) {
                           }
                         },
                         joiner == null ? MoreExecutors.directExecutor() : joiner);
    }
    else {
      timeline.getSelectionRange().clear();
    }
  }

  @Nullable
  public CaptureObject getSelectedCapture() {
    return mySelection.getCaptureObject();
  }
}
