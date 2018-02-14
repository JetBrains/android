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
import com.android.tools.profiler.proto.Profiler.*;
import com.android.tools.profiler.protobuf3jarjar.InvalidProtocolBufferException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Class that wraps database access for profiler level services.
 * The primary information managed by this class is device/process lifetime.
 */
public class ProfilerTable extends DataStoreTable<ProfilerTable.ProfilerStatements> {
  public enum ProfilerStatements {
    INSERT_DEVICE,
    INSERT_PROCESS,
    UPDATE_PROCESS,
    INSERT_SESSION,
    UPDATE_SESSION,
    SELECT_PROCESSES,
    SELECT_PROCESS_BY_ID,
    SELECT_DEVICE,
    SELECT_SESSIONS,
    SELECT_SESSION_BY_ID,
    FIND_AGENT_STATUS,
    UPDATE_AGENT_STATUS,
    INSERT_BYTES,
    GET_BYTES
  }

  // Need to have a lock due to processes being updated and queried at the same time.
  // If a process is being queried, while one is being updated it will not get
  // returned in the query results, this results in the UI flickering.
  private final Object myLock = new Object();

  @Override
  public void initialize(@NotNull Connection connection) {
    super.initialize(connection);
    try {
      createTable("Profiler_Bytes", "Id STRING NOT NULL", "Session INTEGER", "Data BLOB");
      createTable("Profiler_Devices", "DeviceId INTEGER", "Data BLOB");
      createTable("Profiler_Processes", "DeviceId INTEGER", "ProcessId INTEGER", "StartTime INTEGER", "EndTime INTEGER",
                  "HasAgent INTEGER", "LastKnownAttachedTime INTEGER", "Data BLOB");
      createTable("Profiler_Sessions", "SessionId INTEGER", "DeviceId INTEGER", "ProcessId INTEGER", "StartTime INTEGER",
                  "EndTime INTEGER", "StartTimeEpochMs INTEGER", "NAME TEXT");
      createUniqueIndex("Profiler_Processes", "DeviceId", "ProcessId", "StartTime");
      createUniqueIndex("Profiler_Devices", "DeviceId");
      createUniqueIndex("Profiler_Bytes", "Id", "Session");
      createUniqueIndex("Profiler_Sessions", "SessionId");
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  @Override
  public void prepareStatements() {
    try {
      createStatement(ProfilerStatements.INSERT_DEVICE,
                      "INSERT OR REPLACE INTO Profiler_Devices (DeviceId, Data) values (?, ?)");
      createStatement(ProfilerStatements.INSERT_PROCESS,
                      "INSERT OR REPLACE INTO Profiler_Processes (DeviceId, ProcessId, StartTime, EndTime, Data) values (?, ?, ?, ?, ?)");
      createStatement(ProfilerStatements.UPDATE_PROCESS,
                      "UPDATE Profiler_Processes Set EndTime = ?, Data = ? WHERE DeviceId = ? AND ProcessId = ? AND StartTime = ?");
      createStatement(ProfilerStatements.INSERT_SESSION,
                      "INSERT OR REPLACE INTO Profiler_Sessions " +
                      "(SessionId, DeviceId, ProcessId, StartTime, EndTime, StartTimeEpochMs, Name) " +
                      "values (?, ?, ?, ?, ?, ?, ?)");
      createStatement(ProfilerStatements.UPDATE_SESSION,
                      "UPDATE Profiler_Sessions Set EndTime = ? WHERE SessionId = ?");
      createStatement(ProfilerStatements.SELECT_PROCESSES,
                      "SELECT Data from Profiler_Processes WHERE DeviceId = ? AND (EndTime > ? OR EndTime = 0) AND StartTime < ?");
      createStatement(ProfilerStatements.SELECT_PROCESS_BY_ID,
                      "SELECT Data from Profiler_Processes WHERE DeviceId = ? AND ProcessId = ? AND StartTime = ?");
      createStatement(ProfilerStatements.SELECT_DEVICE,
                      "SELECT Data from Profiler_Devices");
      createStatement(ProfilerStatements.SELECT_SESSIONS,
                      "SELECT * from Profiler_Sessions ORDER BY SessionId ASC");
      createStatement(ProfilerStatements.SELECT_SESSION_BY_ID,
                      "SELECT * from Profiler_Sessions WHERE SessionId = ?");
      createStatement(ProfilerStatements.FIND_AGENT_STATUS,
                      "SELECT HasAgent, LastKnownAttachedTime from Profiler_Processes WHERE DeviceId = ? AND ProcessId = ? AND StartTime = ?");
      createStatement(ProfilerStatements.UPDATE_AGENT_STATUS,
                      "UPDATE Profiler_Processes SET HasAgent = ?, LastKnownAttachedTime = ? WHERE DeviceId = ? AND ProcessId = ? AND StartTime = ?");
      createStatement(ProfilerStatements.INSERT_BYTES, "INSERT OR REPLACE INTO Profiler_Bytes (Id, Session, Data) VALUES (?, ?, ?)");
      createStatement(ProfilerStatements.GET_BYTES, "SELECT Data FROM Profiler_Bytes WHERE Id = ? AND Session = ?");
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  @NotNull
  public GetDevicesResponse getDevices(@NotNull GetDevicesRequest request) {
    if (isClosed()) {
      return GetDevicesResponse.getDefaultInstance();
    }

    synchronized (myLock) {
      GetDevicesResponse.Builder responseBuilder = GetDevicesResponse.newBuilder();
      try {
        ResultSet results = executeQuery(ProfilerStatements.SELECT_DEVICE);
        while (results.next()) {
          responseBuilder.addDevice(Common.Device.parseFrom(results.getBytes(1)));
        }
      }
      catch (InvalidProtocolBufferException | SQLException ex) {
        onError(ex);
      }
      return responseBuilder.build();
    }
  }

  @NotNull
  public GetProcessesResponse getProcesses(@NotNull GetProcessesRequest request) {
    if (isClosed()) {
      return GetProcessesResponse.getDefaultInstance();
    }
    synchronized (myLock) {
      GetProcessesResponse.Builder responseBuilder = GetProcessesResponse.newBuilder();
      try {
        ResultSet results = executeQuery(ProfilerStatements.SELECT_PROCESSES, request.getDeviceId(), Long.MIN_VALUE, Long.MAX_VALUE);
        while (results.next()) {
          byte[] data = results.getBytes(1);
          Common.Process process = data == null ? Common.Process.getDefaultInstance() : Common.Process.parseFrom(data);
          responseBuilder.addProcess(process);
        }
      }
      catch (InvalidProtocolBufferException | SQLException ex) {
        onError(ex);
      }
      return responseBuilder.build();
    }
  }

  @NotNull
  public GetSessionMetaDataResponse getSessionMetaData(long sessionId) {
    if (isClosed()) {
      return GetSessionMetaDataResponse.getDefaultInstance();
    }

    // Note - this is not being called from multiple threads at the moment.
    // If we ever need to call getSessions and insertOrUpdateSession synchronously, we should protect the logic below.
    GetSessionMetaDataResponse.Builder responseBuilder = GetSessionMetaDataResponse.newBuilder();
    try {
      ResultSet results = executeQuery(ProfilerStatements.SELECT_SESSION_BY_ID, sessionId);
      while (results.next()) {
        responseBuilder.setData(Common.SessionMetaData.newBuilder()
                                  .setSessionId(results.getLong(1))
                                  .setStartTimestampEpochMs(results.getLong(6))
                                  .setSessionName(results.getString(7))
                                  .build());
      }
    }
    catch (SQLException ex) {
      onError(ex);
    }

    return responseBuilder.build();
  }

  @NotNull
  public GetSessionsResponse getSessions() {
    if (isClosed()) {
      return GetSessionsResponse.getDefaultInstance();
    }

    // Note - this is not being called from multiple threads at the moment.
    // If we ever need to call getSessions and insertOrUpdateSession synchronously, we should protect the logic below.
    GetSessionsResponse.Builder responseBuilder = GetSessionsResponse.newBuilder();
    try {
      ResultSet results = executeQuery(ProfilerStatements.SELECT_SESSIONS);
      while (results.next()) {
        responseBuilder.addSessions(Common.Session.newBuilder()
                                      .setSessionId(results.getLong(1))
                                      .setDeviceId(results.getLong(2))
                                      .setPid(results.getInt(3))
                                      .setStartTimestamp(results.getLong(4))
                                      .setEndTimestamp((results.getLong(5)))
                                      .build());
      }
    }
    catch (SQLException ex) {
      onError(ex);
    }

    return responseBuilder.build();
  }

  public void insertOrUpdateDevice(@NotNull Common.Device device) {
    synchronized (myLock) {
      //TODO: Update start/end times with times polled from device.
      //End time always equals now, start time comes from device. This way if we get disconnected we still have an accurate end time.
      execute(ProfilerStatements.INSERT_DEVICE, device.getDeviceId(), device.toByteArray());
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
        onError(ex);
      }
    }
  }

  public void insertOrUpdateSession(@NotNull Common.Session session, @NotNull String name, long startTimeUtc) {
    // Note - this is not being called from multiple threads at the moment.
    // If we ever need to call getSessions and insertOrUpdateSession synchronously, we should protect the logic below.
    execute(ProfilerStatements.INSERT_SESSION, session.getSessionId(), session.getDeviceId(), session.getPid(),
            session.getStartTimestamp(), session.getEndTimestamp(), startTimeUtc, name);
  }

  public void updateSessionEndTime(long sessionId, long endTimestampNs) {
    // Note - this is not being called from multiple threads at the moment.
    // If we ever need to call getSessions and insertOrUpdateSession synchronously, we should protect the logic below.
    execute(ProfilerStatements.UPDATE_SESSION, endTimestampNs, sessionId);
  }

  /**
   * NOTE: Currently an assumption is made such that the agent lives and dies along with the process it is attached to.
   * If for some reason the agent freezes and we stop receiving a valid heartbeat momentarily, this will not downgrade the HasAgent status
   * in the process entry.
   */
  public void updateAgentStatus(@NotNull DeviceId devicdId,
                                @NotNull Common.Process process,
                                @NotNull AgentStatusResponse agentStatus) {
    synchronized (myLock) {
      try {
        ResultSet results =
          executeQuery(ProfilerStatements.FIND_AGENT_STATUS, devicdId.get(), process.getPid(), 0L);
        if (results.next()) {
          AgentStatusResponse.Status status = AgentStatusResponse.Status.forNumber(results.getInt(1));
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
        onError(ex);
      }
    }
  }

  @NotNull
  public AgentStatusResponse getAgentStatus(@NotNull AgentStatusRequest request) {
    synchronized (myLock) {
      AgentStatusResponse.Builder responseBuilder = AgentStatusResponse.newBuilder();
      try {
        ResultSet results =
          executeQuery(ProfilerStatements.FIND_AGENT_STATUS, request.getDeviceId(), request.getPid(), 0L);
        if (results.next()) {
          responseBuilder.setStatusValue(results.getInt(1));
          responseBuilder.setLastTimestamp(results.getLong(2));
        }
      }
      catch (SQLException ex) {
        onError(ex);
      }

      return responseBuilder.build();
    }
  }

  public void insertOrUpdateBytes(@NotNull String id, @NotNull Common.Session session, @NotNull BytesResponse response) {
    execute(ProfilerStatements.INSERT_BYTES, id, session.getSessionId(), response.toByteArray());
  }

  @Nullable
  public BytesResponse getBytes(@NotNull BytesRequest request) {
    try {
      ResultSet results =
        executeQuery(ProfilerStatements.GET_BYTES, request.getId(), request.getSession().getSessionId());
      if (results.next()) {
        return BytesResponse.parseFrom(results.getBytes(1));
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      onError(ex);
    }

    return null;
  }
}
