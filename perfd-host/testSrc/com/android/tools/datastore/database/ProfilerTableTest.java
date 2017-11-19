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
import com.android.tools.profiler.proto.Profiler.AgentStatusRequest;
import com.android.tools.profiler.proto.Profiler.AgentStatusResponse;
import com.android.tools.profiler.proto.Profiler.GetSessionsRequest;
import com.android.tools.profiler.proto.Profiler.GetSessionsResponse;
import com.intellij.openapi.util.io.FileUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
      Common.Session session = Common.Session.newBuilder()
        .setSessionId(10 + i)
        .setDeviceId(20 + i)
        .setPid(30 + i)
        .setStartTimestamp(40 + i)
        .setEndTimestamp(Long.MAX_VALUE)
        .build();

      myTable.insertOrUpdateSession(session);
      sessions.add(session);
    }

    GetSessionsResponse response = myTable.getSessions(GetSessionsRequest.getDefaultInstance());
    assertThat(response.getSessionsCount()).isEqualTo(10);
    for (int i = 0; i < 10; i++) {
      assertThat(response.getSessions(i)).isEqualTo(sessions.get(i));
    }

    for (int i = 0; i < 10; i++) {
      Common.Session session = sessions.get(i).toBuilder().setEndTimestamp(50 + i).build();
      sessions.set(i, session);
      myTable.insertOrUpdateSession(session);
    }

    response = myTable.getSessions(GetSessionsRequest.getDefaultInstance());
    assertThat(response.getSessionsCount()).isEqualTo(10);
    for (int i = 0; i < 10; i++) {
      assertThat(response.getSessions(i)).isEqualTo(sessions.get(i));
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
      AgentStatusRequest.newBuilder().setProcessId(process.getPid()).setDeviceId(FAKE_DEVICE_ID.get()).build();
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
      AgentStatusRequest.newBuilder().setProcessId(process.getPid()).setDeviceId(FAKE_DEVICE_ID.get()).build();
    assertThat(myTable.getAgentStatus(request).getStatus()).isEqualTo(AgentStatusResponse.Status.ATTACHED);

    // Update the process entry and verify that the agent status remains the same.
    process = process.toBuilder().setState(Common.Process.State.DEAD).build();
    myTable.insertOrUpdateProcess(FAKE_DEVICE_ID, process);
    assertThat(myTable.getAgentStatus(request).getStatus()).isEqualTo(AgentStatusResponse.Status.ATTACHED);
  }
}