/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.protobuf3jarjar.*;
import java.sql.Statement;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class UnifiedEventsTable extends DataStoreTable<UnifiedEventsTable.Statements> {
  public enum Statements {
    // Since no data should be updated after it has been inserted we drop any duplicated request from the poller.
    INSERT(
      "INSERT OR IGNORE INTO [UnifiedEventsTable] (StreamId, SessionId, EventId, Kind, Type, Timestamp, Data) VALUES (?, ?, ?, ?, ?, ?, ?)"),
    QUERY_WITHIN_TIME("SELECT Data FROM [UnifiedEventsTable] WHERE Timestamp >= ? AND Timestamp <= ?");

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
      createTable("UnifiedEventsTable",
                  "StreamId INTEGER NOT NULL", // Optional filter, required for all data.
                  "SessionId INTEGER NOT NULL", // Optional filter, not required for data (eg device/process).
                  "EventId INTEGER NOT NULL", // Optional filter, not required for data.
                  "Kind INTEGER NOT NULL", // Required filter, required for all data.
                  "Type INTEGER NOT NULL", // Post process filter, not required for data.
                  "Timestamp INTEGER NOT NULL", // Optional filter, required for all data.
                  "Data BLOB");

      createUniqueIndex("UnifiedEventsTable", "StreamId", "SessionId", "EventId", "Kind", "Type", "Timestamp");
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }


  public void insertUnifiedEvents(long streamId, @NotNull List<Profiler.Event> eventList) {
    executeBatch(Statements.INSERT, eventList,
            (event -> new Object[]{streamId,
              event.getSessionId(),
              event.getEventId(),
              event.getKind().getNumber(),
              event.getType().getNumber(),
              event.getTimestamp(),
              event.toByteArray()}));
  }

  public List<Profiler.Event> queryUnifiedEvents(@NotNull Profiler.GetEventsRequest request) {
    return queryUnifiedEvents(Statements.QUERY_WITHIN_TIME, request.getFromTimestamp(), request.getToTimestamp());
  }

  public List<Profiler.EventGroup> queryUnifiedEventGroups(@NotNull Profiler.GetEventGroupsRequest request) {
    List<Object> params = new ArrayList<>();
    StringBuilder sql = new StringBuilder("SELECT [Data] From [UnifiedEventsTable] WHERE Kind = ?");
    params.add(request.getKind().getNumber());
    if (request.getSessionId() != 0) {
      sql.append(" AND SessionId = ?");
      params.add(request.getSessionId());
    }

    if (request.getFromTimestamp() != 0) {
      sql.append(" AND Timestamp >= ?");
      params.add(request.getFromTimestamp());
    }

    if (request.getToTimestamp() != 0) {
      sql.append(" AND Timestamp <= ?");
      params.add(request.getToTimestamp());
    }

    List<Profiler.EventGroup> groups = new ArrayList<>();
    try {
      ResultSet results = executeOneTimeQuery(sql.toString(), params.toArray());
      HashMap<Long, Profiler.EventGroup.Builder> builderGroups = new HashMap<>();
      while (results.next()) {
        Profiler.Event event = Profiler.Event.parser().parseFrom(results.getBytes(1));
        Profiler.EventGroup.Builder group = builderGroups.computeIfAbsent(event.getEventId(), key -> Profiler.EventGroup.newBuilder());
        group.addEvents(event);
      }
      builderGroups.values().stream().forEach((builder) -> {
        groups.add(builder.build());
      });
    } catch (SQLException | InvalidProtocolBufferException ex) {
      onError(ex);
    }
    return groups;
  }

  private List<Profiler.Event> queryUnifiedEvents(Statements stmt, Object... args) {
    List<Profiler.Event> records = new ArrayList<>();
    try {
      ResultSet results = executeQuery(stmt, args);
      while (results.next()) {
        records.add(Profiler.Event.parser().parseFrom(results.getBytes(1)));
      }
    }
    catch (SQLException | InvalidProtocolBufferException ex) {
      onError(ex);
    }
    return records;
  }
}
