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

import static com.android.tools.profilers.memory.adapters.CaptureObject.DEFAULT_HEAP_ID;

import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Memory.AllocatedClass;
import com.android.tools.profiler.proto.Memory.AllocationEvent;
import com.android.tools.profiler.proto.Memory.AllocationStack;
import com.android.tools.profiler.proto.Memory.BatchAllocationContexts;
import com.android.tools.profiler.proto.Memory.BatchAllocationEvents;
import com.android.tools.profiler.proto.Memory.BatchJNIGlobalRefEvent;
import com.android.tools.profiler.proto.Memory.JNIGlobalReferenceEvent;
import com.android.tools.profiler.proto.Memory.NativeBacktrace;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationContextsRequest;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationContextsResponse;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationSnapshotRequest;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationsInfo;
import com.android.tools.profiler.proto.MemoryProfiler.HeapDumpInfo;
import com.android.tools.profiler.proto.MemoryProfiler.ImportHeapDumpRequest;
import com.android.tools.profiler.proto.MemoryProfiler.ImportHeapDumpResponse;
import com.android.tools.profiler.proto.MemoryProfiler.ImportLegacyAllocationsRequest;
import com.android.tools.profiler.proto.MemoryProfiler.ImportLegacyAllocationsResponse;
import com.android.tools.profiler.proto.MemoryProfiler.JNIGlobalRefsEventsRequest;
import com.android.tools.profiler.proto.MemoryProfiler.LegacyAllocationContextsRequest;
import com.android.tools.profiler.proto.MemoryProfiler.LegacyAllocationContextsResponse;
import com.android.tools.profiler.proto.MemoryProfiler.LegacyAllocationEvent;
import com.android.tools.profiler.proto.MemoryProfiler.LegacyAllocationEventsRequest;
import com.android.tools.profiler.proto.MemoryProfiler.LegacyAllocationEventsResponse;
import com.android.tools.profiler.proto.MemoryProfiler.ListDumpInfosRequest;
import com.android.tools.profiler.proto.MemoryProfiler.ListHeapDumpInfosResponse;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryRequest;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryStartRequest;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryStartResponse;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryStopRequest;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryStopResponse;
import com.android.tools.profiler.proto.MemoryProfiler.NativeCallStack;
import com.android.tools.profiler.proto.MemoryProfiler.ResolveNativeBacktraceRequest;
import com.android.tools.profiler.proto.MemoryProfiler.StackFrameInfoRequest;
import com.android.tools.profiler.proto.MemoryProfiler.StackFrameInfoResponse;
import com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsRequest;
import com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsResponse;
import com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsResponse.Status;
import com.android.tools.profiler.proto.MemoryProfiler.TriggerHeapDumpRequest;
import com.android.tools.profiler.proto.MemoryProfiler.TriggerHeapDumpResponse;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import io.grpc.stub.StreamObserver;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FakeMemoryService extends MemoryServiceGrpc.MemoryServiceImplBase {

  private static long SEC_TO_NS = TimeUnit.SECONDS.toNanos(1);
  // Live Allocation data constant - duration of each allocation event.
  private static long ALLOC_EVENT_DURATION_NS = 2 * SEC_TO_NS;
  // Live allocation data constant - number of class/method entries in our fake context data pool.
  private static int ALLOC_CONTEXT_NUM = 4;
  // Live allocation data constant - fixed object size.
  private static int ALLOC_SIZE = 1;
  // Live allocation data constant - class names that gets cycled/reused every |ALLOC_CONTEXT_NUM| us.
  private static final List<String> CONTEXT_CLASS_NAMES = Arrays.asList(
    "This.Is.Foo",
    "That.Is.Bar",
    "This.Also.Foo",
    "That.Also.Bar"
  );
  // Live allocation data constant - method names that gets cycled/reused every |ALLOC_CONTEXT_NUM| us.
  private static final List<String> CONTEXT_METHOD_NAMES = Arrays.asList(
    "FooMethodA",
    "BarMethodA",
    "FooMethodB",
    "BarMethodB"
  );
  // Difference between object tag and JNI reference value.
  private static final long JNI_REF_BASE = 0x50000000;

  private static final Long NATIVE_ADDRESSES_BASE = 0xBAADF00Dl;
  private static final List<String> NATIVE_FUNCTION_NAMES = Arrays.asList(
    "NativeNamespace::Foo::FooMethodA(string, int)",
    "NativeNamespace::Bar::BarMethodA(string, int)",
    "NativeNamespace::Foo::FooMethodB(string, int)",
    "NativeNamespace::Bar::BarMethodB(string, int)"
  );

  private static final List<String> NATIVE_SOURCE_FILE = Arrays.asList(
    "/a/path/to/sources/foo.cc",
    "/a/path/to/sources/bar.cc",
    "/a/path/to/sources/foo.h",
    "/a/path/to/sources/bar.h"
  );

  private static final List<String> NATIVE_MODULE_NAMES = Arrays.asList(
    "/data/app/com.example.sum-000==/lib/arm64/libfoo.so",
    "/data/app/com.example.sum-000==/lib/arm/libbar.so",
    "/data/app/com.example.sum-000==/lib/x86/libfoo.so",
    "/data/app/com.example.sum-000==/lib/x86_64/libbar.so"
  );

  private static final String SYSTEM_NATIVE_MODULE = "/system/lib64/libnativewindow.so";

  private Status myExplicitAllocationsStatus = null;
  private AllocationsInfo myExplicitAllocationsInfo = null;
  private Memory.DumpStartStatus.Status myExplicitHeapDumpStatus = null;
  private HeapDumpInfo myExplicitHeapDumpInfo = null;
  private MemoryData myMemoryData = null;
  private ListHeapDumpInfosResponse.Builder myHeapDumpInfoBuilder = ListHeapDumpInfosResponse.newBuilder();
  private LegacyAllocationEventsResponse.Builder myAllocationEventsBuilder = LegacyAllocationEventsResponse.newBuilder();
  private LegacyAllocationContextsResponse.Builder myAllocationContextBuilder = LegacyAllocationContextsResponse.newBuilder();
  private int myTrackAllocationCount;
  private Common.Session mySession;
  private int mySamplingRate = 1;
  private FakeTransportService myTransportService;

  public FakeMemoryService() {
    this(null);
  }

  // TODO b/121392346 remove after legacy pipeline deprecation.
  // The TransportService is temporarily needed for heap dump import workflow to insert the byte buffers into the byte cache.
  // In the new pipeline, this will be handled via a separate data stream.
  public FakeMemoryService(@Nullable FakeTransportService transportService) {
    myTransportService = transportService;
  }

  @Override
  public void startMonitoringApp(MemoryStartRequest request,
                                 StreamObserver<MemoryStartResponse> responseObserver) {

    mySession = request.getSession();
    responseObserver.onNext(MemoryStartResponse.newBuilder().build());
    responseObserver.onCompleted();
  }

  @Override
  public void stopMonitoringApp(MemoryStopRequest request,
                                StreamObserver<MemoryStopResponse> responseObserver) {
    mySession = request.getSession();
    responseObserver.onNext(MemoryStopResponse.newBuilder().build());
    responseObserver.onCompleted();
  }

  public int getProcessId() {
    return mySession.getPid();
  }

  @Override
  public void trackAllocations(TrackAllocationsRequest request,
                               StreamObserver<TrackAllocationsResponse> response) {
    myTrackAllocationCount++;
    TrackAllocationsResponse.Builder builder = TrackAllocationsResponse.newBuilder();
    if (myExplicitAllocationsStatus != null) {
      builder.setStatus(myExplicitAllocationsStatus);
    }
    if (myExplicitAllocationsInfo != null) {
      builder.setInfo(myExplicitAllocationsInfo);
    }
    response.onNext(builder.build());
    response.onCompleted();
  }

  @Override
  public void importLegacyAllocations(ImportLegacyAllocationsRequest request,
                                      StreamObserver<ImportLegacyAllocationsResponse> response) {
    myExplicitAllocationsInfo = request.getInfo();
    myAllocationEventsBuilder = request.getAllocations().toBuilder();
    myAllocationContextBuilder.addAllClasses(request.getClassesList()).addAllStacks(request.getStacksList());
    myMemoryData = MemoryData.newBuilder().addAllocationsInfo(request.getInfo()).build();
    response.onNext(ImportLegacyAllocationsResponse.newBuilder().setStatus(ImportLegacyAllocationsResponse.Status.SUCCESS).build());
    response.onCompleted();
  }

  @Override
  public void getData(MemoryRequest request, StreamObserver<MemoryData> response) {
    response.onNext(myMemoryData != null ? myMemoryData
                                         : MemoryData.newBuilder().setEndTimestamp(request.getStartTime() + 1).build());
    response.onCompleted();
  }

  @Override
  public void getJvmtiData(MemoryRequest request, StreamObserver<MemoryData> responseObserver) {
    responseObserver
      .onNext(myMemoryData != null ? myMemoryData : MemoryData.newBuilder().setEndTimestamp(request.getStartTime() + 1).build());
    responseObserver.onCompleted();
  }

  @Override
  public void getLegacyAllocationEvents(LegacyAllocationEventsRequest request,
                                        StreamObserver<LegacyAllocationEventsResponse> responseObserver) {
    responseObserver.onNext(myAllocationEventsBuilder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getLegacyAllocationContexts(LegacyAllocationContextsRequest request,
                                          StreamObserver<MemoryProfiler.LegacyAllocationContextsResponse> responseObserver) {
    responseObserver.onNext(myAllocationContextBuilder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void listHeapDumpInfos(ListDumpInfosRequest request,
                                StreamObserver<ListHeapDumpInfosResponse> response) {
    response.onNext(myHeapDumpInfoBuilder.build());
    response.onCompleted();
  }

  @Override
  public void importHeapDump(ImportHeapDumpRequest request, StreamObserver<ImportHeapDumpResponse> responseObserver) {
    ImportHeapDumpResponse.Builder responseBuilder = ImportHeapDumpResponse.newBuilder();
    myExplicitHeapDumpInfo = request.getInfo();
    myHeapDumpInfoBuilder.addInfos(myExplicitHeapDumpInfo);
    responseBuilder.setStatus(ImportHeapDumpResponse.Status.SUCCESS);
    if (myTransportService != null) {
      myTransportService.addFile(Long.toString(request.getInfo().getStartTime()), request.getData());
    }
    responseObserver.onNext(responseBuilder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void triggerHeapDump(TriggerHeapDumpRequest request,
                              StreamObserver<TriggerHeapDumpResponse> responseObserver) {
    TriggerHeapDumpResponse.Builder builder = TriggerHeapDumpResponse.newBuilder();
    if (myExplicitHeapDumpStatus != null) {
      builder.setStatus(Memory.DumpStartStatus.newBuilder().setStatus(myExplicitHeapDumpStatus));
    }
    if (myExplicitHeapDumpInfo != null) {
      builder.setInfo(myExplicitHeapDumpInfo);
    }
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getAllocationEvents(AllocationSnapshotRequest request,
                                  StreamObserver<MemoryProfiler.AllocationEventsResponse> responseObserver) {
    BatchAllocationEvents events = getAllocationSample(request.getStartTime(), request.getEndTime());
    responseObserver.onNext(MemoryProfiler.AllocationEventsResponse.newBuilder().addEvents(events).build());
    responseObserver.onCompleted();
  }

  /**
   * Generates a list of allocation events based on the request start + end time.
   * For every microsecond, an allocation event is created and uniquely tagged with the timestamp. The event generation starts
   * at t = 0, and each is expected to be deallocated |ALLOC_EVENT_DURATION_NS| later. Each allocation also references a class and
   * stack, with ids that are cycled every |ALLOC_CONTEXT_NUM| allocations.
   * <p>
   * The following illustrates what the data looks like within {0s,5s}:
   * {0s,2s}, tag = 0, class tag = 1, stack id = 1
   * {1s,3s}, tag = 1, class tag = 2, stack id = 2
   * {2s,4s}, tag = 2, class tag = 3, stack id = 3
   * {3s,5s}, tag = 3, class tag = 4, stack id = 4
   * {4s,6s}, tag = 4, class tag = 1, stack id = 1
   * {5s,7s}, tag = 5, class tag = 2, stack id = 2
   */
  @NotNull
  private static BatchAllocationEvents getAllocationSample(long startTime, long endTime) {
    BatchAllocationEvents.Builder sampleBuilder = BatchAllocationEvents.newBuilder();
    for (long i = 0; i < endTime; i += SEC_TO_NS) {
      int tag = (int)(i / SEC_TO_NS);
      int contextId = tag % ALLOC_CONTEXT_NUM + 1;
      AllocationEvent event = AllocationEvent.newBuilder()
        .setAllocData(
          AllocationEvent.Allocation.newBuilder().setTag(tag).setSize(ALLOC_SIZE).setClassTag(contextId)
            .setStackId(contextId).setHeapId(DEFAULT_HEAP_ID).build())
        .setTimestamp(i).build();
      sampleBuilder.addEvents(event);

      boolean shouldAddDeallocation = i - ALLOC_EVENT_DURATION_NS >= 0;
      if (shouldAddDeallocation) {
        tag = (int)((i - ALLOC_EVENT_DURATION_NS) / SEC_TO_NS);
        event = AllocationEvent.newBuilder()
          .setFreeData(AllocationEvent.Deallocation.newBuilder().setTag(tag))
          .setTimestamp(i)
          .build();
        sampleBuilder.addEvents(event);
      }

      sampleBuilder.setTimestamp(Math.max(sampleBuilder.getTimestamp(), i));
    }

    return sampleBuilder.build();
  }

  @Override
  public void getJNIGlobalRefsEvents(JNIGlobalRefsEventsRequest request,
                                     StreamObserver<BatchJNIGlobalRefEvent> responseObserver) {
    // Just add JNI references for all allocated object from the same interval.
    boolean liveObjectsOnly = request.getLiveObjectsOnly();
    BatchAllocationEvents allocationSample = getAllocationSample(request.getStartTime(), request.getEndTime());

    Iterator<AllocationEvent> allocations;
    if (liveObjectsOnly) {
      // Only look at objects that are still alive at request end time.
      // Instances that are allocated before (request.getEndTime() - ALLOC_EVENT_DURATION_NS) would have already been deallocated.
      allocations = allocationSample.getEventsList().stream()
        .filter(evt -> evt.getEventCase() == AllocationEvent.EventCase.ALLOC_DATA &&
                       evt.getTimestamp() >= request.getEndTime() - ALLOC_EVENT_DURATION_NS)
        .iterator();
    }
    else {
      // Consider all allocations that happen between (request.getStartTime() - ALLOC_EVENT_DURATION_NS) and request.getEndTime
      // We need the additional allocation events before the start time because their info is needed to generate the deallocation events.
      allocations = allocationSample.getEventsList().stream()
        .filter(evt -> evt.getEventCase() == AllocationEvent.EventCase.ALLOC_DATA &&
                       (evt.getTimestamp() >= request.getStartTime() - ALLOC_EVENT_DURATION_NS &&
                        evt.getTimestamp() < request.getEndTime()))
        .iterator();
    }

    BatchJNIGlobalRefEvent.Builder result = BatchJNIGlobalRefEvent.newBuilder();
    while (allocations.hasNext()) {
      AllocationEvent event = allocations.next();
      AllocationEvent.Allocation allocation = event.getAllocData();
      if (liveObjectsOnly) {
        JNIGlobalReferenceEvent.Builder jniEvent = JNIGlobalReferenceEvent.newBuilder()
          .setEventType(JNIGlobalReferenceEvent.Type.CREATE_GLOBAL_REF)
          .setObjectTag(allocation.getTag())
          .setRefValue(allocation.getTag() + JNI_REF_BASE)
          .setThreadId(allocation.getThreadId())
          .setTimestamp(event.getTimestamp())
          .setBacktrace(createBacktrace(allocation.getTag()));
        result.addEvents(jniEvent);
      }
      else {
        if (event.getTimestamp() < request.getEndTime() - ALLOC_EVENT_DURATION_NS) {
          JNIGlobalReferenceEvent.Builder jniEvent = JNIGlobalReferenceEvent.newBuilder()
            .setEventType(JNIGlobalReferenceEvent.Type.DELETE_GLOBAL_REF)
            .setObjectTag(allocation.getTag())
            .setRefValue(allocation.getTag() + JNI_REF_BASE)
            .setThreadId(allocation.getThreadId())
            .setTimestamp(event.getTimestamp())
            .setBacktrace(createBacktrace(allocation.getTag()));
          result.addEvents(jniEvent);
        }

        if (event.getTimestamp() >= request.getStartTime()) {
          JNIGlobalReferenceEvent.Builder jniEvent = JNIGlobalReferenceEvent.newBuilder()
            .setEventType(JNIGlobalReferenceEvent.Type.CREATE_GLOBAL_REF)
            .setObjectTag(allocation.getTag())
            .setRefValue(allocation.getTag() + JNI_REF_BASE)
            .setThreadId(allocation.getThreadId())
            .setTimestamp(event.getTimestamp())
            .setBacktrace(createBacktrace(allocation.getTag()));
          result.addEvents(jniEvent);
        }
      }
    }

    result.setTimestamp(request.getEndTime());
    responseObserver.onNext(result.build());
    responseObserver.onCompleted();
  }

  private static NativeBacktrace createBacktrace(int objTag) {
    NativeBacktrace.Builder result = NativeBacktrace.newBuilder();
    result.addAddresses(NATIVE_ADDRESSES_BASE + objTag);
    result.addAddresses(NATIVE_ADDRESSES_BASE + objTag + 1);
    return result.build();
  }

  @Override
  public void resolveNativeBacktrace(ResolveNativeBacktraceRequest request,
                                     StreamObserver<NativeCallStack> responseObserver) {
    NativeCallStack.Builder result = NativeCallStack.newBuilder();
    for (long addr : request.getBacktrace().getAddressesList()) {
      int index = (int)(addr - NATIVE_ADDRESSES_BASE) % ALLOC_CONTEXT_NUM;
      result.addFramesBuilder()
        .setModuleName(NATIVE_MODULE_NAMES.get(index))
        .setSymbolName(NATIVE_FUNCTION_NAMES.get(index))
        .setFileName(NATIVE_SOURCE_FILE.get(index))
        .setAddress(addr)
        .setLineNumber(index + 100)
        .setModuleOffset(addr);
    }

    /* Add an extra frame from a system module to check that such frames are ignored */
    result.addFramesBuilder()
      .setModuleName(SYSTEM_NATIVE_MODULE)
      .setSymbolName("system_symbol_name()")
      .setFileName("/path/android.cc")
      // Set address as base - 1 to avoid conflicting with the addresses used in createBacktrace above.
      .setAddress(NATIVE_ADDRESSES_BASE - 1)
      .setLineNumber(1)
      .setModuleOffset(NATIVE_ADDRESSES_BASE);

    responseObserver.onNext(result.build());
    responseObserver.onCompleted();
  }

  /**
   * This fake service call auto-generate a list of allocation contexts based on the request start + end time.
   * For simplicity, we only fake a small pool of allocation context data that starts at 0 and ends at |ALLOC_CONTEXT_NUM| us.
   * Subsequent fake allocation events are designed to reuse the same pool of data, so querying beyond |ALLOC_CONTEXT_NUM| us will
   * return empty data. Also note that class and stack ids are 1-based so a context entry at t = 0 would have an id of 1.
   * <p>
   * The following illustrates what the data looks like within {0,5}
   * t = 0s, class tag = 1 (CONTEXT_CLASS_NAMES[0]) stack id = 1 ({CONTEXT_METHOD_NAMES[0], CONTEXT_METHOD_NAMES[1]})
   * t = 1s, class tag = 2 (CONTEXT_CLASS_NAMES[1]) stack id = 2 ({CONTEXT_METHOD_NAMES[1], CONTEXT_METHOD_NAMES[2]})
   * t = 2s, class tag = 3 (CONTEXT_CLASS_NAMES[2]) stack id = 3 ({CONTEXT_METHOD_NAMES[2], CONTEXT_METHOD_NAMES[3]})
   * t = 3s, class tag = 4 (CONTEXT_CLASS_NAMES[3]) stack id = 4 ({CONTEXT_METHOD_NAMES[3], CONTEXT_METHOD_NAMES[0]})
   */
  @Override
  public void getAllocationContexts(AllocationContextsRequest request,
                                    StreamObserver<AllocationContextsResponse> responseObserver) {
    BatchAllocationContexts.Builder contextBuilder = BatchAllocationContexts.newBuilder();
    long endTime = request.getEndTime();
    for (long i = 0; i < endTime; i += SEC_TO_NS) {
      int iAdjusted = (int)(i / SEC_TO_NS);
      if (iAdjusted >= ALLOC_CONTEXT_NUM) {
        break;
      }

      // Add class.
      AllocatedClass allocClass =
        AllocatedClass.newBuilder().setClassId(iAdjusted + 1).setClassName(CONTEXT_CLASS_NAMES.get(iAdjusted)).build();
      contextBuilder.addClasses(allocClass);

      // Add stack.
      AllocationStack.Builder stackBuilder = AllocationStack.newBuilder();
      stackBuilder.setStackId(iAdjusted + 1);
      AllocationStack.EncodedFrameWrapper.Builder frameBuilder = AllocationStack.EncodedFrameWrapper.newBuilder();
      for (int j = 0; j < 2; j++) {
        int contextIndex = (iAdjusted + j) % ALLOC_CONTEXT_NUM;
        int contextId = contextIndex + 1; // valid method Id starts at 1
        frameBuilder.addFrames(AllocationStack.EncodedFrame.newBuilder().setMethodId(contextId).setLineNumber(-1));

        contextBuilder.addMethods(AllocationStack.StackFrame.newBuilder()
                                    .setMethodId(contextId)
                                    .setClassName(CONTEXT_CLASS_NAMES.get(contextIndex))
                                    .setMethodName(CONTEXT_METHOD_NAMES.get(contextIndex)));
      }
      stackBuilder.setEncodedStack(frameBuilder);
      contextBuilder.addEncodedStacks(stackBuilder);
      contextBuilder.setTimestamp(Math.max(i, contextBuilder.getTimestamp()));
    }

    responseObserver.onNext(AllocationContextsResponse.newBuilder().addContexts(contextBuilder).build());
    responseObserver.onCompleted();
  }

  @Override
  public void getStackFrameInfo(StackFrameInfoRequest request,
                                StreamObserver<StackFrameInfoResponse> responseObserver) {
    int id = (int)request.getMethodId() - 1;  // compensate for +1 offset in method Id to get the correct index.
    StackFrameInfoResponse.Builder methodBuilder = StackFrameInfoResponse.newBuilder()
      .setClassName(CONTEXT_CLASS_NAMES.get(id))
      .setMethodName(CONTEXT_METHOD_NAMES.get(id));
    responseObserver.onNext(methodBuilder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void setAllocationSamplingRate(MemoryProfiler.SetAllocationSamplingRateRequest request,
                                        StreamObserver<MemoryProfiler.SetAllocationSamplingRateResponse> responseObserver) {
    mySamplingRate = request.getSamplingRate().getSamplingNumInterval();
    responseObserver.onNext(MemoryProfiler.SetAllocationSamplingRateResponse.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @NotNull
  public FakeMemoryService setExplicitAllocationsStatus(@Nullable Status status) {
    myExplicitAllocationsStatus = status;
    return this;
  }

  public FakeMemoryService setExplicitAllocationsInfo(AllocationsInfo.Status infoStatus,
                                                      long startTime, long endTime, boolean legacy) {
    myExplicitAllocationsInfo =
      AllocationsInfo.newBuilder().setStatus(infoStatus).setStartTime(startTime).setEndTime(endTime)
        .setLegacy(legacy).build();
    return this;
  }

  public FakeMemoryService setExplicitHeapDumpStatus(@Nullable Memory.DumpStartStatus.Status status) {
    myExplicitHeapDumpStatus = status;
    return this;
  }

  public FakeMemoryService setExplicitHeapDumpInfo(long startTime, long endTime) {
    myExplicitHeapDumpInfo = HeapDumpInfo.newBuilder().setStartTime(startTime).setEndTime(endTime).build();
    return this;
  }

  public FakeMemoryService setMemoryData(@Nullable MemoryData data) {
    myMemoryData = data;
    return this;
  }

  public FakeMemoryService addExplicitHeapDumpInfo(@NotNull HeapDumpInfo info) {
    myHeapDumpInfoBuilder.addInfos(info);
    return this;
  }

  public FakeMemoryService setExplicitAllocationEvents(LegacyAllocationEventsResponse.Status status,
                                                       @NotNull List<LegacyAllocationEvent> events) {
    myAllocationEventsBuilder.setStatus(status);
    myAllocationEventsBuilder.addAllEvents(events);
    return this;
  }

  public FakeMemoryService addExplicitAllocationClass(int id, String name) {
    myAllocationContextBuilder.addClasses(AllocatedClass.newBuilder().setClassId(id).setClassName(name).build());
    return this;
  }

  public FakeMemoryService addExplicitAllocationStack(String klass, String method, int line, int stackId) {
    myAllocationContextBuilder.addStacks(AllocationStack.newBuilder().setStackId(stackId).setFullStack(
      AllocationStack.StackFrameWrapper.newBuilder().addFrames(
        AllocationStack.StackFrame.newBuilder().setClassName(klass).setMethodName(method).setLineNumber(line).build()
      )));
    return this;
  }

  public int getTrackAllocationCount() {
    return myTrackAllocationCount;
  }

  public void resetTrackAllocationCount() {
    myTrackAllocationCount = 0;
  }

  public int getSamplingRate() {
    return mySamplingRate;
  }
}
