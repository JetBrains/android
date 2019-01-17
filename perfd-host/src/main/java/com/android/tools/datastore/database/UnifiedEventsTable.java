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

import com.android.annotations.VisibleForTesting;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.protobuf3jarjar.InvalidProtocolBufferException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;

public class UnifiedEventsTable extends DataStoreTable<UnifiedEventsTable.Statements> {
  public enum Statements {
    // Since no data should be updated after it has been inserted we drop any duplicated request from the poller.
    INSERT(
      "INSERT OR IGNORE INTO [UnifiedEventsTable] (StreamId, SessionId, GroupId, Kind, Timestamp, Data) VALUES (?, ?, ?, ?, ?, ?)"),
    // Only used for test.
    QUERY_EVENTS("SELECT Data FROM [UnifiedEventsTable]");

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
                  "GroupId INTEGER NOT NULL", // Optional filter, not required for data.
                  "Kind INTEGER NOT NULL", // Required filter, required for all data.
                  "Timestamp INTEGER NOT NULL", // Optional filter, required for all data.
                  "Data BLOB");

      createUniqueIndex("UnifiedEventsTable", "StreamId", "SessionId", "GroupId", "Kind", "Timestamp");
      createUniqueIndex("UnifiedEventsTable", "StreamId", "Kind", "Timestamp");
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  public void insertUnifiedEvent(long streamId, @NotNull Common.Event event) {
    execute(Statements.INSERT, streamId,
            event.getSessionId(),
            event.getGroupId(),
            event.getKind().getNumber(),
            event.getTimestamp(),
            event.toByteArray());
  }

  @VisibleForTesting
  public List<Common.Event> queryUnifiedEvents() {
    return queryUnifiedEvents(Statements.QUERY_EVENTS);
  }

  /**
   * Queries for set of events then groups them by {@link Common.Event#getGroupId()}
   *
   * The query filters data on {@link Common.Event#getKind()}, {@link Common.Event#getSessionId()}, {@link Common.Event#getGroupId()},
   * and {@link Common.Event#getTimestamp()}.
   *
   * The timestamp is filtered by the optional parameters of {@link Profiler.GetEventGroupsRequest#getFromTimestamp()} and
   * {@link Profiler.GetEventGroupsRequest#getToTimestamp()}.
   *
   * If the parameter {@link Profiler.GetEventGroupsRequest#getFromTimestamp()} is supplied then in addition to all events after the
   * supplied timestamp the latest event before the timestamp is also returned. The exception to this is if the
   * {@link Common.Event#getIsEnded()} returns true, then these events are discarded and no X-1 event is returned.
   *
   * If the parameter {@link Profiler.GetEventGroupsRequest#getToTimestamp()} is supplied then in addition to all events before the
   * supplied timestamp the first event after the timestamp is also returned. The exception to this is if the event at X+1 does not
   * previously have any elements in its group this event is not returned.
   *
   * FromTimestamp X - ToTimestamp Y Example
   *
   * Events
   *  1:  i----i----i-----i
   *  2:----e
   *  3:               i---
   *  4:       i-----i
   *  5:  i---------------
   *
   *  Query: X -> Y
   *          x-----y
   *  Results:
   *  1:  i----i----i-----i
   *  4:       i-----i
   *  5:  i---------------
   *  Note: Group 1 has all elements returned due to +1/-1 behavior.
   *  Note: Group 2 and group 3 do not get returned. Group 2 only has an end event before our from timestamp, while Group 3 only has data
   *  after.
   *  Note: Group 5 gets returned as it has a single event before our from timestamp that does not ended, or ends after our to timestamp.
   * @param request
   */
  public List<Profiler.EventGroup> queryUnifiedEventGroups(@NotNull Profiler.GetEventGroupsRequest request) {
    ArrayList<Object> baseParams = new ArrayList<>();
    List<Object> beforeRangeParams = null;
    List<Object> afterRangeParams = null;

    HashMap<Long, Profiler.EventGroup.Builder> builderGroups = new HashMap<>();
    // The string format allows for altering the group by results for +1 and -1 queries.
    String sql = "SELECT [Data]%s From [UnifiedEventsTable] WHERE Kind = ? %s";
    StringBuilder filter = new StringBuilder();
    baseParams.add(request.getKind().getNumber());
    if (request.getSessionId() != 0) {
      filter.append(" AND SessionId = ?");
      baseParams.add(request.getSessionId());
    }

    if (request.getGroupId() != 0) {
      filter.append(" AND GroupId = ?");
      baseParams.add(request.getGroupId());
    }

    String sqlBefore = String.format(sql, ", MAX(Timestamp), MAX(ROWID)", filter.toString() + " AND Timestamp < ? GROUP BY GroupId");
    String sqlAfter = String.format(sql, ", MIN(Timestamp), MIN(ROWID)", filter.toString() + " AND Timestamp > ? GROUP BY GroupId");
    ArrayList<Object> inRangeQueryParams = new ArrayList<>(baseParams);

    if (request.getFromTimestamp() != 0) {
      beforeRangeParams = new ArrayList<>(baseParams);
      beforeRangeParams.add(request.getFromTimestamp());
      filter.append(" AND Timestamp >= ?");
      inRangeQueryParams.add(request.getFromTimestamp());
    }

    if (request.getToTimestamp() != 0) {
      afterRangeParams = new ArrayList<>(baseParams);
      afterRangeParams.add(request.getToTimestamp());
      filter.append(" AND Timestamp <= ?");
      inRangeQueryParams.add(request.getToTimestamp());
    }

    // Gather before range events if needed.
    // Query before example:
    // SELECT [Data], MAX(Timestamp), MAX(ROWID) From [UnifiedEventsTable] WHERE Kind = ? AND Timestamp < ? GROUP BY GroupId;
    if (beforeRangeParams != null) {
      gatherEvents(sqlBefore, beforeRangeParams, builderGroups, (event) -> !event.getIsEnded());
    }

    // Query example:
    // SELECT [Data] From [UnifiedEventsTable] WHERE Kind = ? AND Timestamp >= ? AND Timestamp <= ?;
    String query = String.format(sql, "", filter.toString());
    gatherEvents(query, inRangeQueryParams, builderGroups, (unused) -> true);

    // Gather after range events if needed.
    // Query after example:
    // SELECT [Data], MIN(Timestamp), MIN(ROWID) From [UnifiedEventsTable] WHERE Kind = ? AND Timestamp > ? GROUP BY GroupId;
    if (afterRangeParams != null) {
      gatherEvents(sqlAfter, afterRangeParams, builderGroups, (event) -> builderGroups.containsKey(event.getGroupId()));
    }

    List<Profiler.EventGroup> groups = new ArrayList<>();
    builderGroups.values().forEach((builder) -> {
      groups.add(builder.build());
    });
    return groups;
  }

  /**
   * Executes the sql statement and passes each event through the filter. If the filter returns true, the event is added
   * to the hashmap. Otherwise it is ignored.
   *
   * @param sql Statement to execute and gather a list of events
   * @param params List of params to pass to the sql statement.
   * @param builderGroups map of event group ids to event groups used to collect events into respective groups.
   * @param filter predicate to determine which events are included in the event group.
   */
  private void gatherEvents(String sql,
                            List<Object> params,
                            HashMap<Long, Profiler.EventGroup.Builder> builderGroups,
                            Predicate<Common.Event> filter) {
    try {
      ResultSet results = executeOneTimeQuery(sql, params.toArray());
      while (results.next()) {
        Common.Event event = Common.Event.parser().parseFrom(results.getBytes(1));
        if (!filter.test(event)) {
          continue;
        }
        Profiler.EventGroup.Builder group =
          builderGroups.computeIfAbsent(event.getGroupId(), key -> Profiler.EventGroup.newBuilder().setGroupId(key));
        group.addEvents(event);
      }
    }
    catch (SQLException | InvalidProtocolBufferException ex) {
      onError(ex);
    }
  }

  private List<Common.Event> queryUnifiedEvents(Statements stmt, Object... args) {
    List<Common.Event> records = new ArrayList<>();
    try {
      ResultSet results = executeQuery(stmt, args);
      while (results.next()) {
        records.add(Common.Event.parser().parseFrom(results.getBytes(1)));
      }
    }
    catch (SQLException | InvalidProtocolBufferException ex) {
      onError(ex);
    }
    return records;
  }
}
