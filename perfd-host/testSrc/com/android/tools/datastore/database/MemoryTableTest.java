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

import com.android.tools.adtui.model.DurationData;
import com.android.tools.datastore.DataStoreDatabase;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.google.protobuf3jarjar.ByteString;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.Assert.*;

public class MemoryTableTest {

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
  private MemoryTable myTable;
  private DataStoreDatabase myDatabase;

  @Before
  public void setUp() throws Exception {
    HashMap<Common.Session, Long> sessionLookup = new HashMap<>();
    sessionLookup.put(VALID_SESSION, 1L);
    myDbFile = FileUtil.createTempFile("MemoryTable", "mysql");
    myDatabase = new DataStoreDatabase(myDbFile.getAbsolutePath());
    myTable = new MemoryTable();
    myDatabase.registerTable(myTable);
    myTable.setSessionLookup(sessionLookup);
  }

  @After
  public void tearDown() throws Exception {
    myDatabase.disconnect();
    myDbFile.delete();
  }

  @Test
  public void testInsertAndGetData() throws Exception {
    /**
     * Insert a cascading sequence of sample data into the database:
     * Timestamp:     0 1 2 3 4 5 6 7 8 9
     * mem              |
     * allocStats         |
     * ongoing heap         |---------->
     * finished heap          |-|
     * ongoing alloc              |---->
     * finished alloc               |-|
     * gcStats                        |-|
     */
    MemoryData.MemorySample memSample = MemoryData.MemorySample.newBuilder().setTimestamp(1).build();
    MemoryData.AllocStatsSample allocStatsSample = MemoryData.AllocStatsSample.newBuilder().setTimestamp(2).build();
    HeapDumpInfo ongoingHeapSample =
      HeapDumpInfo.newBuilder().setStartTime(3).setEndTime(DurationData.UNSPECIFIED_DURATION).build();
    HeapDumpInfo finishedHeapSample = HeapDumpInfo.newBuilder().setStartTime(4).setEndTime(5).build();
    AllocationsInfo ongoingAllocSample =
      AllocationsInfo.newBuilder().setStartTime(6).setEndTime(DurationData.UNSPECIFIED_DURATION).build();
    AllocationsInfo finishedAllocSample = AllocationsInfo.newBuilder().setStartTime(7).setEndTime(8).build();
    MemoryData.GcStatsSample gcStatsSample = MemoryData.GcStatsSample.newBuilder().setStartTime(8).setEndTime(9).build();

    myTable.insertMemory(VALID_PID, VALID_SESSION, Collections.singletonList(memSample));
    myTable.insertAllocStats(VALID_PID, VALID_SESSION, Collections.singletonList(allocStatsSample));
    myTable.insertGcStats(VALID_PID, VALID_SESSION, Collections.singletonList(gcStatsSample));
    myTable.insertOrReplaceHeapInfo(VALID_PID, VALID_SESSION, finishedHeapSample);
    myTable.insertOrReplaceHeapInfo(VALID_PID, VALID_SESSION, ongoingHeapSample);
    myTable.insertOrReplaceAllocationsInfo(VALID_PID, VALID_SESSION, ongoingAllocSample);
    myTable.insertOrReplaceAllocationsInfo(VALID_PID, VALID_SESSION, finishedAllocSample);

    // Perform a sequence of queries to ensure we are getting startTime-exclusive and endTime-inclusive data.
    MemoryData result =
      myTable.getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setProcessId(VALID_PID).setStartTime(-1).setEndTime(0).build());
    verifyMemoryDataResultCounts(result, 0, 0, 0, 0, 0);

    result =
      myTable.getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setProcessId(VALID_PID).setStartTime(0).setEndTime(1).build());
    verifyMemoryDataResultCounts(result, 1, 0, 0, 0, 0);
    assertEquals(memSample, result.getMemSamples(0));

    result =
      myTable.getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setProcessId(VALID_PID).setStartTime(1).setEndTime(2).build());
    verifyMemoryDataResultCounts(result, 0, 1, 0, 0, 0);
    assertEquals(allocStatsSample, result.getAllocStatsSamples(0));

    result =
      myTable.getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setProcessId(VALID_PID).setStartTime(2).setEndTime(3).build());
    verifyMemoryDataResultCounts(result, 0, 0, 0, 1, 0);
    assertEquals(ongoingHeapSample, result.getHeapDumpInfos(0));

    result =
      myTable.getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setProcessId(VALID_PID).setStartTime(3).setEndTime(4).build());
    verifyMemoryDataResultCounts(result, 0, 0, 0, 2, 0);
    assertTrue(result.getHeapDumpInfosList().contains(ongoingHeapSample));
    assertTrue(result.getHeapDumpInfosList().contains(finishedHeapSample));

    result =
      myTable.getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setProcessId(VALID_PID).setStartTime(4).setEndTime(5).build());
    verifyMemoryDataResultCounts(result, 0, 0, 0, 2, 0);
    assertTrue(result.getHeapDumpInfosList().contains(ongoingHeapSample));
    assertTrue(result.getHeapDumpInfosList().contains(finishedHeapSample));

    result =
      myTable.getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setProcessId(VALID_PID).setStartTime(5).setEndTime(6).build());
    verifyMemoryDataResultCounts(result, 0, 0, 0, 1, 1);
    assertEquals(ongoingHeapSample, result.getHeapDumpInfos(0));
    assertEquals(ongoingAllocSample, result.getAllocationsInfo(0));

    result =
      myTable.getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setProcessId(VALID_PID).setStartTime(6).setEndTime(7).build());
    verifyMemoryDataResultCounts(result, 0, 0, 0, 1, 2);
    assertEquals(ongoingHeapSample, result.getHeapDumpInfos(0));
    assertTrue(result.getAllocationsInfoList().contains(ongoingAllocSample));
    assertTrue(result.getAllocationsInfoList().contains(finishedAllocSample));

    result =
      myTable.getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setProcessId(VALID_PID).setStartTime(7).setEndTime(8).build());
    verifyMemoryDataResultCounts(result, 0, 0, 1, 1, 2);
    assertEquals(gcStatsSample, result.getGcStatsSamples(0));
    assertEquals(ongoingHeapSample, result.getHeapDumpInfos(0));
    assertTrue(result.getAllocationsInfoList().contains(ongoingAllocSample));
    assertTrue(result.getAllocationsInfoList().contains(finishedAllocSample));

    result =
      myTable.getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setProcessId(VALID_PID).setStartTime(8).setEndTime(9).build());
    verifyMemoryDataResultCounts(result, 0, 0, 0, 1, 1);
    assertEquals(ongoingHeapSample, result.getHeapDumpInfos(0));
    assertEquals(ongoingAllocSample, result.getAllocationsInfo(0));

    // Test that querying for the invalid app id returns no data
    result =
      myTable.getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setProcessId(INVALID_PID).setStartTime(0).setEndTime(9).build());
    verifyMemoryDataResultCounts(result, 0, 0, 0, 0, 0);

    // Test that querying for an invalid session returns no data.
    result =
      myTable.getData(MemoryRequest.newBuilder().setSession(INVALID_SESSION).setProcessId(VALID_PID).setStartTime(0).setEndTime(9).build());
    verifyMemoryDataResultCounts(result, 0, 0, 0, 0, 0);
  }

  @Test
  public void testHeapDumpQueriesAfterInsertion() throws Exception {
    HeapDumpInfo sample = HeapDumpInfo.newBuilder().setStartTime(0).setEndTime(0).build();
    myTable.insertOrReplaceHeapInfo(VALID_PID, VALID_SESSION, sample);

    // Test that Status is set to NOT_READY and dump data is null
    assertEquals(DumpDataResponse.Status.NOT_READY, myTable.getHeapDumpStatus(VALID_PID, VALID_SESSION, sample.getStartTime()));
    assertNull(myTable.getHeapDumpData(VALID_PID, VALID_SESSION, sample.getStartTime()));

    // Update the HeapInfo with status and data and test that they returned correctly
    byte[] rawBytes = new byte[]{'a', 'b', 'c'};
    myTable
      .insertHeapDumpData(VALID_PID, VALID_SESSION, sample.getStartTime(), DumpDataResponse.Status.SUCCESS, ByteString.copyFrom(rawBytes));

    assertEquals(DumpDataResponse.Status.SUCCESS, myTable.getHeapDumpStatus(VALID_PID, VALID_SESSION, sample.getStartTime()));
    assertTrue(Arrays.equals(rawBytes, myTable.getHeapDumpData(VALID_PID, VALID_SESSION, sample.getStartTime())));

    // Test that querying for the invalid app id returns NOT FOUND
    assertEquals(DumpDataResponse.Status.NOT_FOUND, myTable.getHeapDumpStatus(INVALID_PID, VALID_SESSION, sample.getStartTime()));
    assertNull(myTable.getHeapDumpData(INVALID_PID, VALID_SESSION, sample.getStartTime()));

    // Test that querying for the invalid session returns NOT FOUND
    assertEquals(DumpDataResponse.Status.NOT_FOUND, myTable.getHeapDumpStatus(VALID_PID, INVALID_SESSION, sample.getStartTime()));
    assertNull(myTable.getHeapDumpData(VALID_PID, INVALID_SESSION, sample.getStartTime()));
  }

  @Test
  public void testLegacyAllocationsQueriesAfterInsertion() throws Exception {
    AllocationsInfo sample = AllocationsInfo.newBuilder().setStartTime(1).setEndTime(2).build();
    myTable.insertOrReplaceAllocationsInfo(VALID_PID, VALID_SESSION, sample);

    // Tests that the info has been inserted into table, but the event response + dump data are still null
    assertEquals(sample, myTable.getAllocationsInfo(VALID_PID, VALID_SESSION, sample.getStartTime()));
    assertNull(myTable.getLegacyAllocationData(VALID_PID, VALID_SESSION, sample.getStartTime()));
    assertNull(myTable.getLegacyAllocationDumpData(VALID_PID, VALID_SESSION, sample.getStartTime()));

    byte[] stackBytes = new byte[]{'a', 'b', 'c'};
    LegacyAllocationEventsResponse events = LegacyAllocationEventsResponse.newBuilder()
      .addEvents(LegacyAllocationEvent.newBuilder().setAllocatedClassId(1).setAllocationStackId(ByteString.copyFrom(stackBytes)))
      .addEvents(LegacyAllocationEvent.newBuilder().setAllocatedClassId(2).setAllocationStackId(ByteString.copyFrom(stackBytes))).build();
    myTable.updateLegacyAllocationEvents(VALID_PID, VALID_SESSION, sample.getStartTime(), events);
    assertEquals(events, myTable.getLegacyAllocationData(VALID_PID, VALID_SESSION, sample.getStartTime()));

    byte[] rawBytes = new byte[]{'d', 'e', 'f'};
    myTable.updateLegacyAllocationDump(VALID_PID, VALID_SESSION, sample.getStartTime(), rawBytes);
    assertTrue(Arrays.equals(rawBytes, myTable.getLegacyAllocationDumpData(VALID_PID, VALID_SESSION, sample.getStartTime())));

    // Test that querying for the invalid app id returns null
    assertNull(myTable.getAllocationsInfo(INVALID_PID, VALID_SESSION, sample.getStartTime()));
    assertNull(myTable.getLegacyAllocationData(INVALID_PID, VALID_SESSION, sample.getStartTime()));
    assertNull(myTable.getLegacyAllocationDumpData(INVALID_PID, VALID_SESSION, sample.getStartTime()));

    // Test that querying for the invalid session returns null
    assertNull(myTable.getAllocationsInfo(VALID_PID, INVALID_SESSION, sample.getStartTime()));
    assertNull(myTable.getLegacyAllocationData(VALID_PID, INVALID_SESSION, sample.getStartTime()));
    assertNull(myTable.getLegacyAllocationDumpData(VALID_PID, INVALID_SESSION, sample.getStartTime()));
  }

  @Test
  public void testLegacyAllocationContextQueriesAfterInsertion() throws Exception {
    int classId1 = 1;
    int classId2 = 2;
    byte[] stackBytes1 = new byte[]{'a', 'b', 'c'};
    byte[] stackBytes2 = new byte[]{'d', 'e', 'f'};

    AllocatedClass class1 = AllocatedClass.newBuilder().setClassId(classId1).setClassName("Class1").build();
    AllocatedClass class2 = AllocatedClass.newBuilder().setClassId(classId2).setClassName("Class2").build();
    AllocationStack stack1 = AllocationStack.newBuilder().setStackId(ByteString.copyFrom(new byte[]{'a', 'b', 'c'})).build();
    AllocationStack stack2 = AllocationStack.newBuilder().setStackId(ByteString.copyFrom(new byte[]{'d', 'e', 'f'})).build();
    myTable.insertLegacyAllocationContext(Arrays.asList(class1, class2), Arrays.asList(stack1, stack2));

    LegacyAllocationContextsRequest request =
      LegacyAllocationContextsRequest.newBuilder().addClassIds(classId1).addStackIds(ByteString.copyFrom(stackBytes2)).build();
    LegacyAllocationContextsResponse response = myTable.listAllocationContexts(request);
    assertEquals(1, response.getAllocatedClassesCount());
    assertEquals(1, response.getAllocationStacksCount());
    assertEquals(class1, response.getAllocatedClasses(0));
    assertEquals(stack2, response.getAllocationStacks(0));

    request = LegacyAllocationContextsRequest.newBuilder().addClassIds(classId2).addStackIds(ByteString.copyFrom(stackBytes1)).build();
    response = myTable.listAllocationContexts(request);
    assertEquals(1, response.getAllocatedClassesCount());
    assertEquals(1, response.getAllocationStacksCount());
    assertEquals(class2, response.getAllocatedClasses(0));
    assertEquals(stack1, response.getAllocationStacks(0));
  }

  @Test
  public void testAllocationContextNotFound() throws Exception {
    LegacyAllocationContextsRequest request = LegacyAllocationContextsRequest.newBuilder()
      .addClassIds(1).addClassIds(2)
      .addStackIds(ByteString.copyFrom(new byte[]{'a', 'b', 'c'})).addStackIds(ByteString.copyFrom(new byte[]{'d', 'e', 'f'})).build();
    LegacyAllocationContextsResponse response = myTable.listAllocationContexts(request);

    assertEquals(0, response.getAllocatedClassesCount());
    assertEquals(0, response.getAllocationStacksCount());
  }

  @Test
  public void testInsertAndQueryAllocationData() throws Exception {
    // Handcraft a BatchAllocationSample and test that querying snapshot returns the corrent result.
    final int KLASS1_TAG = 1;
    final int KLASS2_TAG = 2;
    final int KLASS1_INSTANCE1_TAG = 100;
    final int KLASS2_INSTANCE1_TAG = 101;
    final String JNI_KLASS1_NAME = "Ljava/lang/Klass1;";
    final String JNI_KLASS2_NAME = "[[Ljava/lang/Klass2;";
    final String JAVA_KLASS1_NAME = "java.lang.Klass1";
    final String JAVA_KLASS2_NAME = "java.lang.Klass2[][]";

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
      .setAllocData(AllocationEvent.Allocation.newBuilder().setTag(KLASS1_INSTANCE1_TAG).setClassTag(KLASS1_TAG)).setTimestamp(0).build();
    // A klass1 instance free event (t = 7, tag = 100)
    AllocationEvent free1 = AllocationEvent.newBuilder()
      .setFreeData(AllocationEvent.Deallocation.newBuilder().setTag(KLASS1_INSTANCE1_TAG)).setTimestamp(7).build();
    // A klass2 instance allocation event (t = 6, tag = 101)
    AllocationEvent alloc2 = AllocationEvent.newBuilder()
      .setAllocData(AllocationEvent.Allocation.newBuilder().setTag(KLASS2_INSTANCE1_TAG).setClassTag(KLASS2_TAG)).setTimestamp(6).build();

    BatchAllocationSample insertSample = BatchAllocationSample.newBuilder()
      .addEvents(klass1)
      .addEvents(klass2)
      .addEvents(alloc1)
      .addEvents(free1)
      .addEvents(alloc2).build();
    myTable.insertAllocationData(VALID_PID, VALID_SESSION, insertSample);

    // Deallocated events would have their free time lumped into the AllocData messsage.
    AllocationEvent expectedAlloc1 = AllocationEvent.newBuilder()
      .setAllocData(
        AllocationEvent.Allocation.newBuilder().setTag(KLASS1_INSTANCE1_TAG).setClassTag(KLASS1_TAG).setFreeTimestamp(7))
      .setTimestamp(0).build();
    // Ongoing allocation events would have their FreeTime set to Long.MAX_VALUE.
    AllocationEvent expectedAlloc2 = AllocationEvent.newBuilder()
      .setAllocData(
        AllocationEvent.Allocation.newBuilder().setTag(KLASS2_INSTANCE1_TAG).setClassTag(KLASS2_TAG).setFreeTimestamp(Long.MAX_VALUE))
      .setTimestamp(6).build();
    AllocationEvent expectedKlass1 =
      AllocationEvent.newBuilder().setClassData(AllocationEvent.Klass.newBuilder().setName(JAVA_KLASS1_NAME).setTag(KLASS1_TAG))
        .setTimestamp(0).build();
    // A class that is loaded at t=5 (tag = 2)
    AllocationEvent expectedKlass2 =
      AllocationEvent.newBuilder().setClassData(AllocationEvent.Klass.newBuilder().setName(JAVA_KLASS2_NAME).setTag(KLASS2_TAG))
        .setTimestamp(5).build();

    // A query that asks for live objects.
    BatchAllocationSample querySample = myTable.getAllocationSnapshot(VALID_PID, VALID_SESSION, 0, Long.MAX_VALUE);
    // .... should returns class data + klass 2 instance
    assertEquals(3, querySample.getEventsCount());
    assertEquals(expectedKlass1, querySample.getEvents(0));
    assertEquals(expectedKlass2, querySample.getEvents(1));
    assertEquals(expectedAlloc2, querySample.getEvents(2));

    // A query that asks for live objects between t=0 and t=7
    querySample = myTable.getAllocationSnapshot(VALID_PID, VALID_SESSION, 0, 7);
    // .... should returns class data + both class instances
    assertEquals(4, querySample.getEventsCount());
    assertEquals(expectedKlass1, querySample.getEvents(0));
    assertEquals(expectedKlass2, querySample.getEvents(1));
    assertEquals(expectedAlloc1, querySample.getEvents(2));
    assertEquals(expectedAlloc2, querySample.getEvents(3));

    // A query that asks for live objects between t=1 and t=6
    querySample = myTable.getAllocationSnapshot(VALID_PID, VALID_SESSION, 1, 6);
    // .... should returns class data only - since no allocation/free has occurred over the timespan.
    assertEquals(2, querySample.getEventsCount());
    assertEquals(expectedKlass1, querySample.getEvents(0));
    assertEquals(expectedKlass2, querySample.getEvents(1));

    // A query that asks for live objects between t=7 and t=MAX_VALUE
    querySample = myTable.getAllocationSnapshot(VALID_PID, VALID_SESSION, 7, Long.MAX_VALUE);
    // .... should returns class data + klass1 instance (free event)
    assertEquals(3, querySample.getEventsCount());
    assertEquals(expectedKlass1, querySample.getEvents(0));
    assertEquals(expectedKlass2, querySample.getEvents(1));
    assertEquals(expectedAlloc1, querySample.getEvents(2));
  }

  private static void verifyMemoryDataResultCounts(@NotNull MemoryData result,
                                                   int numMemSample,
                                                   int numAllocStatsSample,
                                                   int numGcStatsSample,
                                                   int numHeapInfoSample,
                                                   int numAllocInfoSample) {
    assertEquals(numMemSample, result.getMemSamplesCount());
    assertEquals(numAllocStatsSample, result.getAllocStatsSamplesCount());
    assertEquals(numGcStatsSample, result.getGcStatsSamplesCount());
    assertEquals(numHeapInfoSample, result.getHeapDumpInfosCount());
    assertEquals(numAllocInfoSample, result.getAllocationsInfoCount());
  }
}