/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Transport.AgentStatusRequest;
import com.android.tools.profiler.proto.Transport.GetDevicesResponse;
import com.android.tools.profiler.proto.Transport.GetProcessesRequest;
import com.android.tools.profiler.proto.Transport.GetProcessesResponse;
import com.android.tools.idea.protobuf.InvalidProtocolBufferException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.jetbrains.annotations.NotNull;

public class DeviceProcessTable extends DataStoreTable<DeviceProcessTable.Statements> {
  public enum Statements {
    INSERT_DEVICE("INSERT OR REPLACE INTO [DevicesTable] (DeviceId, Data) values (?, ?)"),
    INSERT_PROCESS("INSERT OR REPLACE INTO [ProcessesTable] (DeviceId, ProcessId, Name, State, StartTime, Arch) " +
                   "values (?, ?, ?, ?, ?, ?)"),
    UPDATE_PROCESS_STATE("UPDATE [ProcessesTable] Set State = ? WHERE DeviceId = ? AND ProcessId = ?"),
    SELECT_PROCESSES("SELECT DeviceId, ProcessId, Name, State, StartTime, Arch from [ProcessesTable] WHERE DeviceId = ?"),
    SELECT_PROCESS_BY_ID("SELECT ProcessId from [ProcessesTable] WHERE DeviceId = ? AND ProcessId = ?"),
    SELECT_DEVICE("SELECT Data from [DevicesTable]"),
    FIND_AGENT_STATUS("SELECT AgentStatus from [ProcessesTable] WHERE DeviceId = ? AND ProcessId = ?"),
    UPDATE_AGENT_STATUS("UPDATE [ProcessesTable] SET AgentStatus = ? WHERE DeviceId = ? AND ProcessId = ?");

    @NotNull private final String mySqlStatement;

    Statements(@NotNull String sqlStatement) {
      mySqlStatement = sqlStatement;
    }

    @NotNull
    public String getStatement() {
      return mySqlStatement;
    }
  }

  @Override
  public void prepareStatements() {
    try {
      for (Statements statement : Statements.values()) {
        createStatement(statement, statement.getStatement());
      }
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  @Override
  public void initialize(@NotNull Connection connection) {
    super.initialize(connection);
    try {
      createTable("DevicesTable", "DeviceId INTEGER", "Data BLOB");
      createTable("ProcessesTable", "DeviceId INTEGER", "ProcessId INTEGER", "Name STRING NOT NULL", "State INTEGER",
                  "StartTime INTEGER", "Arch STRING NOT NULL", "AgentStatus INTEGER");
      createUniqueIndex("DevicesTable", "DeviceId");
      createUniqueIndex("ProcessesTable", "DeviceId", "ProcessId");
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  @NotNull
  public GetDevicesResponse getDevices() {
    if (isClosed()) {
      return GetDevicesResponse.getDefaultInstance();
    }

    GetDevicesResponse.Builder responseBuilder = GetDevicesResponse.newBuilder();
    try {
      ResultSet results = executeQuery(Statements.SELECT_DEVICE);
      while (results.next()) {
        responseBuilder.addDevice(Common.Device.parseFrom(results.getBytes(1)));
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      onError(ex);
    }
    return responseBuilder.build();
  }

  @NotNull
  public GetProcessesResponse getProcesses(@NotNull GetProcessesRequest request) {
    if (isClosed()) {
      return GetProcessesResponse.getDefaultInstance();
    }

    GetProcessesResponse.Builder responseBuilder = GetProcessesResponse.newBuilder();
    try {
      ResultSet results = executeQuery(Statements.SELECT_PROCESSES, request.getDeviceId());
      while (results.next()) {
        long deviceId = results.getLong(1);
        int pid = results.getInt(2);
        String name = results.getString(3);
        int state = results.getInt(4);
        long startTimeNs = results.getLong(5);
        String arch = results.getString(6);
        Common.Process process = Common.Process.newBuilder()
          .setDeviceId(deviceId)
          .setPid(pid)
          .setName(name)
          .setState(Common.Process.State.forNumber(state))
          .setStartTimestampNs(startTimeNs)
          .setAbiCpuArch(arch)
          .build();
        responseBuilder.addProcess(process);
      }
    }
    catch (SQLException ex) {
      onError(ex);
    }
    return responseBuilder.build();
  }

  public void insertOrUpdateDevice(@NotNull Common.Device device) {
    // TODO: Update start/end times with times polled from device.
    // End time always equals now, start time comes from device. This way if we get disconnected we still have an accurate end time.
    execute(Statements.INSERT_DEVICE, device.getDeviceId(), device.toByteArray());
  }

  /**
   * Note - StreamId is analogous to device's id in the legacy pipeline.
   */
  public void insertOrUpdateProcess(@NotNull long deviceId, @NotNull Common.Process process) {
    try {
      ResultSet results = executeQuery(Statements.SELECT_PROCESS_BY_ID, deviceId, process.getPid());
      if (results.next()) {
        execute(Statements.UPDATE_PROCESS_STATE, process.getStateValue(), deviceId, process.getPid());
      }
      else {
        execute(Statements.INSERT_PROCESS, deviceId, process.getPid(), process.getName(), process.getStateValue(),
                process.getStartTimestampNs(), process.getAbiCpuArch());
      }
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  /**
   * NOTE: Currently an assumption is made such that the agent lives and dies along with the process it is attached to.
   * If for some reason the agent freezes and we stop receiving a valid heartbeat momentarily, this will not downgrade the HasAgent status
   * in the process entry.
   */
  public void updateAgentStatus(@NotNull long devicId,
                                @NotNull Common.Process process,
                                @NotNull Common.AgentData agentData) {
    try {
      ResultSet results = executeQuery(Statements.FIND_AGENT_STATUS, devicId, process.getPid());
      if (results.next()) {
        execute(Statements.UPDATE_AGENT_STATUS, agentData.getStatus().ordinal(), devicId, process.getPid());
      }
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  @NotNull
  public Common.AgentData getAgentStatus(@NotNull AgentStatusRequest request) {
    Common.AgentData.Builder responseBuilder = Common.AgentData.newBuilder();
    try {
      ResultSet results = executeQuery(Statements.FIND_AGENT_STATUS, request.getDeviceId(), request.getPid());
      if (results.next()) {
        responseBuilder.setStatusValue(results.getInt(1));
      }
    }
    catch (SQLException ex) {
      onError(ex);
    }

    return responseBuilder.build();
  }
}
