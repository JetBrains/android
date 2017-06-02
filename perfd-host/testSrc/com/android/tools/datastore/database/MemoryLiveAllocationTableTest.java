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

import com.android.tools.datastore.DataStoreDatabase;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationEvent;
import com.android.tools.profiler.proto.MemoryProfiler.BatchAllocationSample;
import com.android.tools.profiler.proto.MemoryProfiler.DecodedStack;
import com.intellij.openapi.util.io.FileUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static com.android.tools.datastore.database.MemoryStatsTable.NO_STACK_ID;
import static org.junit.Assert.assertEquals;

public class MemoryLiveAllocationTableTest {
  private static final int VALID_PID = 1;
  private static final int INVALID_PID = -1;
  private static final Common.Session VALID_SESSION = Common.Session.newBuilder()
    .setBootId("BOOT")
    .setDeviceSerial("SERIAL")
    .build();

  private static final Common.Session INVALID_SESSION = Common.Session.newBuilder()
    .setBootId("INVALID_BOOT")
    .setDeviceSerial("INVALID_SERIAL")
    .build();

  private File myDbFile;
  private MemoryLiveAllocationTable myAllocationTable;
  private DataStoreDatabase myDatabase;

  @Before
  public void setUp() throws Exception {
    HashMap<Common.Session, Long> sessionLookup = new HashMap<>();
    sessionLookup.put(VALID_SESSION, 1L);
    myDbFile = FileUtil.createTempFile("MemoryStatsTable", "mysql");
    myDatabase = new DataStoreDatabase(myDbFile.getAbsolutePath(), DataStoreDatabase.Characteristic.PERFORMANT);
    myAllocationTable = new MemoryLiveAllocationTable(sessionLookup);
    myAllocationTable.initialize(myDatabase.getConnection());
  }

  @After
  public void tearDown() throws Exception {
    myDatabase.disconnect();
    //noinspection ResultOfMethodCallIgnored
    myDbFile.delete();
  }

  @Test
  public void testInsertAndQueryAllocationData() throws Exception {
    // Handcraft a BatchAllocationSample and test that querying snapshot returns the correct result.
    final int KLASS1_TAG = 1;
    final int KLASS2_TAG = 2;
    final int KLASS1_INSTANCE1_TAG = 100;
    final int KLASS2_INSTANCE1_TAG = 101;
    final String JNI_KLASS1_NAME = "Ljava/lang/Klass1;";
    final String JNI_KLASS2_NAME = "[[Ljava/lang/Klass2;";
    final String JAVA_KLASS1_NAME = "java.lang.Klass1";
    final String JAVA_KLASS2_NAME = "java.lang.Klass2[][]";
    final int STACK_ID = 10;

    // A class that is loaded since the beginning (t = 0, tag = 1)
    AllocationEvent klass1 =
      AllocationEvent.newBuilder().setClassData(AllocationEvent.Klass.newBuilder().setName(JNI_KLASS1_NAME).setTag(KLASS1_TAG))
        .setTimestamp(0).build();
    // A class that is loaded at t=5 (tag = 2)
    AllocationEvent klass2 =
      AllocationEvent.newBuilder().setClassData(AllocationEvent.Klass.newBuilder().setName(JNI_KLASS2_NAME).setTag(KLASS2_TAG))
        .setTimestamp(5).build();
    // A klass1 instance allocation event (t = 0, tag = 100)
    AllocationEvent alloc1 = AllocationEvent.newBuilder()
      .setAllocData(AllocationEvent.Allocation.newBuilder().setTag(KLASS1_INSTANCE1_TAG).setClassTag(KLASS1_TAG).addMethodIds(STACK_ID))
      .setTimestamp(0).build();
    // A klass1 instance deallocation event (t = 7, tag = 100)
    AllocationEvent dealloc1 = AllocationEvent.newBuilder()
      .setFreeData(AllocationEvent.Deallocation.newBuilder().setTag(KLASS1_INSTANCE1_TAG)).setTimestamp(7).build();
    // A klass2 instance allocation event (t = 6, tag = 101)
    AllocationEvent alloc2 = AllocationEvent.newBuilder()
      .setAllocData(AllocationEvent.Allocation.newBuilder().setTag(KLASS2_INSTANCE1_TAG).setClassTag(KLASS2_TAG)).setTimestamp(6).build();

    BatchAllocationSample insertSample = BatchAllocationSample.newBuilder()
      .addEvents(klass1)
      .addEvents(klass2)
      .addEvents(klass2)  // Test that insert dupes will be ignored
      .addEvents(alloc1)
      .addEvents(dealloc1)
      .addEvents(alloc2).build();
    myAllocationTable.insertAllocationData(VALID_PID, VALID_SESSION, insertSample);

    AllocationEvent expectedAlloc1 = AllocationEvent.newBuilder()
      .setAllocData(AllocationEvent.Allocation.newBuilder().setClassTag(KLASS1_TAG).setTag(KLASS1_INSTANCE1_TAG).addMethodIds(STACK_ID))
      .setTimestamp(0).build();
    AllocationEvent expectedDealloc1 = AllocationEvent.newBuilder()
      .setFreeData(AllocationEvent.Deallocation.newBuilder().setClassTag(KLASS1_TAG).setTag(KLASS1_INSTANCE1_TAG).build())
      .setTimestamp(7).build();
    // Ongoing allocation events would have their FreeTime set to Long.MAX_VALUE.
    AllocationEvent expectedAlloc2 = AllocationEvent.newBuilder()
      .setAllocData(AllocationEvent.Allocation.newBuilder().setTag(KLASS2_INSTANCE1_TAG).setClassTag(KLASS2_TAG).addMethodIds(NO_STACK_ID))
      .setTimestamp(6).build();
    AllocationEvent expectedKlass1 = AllocationEvent.newBuilder()
      .setClassData(AllocationEvent.Klass.newBuilder().setName(JAVA_KLASS1_NAME).setTag(KLASS1_TAG)).setTimestamp(0).build();
    // A class that is loaded at t=5 (tag = 2)
    AllocationEvent expectedKlass2 = AllocationEvent.newBuilder()
      .setClassData(AllocationEvent.Klass.newBuilder().setName(JAVA_KLASS2_NAME).setTag(KLASS2_TAG)).setTimestamp(5).build();

    BatchAllocationSample querySample =
      myAllocationTable.getAllocationContexts(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(2, querySample.getEventsCount());
    assertEquals(expectedKlass1, querySample.getEvents(0));
    assertEquals(expectedKlass2, querySample.getEvents(1));
    assertEquals(expectedKlass2.getTimestamp(), querySample.getTimestamp());

    // A query that asks for live objects.
    querySample = myAllocationTable.getAllocations(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(3, querySample.getEventsCount());
    assertEquals(expectedAlloc1, querySample.getEvents(0));
    assertEquals(expectedAlloc2, querySample.getEvents(1));
    assertEquals(expectedDealloc1, querySample.getEvents(2));
    assertEquals(expectedDealloc1.getTimestamp(), querySample.getTimestamp());

    // A query that asks for live objects between t=0 and t=7
    querySample = myAllocationTable.getAllocations(VALID_PID, VALID_SESSION, 0, 7);
    // .... should returns class data + both class instances
    assertEquals(2, querySample.getEventsCount());
    assertEquals(expectedAlloc1, querySample.getEvents(0));
    assertEquals(expectedAlloc2, querySample.getEvents(1));
    assertEquals(expectedAlloc2.getTimestamp(), querySample.getTimestamp());

    // A query that asks for live objects between t=7 and t=MAX_VALUE
    querySample = myAllocationTable.getAllocations(VALID_PID, VALID_SESSION, 7, Long.MAX_VALUE);
    // .... should return only the free event
    assertEquals(1, querySample.getEventsCount());
    assertEquals(expectedDealloc1, querySample.getEvents(0));
    assertEquals(expectedDealloc1.getTimestamp(), querySample.getTimestamp());
  }

  @Test
  public void testInsertAndQueryMethodInfo() throws Exception {
    List<MemoryProfiler.StackMethod> methodsToInsert = new ArrayList<>();
    MemoryProfiler.StackMethod
      method1 = MemoryProfiler.StackMethod.newBuilder().setMethodId(1).setMethodName("Method1").setClassName("Class1").build();
    MemoryProfiler.StackMethod
      method2 = MemoryProfiler.StackMethod.newBuilder().setMethodId(2).setMethodName("Method2").setClassName("Class2").build();
    methodsToInsert.add(method1);
    methodsToInsert.add(method2);

    myAllocationTable.insertMethodInfo(VALID_PID, VALID_SESSION, methodsToInsert);

    // Valid cases
    assertEquals(method1, myAllocationTable.queryMethodInfo(VALID_PID, VALID_SESSION, method1.getMethodId()));
    assertEquals(method2, myAllocationTable.queryMethodInfo(VALID_PID, VALID_SESSION, method2.getMethodId()));

    // Non-existent methods / invalid pid
    assertEquals(MemoryProfiler.StackMethod.getDefaultInstance(),
                 myAllocationTable.queryMethodInfo(INVALID_PID, VALID_SESSION, method1.getMethodId()));
    assertEquals(MemoryProfiler.StackMethod.getDefaultInstance(),
                 myAllocationTable.queryMethodInfo(VALID_PID, INVALID_SESSION, method2.getMethodId()));
    assertEquals(MemoryProfiler.StackMethod.getDefaultInstance(), myAllocationTable.queryMethodInfo(VALID_PID, VALID_SESSION, 3));
  }

  @Test
  public void testInsertAndQueryStackInfo() throws Exception {
    final long METHOD1 = 10;
    final long METHOD2 = 11;
    final long METHOD3 = 12;
    final int STACK1 = 1;
    final int STACK2 = 2;
    final int EMPTY_STACK = 3;
    final List<Long> STACK_METHODS1 = Arrays.asList(METHOD1, METHOD2);
    final List<Long> STACK_METHODS2 = Arrays.asList(METHOD2, METHOD3);

    List<MemoryProfiler.StackMethod> methodsToInsert = new ArrayList<>();
    MemoryProfiler.StackMethod
      method1 = MemoryProfiler.StackMethod.newBuilder().setMethodId(METHOD1).setMethodName("Method1").setClassName("Class1").build();
    MemoryProfiler.StackMethod
      method2 = MemoryProfiler.StackMethod.newBuilder().setMethodId(METHOD2).setMethodName("Method2").setClassName("Class2").build();
    MemoryProfiler.StackMethod
      method3 = MemoryProfiler.StackMethod.newBuilder().setMethodId(METHOD3).setMethodName("Method3").setClassName("Class3").build();
    methodsToInsert.add(method1);
    methodsToInsert.add(method2);
    methodsToInsert.add(method3);

    List<MemoryProfiler.EncodedStack> stacksToInsert = new ArrayList<>();
    MemoryProfiler.EncodedStack stack1 =
      MemoryProfiler.EncodedStack.newBuilder().setStackId(STACK1).addAllMethodIds(STACK_METHODS1).build();
    MemoryProfiler.EncodedStack stack2 =
      MemoryProfiler.EncodedStack.newBuilder().setStackId(STACK2).addAllMethodIds(STACK_METHODS2).build();
    stacksToInsert.add(stack1);
    stacksToInsert.add(stack2);

    myAllocationTable.insertMethodInfo(VALID_PID, VALID_SESSION, methodsToInsert);
    myAllocationTable.insertStackInfo(VALID_PID, VALID_SESSION, stacksToInsert);

    // Valid cases
    DecodedStack decodedStack = myAllocationTable.queryStackInfo(VALID_PID, VALID_SESSION, STACK1);
    assertEquals(STACK1, decodedStack.getStackId());
    assertEquals(2, decodedStack.getMethodsCount());
    assertEquals(method1, decodedStack.getMethods(0));
    assertEquals(method2, decodedStack.getMethods(1));
    decodedStack = myAllocationTable.queryStackInfo(VALID_PID, VALID_SESSION, STACK2);
    assertEquals(STACK2, decodedStack.getStackId());
    assertEquals(2, decodedStack.getMethodsCount());
    assertEquals(method2, decodedStack.getMethods(0));
    assertEquals(method3, decodedStack.getMethods(1));

    // Invalid ids
    assertEquals(DecodedStack.newBuilder().setStackId(STACK1).build(),
                 myAllocationTable.queryStackInfo(INVALID_PID, VALID_SESSION, STACK1));
    assertEquals(DecodedStack.newBuilder().setStackId(STACK2).build(),
                 myAllocationTable.queryStackInfo(VALID_PID, INVALID_SESSION, STACK2));
    assertEquals(DecodedStack.newBuilder().setStackId(EMPTY_STACK).build(), myAllocationTable
      .queryStackInfo(VALID_PID, VALID_SESSION, EMPTY_STACK));

    // TODO check for invalid id cases...
  }

  @Test
  public void testPruningAllocationData() throws Exception {
    // Handcraft a BatchAllocationSample and test that querying snapshot returns the correct result.
    final int KLASS1_TAG = 1;
    final int KLASS2_TAG = 2;
    final int KLASS1_INSTANCE1_TAG = 100;
    final int KLASS2_INSTANCE1_TAG = 101;
    final int KLASS1_INSTANCE2_TAG = 102;
    final int KLASS2_INSTANCE2_TAG = 103;
    final String JNI_KLASS1_NAME = "Ljava/lang/Klass1;";
    final String JNI_KLASS2_NAME = "[[Ljava/lang/Klass2;";
    final String JAVA_KLASS1_NAME = "java.lang.Klass1";
    final String JAVA_KLASS2_NAME = "java.lang.Klass2[][]";

    myAllocationTable.setAllocationCountLimit(2);

    BatchAllocationSample querySample = myAllocationTable.getAllocationContexts(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(0, querySample.getEventsCount());

    AllocationEvent expectedKlass1 = AllocationEvent.newBuilder()
      .setClassData(AllocationEvent.Klass.newBuilder().setName(JAVA_KLASS1_NAME).setTag(KLASS1_TAG)).setTimestamp(0).build();
    // A class that is loaded at t=5 (tag = 2)
    AllocationEvent expectedKlass2 = AllocationEvent.newBuilder()
      .setClassData(AllocationEvent.Klass.newBuilder().setName(JAVA_KLASS2_NAME).setTag(KLASS2_TAG)).setTimestamp(1).build();

    // A class that is loaded since the beginning (t = 0, tag = 1)
    AllocationEvent klass1 = AllocationEvent.newBuilder()
      .setClassData(AllocationEvent.Klass.newBuilder().setName(JNI_KLASS1_NAME).setTag(KLASS1_TAG)).setTimestamp(0).build();
    BatchAllocationSample insertSample = BatchAllocationSample.newBuilder().addEvents(klass1).build();
    myAllocationTable.insertAllocationData(VALID_PID, VALID_SESSION, insertSample);
    querySample = myAllocationTable.getAllocationContexts(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(1, querySample.getEventsCount());
    assertEquals(expectedKlass1, querySample.getEvents(0));

    // A klass1 instance allocation event (t = 0, tag = 100)
    AllocationEvent alloc1 = AllocationEvent.newBuilder()
      .setAllocData(AllocationEvent.Allocation.newBuilder().setTag(KLASS1_INSTANCE1_TAG).setClassTag(KLASS1_TAG)).setTimestamp(0).build();
    insertSample = BatchAllocationSample.newBuilder().addEvents(alloc1).build();
    myAllocationTable.insertAllocationData(VALID_PID, VALID_SESSION, insertSample);
    querySample = myAllocationTable.getAllocationContexts(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(1, querySample.getEventsCount());
    assertEquals(expectedKlass1, querySample.getEvents(0));
    querySample = myAllocationTable.getAllocations(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(1, querySample.getEventsCount());
    AllocationEvent expectedAlloc1 = alloc1.toBuilder().setAllocData(alloc1.getAllocData().toBuilder().addMethodIds(-1)).build();
    assertEquals(expectedAlloc1, querySample.getEvents(0));

    // A class that is loaded at t=5 (tag = 2)
    AllocationEvent klass2 = AllocationEvent.newBuilder()
      .setClassData(AllocationEvent.Klass.newBuilder().setName(JNI_KLASS2_NAME).setTag(KLASS2_TAG)).setTimestamp(1).build();
    insertSample = BatchAllocationSample.newBuilder().addEvents(klass2).build();
    myAllocationTable.insertAllocationData(VALID_PID, VALID_SESSION, insertSample);
    querySample = myAllocationTable.getAllocationContexts(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(2, querySample.getEventsCount());
    assertEquals(expectedKlass1, querySample.getEvents(0));
    assertEquals(expectedKlass2, querySample.getEvents(1));
    querySample = myAllocationTable.getAllocations(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(1, querySample.getEventsCount());
    assertEquals(expectedAlloc1, querySample.getEvents(0));

    // A klass2 instance allocation event (t = 2, tag = 101)
    AllocationEvent alloc2 = AllocationEvent.newBuilder()
      .setAllocData(AllocationEvent.Allocation.newBuilder().setTag(KLASS2_INSTANCE1_TAG).setClassTag(KLASS2_TAG)).setTimestamp(2).build();
    insertSample = BatchAllocationSample.newBuilder().addEvents(alloc2).build();
    myAllocationTable.insertAllocationData(VALID_PID, VALID_SESSION, insertSample);
    querySample = myAllocationTable.getAllocationContexts(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(2, querySample.getEventsCount());
    assertEquals(expectedKlass1, querySample.getEvents(0));
    assertEquals(expectedKlass2, querySample.getEvents(1));
    querySample = myAllocationTable.getAllocations(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(2, querySample.getEventsCount());
    assertEquals(expectedAlloc1, querySample.getEvents(0));
    AllocationEvent expectedAlloc2 = alloc2.toBuilder().setAllocData(alloc2.getAllocData().toBuilder().addMethodIds(-1)).build();
    assertEquals(expectedAlloc2, querySample.getEvents(1));

    // A klass1 instance allocation event (t = 3, tag = 102)
    AllocationEvent alloc3 = AllocationEvent.newBuilder()
      .setAllocData(AllocationEvent.Allocation.newBuilder().setTag(KLASS1_INSTANCE2_TAG).setClassTag(KLASS1_TAG)).setTimestamp(3).build();
    insertSample = BatchAllocationSample.newBuilder().addEvents(alloc3).build();
    myAllocationTable.insertAllocationData(VALID_PID, VALID_SESSION, insertSample);
    querySample = myAllocationTable.getAllocationContexts(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(2, querySample.getEventsCount());
    assertEquals(expectedKlass1, querySample.getEvents(0));
    assertEquals(expectedKlass2, querySample.getEvents(1));
    querySample = myAllocationTable.getAllocations(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(3, querySample.getEventsCount());
    assertEquals(expectedAlloc1, querySample.getEvents(0));
    assertEquals(expectedAlloc2, querySample.getEvents(1));
    AllocationEvent expectedAlloc3 = alloc3.toBuilder().setAllocData(alloc3.getAllocData().toBuilder().addMethodIds(-1)).build();
    assertEquals(expectedAlloc3, querySample.getEvents(2));

    // A alloc1 instance deallocation event (t = 5, tag = 100)
    AllocationEvent dealloc1 = AllocationEvent.newBuilder()
      .setFreeData(AllocationEvent.Deallocation.newBuilder().setTag(KLASS1_INSTANCE1_TAG)).setTimestamp(5).build();
    insertSample = BatchAllocationSample.newBuilder().addEvents(dealloc1).build();
    myAllocationTable.insertAllocationData(VALID_PID, VALID_SESSION, insertSample);
    querySample = myAllocationTable.getAllocationContexts(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(2, querySample.getEventsCount());
    assertEquals(expectedKlass1, querySample.getEvents(0));
    assertEquals(expectedKlass2, querySample.getEvents(1));
    querySample = myAllocationTable.getAllocations(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(2, querySample.getEventsCount());
    assertEquals(expectedAlloc2, querySample.getEvents(0));
    assertEquals(expectedAlloc3, querySample.getEvents(1));

    // A alloc2 instance deallocation event (t = 6, tag = 101)
    AllocationEvent dealloc2 = AllocationEvent.newBuilder()
      .setFreeData(AllocationEvent.Deallocation.newBuilder().setTag(KLASS2_INSTANCE1_TAG)).setTimestamp(6).build();
    insertSample = BatchAllocationSample.newBuilder().addEvents(dealloc2).build();
    myAllocationTable.insertAllocationData(VALID_PID, VALID_SESSION, insertSample);
    querySample = myAllocationTable.getAllocationContexts(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(2, querySample.getEventsCount());
    assertEquals(expectedKlass1, querySample.getEvents(0));
    assertEquals(expectedKlass2, querySample.getEvents(1));
    querySample = myAllocationTable.getAllocations(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(3, querySample.getEventsCount());
    assertEquals(expectedAlloc2, querySample.getEvents(0));
    assertEquals(expectedAlloc3, querySample.getEvents(1));
    assertEquals(dealloc2.toBuilder().setFreeData(dealloc2.getFreeData().toBuilder().setClassTag(KLASS2_TAG)).build(),
                 querySample.getEvents(2));

    // A klass2 instance allocation event (t = 2, tag = 103)
    AllocationEvent alloc4 = AllocationEvent.newBuilder()
      .setAllocData(AllocationEvent.Allocation.newBuilder().setTag(KLASS2_INSTANCE2_TAG).setClassTag(KLASS2_TAG)).setTimestamp(7).build();
    insertSample = BatchAllocationSample.newBuilder().addEvents(alloc4).build();
    myAllocationTable.insertAllocationData(VALID_PID, VALID_SESSION, insertSample);
    querySample = myAllocationTable.getAllocationContexts(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(2, querySample.getEventsCount());
    assertEquals(expectedKlass1, querySample.getEvents(0));
    assertEquals(expectedKlass2, querySample.getEvents(1));
    querySample = myAllocationTable.getAllocations(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(2, querySample.getEventsCount());
    assertEquals(expectedAlloc3, querySample.getEvents(0));
    assertEquals(alloc4.toBuilder().setAllocData(alloc4.getAllocData().toBuilder().addMethodIds(-1)).build(), querySample.getEvents(1));
  }
}
