/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers;

import static com.android.tools.profiler.proto.Common.Event.EventGroupIds.NETWORK_RX_VALUE;
import static com.android.tools.profiler.proto.Common.Event.EventGroupIds.NETWORK_TX_VALUE;
import static com.android.tools.profilers.memory.adapters.CaptureObject.DEFAULT_HEAP_ID;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.Energy;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Memory.AllocatedClass;
import com.android.tools.profiler.proto.Memory.AllocationEvent;
import com.android.tools.profiler.proto.Memory.AllocationStack;
import com.android.tools.profiler.proto.Memory.BatchAllocationContexts;
import com.android.tools.profiler.proto.Memory.BatchAllocationEvents;
import com.android.tools.profiler.proto.Memory.BatchJNIGlobalRefEvent;
import com.android.tools.profiler.proto.Memory.JNIGlobalReferenceEvent;
import com.android.tools.profiler.proto.Network;
import com.android.tools.profiler.proto.Trace;
import com.android.tools.profilers.cpu.config.ImportedConfiguration;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/**
 * Profiler test data holder class.
 */
public final class ProfilersTestData {

  // Un-initializable.
  private ProfilersTestData() {
  }

  public static final ProfilingConfiguration DEFAULT_CONFIG = new ImportedConfiguration();

  public static final Common.Session SESSION_DATA = Common.Session.newBuilder()
    .setSessionId(4321)
    .setStreamId(1234)
    .setPid(5678)
    .build();

  public static final Common.AgentData DEFAULT_AGENT_ATTACHED_RESPONSE =
    Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.ATTACHED).build();

  public static final Common.AgentData DEFAULT_AGENT_DETACHED_RESPONSE =
    Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.UNATTACHABLE).build();

  // For live allocation tracking tests - duration of each allocation event.
  public static final long ALLOC_EVENT_DURATION_NS = 2 * FakeTimer.ONE_SECOND_IN_NS;
  // For live allocation tracking tests - number of class/method entries in our fake context data pool.
  public static final int ALLOC_CONTEXT_NUM = 4;
  // For live allocation tracking tests - fixed object size.
  public static final int ALLOC_SIZE = 1;
  // For live allocation tracking tests - class names that gets cycled/reused every |ALLOC_CONTEXT_NUM| us.
  public static final List<String> CONTEXT_CLASS_NAMES = Arrays.asList(
    "LThis/Is/Foo;",
    "LThat/Is/Bar;",
    "LThis/Also/Foo;",
    "LThat/Also/Bar;"
  );
  // For live allocation tracking tests - method names that gets cycled/reused every |ALLOC_CONTEXT_NUM| us.
  public static final List<String> CONTEXT_METHOD_NAMES = Arrays.asList(
    "FooMethodA",
    "BarMethodA",
    "FooMethodB",
    "BarMethodB"
  );

  // Used for testing JNI reference tracking.
  public static final Long NATIVE_ADDRESSES_BASE = 0xBAADF00DL;
  public static final Long SYSTEM_NATIVE_ADDRESSES_BASE = NATIVE_ADDRESSES_BASE - 1;
  // Used for testing JNI reference tracking.
  public static final List<String> FAKE_NATIVE_MODULE_NAMES = Arrays.asList(
    "/data/app/com.example.sum-000==/lib/arm64/libfoo.so",
    "/data/app/com.example.sum-000==/lib/arm/libbar.so",
    "/data/app/com.example.sum-000==/lib/x86/libfoo.so",
    "/data/app/com.example.sum-000==/lib/x86_64/libbar.so"
  );
  // Used for testing JNI reference tracking.
  public static final List<String> FAKE_NATIVE_FUNCTION_NAMES = Arrays.asList(
    "NativeNamespace::Foo::FooMethodA(string, int)",
    "NativeNamespace::Bar::BarMethodA(string, int)",
    "NativeNamespace::Foo::FooMethodB(string, int)",
    "NativeNamespace::Bar::BarMethodB(string, int)"
  );
  // Used for testing JNI reference tracking.
  public static final List<String> FAKE_NATIVE_SOURCE_FILE = Arrays.asList(
    "/a/path/to/sources/foo.cc",
    "/a/path/to/sources/bar.cc",
    "/a/path/to/sources/foo.h",
    "/a/path/to/sources/bar.h"
  );
  // Used for testing JNI reference tracking.
  public static final String FAKE_SYSTEM_NATIVE_MODULE = "/system/lib64/libnativewindow.so";
  // Used for testing JNI reference tracking: difference between object tag and JNI reference value.
  public static final long JNI_REF_BASE = 0x50000000;

  @NotNull
  public static Common.Event.Builder generateSessionStartEvent(long streamId,
                                                               long sessionId,
                                                               long timestampNs,
                                                               Common.SessionData.SessionStarted.SessionType type,
                                                               long startTimestampEpochMs) {
    return Common.Event.newBuilder().setTimestamp(timestampNs).setGroupId(sessionId).setKind(Common.Event.Kind.SESSION)
      .setSession(Common.SessionData.newBuilder().setSessionStarted(
        Common.SessionData.SessionStarted.newBuilder().setStreamId(streamId).setSessionId(sessionId).setType(type)
          .setStartTimestampEpochMs(startTimestampEpochMs)));
  }

  @NotNull
  public static Common.Event.Builder generateSessionEndEvent(long streamId, long sessionId, long timestampNs) {
    return Common.Event.newBuilder().setTimestamp(timestampNs).setGroupId(sessionId).setKind(Common.Event.Kind.SESSION).setIsEnded(true);
  }

  @NotNull
  public static Common.Event.Builder generateNetworkTxEvent(long timestampUs, int throughput) {
    return Common.Event.newBuilder()
      .setTimestamp(TimeUnit.MICROSECONDS.toNanos(timestampUs))
      .setKind(Common.Event.Kind.NETWORK_SPEED)
      .setGroupId(NETWORK_TX_VALUE)
      .setNetworkSpeed(Network.NetworkSpeedData.newBuilder().setThroughput(throughput));
  }

  @NotNull
  public static Common.Event.Builder generateNetworkRxEvent(long timestampUs, int throughput) {
    return Common.Event.newBuilder()
      .setTimestamp(TimeUnit.MICROSECONDS.toNanos(timestampUs))
      .setKind(Common.Event.Kind.NETWORK_SPEED)
      .setGroupId(NETWORK_RX_VALUE)
      .setNetworkSpeed(Network.NetworkSpeedData.newBuilder().setThroughput(throughput));
  }

  @NotNull
  public static Common.Event.Builder generateMemoryUsageData(long timestampUs, Memory.MemoryUsageData memoryUsageData) {
    long timestampNs = TimeUnit.MICROSECONDS.toNanos(timestampUs);
    return Common.Event.newBuilder().setTimestamp(timestampNs).setKind(Common.Event.Kind.MEMORY_USAGE).setMemoryUsage(memoryUsageData);
  }

  @NotNull
  public static Common.Event.Builder generateMemoryGcData(int pid, long timestampUs, Memory.MemoryGcData gcData) {
    long timestampNs = TimeUnit.MICROSECONDS.toNanos(timestampUs);
    return Common.Event.newBuilder().setPid(pid).setTimestamp(timestampNs).setKind(Common.Event.Kind.MEMORY_GC).setMemoryGc(gcData);
  }

  @NotNull
  public static Common.Event.Builder generateMemoryAllocStatsData(int pid, long timestampUs, int alloCount) {
    long timestampNs = TimeUnit.MICROSECONDS.toNanos(timestampUs);
    return Common.Event.newBuilder().setPid(pid).setTimestamp(timestampNs).setKind(Common.Event.Kind.MEMORY_ALLOC_STATS)
      .setMemoryAllocStats(Memory.MemoryAllocStatsData.newBuilder().setJavaAllocationCount(alloCount));
  }

  @NotNull
  public static Common.Event.Builder generateMemoryAllocSamplingData(int pid, long timestampUs, int samplingRate) {
    long timestampNs = TimeUnit.MICROSECONDS.toNanos(timestampUs);
    return Common.Event.newBuilder().setPid(pid).setTimestamp(timestampNs).setKind(Common.Event.Kind.MEMORY_ALLOC_SAMPLING)
      .setMemoryAllocSampling(Memory.MemoryAllocSamplingData.newBuilder().setSamplingNumInterval(samplingRate));
  }

  @NotNull
  public static Common.Event.Builder generateMemoryHeapDumpData(long groupId, long timestampUs, Memory.HeapDumpInfo info) {
    long timestampNs = TimeUnit.MICROSECONDS.toNanos(timestampUs);
    return Common.Event.newBuilder().setTimestamp(timestampNs).setGroupId(groupId).setKind(Common.Event.Kind.MEMORY_HEAP_DUMP)
      .setIsEnded(true).setMemoryHeapdump(Memory.MemoryHeapDumpData.newBuilder().setInfo(info));
  }

  @NotNull
  public static Common.Event.Builder generateMemoryTraceData(long groupId, long timestampUs, Trace.TraceData traceData) {
    long timestampNs = TimeUnit.MICROSECONDS.toNanos(timestampUs);
    return Common.Event.newBuilder().setTimestamp(timestampNs).setGroupId(groupId).setKind(Common.Event.Kind.MEM_TRACE)
      .setIsEnded(true).setTraceData(traceData);
  }


  @NotNull
  public static Common.Event.Builder generateMemoryAllocationInfoData(long timestamp, int pid, Memory.AllocationsInfo info) {
    return Common.Event.newBuilder().setTimestamp(timestamp).setGroupId(timestamp).setKind(Common.Event.Kind.MEMORY_ALLOC_TRACKING)
      .setPid(pid).setIsEnded(true).setMemoryAllocTracking(Memory.MemoryAllocTrackingData.newBuilder().setInfo(info));
  }

  public static Common.Event.Builder generateMemoryAllocSamplingData(Common.Session session,
                                                                     long timestampNs,
                                                                     Memory.MemoryAllocSamplingData data) {
    return Common.Event.newBuilder()
      .setPid(session.getPid())
      .setTimestamp(timestampNs)
      .setKind(Common.Event.Kind.MEMORY_ALLOC_SAMPLING)
      .setMemoryAllocSampling(data);
  }

  /**
   * Auto-generate a list of allocation contexts up to the input endTime.
   * For simplicity, we only fake a small pool of allocation context data that starts at 0 and ends at |ALLOC_CONTEXT_NUM| seconds.
   * Subsequent fake allocation and jni ref events reuse the same pool of data, so querying beyond |ALLOC_CONTEXT_NUM| seconds will
   * return empty data. Also note that class and stack ids are 1-based so a context entry at t = 0 would have an id of 1.
   * <p>
   * The following illustrates what the data looks like within {0s,5s}
   * t = 0s, class tag = 1 (CONTEXT_CLASS_NAMES[0]) stack id = 1 ({CONTEXT_METHOD_NAMES[0], CONTEXT_METHOD_NAMES[1]})
   * t = 1s, class tag = 2 (CONTEXT_CLASS_NAMES[1]) stack id = 2 ({CONTEXT_METHOD_NAMES[1], CONTEXT_METHOD_NAMES[2]})
   * t = 2s, class tag = 3 (CONTEXT_CLASS_NAMES[2]) stack id = 3 ({CONTEXT_METHOD_NAMES[2], CONTEXT_METHOD_NAMES[3]})
   * t = 3s, class tag = 4 (CONTEXT_CLASS_NAMES[3]) stack id = 4 ({CONTEXT_METHOD_NAMES[3], CONTEXT_METHOD_NAMES[0]})
   */
  public static List<BatchAllocationContexts> generateMemoryAllocContext(long startTimeNs, long endTimeNs) {
    List<BatchAllocationContexts> contexts = new ArrayList<>();
    for (long timestamp = Math.max(0, startTimeNs); timestamp < endTimeNs; timestamp += FakeTimer.ONE_SECOND_IN_NS) {
      BatchAllocationContexts.Builder contextBuilder = BatchAllocationContexts.newBuilder();

      // converts the timestamp to index
      int index = (int)(timestamp / FakeTimer.ONE_SECOND_IN_NS);
      if (index >= ProfilersTestData.ALLOC_CONTEXT_NUM) {
        break;
      }

      // +1 because class and stack ids are 1-based.
      int contextId = index + 1;

      // Add class.
      AllocatedClass allocClass =
        AllocatedClass.newBuilder().setClassId(contextId).setClassName(ProfilersTestData.CONTEXT_CLASS_NAMES.get(index)).build();
      contextBuilder.addClasses(allocClass);

      // Add stack.
      AllocationStack.Builder stackBuilder = AllocationStack.newBuilder();
      stackBuilder.setStackId(contextId);
      AllocationStack.EncodedFrameWrapper.Builder frameBuilder = AllocationStack.EncodedFrameWrapper.newBuilder();
      for (int j = 0; j < 2; j++) {
        int contextIndex = (index + j) % ProfilersTestData.ALLOC_CONTEXT_NUM;
        // +1 because class and stack ids are 1-based.
        int frameId = contextIndex + 1;
        frameBuilder.addFrames(AllocationStack.EncodedFrame.newBuilder().setMethodId(frameId).setLineNumber(-1));

        contextBuilder.addMethods(AllocationStack.StackFrame.newBuilder()
                                    .setMethodId(frameId)
                                    .setClassName(ProfilersTestData.CONTEXT_CLASS_NAMES.get(contextIndex))
                                    .setMethodName(ProfilersTestData.CONTEXT_METHOD_NAMES.get(contextIndex)));
      }
      stackBuilder.setEncodedStack(frameBuilder);
      contextBuilder.addEncodedStacks(stackBuilder);
      contextBuilder.setTimestamp(timestamp);

      contexts.add(contextBuilder.build());
    }

    return contexts;
  }

  /**
   * Generates a list of allocation events based on the input end time.
   * For every second, an allocation event is created and uniquely tagged with the timestamp (in seconds). The event generation starts
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
  public static List<BatchAllocationEvents> generateMemoryAllocEvents(long startTimeNs, long endTimeNs) {
    List<BatchAllocationEvents> events = new ArrayList<>();
    for (long timestamp = Math.max(0, startTimeNs); timestamp < endTimeNs; timestamp += FakeTimer.ONE_SECOND_IN_NS) {
      Memory.BatchAllocationEvents.Builder sampleBuilder = Memory.BatchAllocationEvents.newBuilder();

      // Convert timestamp to object tag.
      int tag = (int)(timestamp / FakeTimer.ONE_SECOND_IN_NS);
      // Context ids are 1-based.
      int contextId = tag % ProfilersTestData.ALLOC_CONTEXT_NUM + 1;

      AllocationEvent event = AllocationEvent.newBuilder().setAllocData(AllocationEvent.Allocation.newBuilder()
                                                                          .setTag(tag).setSize(ProfilersTestData.ALLOC_SIZE)
                                                                          .setClassTag(contextId)
                                                                          .setStackId(contextId)
                                                                          .setHeapId(DEFAULT_HEAP_ID))
        .setTimestamp(timestamp).build();
      sampleBuilder.addEvents(event);

      boolean shouldAddDeallocation = timestamp - ProfilersTestData.ALLOC_EVENT_DURATION_NS >= 0;
      if (shouldAddDeallocation) {
        tag = (int)((timestamp - ProfilersTestData.ALLOC_EVENT_DURATION_NS) / FakeTimer.ONE_SECOND_IN_NS);
        event = Memory.AllocationEvent.newBuilder()
          .setFreeData(Memory.AllocationEvent.Deallocation.newBuilder().setTag(tag))
          .setTimestamp(timestamp)
          .build();
        sampleBuilder.addEvents(event);
      }

      sampleBuilder.setTimestamp(timestamp);

      events.add(sampleBuilder.build());
    }

    return events;
  }

  /**
   * Generates a list of jni ref events that match the cadence of the allocation and deallocation events.
   */
  public static List<BatchJNIGlobalRefEvent> generateMemoryJniRefEvents(long startTimeNs, long endTimeNs) {
    List<BatchJNIGlobalRefEvent> events = new ArrayList<>();
    for (long timestamp = Math.max(0, startTimeNs); timestamp < endTimeNs; timestamp += FakeTimer.ONE_SECOND_IN_NS) {
      BatchJNIGlobalRefEvent.Builder sampleBuilder = Memory.BatchJNIGlobalRefEvent.newBuilder();

      // Convert timestamp to object tag.
      int tag = (int)(timestamp / FakeTimer.ONE_SECOND_IN_NS);

      // A global ref creation event that matches the allocation time of the object.
      JNIGlobalReferenceEvent.Builder createEvent = JNIGlobalReferenceEvent.newBuilder()
        .setEventType(Memory.JNIGlobalReferenceEvent.Type.CREATE_GLOBAL_REF)
        .setObjectTag(tag)
        .setRefValue(tag + ProfilersTestData.JNI_REF_BASE)
        .setTimestamp(timestamp)
        .setBacktrace(createBacktrace(tag));
      sampleBuilder.addEvents(createEvent);

      boolean shouldAddDeallocation = timestamp - ProfilersTestData.ALLOC_EVENT_DURATION_NS >= 0;
      if (shouldAddDeallocation) {
        tag = (int)((timestamp - ProfilersTestData.ALLOC_EVENT_DURATION_NS) / FakeTimer.ONE_SECOND_IN_NS);

        Memory.JNIGlobalReferenceEvent.Builder deleteEvent = Memory.JNIGlobalReferenceEvent.newBuilder()
          .setEventType(Memory.JNIGlobalReferenceEvent.Type.DELETE_GLOBAL_REF)
          .setObjectTag(tag)
          .setRefValue(tag + ProfilersTestData.JNI_REF_BASE)
          .setTimestamp(timestamp)
          .setBacktrace(createBacktrace(tag));
        sampleBuilder.addEvents(deleteEvent);
      }

      sampleBuilder.setTimestamp(timestamp);

      events.add(sampleBuilder.build());
    }

    return events;
  }

  private static Memory.NativeBacktrace createBacktrace(int objTag) {
    Memory.NativeBacktrace.Builder result = Memory.NativeBacktrace.newBuilder();
    result.addAddresses(NATIVE_ADDRESSES_BASE + objTag);
    result.addAddresses(NATIVE_ADDRESSES_BASE + objTag + 1);

    // Add an extra address representing a system module to check that such frames are ignored.
    result.addAddresses(SYSTEM_NATIVE_ADDRESSES_BASE);
    return result.build();
  }

  @NotNull
  public static Common.Event.Builder generateCpuThreadEvent(long timestampSeconds, int tid, String name, Cpu.CpuThreadData.State state) {
    return Common.Event.newBuilder()
      .setPid(SESSION_DATA.getPid())
      .setTimestamp(SECONDS.toNanos(timestampSeconds))
      .setKind(Common.Event.Kind.CPU_THREAD)
      .setGroupId(tid)
      .setIsEnded(state == Cpu.CpuThreadData.State.DEAD)
      .setCpuThread(Cpu.CpuThreadData.newBuilder().setTid(tid).setName(name).setState(state));
  }

  public static void populateThreadData(@NotNull FakeTransportService service, long streamId) {
    service.addEventToStream(streamId,
                             ProfilersTestData.generateCpuThreadEvent(1, 1, "Thread 1", Cpu.CpuThreadData.State.RUNNING)
                               .build());
    service.addEventToStream(streamId,
                             ProfilersTestData.generateCpuThreadEvent(8, 1, "Thread 1", Cpu.CpuThreadData.State.DEAD)
                               .build());
    service.addEventToStream(streamId,
                             ProfilersTestData.generateCpuThreadEvent(6, 2, "Thread 2", Cpu.CpuThreadData.State.RUNNING)
                               .build());
    service.addEventToStream(streamId,
                             ProfilersTestData.generateCpuThreadEvent(8, 2, "Thread 2", Cpu.CpuThreadData.State.STOPPED)
                               .build());
    service.addEventToStream(streamId,
                             ProfilersTestData.generateCpuThreadEvent(10, 2, "Thread 2", Cpu.CpuThreadData.State.SLEEPING)
                               .build());
    service.addEventToStream(streamId,
                             ProfilersTestData.generateCpuThreadEvent(12, 2, "Thread 2", Cpu.CpuThreadData.State.WAITING)
                               .build());
    service.addEventToStream(streamId,
                             ProfilersTestData.generateCpuThreadEvent(15, 2, "Thread 2", Cpu.CpuThreadData.State.DEAD)
                               .build());
  }

  // W = Wake lock, J = Job
  // t: 100--150--200--250--300--350--400--450--500
  //     |    |    |    |    |    |    |    |    |
  // 1:  W=========]
  // 2:       J==============]
  // 3:          W======]
  // 4:                           J=========]
  // 5:                                J=========]
  // 6:                                   W====]
  public static List<Common.Event> generateEnergyEvents(int pid) {
    return Arrays.asList(
      Common.Event.newBuilder()
        .setPid(pid)
        .setGroupId(1)
        .setTimestamp(SECONDS.toNanos(100))
        .setKind(Common.Event.Kind.ENERGY_EVENT)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setWakeLockAcquired(Energy.WakeLockAcquired.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setPid(pid)
        .setGroupId(2)
        .setTimestamp(SECONDS.toNanos(150))
        .setKind(Common.Event.Kind.ENERGY_EVENT)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobStarted(Energy.JobStarted.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setPid(pid)
        .setGroupId(3)
        .setTimestamp(SECONDS.toNanos(170))
        .setKind(Common.Event.Kind.ENERGY_EVENT)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setWakeLockAcquired(Energy.WakeLockAcquired.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setPid(pid)
        .setGroupId(1)
        .setTimestamp(SECONDS.toNanos(200))
        .setKind(Common.Event.Kind.ENERGY_EVENT)
        .setIsEnded(true)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setWakeLockReleased(Energy.WakeLockReleased.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setPid(pid)
        .setGroupId(3)
        .setTimestamp(SECONDS.toNanos(250))
        .setKind(Common.Event.Kind.ENERGY_EVENT)
        .setIsEnded(true)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setWakeLockReleased(Energy.WakeLockReleased.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setPid(pid)
        .setGroupId(2)
        .setTimestamp(SECONDS.toNanos(300))
        .setKind(Common.Event.Kind.ENERGY_EVENT)
        .setIsEnded(true)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobFinished(Energy.JobFinished.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setPid(pid)
        .setGroupId(4)
        .setTimestamp(SECONDS.toNanos(350))
        .setKind(Common.Event.Kind.ENERGY_EVENT)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobStarted(Energy.JobStarted.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setPid(pid)
        .setGroupId(5)
        .setTimestamp(SECONDS.toNanos(400))
        .setKind(Common.Event.Kind.ENERGY_EVENT)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobStarted(Energy.JobStarted.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setPid(pid)
        .setGroupId(6)
        .setTimestamp(SECONDS.toNanos(420))
        .setKind(Common.Event.Kind.ENERGY_EVENT)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setWakeLockAcquired(Energy.WakeLockAcquired.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setPid(pid)
        .setGroupId(4)
        .setTimestamp(SECONDS.toNanos(450))
        .setKind(Common.Event.Kind.ENERGY_EVENT)
        .setIsEnded(true)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobFinished(Energy.JobFinished.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setPid(pid)
        .setGroupId(6)
        .setTimestamp(SECONDS.toNanos(480))
        .setKind(Common.Event.Kind.ENERGY_EVENT)
        .setIsEnded(true)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setWakeLockReleased(Energy.WakeLockReleased.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setPid(pid)
        .setGroupId(5)
        .setTimestamp(SECONDS.toNanos(500))
        .setKind(Common.Event.Kind.ENERGY_EVENT)
        .setIsEnded(true)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobFinished(Energy.JobFinished.getDefaultInstance()))
        .build()
    );
  }
}