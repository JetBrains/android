/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.memory.adapters;

import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Trace;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profilers.IdeProfilerServices;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.memory.BaseMemoryProfilerStage;
import com.android.tools.profilers.memory.ClassGrouping;
import com.android.tools.profilers.memory.MemoryProfiler;
import com.android.tools.profilers.memory.adapters.classifiers.ClassifierSet;
import com.android.tools.profilers.memory.adapters.classifiers.HeapSet;
import com.android.tools.profilers.memory.adapters.classifiers.NativeMemoryHeapSet;
import com.android.tools.profilers.perfetto.traceprocessor.TraceProcessorService;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class represents a heapprofd capture. The {@link NativeAllocationSampleCaptureObject:getStartTime } represents the start of the
 * capture as well as the capture id. To load a capture the {@link NativeAllocationSampleCaptureObject:load} function must be called.
 * This call blocks the current thread and request the data from the datastore. It then caches the bytes to a temp file and uses
 * the trace processor daemon to handle converting the file to profiler objects
 */
public final class NativeAllocationSampleCaptureObject implements CaptureObject {
  private final ClassDb myClassDb;
  private final ProfilerClient myClient;
  private final Common.Session mySession;
  private final long myStartTimeNs;
  private final long myEndTimeNs;
  private final List<HeapSet> myHeapSets;
  private final NativeMemoryHeapSet myDefaultHeapSet;
  private final BaseMemoryProfilerStage myStage;

  boolean myIsLoadingError = false;
  boolean myIsDoneLoading = false;
  private final Trace.TraceInfo myInfo;

  public NativeAllocationSampleCaptureObject(@NotNull ProfilerClient client,
                                             @NotNull Common.Session session,
                                             @NotNull Trace.TraceInfo info,
                                             @NotNull BaseMemoryProfilerStage stage) {
    myClassDb = new ClassDb();
    myClient = client;
    mySession = session;
    myInfo = info;
    myStartTimeNs = info.getFromTimestamp();
    myEndTimeNs = info.getToTimestamp();
    myStage = stage;

    myDefaultHeapSet = new NativeMemoryHeapSet(this);
    myHeapSets = new ArrayList<>();
    myHeapSets.add(myDefaultHeapSet);  // default
  }

  @NotNull
  @Override
  public String getName() {
    return "Recorded Native Allocations";
  }

  @Override
  public boolean isExportable() {
    return true;
  }

  @NotNull
  @Override
  public String getExportableExtension() {
    return "heapprofd";
  }

  @Override
  public void saveToFile(@NotNull OutputStream outputStream) {
    MemoryProfiler.saveHeapProfdSampleToFile(myClient, mySession, myInfo, outputStream);
  }

  @NotNull
  @Override
  public Stream<InstanceObject> getInstances() {
    assert isDoneLoading() && !isError();
    return getHeapSets().stream().map(ClassifierSet::getInstancesStream).flatMap(Function.identity());
  }

  @Override
  public long getStartTimeNs() {
    return myStartTimeNs;
  }

  @Override
  public long getEndTimeNs() {
    return myEndTimeNs;
  }

  @NotNull
  @Override
  public ClassDb getClassDatabase() {
    return myClassDb;
  }

  @Override
  public boolean load(@Nullable Range queryRange, @Nullable Executor queryJoiner) {
    Transport.BytesResponse response = Transport.BytesResponse.getDefaultInstance();
    int retryCount = 100; // ~10 seconds.
    while (response.getContents().isEmpty()) {
      response = myClient.getTransportClient().getBytes(Transport.BytesRequest.newBuilder()
                                                          .setStreamId(mySession.getStreamId())
                                                          .setId(Long.toString(myStartTimeNs))
                                                          .build());
      if (!response.getContents().isEmpty()) {
        break;
      }

      if (retryCount-- == 0) {
        myIsLoadingError = true;
        return false;
      }

      try {
        Thread.sleep(100L);
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        myIsLoadingError = true;
        return false;
      }
    }
    File trace;
    try {
      trace = FileUtil.createTempFile(String.format(Locale.US, "heap_trace_%d", myStartTimeNs), "." + getExportableExtension(), true);
      try (FileOutputStream out = new FileOutputStream(trace)) {
        out.write(response.getContents().toByteArray());
        out.flush();
      }
    }
    catch (IOException ex) {
      return false;
    }

    String abi = myStage.getStudioProfilers().getSessionsManager().getSelectedSessionMetaData().getProcessAbi();
    TraceProcessorService service = myStage.getStudioProfilers().getIdeServices().getTraceProcessorService();
    IdeProfilerServices profilerServices = myStage.getStudioProfilers().getIdeServices();
    long traceId = myStartTimeNs;
    service.loadTrace(traceId, trace.getAbsoluteFile(), profilerServices);
    service.loadMemoryData(traceId, abi, myDefaultHeapSet, profilerServices);
    myIsDoneLoading = true;
    return true;
  }

  @Override
  public boolean isDoneLoading() {
    return myIsDoneLoading || myIsLoadingError;
  }

  @Override
  public boolean isError() {
    return myIsLoadingError;
  }

  @Override
  public void unload() {
    myIsLoadingError = false;
    myIsDoneLoading = false;
  }

  @Override
  @NotNull
  public List<ClassifierAttribute> getClassifierAttributes() {
    return Arrays
      .asList(ClassifierAttribute.LABEL, ClassifierAttribute.MODULE, ClassifierAttribute.ALLOCATIONS, ClassifierAttribute.DEALLOCATIONS,
              ClassifierAttribute.ALLOCATIONS_SIZE, ClassifierAttribute.DEALLOCATIONS_SIZE, ClassifierAttribute.TOTAL_COUNT,
              ClassifierAttribute.REMAINING_SIZE);
  }

  @NotNull
  @Override
  public List<CaptureObject.InstanceAttribute> getInstanceAttributes() {
    return Arrays.asList(CaptureObject.InstanceAttribute.LABEL, InstanceAttribute.NATIVE_SIZE);
  }

  @NotNull
  @Override
  public Collection<HeapSet> getHeapSets() {
    return myHeapSets;
  }

  @Override
  @Nullable
  public HeapSet getHeapSet(int heapId) {
    assert heapId == DEFAULT_HEAP_ID;
    return myHeapSets.get(heapId);
  }

  @Override
  public boolean isGroupingSupported(ClassGrouping grouping) {
    switch (grouping) {
      case NATIVE_ARRANGE_BY_ALLOCATION_METHOD:
      case NATIVE_ARRANGE_BY_CALLSTACK:
        return true;
      case ARRANGE_BY_CLASS:
      case ARRANGE_BY_PACKAGE:
      case ARRANGE_BY_CALLSTACK:
      default:
        return false;
    }
  }
}
