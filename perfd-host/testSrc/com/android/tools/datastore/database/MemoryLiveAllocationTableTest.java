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
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationStack.StackFrame;
import com.intellij.openapi.util.io.FileUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

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
    final int VALID_CAPTURE_TIME = 1000;
    final int STACK_ID = 10;

    // A klass1 instance allocation event (t = 0, tag = 100)
    AllocationEvent alloc1 = AllocationEvent.newBuilder()
      .setAllocData(AllocationEvent.Allocation.newBuilder().setTag(KLASS1_INSTANCE1_TAG).setClassTag(KLASS1_TAG).setStackId(STACK_ID))
      .setTimestamp(0)
      .setCaptureTime(VALID_CAPTURE_TIME).build();
    // A klass1 instance deallocation event (t = 7, tag = 100)
    AllocationEvent dealloc1 = AllocationEvent.newBuilder()
      .setFreeData(AllocationEvent.Deallocation.newBuilder().setTag(KLASS1_INSTANCE1_TAG)).setTimestamp(7)
      .setCaptureTime(VALID_CAPTURE_TIME).build();
    // A klass2 instance allocation event (t = 6, tag = 101)
    AllocationEvent alloc2 = AllocationEvent.newBuilder()
      .setAllocData(AllocationEvent.Allocation.newBuilder().setTag(KLASS2_INSTANCE1_TAG).setClassTag(KLASS2_TAG)).setTimestamp(6)
      .setCaptureTime(VALID_CAPTURE_TIME).build();

    BatchAllocationSample insertSample = BatchAllocationSample.newBuilder()
      .addEvents(alloc1)
      .addEvents(dealloc1)
      .addEvents(alloc2).build();
    myAllocationTable.insertAllocationData(VALID_PID, VALID_SESSION, insertSample);

    AllocationEvent expectedAlloc1 = AllocationEvent.newBuilder()
      .setAllocData(AllocationEvent.Allocation.newBuilder().setClassTag(KLASS1_TAG).setTag(KLASS1_INSTANCE1_TAG).setStackId(STACK_ID))
      .setTimestamp(0).build();
    AllocationEvent expectedDealloc1 = AllocationEvent.newBuilder()
      .setFreeData(AllocationEvent.Deallocation.newBuilder().setClassTag(KLASS1_TAG).setTag(KLASS1_INSTANCE1_TAG).build()).setTimestamp(7)
      .build();
    // Ongoing allocation events would have their FreeTime set to Long.MAX_VALUE.
    AllocationEvent expectedAlloc2 = AllocationEvent.newBuilder()
      .setAllocData(AllocationEvent.Allocation.newBuilder().setTag(KLASS2_INSTANCE1_TAG).setClassTag(KLASS2_TAG))
      .setTimestamp(6).build();

    // A query that asks for live objects.
    BatchAllocationSample querySample = myAllocationTable.getAllocations(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
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
    final String METHOD1_NAME = "Method1";
    final String METHOD2_NAME = "Method2";
    final String JNI_KLASS1_NAME = "Ljava/lang/Klass1;";
    final String JNI_KLASS2_NAME = "[[Ljava/lang/Klass2;";
    final String JAVA_KLASS1_NAME = "java.lang.Klass1";
    final String JAVA_KLASS2_NAME = "java.lang.Klass2[][]";

    List<StackFrame> methodsToInsert = new ArrayList<>();
    StackFrame method1 =
      StackFrame.newBuilder().setMethodId(1).setMethodName(METHOD1_NAME).setClassName(JNI_KLASS1_NAME).build();
    StackFrame method2 =
      StackFrame.newBuilder().setMethodId(2).setMethodName(METHOD2_NAME).setClassName(JNI_KLASS2_NAME).build();
    methodsToInsert.add(method1);
    methodsToInsert.add(method2);

    myAllocationTable.insertMethodInfo(VALID_PID, VALID_SESSION, methodsToInsert);

    // Valid cases
    StackFrame convertedMethod1 =
      StackFrame.newBuilder().setMethodId(1).setMethodName(METHOD1_NAME).setClassName(JAVA_KLASS1_NAME).build();
    StackFrame convertedMethod2 =
      StackFrame.newBuilder().setMethodId(2).setMethodName(METHOD2_NAME).setClassName(JAVA_KLASS2_NAME).build();
    assertEquals(convertedMethod1, myAllocationTable.queryMethodInfo(VALID_PID, VALID_SESSION, method1.getMethodId()));
    assertEquals(convertedMethod2, myAllocationTable.queryMethodInfo(VALID_PID, VALID_SESSION, method2.getMethodId()));

    // Non-existent methods / invalid pid
    assertEquals(StackFrame.getDefaultInstance(),
                 myAllocationTable.queryMethodInfo(INVALID_PID, VALID_SESSION, method1.getMethodId()));
    assertEquals(StackFrame.getDefaultInstance(),
                 myAllocationTable.queryMethodInfo(VALID_PID, INVALID_SESSION, method2.getMethodId()));
    assertEquals(StackFrame.getDefaultInstance(), myAllocationTable.queryMethodInfo(VALID_PID, VALID_SESSION, 3));
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

    AllocationContextsResponse contextSample = myAllocationTable.getAllocationContexts(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(0, contextSample.getAllocatedClassesCount());

    AllocatedClass expectedKlass1 = AllocatedClass.newBuilder().setClassName(JAVA_KLASS1_NAME).setClassId(KLASS1_TAG).build();
    AllocatedClass expectedKlass2 = AllocatedClass.newBuilder().setClassName(JAVA_KLASS2_NAME).setClassId(KLASS2_TAG).build();

    // A class that is loaded since the beginning (t = 0, tag = 1)
    AllocationEvent klass1 = AllocationEvent.newBuilder()
      .setClassData(AllocatedClass.newBuilder().setClassName(JNI_KLASS1_NAME).setClassId(KLASS1_TAG)).setTimestamp(0).build();
    BatchAllocationSample insertSample = BatchAllocationSample.newBuilder().addEvents(klass1).build();
    myAllocationTable.insertAllocationData(VALID_PID, VALID_SESSION, insertSample);
    contextSample = myAllocationTable.getAllocationContexts(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(1, contextSample.getAllocatedClassesCount());
    assertEquals(expectedKlass1, contextSample.getAllocatedClasses(0));

    // A klass1 instance allocation event (t = 0, tag = 100)
    AllocationEvent alloc1 = AllocationEvent.newBuilder()
      .setAllocData(AllocationEvent.Allocation.newBuilder().setTag(KLASS1_INSTANCE1_TAG).setClassTag(KLASS1_TAG)).setTimestamp(0).build();
    insertSample = BatchAllocationSample.newBuilder().addEvents(alloc1).build();
    myAllocationTable.insertAllocationData(VALID_PID, VALID_SESSION, insertSample);
    contextSample = myAllocationTable.getAllocationContexts(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(1, contextSample.getAllocatedClassesCount());
    assertEquals(expectedKlass1, contextSample.getAllocatedClasses(0));

    BatchAllocationSample querySample = myAllocationTable.getAllocations(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(1, querySample.getEventsCount());
    AllocationEvent expectedAlloc1 = alloc1.toBuilder().setAllocData(alloc1.getAllocData().toBuilder()).build();
    assertEquals(expectedAlloc1, querySample.getEvents(0));

    // A class that is loaded at t=5 (tag = 2)
    AllocationEvent klass2 = AllocationEvent.newBuilder()
      .setClassData(AllocatedClass.newBuilder().setClassName(JNI_KLASS2_NAME).setClassId(KLASS2_TAG)).setTimestamp(1).build();
    insertSample = BatchAllocationSample.newBuilder().addEvents(klass2).build();
    myAllocationTable.insertAllocationData(VALID_PID, VALID_SESSION, insertSample);
    contextSample = myAllocationTable.getAllocationContexts(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(2, contextSample.getAllocatedClassesCount());
    assertEquals(expectedKlass1, contextSample.getAllocatedClasses(0));
    assertEquals(expectedKlass2, contextSample.getAllocatedClasses(1));
    querySample = myAllocationTable.getAllocations(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(1, querySample.getEventsCount());
    assertEquals(expectedAlloc1, querySample.getEvents(0));

    // A klass2 instance allocation event (t = 2, tag = 101)
    AllocationEvent alloc2 = AllocationEvent.newBuilder()
      .setAllocData(AllocationEvent.Allocation.newBuilder().setTag(KLASS2_INSTANCE1_TAG).setClassTag(KLASS2_TAG)).setTimestamp(2).build();
    insertSample = BatchAllocationSample.newBuilder().addEvents(alloc2).build();
    myAllocationTable.insertAllocationData(VALID_PID, VALID_SESSION, insertSample);
    contextSample = myAllocationTable.getAllocationContexts(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(2, contextSample.getAllocatedClassesCount());
    assertEquals(expectedKlass1, contextSample.getAllocatedClasses(0));
    assertEquals(expectedKlass2, contextSample.getAllocatedClasses(1));
    querySample = myAllocationTable.getAllocations(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(2, querySample.getEventsCount());
    assertEquals(expectedAlloc1, querySample.getEvents(0));
    AllocationEvent expectedAlloc2 = alloc2.toBuilder().setAllocData(alloc2.getAllocData().toBuilder()).build();
    assertEquals(expectedAlloc2, querySample.getEvents(1));

    // A klass1 instance allocation event (t = 3, tag = 102)
    AllocationEvent alloc3 = AllocationEvent.newBuilder()
      .setAllocData(AllocationEvent.Allocation.newBuilder().setTag(KLASS1_INSTANCE2_TAG).setClassTag(KLASS1_TAG)).setTimestamp(3).build();
    insertSample = BatchAllocationSample.newBuilder().addEvents(alloc3).build();
    myAllocationTable.insertAllocationData(VALID_PID, VALID_SESSION, insertSample);
    contextSample = myAllocationTable.getAllocationContexts(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(2, contextSample.getAllocatedClassesCount());
    assertEquals(expectedKlass1, contextSample.getAllocatedClasses(0));
    assertEquals(expectedKlass2, contextSample.getAllocatedClasses(1));
    querySample = myAllocationTable.getAllocations(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(3, querySample.getEventsCount());
    assertEquals(expectedAlloc1, querySample.getEvents(0));
    assertEquals(expectedAlloc2, querySample.getEvents(1));
    AllocationEvent expectedAlloc3 = alloc3.toBuilder().setAllocData(alloc3.getAllocData().toBuilder()).build();
    assertEquals(expectedAlloc3, querySample.getEvents(2));

    // A alloc1 instance deallocation event (t = 5, tag = 100)
    AllocationEvent dealloc1 = AllocationEvent.newBuilder()
      .setFreeData(AllocationEvent.Deallocation.newBuilder().setTag(KLASS1_INSTANCE1_TAG)).setTimestamp(5).build();
    insertSample = BatchAllocationSample.newBuilder().addEvents(dealloc1).build();
    myAllocationTable.insertAllocationData(VALID_PID, VALID_SESSION, insertSample);
    contextSample = myAllocationTable.getAllocationContexts(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(2, contextSample.getAllocatedClassesCount());
    assertEquals(expectedKlass1, contextSample.getAllocatedClasses(0));
    assertEquals(expectedKlass2, contextSample.getAllocatedClasses(1));
    querySample = myAllocationTable.getAllocations(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(2, querySample.getEventsCount());
    assertEquals(expectedAlloc2, querySample.getEvents(0));
    assertEquals(expectedAlloc3, querySample.getEvents(1));

    // A alloc2 instance deallocation event (t = 6, tag = 101)
    AllocationEvent dealloc2 = AllocationEvent.newBuilder()
      .setFreeData(AllocationEvent.Deallocation.newBuilder().setTag(KLASS2_INSTANCE1_TAG)).setTimestamp(6).build();
    insertSample = BatchAllocationSample.newBuilder().addEvents(dealloc2).build();
    myAllocationTable.insertAllocationData(VALID_PID, VALID_SESSION, insertSample);
    contextSample = myAllocationTable.getAllocationContexts(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(2, contextSample.getAllocatedClassesCount());
    assertEquals(expectedKlass1, contextSample.getAllocatedClasses(0));
    assertEquals(expectedKlass2, contextSample.getAllocatedClasses(1));
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
    contextSample = myAllocationTable.getAllocationContexts(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(2, contextSample.getAllocatedClassesCount());
    assertEquals(expectedKlass1, contextSample.getAllocatedClasses(0));
    assertEquals(expectedKlass2, contextSample.getAllocatedClasses(1));
    querySample = myAllocationTable.getAllocations(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(2, querySample.getEventsCount());
    assertEquals(expectedAlloc3, querySample.getEvents(0));
    assertEquals(alloc4.toBuilder().setAllocData(alloc4.getAllocData().toBuilder()).build(), querySample.getEvents(1));
  }

  @Test
  public void testAllocationContextQueriesAfterInsertion() throws Exception {
    final int STACK1 = 1;
    final int STACK2 = 2;
    final long METHOD1 = 10;
    final long METHOD2 = 11;
    final long METHOD3 = 12;
    final long CLASS1 = 1000;
    final long CLASS2 = 1001;
    final List<Long> STACK_METHODS1 = Arrays.asList(METHOD1, METHOD2);
    final List<Long> STACK_METHODS2 = Arrays.asList(METHOD2, METHOD3);
    final String METHOD1_NAME = "Method1";
    final String METHOD2_NAME = "Method2";
    final String METHOD3_NAME = "Method3";
    final String JNI_KLASS1_NAME = "Ljava/lang/Klass1;";
    final String JNI_KLASS2_NAME = "[[Ljava/lang/Klass2;";
    final String JNI_KLASS3_NAME = "[[[Ljava/lang/Klass3;";
    final String JAVA_KLASS1_NAME = "java.lang.Klass1";
    final String JAVA_KLASS2_NAME = "java.lang.Klass2[][]";
    final String JAVA_KLASS3_NAME = "java.lang.Klass3[][][]";

    final long CLASS1_TIME = 10;
    final long CLASS2_TIME = 15;
    final long STACK1_TIME = 12;
    final long STACK2_TIME = 17;

    List<StackFrame> methodsToInsert = new ArrayList<>();
    StackFrame method1 =
      StackFrame.newBuilder().setMethodId(METHOD1).setMethodName(METHOD1_NAME).setClassName(JNI_KLASS1_NAME).build();
    StackFrame method2 =
      StackFrame.newBuilder().setMethodId(METHOD2).setMethodName(METHOD2_NAME).setClassName(JNI_KLASS2_NAME).build();
    StackFrame method3 =
      StackFrame.newBuilder().setMethodId(METHOD3).setMethodName(METHOD3_NAME).setClassName(JNI_KLASS3_NAME).build();
    methodsToInsert.add(method1);
    methodsToInsert.add(method2);
    methodsToInsert.add(method3);

    List<EncodedAllocationStack> stacksToInsert = new ArrayList<>();
    EncodedAllocationStack stack1 =
      EncodedAllocationStack.newBuilder().setStackId(STACK1).addAllMethodIds(STACK_METHODS1).setTimestamp(STACK1_TIME).build();
    EncodedAllocationStack stack2 =
      EncodedAllocationStack.newBuilder().setStackId(STACK2).addAllMethodIds(STACK_METHODS2).setTimestamp(STACK2_TIME).build();
    stacksToInsert.add(stack1);
    stacksToInsert.add(stack2);

    BatchAllocationSample.Builder classesBuilder = BatchAllocationSample.newBuilder();
    AllocatedClass class1 = AllocatedClass.newBuilder().setClassId(CLASS1).setClassName(JNI_KLASS1_NAME).build();
    AllocatedClass class2 = AllocatedClass.newBuilder().setClassId(CLASS2).setClassName(JNI_KLASS2_NAME).build();
    classesBuilder.addEvents(AllocationEvent.newBuilder().setTimestamp(CLASS1_TIME).setClassData(class1));
    classesBuilder.addEvents(AllocationEvent.newBuilder().setTimestamp(CLASS2_TIME).setClassData(class2));

    // Insert handcrafted data.
    myAllocationTable.insertMethodInfo(VALID_PID, VALID_SESSION, methodsToInsert);
    myAllocationTable.insertStackInfo(VALID_PID, VALID_SESSION, stacksToInsert);
    myAllocationTable.insertAllocationData(VALID_PID, VALID_SESSION, classesBuilder.build());

    AllocatedClass expectedKlass1 = AllocatedClass.newBuilder().setClassId(CLASS1).setClassName(JAVA_KLASS1_NAME).build();
    AllocatedClass expectedKlass2 = AllocatedClass.newBuilder().setClassId(CLASS2).setClassName(JAVA_KLASS2_NAME).build();
    AllocationStack expectedStack1 = AllocationStack.newBuilder().setStackId(STACK1)
      .addStackFrames(StackFrame.newBuilder().setMethodId(METHOD1).setMethodName(METHOD1_NAME).setClassName(JAVA_KLASS1_NAME))
      .addStackFrames(StackFrame.newBuilder().setMethodId(METHOD2).setMethodName(METHOD2_NAME).setClassName(JAVA_KLASS2_NAME)).build();
    AllocationStack expectedStack2 = AllocationStack.newBuilder().setStackId(STACK2)
      .addStackFrames(StackFrame.newBuilder().setMethodId(METHOD2).setMethodName(METHOD2_NAME).setClassName(JAVA_KLASS2_NAME))
      .addStackFrames(StackFrame.newBuilder().setMethodId(METHOD3).setMethodName(METHOD3_NAME).setClassName(JAVA_KLASS3_NAME)).build();

    AllocationContextsResponse contexts = myAllocationTable.getAllocationContexts(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    assertEquals(2, contexts.getAllocatedClassesCount());
    assertEquals(expectedKlass1, contexts.getAllocatedClasses(0));
    assertEquals(expectedKlass2, contexts.getAllocatedClasses(1));
    assertEquals(2, contexts.getAllocationStacksCount());
    assertEquals(expectedStack1, contexts.getAllocationStacks(0));
    assertEquals(expectedStack2, contexts.getAllocationStacks(1));

    // Timestamp should be set to the latest AllocatedClass's.
    assertEquals(CLASS2_TIME, contexts.getTimestamp());
  }
}
