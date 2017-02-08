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
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.google.protobuf3jarjar.ByteString;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class MemoryTableTest {

  private static final int VALID_PID = 1;
  private static final int INVALID_PID = -1;

  private File myDbFile;
  private MemoryTable myTable;
  private DataStoreDatabase myDatabase;

  @Before
  public void setUp() throws Exception {
    // TODO: Update to work on windows. PathUtil.getTempPath() fails with bazel
    myDbFile = new File("/tmp/MemoryTableTestDb");
    myDatabase = new DataStoreDatabase(myDbFile.getAbsolutePath());
    myTable = new MemoryTable();
    myDatabase.registerTable(myTable);
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
     * Timestamp:     0 1 2 3 4 5 6 7 8
     * mem              |
     * vmStats            |
     * ongoing heap         |---------->
     * finished heap          |-|
     * ongoing alloc              |---->
     * finished alloc               |--|
     */
    MemoryData.MemorySample memSample = MemoryData.MemorySample.newBuilder().setTimestamp(1).build();
    MemoryData.VmStatsSample vmStatsSample = MemoryData.VmStatsSample.newBuilder().setTimestamp(2).build();
    HeapDumpInfo ongoingHeapSample =
      HeapDumpInfo.newBuilder().setStartTime(3).setEndTime(DurationData.UNSPECIFIED_DURATION).build();
    HeapDumpInfo finishedHeapSample = HeapDumpInfo.newBuilder().setStartTime(4).setEndTime(5).build();
    AllocationsInfo ongoingAllocSample =
      AllocationsInfo.newBuilder().setStartTime(6).setEndTime(DurationData.UNSPECIFIED_DURATION).build();
    AllocationsInfo finishedAllocSample = AllocationsInfo.newBuilder().setStartTime(7).setEndTime(8).build();

    myTable.insertMemory(VALID_PID, Collections.singletonList(memSample));
    myTable.insertVmStats(VALID_PID, Collections.singletonList(vmStatsSample));
    myTable.insertOrReplaceHeapInfo(VALID_PID, finishedHeapSample);
    myTable.insertOrReplaceHeapInfo(VALID_PID, ongoingHeapSample);
    myTable.insertOrReplaceAllocationsInfo(VALID_PID, ongoingAllocSample);
    myTable.insertOrReplaceAllocationsInfo(VALID_PID, finishedAllocSample);

    // Perform a sequence of queries to ensure we are getting startTime-exclusive and endTime-inclusive data.
    MemoryData result = myTable.getData(MemoryRequest.newBuilder().setProcessId(VALID_PID).setStartTime(-1).setEndTime(0).build());
    verifyMemoryDataResultCounts(result, 0, 0, 0, 0);

    result = myTable.getData(MemoryRequest.newBuilder().setProcessId(VALID_PID).setStartTime(0).setEndTime(1).build());
    verifyMemoryDataResultCounts(result, 1, 0, 0, 0);
    assertEquals(memSample, result.getMemSamples(0));

    result = myTable.getData(MemoryRequest.newBuilder().setProcessId(VALID_PID).setStartTime(1).setEndTime(2).build());
    verifyMemoryDataResultCounts(result, 0, 1, 0, 0);
    assertEquals(vmStatsSample, result.getVmStatsSamples(0));

    result = myTable.getData(MemoryRequest.newBuilder().setProcessId(VALID_PID).setStartTime(2).setEndTime(3).build());
    verifyMemoryDataResultCounts(result, 0, 0, 1, 0);
    assertEquals(ongoingHeapSample, result.getHeapDumpInfos(0));

    result = myTable.getData(MemoryRequest.newBuilder().setProcessId(VALID_PID).setStartTime(3).setEndTime(4).build());
    verifyMemoryDataResultCounts(result, 0, 0, 2, 0);
    assertTrue(result.getHeapDumpInfosList().contains(ongoingHeapSample));
    assertTrue(result.getHeapDumpInfosList().contains(finishedHeapSample));

    result = myTable.getData(MemoryRequest.newBuilder().setProcessId(VALID_PID).setStartTime(4).setEndTime(5).build());
    verifyMemoryDataResultCounts(result, 0, 0, 2, 0);
    assertTrue(result.getHeapDumpInfosList().contains(ongoingHeapSample));
    assertTrue(result.getHeapDumpInfosList().contains(finishedHeapSample));

    result = myTable.getData(MemoryRequest.newBuilder().setProcessId(VALID_PID).setStartTime(5).setEndTime(6).build());
    verifyMemoryDataResultCounts(result, 0, 0, 1, 1);
    assertEquals(ongoingHeapSample, result.getHeapDumpInfos(0));
    assertEquals(ongoingAllocSample, result.getAllocationsInfo(0));

    result = myTable.getData(MemoryRequest.newBuilder().setProcessId(VALID_PID).setStartTime(6).setEndTime(7).build());
    verifyMemoryDataResultCounts(result, 0, 0, 1, 2);
    assertEquals(ongoingHeapSample, result.getHeapDumpInfos(0));
    assertTrue(result.getAllocationsInfoList().contains(ongoingAllocSample));
    assertTrue(result.getAllocationsInfoList().contains(finishedAllocSample));

    result = myTable.getData(MemoryRequest.newBuilder().setProcessId(VALID_PID).setStartTime(7).setEndTime(8).build());
    verifyMemoryDataResultCounts(result, 0, 0, 1, 2);
    assertEquals(ongoingHeapSample, result.getHeapDumpInfos(0));
    assertTrue(result.getAllocationsInfoList().contains(ongoingAllocSample));
    assertTrue(result.getAllocationsInfoList().contains(finishedAllocSample));

    result = myTable.getData(MemoryRequest.newBuilder().setProcessId(VALID_PID).setStartTime(8).setEndTime(9).build());
    verifyMemoryDataResultCounts(result, 0, 0, 1, 1);
    assertEquals(ongoingHeapSample, result.getHeapDumpInfos(0));
    assertEquals(ongoingAllocSample, result.getAllocationsInfo(0));

    // Test that querying for the invalid app id returns no data
    result = myTable.getData(MemoryRequest.newBuilder().setProcessId(INVALID_PID).setStartTime(0).setEndTime(9).build());
    verifyMemoryDataResultCounts(result, 0, 0, 0, 0);
  }

  @Test
  public void testHeapDumpQueriesAfterInsertion() throws Exception {
    HeapDumpInfo sample = HeapDumpInfo.newBuilder().setStartTime(0).setEndTime(0).build();
    myTable.insertOrReplaceHeapInfo(VALID_PID, sample);

    // Test that Status is set to NOT_READY and dump data is null
    assertEquals(DumpDataResponse.Status.NOT_READY, myTable.getHeapDumpStatus(VALID_PID, sample.getStartTime()));
    assertNull(myTable.getHeapDumpData(VALID_PID, sample.getStartTime()));

    // Update the HeapInfo with status and data and test that they returned correctly
    byte[] rawBytes = new byte[]{'a', 'b', 'c'};
    myTable.insertHeapDumpData(VALID_PID, sample.getStartTime(), DumpDataResponse.Status.SUCCESS, ByteString.copyFrom(rawBytes));

    assertEquals(DumpDataResponse.Status.SUCCESS, myTable.getHeapDumpStatus(VALID_PID, sample.getStartTime()));
    assertTrue(Arrays.equals(rawBytes, myTable.getHeapDumpData(VALID_PID, sample.getStartTime())));

    // Test that querying for the invalid app id returns NOT FOUND
    assertEquals(DumpDataResponse.Status.NOT_FOUND, myTable.getHeapDumpStatus(INVALID_PID, sample.getStartTime()));
    assertNull(myTable.getHeapDumpData(INVALID_PID, sample.getStartTime()));
  }

  @Test
  public void testAllocationsQueriesAfterInsertion() throws Exception {
    AllocationsInfo sample = AllocationsInfo.newBuilder().setStartTime(1).setEndTime(2).build();
    myTable.insertOrReplaceAllocationsInfo(VALID_PID, sample);

    // Tests that the info has been inserted into table, but the event response + dump data are still null
    assertEquals(sample, myTable.getAllocationsInfo(VALID_PID, sample.getStartTime()));
    assertNull(myTable.getAllocationData(VALID_PID, sample.getStartTime()));
    assertNull(myTable.getAllocationDumpData(VALID_PID, sample.getStartTime()));

    byte[] stackBytes = new byte[]{'a', 'b', 'c'};
    AllocationEventsResponse events = AllocationEventsResponse.newBuilder()
      .addEvents(AllocationEvent.newBuilder().setAllocatedClassId(1).setAllocationStackId(ByteString.copyFrom(stackBytes)))
      .addEvents(AllocationEvent.newBuilder().setAllocatedClassId(2).setAllocationStackId(ByteString.copyFrom(stackBytes))).build();
    myTable.updateAllocationEvents(VALID_PID, sample.getStartTime(), events);
    assertEquals(events, myTable.getAllocationData(VALID_PID, sample.getStartTime()));

    byte[] rawBytes = new byte[]{'d', 'e', 'f'};
    myTable.updateAllocationDump(VALID_PID, sample.getStartTime(), rawBytes);
    assertTrue(Arrays.equals(rawBytes, myTable.getAllocationDumpData(VALID_PID, sample.getStartTime())));

    // Test that querying for the invalid app id returns null
    assertNull(myTable.getAllocationsInfo(INVALID_PID, sample.getStartTime()));
    assertNull(myTable.getAllocationData(INVALID_PID, sample.getStartTime()));
    assertNull(myTable.getAllocationDumpData(INVALID_PID, sample.getStartTime()));
  }

  @Test
  public void testAllocationContextQueriesAfterInsertion() throws Exception {
    int classId1 = 1;
    int classId2 = 2;
    byte[] stackBytes1 = new byte[]{'a', 'b', 'c'};
    byte[] stackBytes2 = new byte[]{'d', 'e', 'f'};

    AllocatedClass class1 = AllocatedClass.newBuilder().setClassId(classId1).setClassName("Class1").build();
    AllocatedClass class2 = AllocatedClass.newBuilder().setClassId(classId2).setClassName("Class2").build();
    AllocationStack stack1 = AllocationStack.newBuilder().setStackId(ByteString.copyFrom(new byte[]{'a', 'b', 'c'})).build();
    AllocationStack stack2 = AllocationStack.newBuilder().setStackId(ByteString.copyFrom(new byte[]{'d', 'e', 'f'})).build();
    myTable.insertAllocationContext(Arrays.asList(class1, class2), Arrays.asList(stack1, stack2));

    AllocationContextsRequest request =
      AllocationContextsRequest.newBuilder().addClassIds(classId1).addStackIds(ByteString.copyFrom(stackBytes2)).build();
    AllocationContextsResponse response = myTable.listAllocationContexts(request);
    assertEquals(1, response.getAllocatedClassesCount());
    assertEquals(1, response.getAllocationStacksCount());
    assertEquals(class1, response.getAllocatedClasses(0));
    assertEquals(stack2, response.getAllocationStacks(0));

    request =
      AllocationContextsRequest.newBuilder().addClassIds(classId2).addStackIds(ByteString.copyFrom(stackBytes1)).build();
    response = myTable.listAllocationContexts(request);
    assertEquals(1, response.getAllocatedClassesCount());
    assertEquals(1, response.getAllocationStacksCount());
    assertEquals(class2, response.getAllocatedClasses(0));
    assertEquals(stack1, response.getAllocationStacks(0));
  }

  @Test
  public void testAllocationContextNotFound() throws Exception {
    AllocationContextsRequest request = AllocationContextsRequest.newBuilder()
      .addClassIds(1).addClassIds(2)
      .addStackIds(ByteString.copyFrom(new byte[]{'a', 'b', 'c'})).addStackIds(ByteString.copyFrom(new byte[]{'d', 'e', 'f'})).build();
    AllocationContextsResponse response = myTable.listAllocationContexts(request);

    assertEquals(0, response.getAllocatedClassesCount());
    assertEquals(0, response.getAllocationStacksCount());
  }

  private static void verifyMemoryDataResultCounts(@NotNull MemoryData result,
                                                   int numMemSample,
                                                   int numVmStatsSample,
                                                   int numHeapInfoSample,
                                                   int numAllocInfoSample) {
    assertEquals(numMemSample, result.getMemSamplesCount());
    assertEquals(numVmStatsSample, result.getVmStatsSamplesCount());
    assertEquals(numHeapInfoSample, result.getHeapDumpInfosCount());
    assertEquals(numAllocInfoSample, result.getAllocationsInfoCount());
  }
}