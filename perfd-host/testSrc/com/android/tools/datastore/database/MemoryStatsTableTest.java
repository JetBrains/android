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
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class MemoryStatsTableTest {
  private static final Common.Session VALID_SESSION = Common.Session.newBuilder().setSessionId(1L).setDeviceId(1234).setPid(1).build();
  private static final Common.Session INVALID_SESSION = Common.Session.newBuilder().setSessionId(-1L).setDeviceId(4321).setPid(-1).build();

  private File myDbFile;
  private MemoryStatsTable myStatsTable;
  private DataStoreDatabase myDatabase;

  @Before
  public void setUp() throws Exception {
    myDbFile = FileUtil.createTempFile("MemoryStatsTable", "mysql");
    myDatabase = new DataStoreDatabase(myDbFile.getAbsolutePath(), DataStoreDatabase.Characteristic.DURABLE);
    myStatsTable = new MemoryStatsTable();
    myStatsTable.initialize(myDatabase.getConnection());
  }

  @After
  public void tearDown() throws Exception {
    myDatabase.disconnect();
    //noinspection ResultOfMethodCallIgnored
    myDbFile.delete();
  }

  @Test
  public void testInsertAndGetData() throws Exception {
    /*
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
      HeapDumpInfo.newBuilder().setStartTime(3).setEndTime(Long.MAX_VALUE).build();
    HeapDumpInfo finishedHeapSample = HeapDumpInfo.newBuilder().setStartTime(4).setEndTime(5).build();
    AllocationsInfo ongoingAllocSample =
      AllocationsInfo.newBuilder().setStartTime(6).setEndTime(Long.MAX_VALUE).build();
    AllocationsInfo finishedAllocSample = AllocationsInfo.newBuilder().setStartTime(7).setEndTime(8).build();
    MemoryData.GcStatsSample gcStatsSample = MemoryData.GcStatsSample.newBuilder().setStartTime(8).setEndTime(9).build();

    myStatsTable.insertMemory(VALID_SESSION, Collections.singletonList(memSample));
    myStatsTable.insertAllocStats(VALID_SESSION, Collections.singletonList(allocStatsSample));
    myStatsTable.insertGcStats(VALID_SESSION, Collections.singletonList(gcStatsSample));
    myStatsTable.insertOrReplaceHeapInfo(VALID_SESSION, finishedHeapSample);
    myStatsTable.insertOrReplaceHeapInfo(VALID_SESSION, ongoingHeapSample);
    myStatsTable.insertOrReplaceAllocationsInfo(VALID_SESSION, ongoingAllocSample);
    myStatsTable.insertOrReplaceAllocationsInfo(VALID_SESSION, finishedAllocSample);

    // Perform a sequence of queries to ensure we are getting startTime-exclusive and endTime-inclusive data.
    MemoryData result = myStatsTable.getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setStartTime(-1).setEndTime(0).build());
    verifyMemoryDataResultCounts(result, 0, 0, 0, 0, 0);

    result = myStatsTable.getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setStartTime(0).setEndTime(1).build());
    verifyMemoryDataResultCounts(result, 1, 0, 0, 0, 0);
    assertEquals(memSample, result.getMemSamples(0));

    result = myStatsTable.getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setStartTime(1).setEndTime(2).build());
    verifyMemoryDataResultCounts(result, 0, 1, 0, 0, 0);
    assertEquals(allocStatsSample, result.getAllocStatsSamples(0));

    result = myStatsTable.getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setStartTime(2).setEndTime(3).build());
    verifyMemoryDataResultCounts(result, 0, 0, 0, 1, 0);
    assertEquals(ongoingHeapSample, result.getHeapDumpInfos(0));

    result = myStatsTable.getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setStartTime(3).setEndTime(4).build());
    verifyMemoryDataResultCounts(result, 0, 0, 0, 2, 0);
    assertTrue(result.getHeapDumpInfosList().contains(ongoingHeapSample));
    assertTrue(result.getHeapDumpInfosList().contains(finishedHeapSample));

    result = myStatsTable.getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setStartTime(4).setEndTime(5).build());
    verifyMemoryDataResultCounts(result, 0, 0, 0, 2, 0);
    assertTrue(result.getHeapDumpInfosList().contains(ongoingHeapSample));
    assertTrue(result.getHeapDumpInfosList().contains(finishedHeapSample));

    result = myStatsTable.getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setStartTime(5).setEndTime(6).build());
    verifyMemoryDataResultCounts(result, 0, 0, 0, 1, 1);
    assertEquals(ongoingHeapSample, result.getHeapDumpInfos(0));
    assertEquals(ongoingAllocSample, result.getAllocationsInfo(0));

    result = myStatsTable.getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setStartTime(6).setEndTime(7).build());
    verifyMemoryDataResultCounts(result, 0, 0, 0, 1, 2);
    assertEquals(ongoingHeapSample, result.getHeapDumpInfos(0));
    assertTrue(result.getAllocationsInfoList().contains(ongoingAllocSample));
    assertTrue(result.getAllocationsInfoList().contains(finishedAllocSample));

    result = myStatsTable.getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setStartTime(7).setEndTime(8).build());
    verifyMemoryDataResultCounts(result, 0, 0, 1, 1, 2);
    assertEquals(gcStatsSample, result.getGcStatsSamples(0));
    assertEquals(ongoingHeapSample, result.getHeapDumpInfos(0));
    assertTrue(result.getAllocationsInfoList().contains(ongoingAllocSample));
    assertTrue(result.getAllocationsInfoList().contains(finishedAllocSample));

    result = myStatsTable.getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setStartTime(8).setEndTime(9).build());
    verifyMemoryDataResultCounts(result, 0, 0, 0, 1, 1);
    assertEquals(ongoingHeapSample, result.getHeapDumpInfos(0));
    assertEquals(ongoingAllocSample, result.getAllocationsInfo(0));

    // Test that querying for an invalid session returns no data.
    result = myStatsTable.getData(MemoryRequest.newBuilder().setSession(INVALID_SESSION).setStartTime(0).setEndTime(9).build());
    verifyMemoryDataResultCounts(result, 0, 0, 0, 0, 0);
  }

  @Test
  public void testHeapDumpQueriesAfterInsertion() throws Exception {
    HeapDumpInfo sample = HeapDumpInfo.newBuilder().setStartTime(0).setEndTime(0).build();
    myStatsTable.insertOrReplaceHeapInfo(VALID_SESSION, sample);

    // Test that Status is set to NOT_READY and dump data is null
    assertEquals(DumpDataResponse.Status.NOT_READY, myStatsTable.getHeapDumpStatus(VALID_SESSION, sample.getStartTime()));
    assertNull(myStatsTable.getHeapDumpData(VALID_SESSION, sample.getStartTime()));

    // Update the HeapInfo with status and data and test that they returned correctly
    byte[] rawBytes = new byte[]{'a', 'b', 'c'};
    myStatsTable
      .insertHeapDumpData(VALID_SESSION, sample.getStartTime(), DumpDataResponse.Status.SUCCESS, ByteString.copyFrom(rawBytes));

    assertEquals(DumpDataResponse.Status.SUCCESS, myStatsTable.getHeapDumpStatus(VALID_SESSION, sample.getStartTime()));
    assertTrue(Arrays.equals(rawBytes, myStatsTable.getHeapDumpData(VALID_SESSION, sample.getStartTime())));

    // Test that querying for the invalid app id returns NOT FOUND
    assertEquals(DumpDataResponse.Status.NOT_FOUND, myStatsTable.getHeapDumpStatus(INVALID_SESSION, sample.getStartTime()));
    assertNull(myStatsTable.getHeapDumpData(INVALID_SESSION, sample.getStartTime()));

    // Test that querying for the invalid session returns NOT FOUND
    assertEquals(DumpDataResponse.Status.NOT_FOUND, myStatsTable.getHeapDumpStatus(INVALID_SESSION, sample.getStartTime()));
    assertNull(myStatsTable.getHeapDumpData(INVALID_SESSION, sample.getStartTime()));
  }

  @Test
  public void testLegacyAllocationsQueriesAfterInsertion() throws Exception {
    AllocationsInfo sample = AllocationsInfo.newBuilder().setStartTime(1).setEndTime(2).build();
    myStatsTable.insertOrReplaceAllocationsInfo(VALID_SESSION, sample);

    // Tests that the info has been inserted into table, but the event response + dump data are still null
    assertEquals(sample, myStatsTable.getAllocationsInfo(VALID_SESSION, sample.getStartTime()));
    assertNull(myStatsTable.getLegacyAllocationData(VALID_SESSION, sample.getStartTime()));
    assertNull(myStatsTable.getLegacyAllocationDumpData(VALID_SESSION, sample.getStartTime()));

    int stackId = 1;
    LegacyAllocationEventsResponse events = LegacyAllocationEventsResponse.newBuilder()
      .addEvents(LegacyAllocationEvent.newBuilder().setClassId(1).setStackId(stackId))
      .addEvents(LegacyAllocationEvent.newBuilder().setClassId(2).setStackId(stackId)).build();
    myStatsTable.updateLegacyAllocationEvents(VALID_SESSION, sample.getStartTime(), events);
    assertEquals(events, myStatsTable.getLegacyAllocationData(VALID_SESSION, sample.getStartTime()));

    byte[] rawBytes = new byte[]{'d', 'e', 'f'};
    myStatsTable.updateLegacyAllocationDump(VALID_SESSION, sample.getStartTime(), rawBytes);
    assertTrue(Arrays.equals(rawBytes, myStatsTable.getLegacyAllocationDumpData(VALID_SESSION, sample.getStartTime())));

    // Test that querying for the invalid app id returns null
    assertNull(myStatsTable.getAllocationsInfo(INVALID_SESSION, sample.getStartTime()));
    assertNull(myStatsTable.getLegacyAllocationData(INVALID_SESSION, sample.getStartTime()));
    assertNull(myStatsTable.getLegacyAllocationDumpData(INVALID_SESSION, sample.getStartTime()));

    // Test that querying for the invalid session returns null
    assertNull(myStatsTable.getAllocationsInfo(INVALID_SESSION, sample.getStartTime()));
    assertNull(myStatsTable.getLegacyAllocationData(INVALID_SESSION, sample.getStartTime()));
    assertNull(myStatsTable.getLegacyAllocationDumpData(INVALID_SESSION, sample.getStartTime()));
  }

  @Test
  public void testLegacyAllocationContextQueriesAfterInsertion() throws Exception {
    int classId1 = 1;
    int classId2 = 2;
    int stackId3 = 3;
    int stackId4 = 4;

    AllocatedClass class1 = AllocatedClass.newBuilder().setClassId(classId1).setClassName("Class1").build();
    AllocatedClass class2 = AllocatedClass.newBuilder().setClassId(classId2).setClassName("Class2").build();
    AllocationStack stack1 = AllocationStack.newBuilder().setStackId(stackId3).build();
    AllocationStack stack2 = AllocationStack.newBuilder().setStackId(stackId4).build();
    myStatsTable.insertLegacyAllocationContext(VALID_SESSION, Arrays.asList(class1, class2), Arrays.asList(stack1, stack2));

    LegacyAllocationContextsRequest request =
      LegacyAllocationContextsRequest.newBuilder().setSession(VALID_SESSION).addClassIds(classId1)
        .addStackIds(stackId4).build();
    AllocationContextsResponse response = myStatsTable.getLegacyAllocationContexts(request);
    assertEquals(1, response.getAllocatedClassesCount());
    assertEquals(1, response.getAllocationStacksCount());
    assertEquals(class1, response.getAllocatedClasses(0));
    assertEquals(stack2, response.getAllocationStacks(0));

    request = LegacyAllocationContextsRequest.newBuilder().setSession(VALID_SESSION).addClassIds(classId2)
      .addStackIds(stackId3).build();
    response = myStatsTable.getLegacyAllocationContexts(request);
    assertEquals(1, response.getAllocatedClassesCount());
    assertEquals(1, response.getAllocationStacksCount());
    assertEquals(class2, response.getAllocatedClasses(0));
    assertEquals(stack1, response.getAllocationStacks(0));
  }

  @Test
  public void testAllocationContextNotFound() throws Exception {
    LegacyAllocationContextsRequest request = LegacyAllocationContextsRequest.newBuilder().setSession(VALID_SESSION)
      .addClassIds(1).addClassIds(2).addStackIds(1).addStackIds(2).build();
    AllocationContextsResponse response = myStatsTable.getLegacyAllocationContexts(request);

    assertEquals(0, response.getAllocatedClassesCount());
    assertEquals(0, response.getAllocationStacksCount());
  }

  private static void verifyMemoryDataResultCounts(@NotNull MemoryProfiler.MemoryData result,
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