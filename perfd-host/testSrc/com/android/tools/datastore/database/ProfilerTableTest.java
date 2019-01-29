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

import com.android.tools.datastore.DeviceId;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.Profiler.AgentStatusRequest;
import com.android.tools.profiler.proto.Profiler.AgentStatusResponse;
import com.android.tools.profiler.proto.Profiler.GetSessionsResponse;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import static com.google.common.truth.Truth.assertThat;

public class ProfilerTableTest extends DatabaseTest<ProfilerTable> {
  private static final DeviceId FAKE_DEVICE_ID = DeviceId.of(1);

  @Override
  protected ProfilerTable createTable() {
    return new ProfilerTable();
  }

  @Override
  protected List<Consumer<ProfilerTable>> getTableQueryMethodsForVerification() {
    List<Consumer<ProfilerTable>> methodCalls = new ArrayList<>();
    methodCalls.add((table) -> assertThat(table.getDeviceLastKnownTime(DeviceId.of(-1))).isEqualTo(Long.MIN_VALUE));
    methodCalls.add((table) -> assertThat(table.getDevices()).isEqualTo(Profiler.GetDevicesResponse.getDefaultInstance()));
    methodCalls.add((table) -> assertThat(table.getProcesses(Profiler.GetProcessesRequest.getDefaultInstance()))
      .isEqualTo(Profiler.GetProcessesResponse.getDefaultInstance()));
    methodCalls.add((table) -> assertThat(table.getSessionById(-1)).isEqualTo(Common.Session.getDefaultInstance()));
    methodCalls
      .add((table) -> assertThat(table.getSessionMetaData(-1)).isEqualTo(Profiler.GetSessionMetaDataResponse.getDefaultInstance()));
    methodCalls.add((table) -> assertThat(table.getSessions()).isEqualTo(GetSessionsResponse.getDefaultInstance()));
    methodCalls.add((table) -> assertThat(table.getAgentStatus(AgentStatusRequest.getDefaultInstance()))
      .isEqualTo(AgentStatusResponse.getDefaultInstance()));
    methodCalls.add((table) -> assertThat(table.getBytes(Profiler.BytesRequest.getDefaultInstance())).isEqualTo(null));
    methodCalls.add((table) -> table.insertOrUpdateDevice(Common.Device.getDefaultInstance()));
    methodCalls
      .add((table) -> table.insertOrUpdateBytes("id", Common.Session.getDefaultInstance(), Profiler.BytesResponse.getDefaultInstance()));
    methodCalls.add((table) -> table.insertOrUpdateProcess(DeviceId.of(-1), Common.Process.getDefaultInstance()));
    methodCalls.add((table) -> table
      .insertOrUpdateSession(Common.Session.getDefaultInstance(), "Name", 0, false, false, Common.SessionMetaData.SessionType.UNSPECIFIED));
    methodCalls.add(
      (table) -> table.updateAgentStatus(DeviceId.of(-1), Common.Process.getDefaultInstance(), AgentStatusResponse.getDefaultInstance()));
    methodCalls.add((table) -> table.updateDeviceLastKnownTime(Common.Device.getDefaultInstance(), 0));
    methodCalls.add((table) -> table.updateSessionEndTime(0, 0));
    methodCalls.add((table) -> table.deleteSession(-1));
    return methodCalls;
  }

  @Test
  public void testInsertAndGetDeviceKnownTime() {
    Common.Device device = Common.Device.newBuilder().setDeviceId(1).build();
    long knownTimestamp = 100L;
    long knownTimestamp2 = 200L;
    getTable().insertOrUpdateDevice(device);
    getTable().updateDeviceLastKnownTime(device, knownTimestamp);
    assertThat(getTable().getDeviceLastKnownTime(DeviceId.of(device.getDeviceId()))).isEqualTo(knownTimestamp);

    // Test that subsequent call returns the most recent value.
    getTable().updateDeviceLastKnownTime(device, knownTimestamp2);
    assertThat(getTable().getDeviceLastKnownTime(DeviceId.of(device.getDeviceId()))).isEqualTo(knownTimestamp2);

    // Test that invalid device's id return Long.MIN_VALUE;
    assertThat(getTable().getDeviceLastKnownTime(DeviceId.of(-1))).isEqualTo(Long.MIN_VALUE);
  }

  @Test
  public void testInsertAndGetSessions() {
    List<Common.Session> sessions = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      long startTime = 40 + i;
      String sessionName = Integer.toString(60 + i);
      Common.Session session = Common.Session
        .newBuilder()
        .setSessionId(10 + i)
        .setDeviceId(20 + i)
        .setPid(30 + i)
        .setStartTimestamp(startTime)
        .setEndTimestamp(Long.MAX_VALUE)
        .build();

      getTable().insertOrUpdateSession(session, sessionName, startTime, true, false, Common.SessionMetaData.SessionType.FULL);
      sessions.add(session);
    }

    GetSessionsResponse response = getTable().getSessions();
    assertThat(response.getSessionsCount()).isEqualTo(10);
    for (int i = 0; i < 10; i++) {
      assertThat(response.getSessions(i)).isEqualTo(sessions.get(i));
    }

    for (int i = 0; i < 10; i++) {
      Common.Session session = sessions.get(i).toBuilder().setEndTimestamp(50 + i).build();
      getTable().updateSessionEndTime(session.getSessionId(), session.getEndTimestamp());
      sessions.set(i, session);
    }

    response = getTable().getSessions();
    assertThat(response.getSessionsCount()).isEqualTo(10);
    for (int i = 0; i < 10; i++) {
      assertThat(response.getSessions(i)).isEqualTo(sessions.get(i));
    }
  }

  @Test
  public void testGetSessionById() {
    List<Common.Session> sessions = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      long startTime = 40 + i;
      String sessionName = Integer.toString(60 + i);
      Common.Session session = Common.Session
        .newBuilder()
        .setSessionId(10 + i)
        .setDeviceId(20 + i)
        .setPid(30 + i)
        .setStartTimestamp(startTime)
        .setEndTimestamp(Long.MAX_VALUE)
        .build();

      getTable().insertOrUpdateSession(session, sessionName, startTime, true, false, Common.SessionMetaData.SessionType.FULL);
      sessions.add(session);
    }

    for (int i = 0; i < 2; i++) {
      assertThat(getTable().getSessionById(sessions.get(i).getSessionId())).isEqualTo(sessions.get(i));
    }

    // Test the invalid case.
    assertThat(getTable().getSessionById(-1)).isEqualTo(Common.Session.getDefaultInstance());
  }

  @Test
  public void testDeleteSession() {
    List<Common.Session> sessions = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      long startTime = 40 + i;
      String sessionName = Integer.toString(60 + i);
      Common.Session session = Common.Session
        .newBuilder()
        .setSessionId(10 + i)
        .setDeviceId(20 + i)
        .setPid(30 + i)
        .setStartTimestamp(startTime)
        .setEndTimestamp(Long.MAX_VALUE)
        .build();

      getTable().insertOrUpdateSession(session, sessionName, startTime, true, false, Common.SessionMetaData.SessionType.FULL);
      sessions.add(session);
    }

    for (int i = 0; i < 2; i++) {
      assertThat(getTable().getSessionById(sessions.get(i).getSessionId())).isEqualTo(sessions.get(i));
      getTable().deleteSession(sessions.get(i).getSessionId());
    }

    for (int i = 0; i < 2; i++) {
      assertThat(getTable().getSessionById(sessions.get(i).getSessionId())).isEqualTo(Common.Session.getDefaultInstance());
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
      Common.Session session = Common.Session.newBuilder().setSessionId(sessionId).build();
      Common.SessionMetaData metaData = Common.SessionMetaData
        .newBuilder()
        .setSessionId(sessionId)
        .setStartTimestampEpochMs(startTime)
        .setSessionName(sessionName)
        .setJvmtiEnabled(useJvmti)
        .setLiveAllocationEnabled(useLiveAllocation)
        .setType(Common.SessionMetaData.SessionType.FULL)
        .build();

      getTable().insertOrUpdateSession(session, sessionName, startTime, useJvmti, useLiveAllocation, Common.SessionMetaData.SessionType.FULL);
      metaDatas.add(metaData);
    }

    for (int i = 0; i < 10; i++) {
      Common.SessionMetaData data = metaDatas.get(i);
      Profiler.GetSessionMetaDataResponse response = getTable().getSessionMetaData(data.getSessionId());
      assertThat(response.getData()).isEqualTo(data);
    }
  }

  @Test
  public void testAgentStatusCannotDowngrade() {
    Common.Process process = Common.Process.newBuilder().setPid(99).setName("FakeProcess").build();

    // Setup initial process and status
    AgentStatusResponse status =
      AgentStatusResponse.newBuilder().setStatus(AgentStatusResponse.Status.DETACHED).build();
    getTable().insertOrUpdateProcess(FAKE_DEVICE_ID, process);
    getTable().updateAgentStatus(FAKE_DEVICE_ID, process, status);

    AgentStatusRequest request =
      AgentStatusRequest.newBuilder().setPid(process.getPid()).setDeviceId(FAKE_DEVICE_ID.get()).build();
    assertThat(getTable().getAgentStatus(request).getStatus()).isEqualTo(AgentStatusResponse.Status.DETACHED);

    // Upgrading status to attach should work
    status = AgentStatusResponse.newBuilder().setStatus(AgentStatusResponse.Status.ATTACHED).build();
    getTable().updateAgentStatus(FAKE_DEVICE_ID, process, status);
    assertThat(getTable().getAgentStatus(request).getStatus()).isEqualTo(AgentStatusResponse.Status.ATTACHED);

    // Attempt to downgrade status
    status = AgentStatusResponse.newBuilder().setStatus(AgentStatusResponse.Status.DETACHED).build();
    getTable().updateAgentStatus(FAKE_DEVICE_ID, process, status);
    assertThat(getTable().getAgentStatus(request).getStatus()).isEqualTo(AgentStatusResponse.Status.ATTACHED);
  }

  @Test
  public void testExistingProcessIsUpdated() {
    Common.Process process =
      Common.Process.newBuilder().setDeviceId(FAKE_DEVICE_ID.get()).setPid(99).setName("FakeProcess").setState(Common.Process.State.ALIVE)
                    .setStartTimestampNs(10).build();

    // Setup initial process and status.
    AgentStatusResponse status =
      AgentStatusResponse.newBuilder().setStatus(AgentStatusResponse.Status.ATTACHED).build();
    getTable().insertOrUpdateProcess(FAKE_DEVICE_ID, process);
    getTable().updateAgentStatus(FAKE_DEVICE_ID, process, status);

    // Double-check the process has been added.
    Profiler.GetProcessesResponse processes =
      getTable().getProcesses(Profiler.GetProcessesRequest.newBuilder().setDeviceId(FAKE_DEVICE_ID.get()).build());
    assertThat(processes.getProcessList()).hasSize(1);
    assertThat(processes.getProcess(0)).isEqualTo(process);

    // Double-check status has been set.
    AgentStatusRequest request =
      AgentStatusRequest.newBuilder().setPid(process.getPid()).setDeviceId(FAKE_DEVICE_ID.get()).build();
    assertThat(getTable().getAgentStatus(request).getStatus()).isEqualTo(AgentStatusResponse.Status.ATTACHED);

    // Kill the process entry and verify that the process state is updated and the agent status remains the same.
    Common.Process deadProcess = process.toBuilder().setState(Common.Process.State.DEAD).build();
    getTable().insertOrUpdateProcess(FAKE_DEVICE_ID, deadProcess);
    processes = getTable().getProcesses(Profiler.GetProcessesRequest.newBuilder().setDeviceId(FAKE_DEVICE_ID.get()).build());
    assertThat(processes.getProcessList()).hasSize(1);
    assertThat(processes.getProcess(0)).isEqualTo(deadProcess);
    assertThat(getTable().getAgentStatus(request).getStatus()).isEqualTo(AgentStatusResponse.Status.ATTACHED);

    // Resurrects the process and verify that the start time does not change. This is a scenario for Emulator Snapshots.
    Common.Process resurrectedProcess = process.toBuilder().setStartTimestampNs(20).build();
    getTable().insertOrUpdateProcess(FAKE_DEVICE_ID, resurrectedProcess);
    processes = getTable().getProcesses(Profiler.GetProcessesRequest.newBuilder().setDeviceId(FAKE_DEVICE_ID.get()).build());
    assertThat(processes.getProcessList()).hasSize(1);
    assertThat(processes.getProcess(0)).isEqualTo(process);
    assertThat(getTable().getAgentStatus(request).getStatus()).isEqualTo(AgentStatusResponse.Status.ATTACHED);
  }
}