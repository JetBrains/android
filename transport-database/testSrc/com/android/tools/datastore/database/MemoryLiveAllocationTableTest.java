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
package com.android.tools.datastore.database;

import static com.android.tools.profiler.proto.Memory.JNIGlobalReferenceEvent.Type.CREATE_GLOBAL_REF;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.datastore.FakeLogService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory.AllocatedClass;
import com.android.tools.profiler.proto.Memory.AllocationEvent;
import com.android.tools.profiler.proto.Memory.BatchAllocationContexts;
import com.android.tools.profiler.proto.Memory.BatchAllocationEvents;
import com.android.tools.profiler.proto.Memory.BatchJNIGlobalRefEvent;
import com.android.tools.profiler.proto.Memory.JNIGlobalReferenceEvent;
import com.android.tools.profiler.proto.Memory.MemoryMap;
import com.android.tools.profiler.proto.Memory.NativeBacktrace;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationSamplingRateEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class MemoryLiveAllocationTableTest extends DatabaseTest<MemoryLiveAllocationTable> {
  private static final Common.Session VALID_SESSION = Common.Session.newBuilder().setSessionId(1L).setStreamId(1234).setPid(1).build();
  private static final Common.Session INVALID_SESSION = Common.Session.newBuilder().setSessionId(-1L).setStreamId(4321).setPid(-1).build();

  // Live allocation test data
  private static final int HEAP0 = 0;
  private static final int HEAP1 = 1;
  private static final int STACK1 = 1;
  private static final int THREAD1 = 100;
  private static final int THREAD2 = 101;
  private static final int THREAD3 = 103;
  private static final int CLASS1 = 1000;
  private static final int CLASS2 = 1001;
  private static final int KLASS1_INSTANCE1_TAG = 1002;
  private static final int KLASS1_INSTANCE2_TAG = 1003;
  private static final int KLASS2_INSTANCE1_TAG = 1004;
  private static final String JNI_KLASS1_NAME = "Ljava/lang/Klass1;";
  private static final String JNI_KLASS2_NAME = "[[Ljava/lang/Klass2;";
  private static final String JAVA_KLASS1_NAME = "java.lang.Klass1";
  private static final String JAVA_KLASS2_NAME = "java.lang.Klass2[][]";
  private static final long NATIVE_ADDRESS1 = 1300;
  private static final long NATIVE_ADDRESS2 = 2300;
  private static final long NATIVE_ADDRESS3 = 3300;
  private static final long NATIVE_ADDRESS4 = 4300;
  private static final long NATIVE_LIB_OFFSET = 100;
  private static final String NATIVE_LIB1 = "/path/to/native/lib1.so";
  private static final String NATIVE_LIB2 = "/path/to/native/lib2.so";
  private static final String NATIVE_LIB3 = "/path/to/native/lib3.so";
  private static final long JNI_REF_VALUE1 = 2001;
  private static final long JNI_REF_VALUE2 = 2002;

  @Override
  @NotNull
  protected List<Consumer<MemoryLiveAllocationTable>> getTableQueryMethodsForVerification() {
    List<Consumer<MemoryLiveAllocationTable>> methodCalls = new ArrayList<>();
    Common.Session session = Common.Session.getDefaultInstance();
    methodCalls.add((table) -> {
      BatchAllocationEvents batch = BatchAllocationEvents.newBuilder().addEvents(
        AllocationEvent.newBuilder().setAllocData(AllocationEvent.Allocation.newBuilder().setTag(1).build())).build();
      table.insertAllocationEvents(session, batch);
    });
    methodCalls.add((table) -> {
      BatchAllocationContexts batch = BatchAllocationContexts.newBuilder().addClasses(
        AllocatedClass.newBuilder().setClassId(1).build()).build();
      table.insertAllocationContexts(session, batch);
    });
    methodCalls.add((table) -> {
      BatchJNIGlobalRefEvent batch =
        BatchJNIGlobalRefEvent.newBuilder().addEvents(JNIGlobalReferenceEvent.newBuilder().setEventType(CREATE_GLOBAL_REF)).build();
      table.insertJniReferenceData(session, batch);
    });
    methodCalls.add((table) -> table.getAllocationContexts(session, 0, 0));
    methodCalls.add((table) -> table.getAllocationEvents(session, 0, 0));
    methodCalls.add((table) -> table.getJniReferenceEvents(session, 0, 0));
    methodCalls.add((table) -> table.insertOrReplaceAllocationSamplingRateEvent(session, AllocationSamplingRateEvent.getDefaultInstance()));
    methodCalls.add((table) -> table.getAllocationSamplingRateEvents(session.getSessionId(), 0, 0));
    return methodCalls;
  }

  @Override
  @NotNull
  protected MemoryLiveAllocationTable createTable() {
    return new MemoryLiveAllocationTable(new FakeLogService());
  }

  @Test
  public void testIgnoreDuplicatedAllocationEvents() {
    AllocationEvent alloc1 = AllocationEvent
      .newBuilder()
      .setAllocData(AllocationEvent.Allocation.newBuilder().setTag(KLASS1_INSTANCE1_TAG).setClassTag(CLASS1))
      .setTimestamp(0)
      .build();
    AllocationEvent dupAlloc1 = AllocationEvent
      .newBuilder()
      .setAllocData(AllocationEvent.Allocation.newBuilder().setTag(KLASS1_INSTANCE1_TAG).setClassTag(CLASS2))
      .setTimestamp(6)
      .build();

    BatchAllocationEvents sample1 = BatchAllocationEvents.newBuilder().setTimestamp(1).addEvents(alloc1).build();
    BatchAllocationEvents sample2 = BatchAllocationEvents.newBuilder().setTimestamp(1).addEvents(dupAlloc1).build();
    getTable().insertAllocationEvents(VALID_SESSION, sample1);
    getTable().insertAllocationEvents(VALID_SESSION, sample2);

    // A query that asks for live objects should return sample1 and considered sample2 duplicated.
    List<BatchAllocationEvents> querySample = getTable().getAllocationEvents(VALID_SESSION, 0, Long.MAX_VALUE);
    assertThat(querySample.size()).isEqualTo(1);
    assertThat(querySample.get(0)).isEqualTo(sample1);
  }

  NativeBacktrace createBacktrace(long... addresses) {
    NativeBacktrace.Builder result = NativeBacktrace.newBuilder();
    for (long address : addresses) {
      result.addAddresses(address);
    }
    return result.build();
  }


  private static MemoryMap createMemoryMap() {
    MemoryMap.Builder memMap = MemoryMap.newBuilder();
    memMap.addRegions(MemoryMap.MemoryRegion
                        .newBuilder()
                        .setStartAddress((NATIVE_ADDRESS1 - NATIVE_LIB_OFFSET))
                        .setEndAddress(NATIVE_ADDRESS1 + NATIVE_LIB_OFFSET)
                        .setName(NATIVE_LIB1));

    memMap.addRegions(MemoryMap.MemoryRegion
                        .newBuilder()
                        .setStartAddress((NATIVE_ADDRESS2 - NATIVE_LIB_OFFSET))
                        .setEndAddress(NATIVE_ADDRESS2 + NATIVE_LIB_OFFSET)
                        .setName(NATIVE_LIB2));

    memMap.addRegions(MemoryMap.MemoryRegion
                        .newBuilder()
                        .setStartAddress((NATIVE_ADDRESS3 - NATIVE_LIB_OFFSET))
                        .setEndAddress(NATIVE_ADDRESS3 + NATIVE_LIB_OFFSET)
                        .setName(NATIVE_LIB3));

    // NATIVE_ADDRESS4 intentionally left unmapped
    return memMap.build();
  }

  @Test
  public void testInsertAndQueryJniRefEvents() {
    // create jni ref for instance 1 at t=1
    JNIGlobalReferenceEvent alloc1 = JNIGlobalReferenceEvent
      .newBuilder()
      .setEventType(CREATE_GLOBAL_REF)
      .setObjectTag(KLASS1_INSTANCE1_TAG)
      .setRefValue(JNI_REF_VALUE1)
      .setThreadId(THREAD1)
      .setBacktrace(createBacktrace(NATIVE_ADDRESS1, NATIVE_ADDRESS2))
      .setTimestamp(1).build();

    // create jni ref for instance 2 at t=5
    JNIGlobalReferenceEvent alloc2 = JNIGlobalReferenceEvent
      .newBuilder()
      .setEventType(CREATE_GLOBAL_REF)
      .setObjectTag(KLASS1_INSTANCE2_TAG)
      .setRefValue(JNI_REF_VALUE2)
      .setThreadId(THREAD2)
      .setBacktrace(createBacktrace(NATIVE_ADDRESS3, NATIVE_ADDRESS4))
      .setTimestamp(5).build();

    // free jni ref for instance 1 at t=10
    JNIGlobalReferenceEvent dealloc1 = JNIGlobalReferenceEvent
      .newBuilder()
      .setEventType(JNIGlobalReferenceEvent.Type.DELETE_GLOBAL_REF)
      .setObjectTag(KLASS1_INSTANCE1_TAG)
      .setRefValue(JNI_REF_VALUE1)
      .setThreadId(THREAD3)
      .setBacktrace(createBacktrace(NATIVE_ADDRESS2, NATIVE_ADDRESS1))
      .setTimestamp(10).build();

    BatchJNIGlobalRefEvent.Builder insertBatch = BatchJNIGlobalRefEvent.newBuilder();
    insertBatch.setTimestamp(1).addEvents(alloc1).addEvents(alloc2).addEvents(dealloc1);
    getTable().insertJniReferenceData(VALID_SESSION, insertBatch.build());

    // Query all events
    List<BatchJNIGlobalRefEvent> queryBatch = getTable().getJniReferenceEvents(VALID_SESSION, 0, Long.MAX_VALUE);
    assertThat(queryBatch.size()).isEqualTo(1);
    assertThat(queryBatch.get(0)).isEqualTo(insertBatch.build());
    assertThat(queryBatch.get(0).getTimestamp()).isEqualTo(1);
  }

  @Test
  public void testInsertAndQueryAllocationEvents() {
    // A klass1 instance allocation event (t = 0)
    AllocationEvent alloc1 = AllocationEvent
      .newBuilder().setAllocData(
        AllocationEvent.Allocation
          .newBuilder().setTag(KLASS1_INSTANCE1_TAG).setClassTag(CLASS1).setThreadId(THREAD1).setStackId(STACK1).setHeapId(HEAP0))
      .setTimestamp(0).build();
    // A klass1 instance deallocation event (t = 7)
    AllocationEvent dealloc1 = AllocationEvent
      .newBuilder().setFreeData(
        AllocationEvent.Deallocation.newBuilder().setTag(KLASS1_INSTANCE1_TAG))
      .setTimestamp(7).build();
    // A klass2 instance allocation event (t = 6)
    AllocationEvent alloc2 = AllocationEvent
      .newBuilder().setAllocData(
        AllocationEvent.Allocation
          .newBuilder().setTag(KLASS2_INSTANCE1_TAG).setClassTag(CLASS2).setHeapId(HEAP1)).setTimestamp(6).build();

    BatchAllocationEvents insertSample = BatchAllocationEvents.newBuilder()
      .setTimestamp(1).addEvents(alloc1).addEvents(dealloc1).addEvents(alloc2).build();
    getTable().insertAllocationEvents(VALID_SESSION, insertSample);

    // A query that asks for live objects.
    List<BatchAllocationEvents> querySample = getTable().getAllocationEvents(VALID_SESSION, 0, Long.MAX_VALUE);
    assertThat(querySample.size()).isEqualTo(1);
    assertThat(querySample.get(0)).isEqualTo(insertSample);
  }

  @Test
  public void testIgnoreDuplicatedAllocationContext() {
    AllocatedClass class1 = AllocatedClass.newBuilder().setClassId(CLASS1).setClassName(JNI_KLASS1_NAME).build();
    AllocatedClass dupClass1 = AllocatedClass.newBuilder().setClassId(CLASS1).setClassName(JNI_KLASS2_NAME).build();
    BatchAllocationContexts context1 = BatchAllocationContexts.newBuilder().setTimestamp(1).addClasses(class1).build();
    BatchAllocationContexts dupContext1 = BatchAllocationContexts.newBuilder().setTimestamp(1).addClasses(dupClass1).build();
    AllocatedClass expectedKlass1 = class1.toBuilder().setClassName(JAVA_KLASS1_NAME).build();

    // Insert handcrafted data.
    getTable().insertAllocationContexts(VALID_SESSION, context1);
    getTable().insertAllocationContexts(VALID_SESSION, dupContext1);

    List<BatchAllocationContexts> contexts = getTable().getAllocationContexts(VALID_SESSION, 0, Long.MAX_VALUE);
    assertThat(contexts.size()).isEqualTo(1);
    assertThat(contexts.get(0)).isEqualTo(context1.toBuilder().setClasses(0, expectedKlass1).build());
  }

  @Test
  public void testAllocationContextQueriesAfterInsertion() {
    AllocatedClass class1 = AllocatedClass.newBuilder().setClassId(CLASS1).setClassName(JNI_KLASS1_NAME).build();
    AllocatedClass class2 = AllocatedClass.newBuilder().setClassId(CLASS1).setClassName(JNI_KLASS2_NAME).build();
    BatchAllocationContexts context1 = BatchAllocationContexts.newBuilder().setTimestamp(1).addClasses(class1).build();
    BatchAllocationContexts context2 = BatchAllocationContexts.newBuilder().setTimestamp(2).addClasses(class2).build();

    // Insert handcrafted data.
    getTable().insertAllocationContexts(VALID_SESSION, context1);
    getTable().insertAllocationContexts(VALID_SESSION, context2);

    AllocatedClass expectedKlass1 = class1.toBuilder().setClassName(JAVA_KLASS1_NAME).build();
    AllocatedClass expectedKlass2 = class2.toBuilder().setClassName(JAVA_KLASS2_NAME).build();

    List<BatchAllocationContexts> contexts = getTable().getAllocationContexts(VALID_SESSION, 0, 1);
    assertThat(contexts.size()).isEqualTo(1);
    assertThat(contexts.get(0)).isEqualTo(context1.toBuilder().setClasses(0, expectedKlass1).build());

    contexts = getTable().getAllocationContexts(VALID_SESSION, 1, 2);
    assertThat(contexts.size()).isEqualTo(1);
    assertThat(contexts.get(0)).isEqualTo(context2.toBuilder().setClasses(0, expectedKlass2).build());
  }

  @Test
  public void testJNIPrimitiveTypesConversion() {
    BatchAllocationContexts.Builder classesBuilder = BatchAllocationContexts.newBuilder().setTimestamp(1);
    AllocatedClass boolClass = AllocatedClass.newBuilder().setClassId(1).setClassName("Z").build();
    AllocatedClass byteClass = AllocatedClass.newBuilder().setClassId(2).setClassName("B").build();
    AllocatedClass charClass = AllocatedClass.newBuilder().setClassId(3).setClassName("C").build();
    AllocatedClass shortClass = AllocatedClass.newBuilder().setClassId(4).setClassName("S").build();
    AllocatedClass intClass = AllocatedClass.newBuilder().setClassId(5).setClassName("I").build();
    AllocatedClass longClass = AllocatedClass.newBuilder().setClassId(6).setClassName("J").build();
    AllocatedClass floatClass = AllocatedClass.newBuilder().setClassId(7).setClassName("F").build();
    AllocatedClass doubleClass = AllocatedClass.newBuilder().setClassId(8).setClassName("D").build();

    classesBuilder.addAllClasses(Arrays.asList(
      boolClass,
      byteClass,
      charClass,
      shortClass,
      intClass,
      longClass,
      floatClass,
      doubleClass
    ));
    getTable().insertAllocationContexts(VALID_SESSION, classesBuilder.build());

    List<BatchAllocationContexts> contexts = getTable().getAllocationContexts(VALID_SESSION, 0, 1);
    assertThat(contexts.size()).isEqualTo(1);

    List<AllocatedClass> convertedClasses = contexts.get(0).getClassesList();
    assertThat(convertedClasses.size()).isEqualTo(8);
    assertThat(convertedClasses.get(0)).isEqualTo(boolClass.toBuilder().setClassName("boolean").build());
    assertThat(convertedClasses.get(1)).isEqualTo(byteClass.toBuilder().setClassName("byte").build());
    assertThat(convertedClasses.get(2)).isEqualTo(charClass.toBuilder().setClassName("char").build());
    assertThat(convertedClasses.get(3)).isEqualTo(shortClass.toBuilder().setClassName("short").build());
    assertThat(convertedClasses.get(4)).isEqualTo(intClass.toBuilder().setClassName("int").build());
    assertThat(convertedClasses.get(5)).isEqualTo(longClass.toBuilder().setClassName("long").build());
    assertThat(convertedClasses.get(6)).isEqualTo(floatClass.toBuilder().setClassName("float").build());
    assertThat(convertedClasses.get(7)).isEqualTo(doubleClass.toBuilder().setClassName("double").build());
  }

  @Test
  public void testInsertAndGetAllocationSamplingRateEvents() {
    AllocationSamplingRateEvent oldSamplingRate = AllocationSamplingRateEvent.newBuilder().setTimestamp(2).build();
    AllocationSamplingRateEvent newSamplingRate = AllocationSamplingRateEvent.newBuilder().setTimestamp(3).build();

    getTable().insertOrReplaceAllocationSamplingRateEvent(VALID_SESSION, oldSamplingRate);
    getTable().insertOrReplaceAllocationSamplingRateEvent(VALID_SESSION, newSamplingRate);

    List<AllocationSamplingRateEvent> result = getTable().getAllocationSamplingRateEvents(VALID_SESSION.getSessionId(), 0, 1);
    assertThat(result.isEmpty()).isTrue();

    result = getTable().getAllocationSamplingRateEvents(VALID_SESSION.getSessionId(), 1, 2);
    assertThat(result).containsExactly(oldSamplingRate);

    result = getTable().getAllocationSamplingRateEvents(VALID_SESSION.getSessionId(), 1, 3);
    assertThat(result).containsExactly(oldSamplingRate, newSamplingRate).inOrder();

    result = getTable().getAllocationSamplingRateEvents(INVALID_SESSION.getSessionId(), 0, Long.MAX_VALUE);
    assertThat(result.isEmpty()).isTrue();
  }
}
