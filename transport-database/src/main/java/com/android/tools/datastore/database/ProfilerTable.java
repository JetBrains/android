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

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler.GetSessionMetaDataResponse;
import com.android.tools.profiler.proto.Profiler.GetSessionsResponse;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.jetbrains.annotations.NotNull;

/**
 * Class that wraps database access for profiler level services.
 * The primary information managed by this class are sessions.
 */
public class ProfilerTable extends DataStoreTable<ProfilerTable.ProfilerStatements> {
  public enum ProfilerStatements {
    INSERT_SESSION,
    UPDATE_SESSION,
    SELECT_SESSIONS,
    SELECT_SESSION_BY_ID,
    DELETE_SESSION_BY_ID,
  }

  @Override
  public void initialize(@NotNull Connection connection) {
    super.initialize(connection);
    try {
      // In the legacy pipeline we set the device ID to the stream ID of a Session.
      createTable("Profiler_Sessions", "SessionId INTEGER", "DeviceId INTEGER", "ProcessId INTEGER", "StartTime INTEGER",
                  "EndTime INTEGER", "StartTimeEpochMs INTEGER", "Name TEXT", "ProcessAbi TEXT", "JvmtiEnabled INTEGER", "TypeId INTEGER");
      createUniqueIndex("Profiler_Sessions", "SessionId");
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  @Override
  public void prepareStatements() {
    try {
      createStatement(ProfilerStatements.INSERT_SESSION,
                      "INSERT OR REPLACE INTO Profiler_Sessions " +
                      "(SessionId, DeviceId, ProcessId, StartTime, EndTime, StartTimeEpochMs, Name, ProcessAbi, JvmtiEnabled, TypeId) " +
                      "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
      createStatement(ProfilerStatements.UPDATE_SESSION,
                      "UPDATE Profiler_Sessions Set EndTime = ? WHERE SessionId = ?");
      createStatement(ProfilerStatements.SELECT_SESSIONS,
                      "SELECT * from Profiler_Sessions ORDER BY SessionId ASC");
      createStatement(ProfilerStatements.SELECT_SESSION_BY_ID,
                      "SELECT * from Profiler_Sessions WHERE SessionId = ?");
      createStatement(ProfilerStatements.DELETE_SESSION_BY_ID,
                      "DELETE from Profiler_Sessions WHERE SessionId = ?");
    }
    catch (SQLException ex) {
      onError(ex);
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
          .setStreamId(results.getLong(2))
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
            .newBuilder().setSessionId(results.getLong(1)).setStartTimestampEpochMs(results.getLong(6))
            .setSessionName(results.getString(7)).setProcessAbi(results.getString(8))
            .setJvmtiEnabled(results.getBoolean(9))
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
            .newBuilder().setSessionId(results.getLong(1)).setStreamId(results.getLong(2)).setPid(results.getInt(3))
            .setStartTimestamp(results.getLong(4)).setEndTimestamp((results.getLong(5)))
            .build());
      }
    }
    catch (SQLException ex) {
      onError(ex);
    }

    return responseBuilder.build();
  }

  public void deleteSession(long sessionId) {
    execute(ProfilerStatements.DELETE_SESSION_BY_ID, sessionId);
  }

  public void insertOrUpdateSession(@NotNull Common.Session session,
                                    @NotNull String name,
                                    long startTimeUtc,
                                    String processAbi,
                                    boolean jvmtiEnabled,
                                    Common.SessionMetaData.SessionType sessionType) {
    // Note - this is not being called from multiple threads at the moment.
    // If we ever need to call getSessions and insertOrUpdateSession synchronously, we should protect the logic below.
    execute(ProfilerStatements.INSERT_SESSION, session.getSessionId(), session.getStreamId(), session.getPid(),
            session.getStartTimestamp(), session.getEndTimestamp(), startTimeUtc, name, processAbi, jvmtiEnabled,
            sessionType.getNumber());
  }

  public void updateSessionEndTime(long sessionId, long endTimestampNs) {
    // Note - this is not being called from multiple threads at the moment.
    // If we ever need to call getSessions and insertOrUpdateSession synchronously, we should protect the logic below.
    execute(ProfilerStatements.UPDATE_SESSION, endTimestampNs, sessionId);
  }
}
