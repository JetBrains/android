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
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.Trace.TraceInfo;
import com.android.tools.profiler.proto.Cpu.CpuUsageData;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuProfiler.CpuDataRequest;
import com.android.tools.profiler.proto.CpuProfiler.GetThreadsRequest;
import com.android.tools.profiler.proto.CpuProfiler.GetThreadsResponse;
import com.android.tools.profiler.proto.CpuProfiler.GetTraceInfoRequest;
import com.android.tools.idea.protobuf.InvalidProtocolBufferException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jetbrains.annotations.NotNull;

public class CpuTable extends DataStoreTable<CpuTable.CpuStatements> {
  private static final int DATA_COLUMN = 1;

  /**
   * A cache of unique thread IDs per session index.
   * <p>
   * We keep a cache of all seen thread IDs because querying the DB for distinct thread IDs is slow.
   */
  private final Map<Long, Set<Integer>> mySessionThreadIdsCache = Collections.synchronizedMap(new HashMap<>());

  public enum CpuStatements {
    INSERT_THREAD_ACTIVITY,
    QUERY_ALL_DISTINCT_THREADS,
    QUERY_THREAD_ACTIVITIES,
    INSERT_CPU_DATA,
    QUERY_CPU_DATA,
    QUERY_TRACE_INFO,
    INSERT_TRACE_INFO,
  }

  @Override
  public void initialize(@NotNull Connection connection) {
    super.initialize(connection);
    try {
      createTable("Cpu_Data",
                  "Session INTEGER NOT NULL",
                  "Timestamp INTEGER NOT NULL",
                  "Data BLOB");
      createTable("Thread_Activities",
                  "Session INTEGER NOT NULL",
                  "ThreadId INTEGER NOT NULL",
                  "Timestamp INTEGER NOT NULL",
                  "State TEXT",
                  "Name TEXT",
                  "PRIMARY KEY (Session, ThreadId, Timestamp)");
      createTable("Cpu_Trace_Info",
                  "Session INTEGER NOT NULL",
                  "StartTime INTEGER",
                  "EndTime INTEGER",
                  "TraceInfo BLOB");
      createUniqueIndex("Cpu_Data", "Session", "Timestamp");
      createUniqueIndex("Cpu_Trace_Info", "Session", "StartTime");
      // Uniqueness guaranteed by PRIMARY KEY field in this table.
      createIndex("Thread_Activities", 0, "Session", "ThreadId", "Timestamp");
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  @Override
  public void prepareStatements() {
    try {
      createStatement(CpuTable.CpuStatements.INSERT_CPU_DATA,
                      "INSERT OR REPLACE INTO Cpu_Data (Session, Timestamp, Data) values (?, ?, ?)");
      createStatement(CpuTable.CpuStatements.QUERY_CPU_DATA,
                      "SELECT Data from Cpu_Data WHERE Session = ? AND Timestamp > ? AND Timestamp <= ? ");
      createStatement(CpuTable.CpuStatements.QUERY_TRACE_INFO,
                      "SELECT TraceInfo from Cpu_Trace_Info WHERE " +
                      "Session = ? AND (StartTime < ? AND (EndTime >= ? OR EndTime = -1));");
      createStatement(CpuTable.CpuStatements.INSERT_TRACE_INFO,
                      "INSERT OR REPLACE INTO Cpu_Trace_Info (Session, StartTime, EndTime, TraceInfo) values (?, ?, ?, ?)");
      createStatement(CpuTable.CpuStatements.INSERT_THREAD_ACTIVITY,
                      "INSERT OR REPLACE INTO Thread_Activities " +
                      "(Session, ThreadId, Timestamp, State, Name) VALUES (?, ?, ?, ?, ?)");
      createStatement(CpuTable.CpuStatements.QUERY_ALL_DISTINCT_THREADS,
                      "SELECT DISTINCT ThreadId FROM Thread_Activities WHERE Session = ?");
      createStatement(CpuTable.CpuStatements.QUERY_THREAD_ACTIVITIES,
                      // First make sure to fetch the states of all threads that were alive at request's start timestamp
                      "SELECT t2.Name, t2.State, ? as ReqStart " +
                      "FROM (SELECT MAX(Timestamp) AS Timestamp " +
                      "      FROM Thread_Activities " +
                      "      WHERE Session = ? AND ThreadId = ? AND Timestamp <= ? " +
                      ") t1 " +
                      "JOIN Thread_Activities t2 " +
                      "ON (t2.Session = ? AND t2.ThreadId = ? AND t2.timestamp = t1.timestamp AND t2.State <> 'DEAD')" +
                      "UNION ALL " +
                      // Then fetch all the activities that happened in the request interval
                      "SELECT Name, State, Timestamp " +
                      "FROM Thread_Activities " +
                      "WHERE Session = ? AND ThreadId = ? AND Timestamp > ? AND Timestamp <= ?");
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  public void insert(Common.Session session, CpuUsageData data) {
    execute(CpuStatements.INSERT_CPU_DATA, session.getSessionId(), data.getEndTimestamp(), data.toByteArray());
  }

  public List<CpuUsageData> getCpuDataByRequest(CpuDataRequest request) {
    List<CpuUsageData> cpuData = new ArrayList<>();
    try {
      ResultSet results =
        executeQuery(CpuStatements.QUERY_CPU_DATA, request.getSession().getSessionId(), request.getStartTimestamp(),
                     request.getEndTimestamp());
      while (results.next()) {
        CpuUsageData.Builder data = CpuUsageData.newBuilder();
        data.mergeFrom(results.getBytes(DATA_COLUMN));
        cpuData.add(data.build());
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      onError(ex);
    }
    return cpuData;
  }

  public void insertActivities(Common.Session session,
                               int tid,
                               String name,
                               List<GetThreadsResponse.ThreadActivity> activities) {
    getThreadIdCacheForSession(session.getSessionId()).add(tid);
    for (GetThreadsResponse.ThreadActivity activity : activities) {
      // TODO: optimize it by adding the states in batches
      execute(CpuStatements.INSERT_THREAD_ACTIVITY, session.getSessionId(), tid, activity.getTimestamp(), activity.getNewState().toString(),
              name);
    }
  }

  public void insertSnapshot(Common.Session session,
                             long timestamp,
                             List<GetThreadsResponse.ThreadSnapshot.Snapshot> snapshots) {
    Set<Integer> idSet = getThreadIdCacheForSession(session.getSessionId());
    // For now, insert it as activity. TODO: differentiate the concepts of snapshot and activity
    for (GetThreadsResponse.ThreadSnapshot.Snapshot snapshot : snapshots) {
      idSet.add(snapshot.getTid());
      execute(CpuStatements.INSERT_THREAD_ACTIVITY,
              session.getSessionId(), snapshot.getTid(), timestamp, snapshot.getState().toString(), snapshot.getName());
    }
  }

  public List<GetThreadsResponse.Thread> getThreadsDataByRequest(GetThreadsRequest request) {
    // Use a TreeMap to preserve the threads sorting order (by tid)
    Map<Integer, GetThreadsResponse.Thread.Builder> threads = new TreeMap<>();
    try {
      long sessionId = request.getSession().getSessionId();

      List<Integer> threadIds = new ArrayList<>();
      if (!mySessionThreadIdsCache.containsKey(sessionId)) {
        Set<Integer> tidSet = getThreadIdCacheForSession(sessionId);

        ResultSet threadResults = executeQuery(CpuStatements.QUERY_ALL_DISTINCT_THREADS, sessionId);
        while (threadResults.next()) {
          // Don't add directly to tidSet, since it's synchronized and would be slow to add each element individually.
          threadIds.add(threadResults.getInt(1));
        }
        tidSet.addAll(threadIds);
      }
      else {
        // Add all ids to a list so we release the implicit lock in threadIds ASAP.
        //noinspection UseBulkOperation
        mySessionThreadIdsCache.get(sessionId).forEach(tid -> threadIds.add(tid));
      }

      long startTimestamp = request.getStartTimestamp();
      long endTimestamp = request.getEndTimestamp();
      for (int tid : threadIds) {
        ResultSet activities = executeQuery(
          CpuStatements.QUERY_THREAD_ACTIVITIES,
          // Used as the timestamp of the states that happened before the request
          startTimestamp,
          // Used to get the last activity just prior to the request range
          sessionId, tid, startTimestamp,
          // Used for the JOIN
          sessionId, tid,
          // The start and end timestamps below are used to get the activities that
          // happened in the interval (start, end]
          sessionId, tid, startTimestamp, endTimestamp);

        CpuProfiler.GetThreadsResponse.Thread.Builder builder = null;
        while (activities.next()) {
          if (builder == null) {
            // Please refer QUERY_THREAD_ACTIVITIES statement for the ResultSet's column to type/value mapping.
            builder = createThreadBuilder(tid, activities.getString(1));
            threads.put(tid, builder);
          }

          Cpu.CpuThreadData.State state = Cpu.CpuThreadData.State.valueOf(activities.getString(2));
          GetThreadsResponse.ThreadActivity.Builder activity =
            GetThreadsResponse.ThreadActivity.newBuilder().setNewState(state).setTimestamp(activities.getLong(3));
          builder.addActivities(activity.build());
        }
      }
    }
    catch (SQLException ex) {
      onError(ex);
    }

    // Add all threads that should be included in the response.
    List<GetThreadsResponse.Thread> cpuData = new ArrayList<>();
    for (GetThreadsResponse.Thread.Builder thread : threads.values()) {
      cpuData.add(thread.build());
    }
    return cpuData;
  }

  public List<TraceInfo> getTraceInfo(GetTraceInfoRequest request) {
    List<TraceInfo> traceInfo = new ArrayList<>();
    try {
      ResultSet results =
        executeQuery(CpuStatements.QUERY_TRACE_INFO, request.getSession().getSessionId(),
                     request.getToTimestamp(), request.getFromTimestamp());
      while (results.next()) {
        // QUERY_TRACE_INFO will return only one column.
        byte[] data = results.getBytes(1);
        if (data != null) {
          traceInfo.add(TraceInfo.parseFrom(data));
        }
      }
    }
    catch (SQLException | InvalidProtocolBufferException ex) {
      onError(ex);
    }

    return traceInfo;
  }

  public void insertTraceInfo(Common.Session session, TraceInfo trace) {
    execute(CpuStatements.INSERT_TRACE_INFO, session.getSessionId(), trace.getFromTimestamp(), trace.getToTimestamp(), trace.toByteArray());
  }

  private static GetThreadsResponse.Thread.Builder createThreadBuilder(int tid, String name) {
    GetThreadsResponse.Thread.Builder thread = GetThreadsResponse.Thread.newBuilder();
    thread.setTid(tid);
    thread.setName(name);
    return thread;
  }

  @NotNull
  private Set<Integer> getThreadIdCacheForSession(long sessionId) {
    return mySessionThreadIdsCache.computeIfAbsent(sessionId, id -> Collections.synchronizedSet(new HashSet<>()));
  }
}
