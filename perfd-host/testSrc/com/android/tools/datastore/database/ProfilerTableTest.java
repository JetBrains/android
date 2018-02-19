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
import com.android.tools.datastore.DeviceId;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.Profiler.AgentStatusRequest;
import com.android.tools.profiler.proto.Profiler.AgentStatusResponse;
import com.android.tools.profiler.proto.Profiler.GetSessionsResponse;
import com.intellij.openapi.util.io.FileUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.google.common.truth.Truth.assertThat;

public class ProfilerTableTest {
  private static final DeviceId FAKE_DEVICE_ID = DeviceId.of(1);
  private File myDbFile;
  private ProfilerTable myTable;
  private DataStoreDatabase myDatabase;

  @Before
  public void setUp() throws Exception {
    myDbFile = FileUtil.createTempFile("ProfileTable", "mysql");
    myDatabase = new DataStoreDatabase(myDbFile.getAbsolutePath(), DataStoreDatabase.Characteristic.DURABLE);
    myTable = new ProfilerTable();
    myTable.initialize(myDatabase.getConnection());
  }

  @After
  public void tearDown() throws Exception {
    myDatabase.disconnect();
    myDbFile.delete();
  }

  @Test
  public void testInsertAndGetSessions() throws Exception {
    List<Common.Session> sessions = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      long startTime = 40 + i;
      String sessionName = Integer.toString(60 + i);
      Common.Session session = Common.Session.newBuilder()
        .setSessionId(10 + i)
        .setDeviceId(20 + i)
        .setPid(30 + i)
        .setStartTimestamp(startTime)
        .setEndTimestamp(Long.MAX_VALUE)
        .build();

      myTable.insertOrUpdateSession(session, sessionName, startTime, true, false);
      sessions.add(session);
    }

    GetSessionsResponse response = myTable.getSessions();
    assertThat(response.getSessionsCount()).isEqualTo(10);
    for (int i = 0; i < 10; i++) {
      assertThat(response.getSessions(i)).isEqualTo(sessions.get(i));
    }

    for (int i = 0; i < 10; i++) {
      Common.Session session = sessions.get(i).toBuilder().setEndTimestamp(50 + i).build();
      myTable.updateSessionEndTime(session.getSessionId(), session.getEndTimestamp());
      sessions.set(i, session);
    }

    response = myTable.getSessions();
    assertThat(response.getSessionsCount()).isEqualTo(10);
    for (int i = 0; i < 10; i++) {
      assertThat(response.getSessions(i)).isEqualTo(sessions.get(i));
    }
  }

  @Test
  public void testInsertAndGetSessionMetaData() {
    List<Common.SessionMetaData> metaDatas = new ArrayList<>();
    Random rand = new Random();
    for (int i = 0; i < 10; i++) {
      long sessionId = 10 + i;
      long startTime = 40 + i;
      boolean useJvmti = rand.nextBoolean();
      boolean useLiveAllocation = rand.nextBoolean();
      String sessionName = Integer.toString(60 + i);
      Common.Session session = Common.Session.newBuilder()
        .setSessionId(sessionId)
        .build();
      Common.SessionMetaData metaData = Common.SessionMetaData.newBuilder()
        .setSessionId(sessionId)
        .setStartTimestampEpochMs(startTime)
        .setSessionName(sessionName)
        .setJvmtiEnabled(useJvmti)
        .setLiveAllocationEnabled(useLiveAllocation)
        .build();

      myTable.insertOrUpdateSession(session, sessionName, startTime, useJvmti, useLiveAllocation);
      metaDatas.add(metaData);
    }

    for (int i = 0; i < 10; i++) {
      Common.SessionMetaData data = metaDatas.get(i);
      Profiler.GetSessionMetaDataResponse response = myTable.getSessionMetaData(data.getSessionId());
      assertThat(response.getData()).isEqualTo(data);
    }
  }

  @Test
  public void testAgentStatusCannotDowngrade() throws Exception {
    Common.Process process = Common.Process.newBuilder()
      .setPid(99)
      .setName("FakeProcess")
      .build();

    // Setup initial process and status
    AgentStatusResponse status =
      AgentStatusResponse.newBuilder().setStatus(AgentStatusResponse.Status.DETACHED).build();
    myTable.insertOrUpdateProcess(FAKE_DEVICE_ID, process);
    myTable.updateAgentStatus(FAKE_DEVICE_ID, process, status);

    AgentStatusRequest request =
      AgentStatusRequest.newBuilder().setPid(process.getPid()).setDeviceId(FAKE_DEVICE_ID.get()).build();
    assertThat(myTable.getAgentStatus(request).getStatus()).isEqualTo(AgentStatusResponse.Status.DETACHED);

    // Upgrading status to attach should work
    status = AgentStatusResponse.newBuilder().setStatus(AgentStatusResponse.Status.ATTACHED).build();
    myTable.updateAgentStatus(FAKE_DEVICE_ID, process, status);
    assertThat(myTable.getAgentStatus(request).getStatus()).isEqualTo(AgentStatusResponse.Status.ATTACHED);

    // Attempt to downgrade status
    status = AgentStatusResponse.newBuilder().setStatus(AgentStatusResponse.Status.DETACHED).build();
    myTable.updateAgentStatus(FAKE_DEVICE_ID, process, status);
    assertThat(myTable.getAgentStatus(request).getStatus()).isEqualTo(AgentStatusResponse.Status.ATTACHED);
  }

  @Test
  public void testExistingProcessIsUpdated() throws Exception {
    Common.Process process = Common.Process.newBuilder()
      .setPid(99)
      .setName("FakeProcess")
      .setState(Common.Process.State.ALIVE)
      .build();

    // Setup initial process and status.
    AgentStatusResponse status =
      AgentStatusResponse.newBuilder().setStatus(AgentStatusResponse.Status.ATTACHED).build();
    myTable.insertOrUpdateProcess(FAKE_DEVICE_ID, process);
    myTable.updateAgentStatus(FAKE_DEVICE_ID, process, status);

    // Double-check status has been set.
    AgentStatusRequest request =
      AgentStatusRequest.newBuilder().setPid(process.getPid()).setDeviceId(FAKE_DEVICE_ID.get()).build();
    assertThat(myTable.getAgentStatus(request).getStatus()).isEqualTo(AgentStatusResponse.Status.ATTACHED);

    // Update the process entry and verify that the agent status remains the same.
    process = process.toBuilder().setState(Common.Process.State.DEAD).build();
    myTable.insertOrUpdateProcess(FAKE_DEVICE_ID, process);
    assertThat(myTable.getAgentStatus(request).getStatus()).isEqualTo(AgentStatusResponse.Status.ATTACHED);
  }
}