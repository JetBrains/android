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
import com.intellij.openapi.util.io.FileUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;

public class ProfilerTableTest {
  private static final DeviceId FAKE_DEVICE_ID = DeviceId.of(1);
  private File myDbFile;
  private ProfilerTable myTable;
  private DataStoreDatabase myDatabase;

  @Before
  public void setUp() throws Exception {
    myDbFile = FileUtil.createTempFile("ProfileTable", "mysql");
    myDatabase = new DataStoreDatabase(myDbFile.getAbsolutePath(), DataStoreDatabase.Characteristic.DURABLE);
    myTable = new ProfilerTable(new HashMap<>());
    myTable.initialize(myDatabase.getConnection());
  }

  @After
  public void tearDown() throws Exception {
    myDatabase.disconnect();
    myDbFile.delete();
  }

  @Test
  public void testAgentStatusCannotDowngrade() throws Exception {
    Common.Process process = Common.Process.newBuilder()
      .setPid(99)
      .setName("FakeProcess")
      .build();

    // Setup initial process and status
    Profiler.AgentStatusResponse status =
      Profiler.AgentStatusResponse.newBuilder().setStatus(Profiler.AgentStatusResponse.Status.DETACHED).build();
    myTable.insertOrUpdateProcess(FAKE_DEVICE_ID, process);
    myTable.updateAgentStatus(FAKE_DEVICE_ID, process, status);

    Profiler.AgentStatusRequest request =
      Profiler.AgentStatusRequest.newBuilder().setProcessId(process.getPid()).setDeviceId(FAKE_DEVICE_ID.get()).build();
    assertEquals(Profiler.AgentStatusResponse.Status.DETACHED, myTable.getAgentStatus(request).getStatus());

    // Upgrading status to attach should work
    status = Profiler.AgentStatusResponse.newBuilder().setStatus(Profiler.AgentStatusResponse.Status.ATTACHED).build();
    myTable.updateAgentStatus(FAKE_DEVICE_ID, process, status);
    assertEquals(Profiler.AgentStatusResponse.Status.ATTACHED, myTable.getAgentStatus(request).getStatus());

    // Attempt to downgrade status
    status = Profiler.AgentStatusResponse.newBuilder().setStatus(Profiler.AgentStatusResponse.Status.DETACHED).build();
    myTable.updateAgentStatus(FAKE_DEVICE_ID, process, status);
    assertEquals(Profiler.AgentStatusResponse.Status.ATTACHED, myTable.getAgentStatus(request).getStatus());
  }

  @Test
  public void testExistingProcessIsUpdated() throws Exception {
    Common.Process process = Common.Process.newBuilder()
      .setPid(99)
      .setName("FakeProcess")
      .setState(Common.Process.State.ALIVE)
      .build();

    // Setup initial process and status.
    Profiler.AgentStatusResponse status =
      Profiler.AgentStatusResponse.newBuilder().setStatus(Profiler.AgentStatusResponse.Status.ATTACHED).build();
    myTable.insertOrUpdateProcess(FAKE_DEVICE_ID, process);
    myTable.updateAgentStatus(FAKE_DEVICE_ID, process, status);

    // Double-check status has been set.
    Profiler.AgentStatusRequest request =
      Profiler.AgentStatusRequest.newBuilder().setProcessId(process.getPid()).setDeviceId(FAKE_DEVICE_ID.get()).build();
    assertEquals(Profiler.AgentStatusResponse.Status.ATTACHED, myTable.getAgentStatus(request).getStatus());

    // Update the process entry and verify that the agent status remains the same.
    process = process.toBuilder().setState(Common.Process.State.DEAD).build();
    myTable.insertOrUpdateProcess(FAKE_DEVICE_ID, process);
    assertEquals(Profiler.AgentStatusResponse.Status.ATTACHED, myTable.getAgentStatus(request).getStatus());
  }
}