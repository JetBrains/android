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
import com.google.protobuf3jarjar.InvalidProtocolBufferException;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/**
 * Class that wraps database access for profiler level services.
 * The primary information managed by this class is device/process lifetime.
 */
public class ProfilerTable extends DataStoreTable<ProfilerTable.ProfilerStatements> {
  public enum ProfilerStatements {
    INSERT_DEVICE,
    UPDATE_DEVICE,
    INSERT_PROCESS,
    UPDATE_PROCESS,
    SELECT_PROCESSES,
    SELECT_PROCESS_BY_ID,
    SELECT_DEVICE,
    FIND_AGENT_STATUS,
    UPDATE_AGENT_STATUS,
    INSERT_BYTES,
    GET_BYTES
  }

  // Need to have a lock due to processes being updated and queried at the same time.
  // If a process is being queried, while one is being updated it will not get
  // returned in the query results, this results in the UI flickering.
  private final Object myLock = new Object();

  private static Logger getLogger() {
    return Logger.getInstance(ProfilerTable.class);
  }

  public ProfilerTable(@NotNull Map<Common.Session, Long> sesstionIdLookup) {
    super(sesstionIdLookup);
  }

  @Override
  public void initialize(@NotNull Connection connection) {
    super.initialize(connection);
    try {
      createTable("Profiler_Bytes", "Id STRING NOT NULL", "Session INTEGER", "Data BLOB");
      createTable("Profiler_Devices", "DeviceId INTEGER", "Data BLOB");
      createTable("Profiler_Processes", "DeviceId INTEGER", "ProcessId INTEGER", "StartTime INTEGER", "EndTime INTEGER",
                  "HasAgent INTEGER", "LastKnownAttachedTime INTEGER", "Data BLOB");
      createUniqueIndex("Profiler_Processes", "DeviceId", "ProcessId", "StartTime");
      createUniqueIndex("Profiler_Devices", "DeviceId");
      createUniqueIndex("Profiler_Bytes", "Id", "Session");
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  @Override
  public void prepareStatements() {
    try {
      createStatement(ProfilerStatements.INSERT_DEVICE,
                      "INSERT INTO Profiler_Devices (DeviceId, Data) values (?, ?)", Statement.RETURN_GENERATED_KEYS);
      createStatement(ProfilerStatements.UPDATE_DEVICE,
                      "UPDATE Profiler_Devices SET Data = ? WHERE DeviceId = ?");
      createStatement(ProfilerStatements.INSERT_PROCESS,
                      "INSERT OR REPLACE INTO Profiler_Processes (DeviceId, ProcessId, StartTime, EndTime, Data) values (?, ?, ?, ?, ?)");
      createStatement(ProfilerStatements.UPDATE_PROCESS,
                      "UPDATE Profiler_Processes Set EndTime = ?, Data = ? WHERE DeviceId = ? AND ProcessId = ? AND StartTime = ?");
      createStatement(ProfilerStatements.SELECT_PROCESSES,
                      "SELECT Data from Profiler_Processes WHERE DeviceId = ? AND (EndTime > ? OR EndTime = 0) AND StartTime < ?");
      createStatement(ProfilerStatements.SELECT_PROCESS_BY_ID,
                      "SELECT Data from Profiler_Processes WHERE DeviceId = ? AND ProcessId = ? AND StartTime = ?");
      createStatement(ProfilerStatements.SELECT_DEVICE,
                      "SELECT Data from Profiler_Devices");
      createStatement(ProfilerStatements.FIND_AGENT_STATUS,
                      "SELECT HasAgent, LastKnownAttachedTime from Profiler_Processes WHERE DeviceId = ? AND ProcessId = ? AND StartTime = ?");
      createStatement(ProfilerStatements.UPDATE_AGENT_STATUS,
                      "UPDATE Profiler_Processes SET HasAgent = ?, LastKnownAttachedTime = ? WHERE DeviceId = ? AND ProcessId = ? AND StartTime = ?");
      createStatement(ProfilerStatements.INSERT_BYTES, "INSERT OR REPLACE INTO Profiler_Bytes (Id, Session, Data) VALUES (?, ?, ?)");
      createStatement(ProfilerStatements.GET_BYTES, "SELECT Data FROM Profiler_Bytes WHERE Id = ? AND Session = ?");
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  @NotNull
  public Profiler.GetDevicesResponse getDevices(@NotNull Profiler.GetDevicesRequest request) {
    if (isClosed()) {
      return Profiler.GetDevicesResponse.getDefaultInstance();
    }

    synchronized (myLock) {
      Profiler.GetDevicesResponse.Builder responseBuilder = Profiler.GetDevicesResponse.newBuilder();
      try {
        ResultSet results = executeQuery(ProfilerStatements.SELECT_DEVICE);
        while (results.next()) {
          responseBuilder.addDevice(Common.Device.parseFrom(results.getBytes(1)));
        }
      }
      catch (InvalidProtocolBufferException | SQLException ex) {
        getLogger().error(ex);
      }
      return responseBuilder.build();
    }
  }

  @NotNull
  public Profiler.GetProcessesResponse getProcesses(@NotNull Profiler.GetProcessesRequest request) {
    if (isClosed()) {
      return Profiler.GetProcessesResponse.getDefaultInstance();
    }
    synchronized (myLock) {
      Profiler.GetProcessesResponse.Builder responseBuilder = Profiler.GetProcessesResponse.newBuilder();
      try {
        ResultSet results = executeQuery(ProfilerStatements.SELECT_PROCESSES, request.getDeviceId(), Long.MIN_VALUE, Long.MAX_VALUE);
        while (results.next()) {
          byte[] data = results.getBytes(1);
          Common.Process process = data == null ? Common.Process.getDefaultInstance() : Common.Process.parseFrom(data);
          responseBuilder.addProcess(process);
        }
      }
      catch (InvalidProtocolBufferException | SQLException ex) {
        getLogger().error(ex);
      }
      return responseBuilder.build();
    }
  }

  public void insertOrUpdateDevice(@NotNull Common.Device device) {
    synchronized (myLock) {
      //TODO: Update start/end times with times polled from device.
      //End time always equals now, start time comes from device. This way if we get disconnected we still have an accurate end time.
      Common.Session session = Common.Session.newBuilder().setDeviceId(device.getDeviceId()).build();
      if (mySessionIdLookup.containsKey(session)) {
        execute(ProfilerStatements.UPDATE_DEVICE, device.toByteArray(), device.getDeviceId());
      }
      else {
        long id = executeWithGeneratedKeys(ProfilerStatements.INSERT_DEVICE, device.toString(), device.toByteArray());
        mySessionIdLookup.put(session, id);
      }
    }
  }

  public void insertOrUpdateProcess(@NotNull DeviceId devicdId, @NotNull Common.Process process) {
    synchronized (myLock) {
      try {
        ResultSet results = executeQuery(ProfilerStatements.SELECT_PROCESS_BY_ID, devicdId.get(), process.getPid(), 0L);
        if (results.next()) {
          execute(ProfilerStatements.UPDATE_PROCESS, 0L, process.toByteArray(), devicdId.get(), process.getPid(), 0L);
        }
        else {
          //TODO: Properly set end time. Here the end time comes from the device, or is set to now, so we don't leave
          //end times open.
          execute(ProfilerStatements.INSERT_PROCESS, devicdId.get(), process.getPid(), 0L, 0L, process.toByteArray());
        }
      }
      catch (SQLException ex) {
        getLogger().error(ex);
      }
    }
  }

  /**
   * NOTE: Currently an assumption is made such that the agent lives and dies along with the process it is attached to.
   * If for some reason the agent freezes and we stop receiving a valid heartbeat momentarily, this will not downgrade the HasAgent status
   * in the process entry.
   */
  public void updateAgentStatus(@NotNull DeviceId devicdId,
                                @NotNull Common.Process process,
                                @NotNull Profiler.AgentStatusResponse agentStatus) {
    synchronized (myLock) {
      try {
        ResultSet results =
          executeQuery(ProfilerStatements.FIND_AGENT_STATUS, devicdId.get(), process.getPid(), 0L);
        if (results.next()) {
          Profiler.AgentStatusResponse.Status status = Profiler.AgentStatusResponse.Status.forNumber(results.getInt(1));
          switch (status) {
            case DETACHED:
            case UNSPECIFIED:
            case UNRECOGNIZED:
              status = agentStatus.getStatus();
              break;
            case ATTACHED:
              break;
          }

          execute(ProfilerStatements.UPDATE_AGENT_STATUS, status.ordinal(), agentStatus.getLastTimestamp(),
                  devicdId.get(), process.getPid(), 0L);
        }
      }
      catch (SQLException ex) {
        getLogger().error(ex);
      }
    }
  }

  @NotNull
  public Profiler.AgentStatusResponse getAgentStatus(@NotNull Profiler.AgentStatusRequest request) {
    synchronized (myLock) {
      Profiler.AgentStatusResponse.Builder responseBuilder = Profiler.AgentStatusResponse.newBuilder();
      try {
        ResultSet results =
          executeQuery(ProfilerStatements.FIND_AGENT_STATUS, request.getDeviceId(), request.getProcessId(), 0L);
        if (results.next()) {
          responseBuilder.setStatusValue(results.getInt(1));
          responseBuilder.setLastTimestamp(results.getLong(2));
        }
      }
      catch (SQLException ex) {
        getLogger().error(ex);
      }

      return responseBuilder.build();
    }
  }

  public void insertOrUpdateBytes(@NotNull String id, @NotNull Common.Session session, @NotNull Profiler.BytesResponse response) {
    execute(ProfilerStatements.INSERT_BYTES, id, session, response.toByteArray());
  }

  @Nullable
  public Profiler.BytesResponse getBytes(@NotNull Profiler.BytesRequest request) {
    try {
      ResultSet results =
        executeQuery(ProfilerStatements.GET_BYTES, request.getId(), request.getSession());
      if (results.next()) {
        return Profiler.BytesResponse.parseFrom(results.getBytes(1));
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      getLogger().error(ex);
    }

    return null;
  }
}
