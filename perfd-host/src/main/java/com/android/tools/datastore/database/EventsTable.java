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
import com.google.protobuf3jarjar.InvalidProtocolBufferException;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EventsTable extends DataStoreTable<EventsTable.EventStatements> {
  public enum EventStatements {
    FIND_ACTIVITY,
    INSERT_ACTIVITY,
    INSERT_SYSTEM,
    QUERY_SYSTEM,
    QUERY_ACTIVITY,
  }

  private static Logger getLogger() {
    return Logger.getInstance(EventsTable.class);
  }

  @Override
  public void initialize(@NotNull Connection connection) {
    super.initialize(connection);
    try {
      createTable("Events_Activity", "Id INTEGER NOT NULL", "AppId INTEGER NOT NULL", "Session INTEGER NOT NULL", "Data BLOB");
      createTable("Events_System", "Id INTEGER NOT NULL", "AppId INTEGER NOT NULL", "Session INTEGER NOT NULL", "StartTime INTEGER", "EndTime INTEGER", "Data BLOB");
      createUniqueIndex("Events_Activity", "Id", "AppId", "Session");
      createUniqueIndex("Events_System", "Id", "AppId", "Session");
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  @Override
  public void prepareStatements() {
    try {
      createStatement(EventStatements.FIND_ACTIVITY, "SELECT Data from Events_Activity WHERE Id = ? AND AppId = ? AND Session = ?");
      createStatement(EventStatements.INSERT_ACTIVITY, "INSERT OR REPLACE INTO Events_Activity (Id, AppId, Session, Data) values (?, ?, ?, ?)");
      createStatement(EventStatements.INSERT_SYSTEM,
                      "INSERT OR REPLACE INTO Events_System (Id, AppId, Session, StartTime, EndTime, Data) values ( ?, ?, ?, ?, ?, ?)");
      createStatement(EventStatements.QUERY_SYSTEM,
                      "SELECT Data from Events_System WHERE Session = ? AND AppId = ? AND (EndTime >= ? OR EndTime = 0) AND StartTime < ?;");
      createStatement(EventStatements.QUERY_ACTIVITY, "SELECT Data from Events_Activity WHERE AppId = ? AND Session = ?");
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  public EventProfiler.ActivityData findActivityDataOrNull(long appId, long id, Common.Session session) {
    try {
      ResultSet results = executeQuery(EventStatements.FIND_ACTIVITY, id, appId, session);
      List<EventProfiler.ActivityData> datas = getActivityDataFromResultSet(results);
      if (!datas.isEmpty()) {
        return datas.get(0);
      }
    } catch (SQLException ex) {
      getLogger().error(ex);
    }
    return null;
  }

  public void insertOrReplace(long id, Common.Session session, EventProfiler.ActivityData activity) {
    execute(EventStatements.INSERT_ACTIVITY, id, activity.getProcessId(), session, activity.toByteArray());
  }

  public List<EventProfiler.ActivityData> getActivityDataByApp(long appId, Common.Session session) {
    try {
      ResultSet results = executeQuery(EventStatements.QUERY_ACTIVITY, appId, session);
      return getActivityDataFromResultSet(results);
    } catch (SQLException ex) {
      getLogger().error(ex);
    }
    return null;
  }

  public void insertOrReplace(long id, Common.Session session, EventProfiler.SystemData activity) {
    execute(EventStatements.INSERT_SYSTEM, id, activity.getProcessId(), session, activity.getStartTimestamp(), activity.getEndTimestamp(),
            activity.toByteArray());
  }

  public List<EventProfiler.SystemData> getSystemDataByRequest(EventProfiler.EventDataRequest request) {
    List<EventProfiler.SystemData> events = new ArrayList<>();
    try {
      ResultSet results =
        executeQuery(EventStatements.QUERY_SYSTEM, request.getSession(), request.getProcessId(), request.getStartTimestamp(), request.getEndTimestamp());
      while (results.next()) {
        EventProfiler.SystemData.Builder data = EventProfiler.SystemData.newBuilder();
        data.mergeFrom(results.getBytes(1));
        events.add(data.build());
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      getLogger().error(ex);
    }
    return events;
  }

  private List<EventProfiler.ActivityData> getActivityDataFromResultSet(ResultSet results) {
    List<EventProfiler.ActivityData> activities = new ArrayList<>();
    try {
      while (results.next()) {
        EventProfiler.ActivityData.Builder data = EventProfiler.ActivityData.newBuilder();
        data.mergeFrom(results.getBytes(1));
        activities.add(data.build());
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      getLogger().error(ex);
    }
    return activities;
  }
}
