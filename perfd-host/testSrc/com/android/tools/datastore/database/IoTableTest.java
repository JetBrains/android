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
import com.android.tools.profiler.proto.IoProfiler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IoTableTest {
  private static final int PROCESS_ID = 1;
  private static final int PROCESS_ID_INVALID = 2;
  private static final int SESSION_ID = 3;
  private static final int BYTES_READ = 1024;
  private static final int BYTES_WRITTEN = 2048;
  private static final int READ_SPEED_B_S = 20971520; // 20 MB/S
  private static final int WRITE_SPEED_B_S = 15728640; // 15 MB/S
  private static final int SESSIONS_TEST_DATA_COUNT = 10;
  private static final int IO_CALLS_TEST_DATA_COUNT = 10;
  private static final int SPEED_TEST_DATA_COUNT = 10;
  private static final Common.Session VALID_SESSION = Common.Session.newBuilder().setDeviceId(1234).build();
  private static final Common.Session INVALID_SESSION = Common.Session.newBuilder().setDeviceId(4321).build();
  private static final List<IoProfiler.FileSession> ourFileSessionsList = new ArrayList<>();
  private static final List<IoProfiler.IoSpeedData> ourReadSpeedDataList = new ArrayList<>();
  private static final List<IoProfiler.IoSpeedData> ourWriteSpeedDataList = new ArrayList<>();

  private File myDbFile;
  private IoTable myTable;
  private DataStoreDatabase myDatabase;

  @Before
  public void setUp() throws Exception {
    HashMap<Common.Session, Long> sessionLookup = new HashMap<>();
    sessionLookup.put(VALID_SESSION, 1L);
    myDbFile = File.createTempFile("IoTable", "mysql");
    myDatabase = new DataStoreDatabase(myDbFile.getAbsolutePath(), DataStoreDatabase.Characteristic.DURABLE);
    myTable = new IoTable(sessionLookup);
    myTable.initialize(myDatabase.getConnection());
    populateDatabase();
  }

  @After
  public void tearDown() throws Exception {
    ourFileSessionsList.clear();
    ourReadSpeedDataList.clear();
    ourWriteSpeedDataList.clear();
    myDatabase.disconnect();
    myDbFile.delete();
  }

  private void populateDatabase() {
    // File Session Data
    for (int i = 0; i < SESSIONS_TEST_DATA_COUNT; i++) {
      long startTimestamp = i * 100;
      long endTimestamp = startTimestamp + 101;
      IoProfiler.FileSession.Builder fileDataBuilder =
        IoProfiler.FileSession.newBuilder().setStartTimestamp(startTimestamp).setEndTimestamp(endTimestamp)
          .setFilePath(i + ".txt").setIoSessionId(SESSION_ID + i);
      for (int j = 0; j < IO_CALLS_TEST_DATA_COUNT; j++) {
        boolean isRead = (j % 2 == 0);
        fileDataBuilder.addIoCalls(
          IoProfiler.IoCall.newBuilder().setStartTimestamp(startTimestamp + j * 2).setEndTimestamp(startTimestamp + j * 2 + 1)
            .setBytesCount(isRead ? BYTES_READ : BYTES_WRITTEN).setType(isRead ? IoProfiler.IoType.READ : IoProfiler.IoType.WRITE).build());
      }
      IoProfiler.FileSession fileSession = fileDataBuilder.build();
      ourFileSessionsList.add(fileSession);
      myTable.insert(PROCESS_ID, VALID_SESSION, fileSession);
    }

    // File Speed Data
    for (int i = 0; i < SPEED_TEST_DATA_COUNT; i++) {
      boolean isRead = (i % 2 == 0);
      long timestamp = i * 100 + 50;
      IoProfiler.IoSpeedData speedData = IoProfiler.IoSpeedData.newBuilder().setSpeed(isRead ? READ_SPEED_B_S : WRITE_SPEED_B_S)
        .setType(isRead ? IoProfiler.IoType.READ : IoProfiler.IoType.WRITE)
        .setBasicInfo(Common.CommonData.newBuilder().setProcessId(PROCESS_ID).setSession(VALID_SESSION).setEndTimestamp(timestamp).build())
        .build();

      if (isRead) {
        ourReadSpeedDataList.add(speedData);
      }
      else {
        ourWriteSpeedDataList.add(speedData);
      }
      myTable.insert(PROCESS_ID, VALID_SESSION, timestamp, speedData);
    }
  }

  private List<IoProfiler.FileSession> getFileData(long startTimestamp, long endTimestamp) {
    return myTable.getFileData(PROCESS_ID, VALID_SESSION, startTimestamp, endTimestamp);
  }

  @Test
  public void testGetAllFileData() {
    assertEquals(getFileData(0, 1000), ourFileSessionsList);
  }

  @Test
  public void testGetFileDataTailExcluded() {
    assertEquals(getFileData(0, 500), ourFileSessionsList.subList(0, 6));
  }

  @Test
  public void testGetFileDataHeadExcluded() {
    assertEquals(getFileData(600, 1000), ourFileSessionsList.subList(5, 10));
  }

  @Test
  public void testGetFileDataQueryRangeInsideSessionRange() {
    assertEquals(getFileData(623, 624), ourFileSessionsList.subList(6, 7));
  }

  @Test
  public void testGetFileDataInvalidRange() {
    assertTrue(getFileData(1000, 0).isEmpty());
  }

  @Test
  public void testGetFileDataEmptyRange() {
    assertTrue(getFileData(1001, 2000).isEmpty());
  }

  @Test
  public void testGetFileDataInvalidSession() throws Exception {
    List<IoProfiler.FileSession> data = myTable.getFileData(PROCESS_ID, INVALID_SESSION, 0, 1000);
    assertTrue(data.isEmpty());
  }

  @Test
  public void testGetFileDataInvalidProcess() throws Exception {
    List<IoProfiler.FileSession> data = myTable.getFileData(PROCESS_ID_INVALID, VALID_SESSION, 0, 1000);
    assertTrue(data.isEmpty());
  }

  private List<IoProfiler.IoSpeedData> getSpeedData(long startTimestamp, long endTimestamp, IoProfiler.IoType type) {
    return myTable.getSpeedData(PROCESS_ID, VALID_SESSION, startTimestamp, endTimestamp, type);
  }

  @Test
  public void testGetAllReadSpeedData() {
    assertEquals(getSpeedData(0, 1000, IoProfiler.IoType.READ), ourReadSpeedDataList);
  }

  @Test
  public void testGetAllWriteSpeedData() {
    assertEquals(getSpeedData(0, 1000, IoProfiler.IoType.WRITE), ourWriteSpeedDataList);
  }

  @Test
  public void testGetReadSpeedDataTailExcluded() {
    assertEquals(getSpeedData(0, 500, IoProfiler.IoType.READ), ourReadSpeedDataList.subList(0, 3));
  }

  @Test
  public void testGetWriteSpeedDataTailExcluded() {
    assertEquals(getSpeedData(0, 500, IoProfiler.IoType.WRITE), ourWriteSpeedDataList.subList(0, 2));
  }

  @Test
  public void testGetReadDataHeadExcluded() {
    assertEquals(getSpeedData(500, 1000, IoProfiler.IoType.READ), ourReadSpeedDataList.subList(3, 5));
  }

  @Test
  public void testGetWriteDataHeadExcluded() {
    assertEquals(getSpeedData(500, 1000, IoProfiler.IoType.WRITE), ourWriteSpeedDataList.subList(2, 5));
  }

  @Test
  public void testGetReadDataInvalidRange() {
    assertTrue(getSpeedData(1000, 0, IoProfiler.IoType.READ).isEmpty());
  }

  @Test
  public void testGetWriteDataInvalidRange() {
    assertTrue(getSpeedData(1000, 0, IoProfiler.IoType.WRITE).isEmpty());
  }

  @Test
  public void testGetReadDataEmptyRange() {
    assertTrue(getSpeedData(1001, 2000, IoProfiler.IoType.READ).isEmpty());
  }

  @Test
  public void testGetWriteDataEmptyRange() {
    assertTrue(getSpeedData(1001, 2000, IoProfiler.IoType.WRITE).isEmpty());
  }

  @Test
  public void testGetReadSpeedDataInvalidSession() throws Exception {
    List<IoProfiler.IoSpeedData> data = myTable.getSpeedData(PROCESS_ID, INVALID_SESSION, 0, 1000,
                                                             IoProfiler.IoType.READ);
    assertTrue(data.isEmpty());
  }

  @Test
  public void testGetWriteSpeedDataInvalidSession() throws Exception {
    List<IoProfiler.IoSpeedData> data = myTable.getSpeedData(PROCESS_ID, INVALID_SESSION, 0, 1000,
                                                             IoProfiler.IoType.WRITE);
    assertTrue(data.isEmpty());
  }

  @Test
  public void testGetReadSpeedDataInvalidProcess() throws Exception {
    List<IoProfiler.IoSpeedData> data = myTable.getSpeedData(PROCESS_ID_INVALID, VALID_SESSION, 0, 1000,
                                                             IoProfiler.IoType.READ);
    assertTrue(data.isEmpty());
  }

  @Test
  public void testGetWriteSpeedDataInvalidProcess() throws Exception {
    List<IoProfiler.IoSpeedData> data = myTable.getSpeedData(PROCESS_ID_INVALID, VALID_SESSION, 0, 1000,
                                                             IoProfiler.IoType.WRITE);
    assertTrue(data.isEmpty());
  }
}
