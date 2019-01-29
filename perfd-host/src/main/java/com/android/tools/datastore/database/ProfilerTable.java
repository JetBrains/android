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
    UPDATE_DEVICE_LAST_KNOWN_TIME,
    INSERT_PROCESS,
    UPDATE_PROCESS_STATE,
    INSERT_SESSION,
    UPDATE_SESSION,
    SELECT_PROCESSES,
    SELECT_PROCESS_BY_ID,
    SELECT_DEVICE,
    SELECT_DEVICE_LAST_KNOWN_TIME,
    SELECT_SESSIONS,
    SELECT_SESSION_BY_ID,
    DELETE_SESSION_BY_ID,
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
      createTable("Profiler_Devices", "DeviceId INTEGER", "LastKnownTime INTEGER", "Data BLOB");
      createTable("Profiler_Processes", "DeviceId INTEGER", "ProcessId INTEGER", "Name STRING NOT NULL", "State INTEGER",
                  "StartTime INTEGER", "Arch STRING NOT NULL", "AgentStatus INTEGER", "IsAgentAttachable INTEGER");
      createTable("Profiler_Sessions", "SessionId INTEGER", "DeviceId INTEGER", "ProcessId INTEGER", "StartTime INTEGER",
                  "EndTime INTEGER", "StartTimeEpochMs INTEGER", "NAME TEXT", "JvmtiEnabled INTEGER", "LiveAllocationEnabled INTEGER",
                  "TypeId INTEGER");
      createUniqueIndex("Profiler_Processes", "DeviceId", "ProcessId");
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
      createStatement(ProfilerStatements.UPDATE_DEVICE_LAST_KNOWN_TIME,
                      "UPDATE Profiler_Devices Set LastKnownTime = ? WHERE DeviceId = ?");
      createStatement(ProfilerStatements.INSERT_PROCESS,
                      "INSERT OR REPLACE INTO Profiler_Processes (DeviceId, ProcessId, Name, State, StartTime, Arch) " +
                      "values (?, ?, ?, ?, ?, ?)");
      createStatement(ProfilerStatements.UPDATE_PROCESS_STATE,
                      "UPDATE Profiler_Processes Set State = ? WHERE DeviceId = ? AND ProcessId = ?");
      createStatement(ProfilerStatements.INSERT_SESSION,
                      "INSERT OR REPLACE INTO Profiler_Sessions " +
                      "(SessionId, DeviceId, ProcessId, StartTime, EndTime, StartTimeEpochMs, Name, JvmtiEnabled, LiveAllocationEnabled, TypeId) " +
                      "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
      createStatement(ProfilerStatements.UPDATE_SESSION,
                      "UPDATE Profiler_Sessions Set EndTime = ? WHERE SessionId = ?");
      createStatement(ProfilerStatements.SELECT_PROCESSES,
                      "SELECT DeviceId, ProcessId, Name, State, StartTime, Arch from Profiler_Processes WHERE DeviceId = ?");
      createStatement(ProfilerStatements.SELECT_PROCESS_BY_ID,
                      "SELECT ProcessId from Profiler_Processes WHERE DeviceId = ? AND ProcessId = ?");
      createStatement(ProfilerStatements.SELECT_DEVICE,
                      "SELECT Data from Profiler_Devices");
      createStatement(ProfilerStatements.SELECT_DEVICE_LAST_KNOWN_TIME,
                      "SELECT LastKnownTime FROM Profiler_Devices WHERE DeviceId = ?");
      createStatement(ProfilerStatements.SELECT_SESSIONS,
                      "SELECT * from Profiler_Sessions ORDER BY SessionId ASC");
      createStatement(ProfilerStatements.SELECT_SESSION_BY_ID,
                      "SELECT * from Profiler_Sessions WHERE SessionId = ?");
      createStatement(ProfilerStatements.DELETE_SESSION_BY_ID,
                      "DELETE from Profiler_Sessions WHERE SessionId = ?");
      createStatement(ProfilerStatements.FIND_AGENT_STATUS,
                      "SELECT AgentStatus, IsAgentAttachable from Profiler_Processes WHERE DeviceId = ? AND ProcessId = ?");
      createStatement(ProfilerStatements.UPDATE_AGENT_STATUS,
                      "UPDATE Profiler_Processes SET AgentStatus = ?, IsAgentAttachable = ? WHERE DeviceId = ? AND ProcessId = ?");
      createStatement(ProfilerStatements.INSERT_BYTES, "INSERT OR REPLACE INTO Profiler_Bytes (Id, Session, Data) VALUES (?, ?, ?)");
      createStatement(ProfilerStatements.GET_BYTES, "SELECT Data FROM Profiler_Bytes WHERE Id = ? AND Session = ?");
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

  public long getDeviceLastKnownTime(@NotNull DeviceId deviceId) {
    if (isClosed()) {
      return Long.MIN_VALUE;
    }

    synchronized (myLock) {
      try {
        ResultSet results = executeQuery(ProfilerStatements.SELECT_DEVICE_LAST_KNOWN_TIME, deviceId.get());
        if (results.next()) {
          return results.getLong(1);
        }
      }
      catch (SQLException ex) {
        onError(ex);
      }
    }

    return Long.MIN_VALUE;
  }

  @NotNull
  public GetProcessesResponse getProcesses(@NotNull GetProcessesRequest request) {
    if (isClosed()) {
      return GetProcessesResponse.getDefaultInstance();
    }
    synchronized (myLock) {
      GetProcessesResponse.Builder responseBuilder = GetProcessesResponse.newBuilder();
      try {
        ResultSet results = executeQuery(ProfilerStatements.SELECT_PROCESSES, request.getDeviceId());
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
  }

  @NotNull
  public Common.Session getSessionById(long sessionId) {
    if (isClosed()) {
      return Common.Session.getDefaultInstance();
    }

    Common.Session.Builder builder = Common.Session.newBuilder();
    try {
      ResultSet results = executeQuery(ProfilerStatements.SELECT_SESSION_BY_ID, sessionId);
      if (results.next()) {
        builder.setSessionId(results.getLong(1))
               .setDeviceId(results.getLong(2))
               .setPid(results.getInt(3))
               .setStartTimestamp(results.getLong(4))
               .setEndTimestamp((results.getLong(5)));
      }
    }
    catch (SQLException ex) {
      onError(ex);
    }

    return builder.build();
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
        responseBuilder.setData(
          Common.SessionMetaData
            .newBuilder().setSessionId(results.getLong(1)).setStartTimestampEpochMs(results.getLong(6)).setSessionName(results.getString(7))
            .setJvmtiEnabled(results.getBoolean(8)).setLiveAllocationEnabled(results.getBoolean(9))
            .setType(Common.SessionMetaData.SessionType.forNumber(results.getInt(10)))
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
        responseBuilder.addSessions(
          Common.Session
            .newBuilder().setSessionId(results.getLong(1)).setDeviceId(results.getLong(2)).setPid(results.getInt(3))
            .setStartTimestamp(results.getLong(4)).setEndTimestamp((results.getLong(5)))
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

  public void deleteSession(long sessionId) {
    synchronized (myLock) {
      execute(ProfilerStatements.DELETE_SESSION_BY_ID, sessionId);
    }
  }

  public void updateDeviceLastKnownTime(@NotNull Common.Device device, long lastKnownTimeNs) {
    synchronized (myLock) {
      execute(ProfilerStatements.UPDATE_DEVICE_LAST_KNOWN_TIME, lastKnownTimeNs, device.getDeviceId());
    }
  }

  public void insertOrUpdateProcess(@NotNull DeviceId devicdId, @NotNull Common.Process process) {
    synchronized (myLock) {
      try {
        ResultSet results = executeQuery(ProfilerStatements.SELECT_PROCESS_BY_ID, devicdId.get(), process.getPid());
        if (results.next()) {
          execute(ProfilerStatements.UPDATE_PROCESS_STATE, process.getStateValue(), devicdId.get(), process.getPid());
        }
        else {
          execute(ProfilerStatements.INSERT_PROCESS, devicdId.get(), process.getPid(), process.getName(), process.getStateValue(),
                  process.getStartTimestampNs(), process.getAbiCpuArch());
        }
      }
      catch (SQLException ex) {
        onError(ex);
      }
    }
  }

  public void insertOrUpdateSession(@NotNull Common.Session session,
                                    @NotNull String name,
                                    long startTimeUtc,
                                    boolean jvmtiEnabled,
                                    boolean liveAllocationEnabled,
                                    Common.SessionMetaData.SessionType sessionType) {
    // Note - this is not being called from multiple threads at the moment.
    // If we ever need to call getSessions and insertOrUpdateSession synchronously, we should protect the logic below.
    execute(ProfilerStatements.INSERT_SESSION, session.getSessionId(), session.getDeviceId(), session.getPid(),
            session.getStartTimestamp(), session.getEndTimestamp(), startTimeUtc, name, jvmtiEnabled, liveAllocationEnabled,
            sessionType.getNumber());
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
        ResultSet results = executeQuery(ProfilerStatements.FIND_AGENT_STATUS, devicdId.get(), process.getPid());
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

          execute(ProfilerStatements.UPDATE_AGENT_STATUS, status.ordinal(), agentStatus.getIsAgentAttachable(),
                  devicdId.get(), process.getPid());
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
        ResultSet results = executeQuery(ProfilerStatements.FIND_AGENT_STATUS, request.getDeviceId(), request.getPid());
        if (results.next()) {
          responseBuilder.setStatusValue(results.getInt(1));
          responseBuilder.setIsAgentAttachable(results.getBoolean(2));
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
