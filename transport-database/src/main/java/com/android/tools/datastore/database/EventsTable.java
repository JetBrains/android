/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.android.tools.profiler.proto.EventProfiler;
import com.android.tools.idea.protobuf.InvalidProtocolBufferException;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class EventsTable extends DataStoreTable<EventsTable.EventStatements> {
  public enum EventStatements {
    FIND_ACTIVITY,
    INSERT_ACTIVITY,
    INSERT_SYSTEM,
    QUERY_SYSTEM,
    QUERY_ACTIVITY,
  }

  @Override
  public void initialize(@NotNull Connection connection) {
    super.initialize(connection);
    try {
      createTable("Events_Activity", "Id INTEGER NOT NULL", "Session INTEGER NOT NULL", "Data BLOB");
      createTable("Events_System", "Id INTEGER NOT NULL", "Session INTEGER NOT NULL", "StartTime INTEGER", "EndTime INTEGER", "Data BLOB");
      createUniqueIndex("Events_Activity", "Id", "Session");
      createUniqueIndex("Events_System", "Id", "Session");
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  @Override
  public void prepareStatements() {
    try {
      createStatement(EventStatements.FIND_ACTIVITY, "SELECT Data from Events_Activity WHERE Id = ? AND Session = ?");
      createStatement(EventStatements.INSERT_ACTIVITY, "INSERT OR REPLACE INTO Events_Activity (Id, Session, Data) values (?, ?, ?)");
      createStatement(EventStatements.INSERT_SYSTEM,
                      "INSERT OR REPLACE INTO Events_System (Id, Session, StartTime, EndTime, Data) values ( ?, ?, ?, ?, ?)");
      createStatement(EventStatements.QUERY_SYSTEM,
                      "SELECT Data from Events_System WHERE Session = ? AND (EndTime >= ? OR EndTime = 0) AND StartTime < ?;");
      createStatement(EventStatements.QUERY_ACTIVITY, "SELECT Data from Events_Activity WHERE Session = ?");
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  public EventProfiler.ActivityData findActivityDataOrNull(Common.Session session, long id) {
    try {
      ResultSet results = executeQuery(EventStatements.FIND_ACTIVITY, id, session.getSessionId());
      List<EventProfiler.ActivityData> datas = getActivityDataFromResultSet(results);
      if (!datas.isEmpty()) {
        return datas.get(0);
      }
    }
    catch (SQLException ex) {
      onError(ex);
    }
    return null;
  }

  public void insertOrReplace(long id, Common.Session session, EventProfiler.ActivityData activity) {
    execute(EventStatements.INSERT_ACTIVITY, id, session.getSessionId(), activity.toByteArray());
  }

  public List<EventProfiler.ActivityData> getActivityDataBySession(Common.Session session) {
    try {
      ResultSet results = executeQuery(EventStatements.QUERY_ACTIVITY, session.getSessionId());
      return getActivityDataFromResultSet(results);
    }
    catch (SQLException ex) {
      onError(ex);
    }
    return new ArrayList<>();
  }

  public void insertOrReplace(long id, Common.Session session, EventProfiler.SystemData activity) {
    execute(EventStatements.INSERT_SYSTEM, id, session.getSessionId(), activity.getStartTimestamp(), activity.getEndTimestamp(),
            activity.toByteArray());
  }

  public List<EventProfiler.SystemData> getSystemDataByRequest(EventProfiler.EventDataRequest request) {
    List<EventProfiler.SystemData> events = new ArrayList<>();
    try {
      ResultSet results =
        executeQuery(EventStatements.QUERY_SYSTEM, request.getSession().getSessionId(), request.getStartTimestamp(),
                     request.getEndTimestamp());
      while (results.next()) {
        EventProfiler.SystemData.Builder data = EventProfiler.SystemData.newBuilder();
        data.mergeFrom(results.getBytes(1));
        events.add(data.build());
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      onError(ex);
    }
    return events;
  }

  private static List<EventProfiler.ActivityData> getActivityDataFromResultSet(ResultSet results) {
    List<EventProfiler.ActivityData> activities = new ArrayList<>();
    try {
      while (results.next()) {
        EventProfiler.ActivityData.Builder data = EventProfiler.ActivityData.newBuilder();
        data.mergeFrom(results.getBytes(1));
        activities.add(data.build());
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      onError(ex);
    }
    return activities;
  }
}
