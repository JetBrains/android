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
import com.android.tools.profiler.proto.CpuProfiler;
import com.google.protobuf3jarjar.ByteString;
import com.google.protobuf3jarjar.InvalidProtocolBufferException;
import com.intellij.openapi.diagnostic.Logger;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CpuTable extends DatastoreTable<CpuTable.CpuStatements> {

  private static final int DATA_COLUMN = 1;
  private static final int TRACE_ID_COLUMN = 1;
  private static final int FROM_TIMESTAMP_COLUMN = 2;
  private static final int TO_TIMESTAMP_COLUMN = 3;

  public enum CpuStatements {
    INSERT_THREAD_ACTIVITY,
    QUERY_THREAD_ACTIVITIES,
    INSERT_CPU_DATA,
    QUERY_CPU_DATA,
    QUERY_TRACE_INFO,
    FIND_TRACE_DATA,
    INSERT_TRACE_DATA
  }

  private static Logger getLogger() {
    return Logger.getInstance(CpuTable.class);
  }

  @Override
  public void initialize(Connection connection) {
    super.initialize(connection);
    try {
      createTable("Cpu_Data",
                  "AppId INTEGER NOT NULL",
                  "Timestamp INTEGER NOT NULL",
                  "Session STRING NOT NULL",
                  "Data BLOB");
      createTable("Thread_Activities",
                  "AppId INTEGER NOT NULL", "Session STRING NOT NULL", "ThreadId INTEGER NOT NULL", "Timestamp INTEGER",
                  "State TEXT, Name TEXT");
      createTable("Cpu_Trace",
                  "TraceId INTEGER NOT NULL",
                  "Session STRING NOT NULL",
                  "StartTime INTEGER",
                  "EndTime INTEGER",
                  "Data BLOB");
      createIndex("Cpu_Data", "AppId", "Timestamp", "Session");
      createIndex("Cpu_Trace", "TraceId", "Session");
      createIndex("Thread_Activities", "AppId", "Session", "ThreadId", "Timestamp");
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  @Override
  public void prepareStatements(Connection connection) {
    try {
      createStatement(CpuTable.CpuStatements.INSERT_CPU_DATA,
                      "INSERT OR REPLACE INTO Cpu_Data (AppId, Timestamp, Session, Data) values (?, ?, ?, ?)");
      createStatement(CpuTable.CpuStatements.QUERY_CPU_DATA,
                      "SELECT Data from Cpu_Data WHERE AppId = ? AND Session = ? AND Timestamp > ? AND Timestamp <= ? ");
      createStatement(CpuTable.CpuStatements.QUERY_TRACE_INFO,
                      "SELECT TraceId, StartTime, EndTime from Cpu_Trace WHERE " +
                      "Session = ? AND ((StartTime < ? AND ? <= EndTime) OR (StartTime > ? AND EndTime = 0));");
      createStatement(CpuTable.CpuStatements.FIND_TRACE_DATA, "SELECT Data from Cpu_Trace WHERE TraceId = ? AND Session = ?");
      createStatement(CpuTable.CpuStatements.INSERT_TRACE_DATA,
                      "INSERT INTO Cpu_Trace (TraceId, Session, StartTime, EndTime, Data) values (?, ?, ?, ?, ?)");
      createStatement(CpuTable.CpuStatements.INSERT_THREAD_ACTIVITY,
                      "INSERT OR REPLACE INTO Thread_Activities " +
                      "(AppId, Session, ThreadId, Timestamp, State, Name) VALUES (?, ?, ?, ?, ?, ?)");
      createStatement(CpuTable.CpuStatements.QUERY_THREAD_ACTIVITIES,
                      // First make sure to fetch the states of all threads that were alive at request's start timestamp
                      "SELECT t1.ThreadId, t1.Name, t1.State, ? as ReqStart FROM Thread_Activities AS t1 " +
                      "JOIN (SELECT ThreadId, MAX(Timestamp) AS Timestamp " +
                      "FROM Thread_Activities WHERE AppId = ? AND Session = ? AND Timestamp <= ? GROUP BY ThreadId) AS t2 " +
                      "ON t1.ThreadId = t2.ThreadId AND t1.Timestamp = t2.Timestamp AND t1.State <> 'DEAD' " +
                      "UNION ALL " +
                      // Then fetch all the activities that happened in the request interval
                      "SELECT ThreadId, Name, State, Timestamp FROM Thread_Activities " +
                      "WHERE AppId = ? AND Session = ? AND Timestamp > ? AND Timestamp <= ?;");
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  public void insert(Common.Session session, CpuProfiler.CpuProfilerData data) {
    execute(CpuStatements.INSERT_CPU_DATA, data.getBasicInfo().getProcessId(), data.getBasicInfo().getEndTimestamp(), session,
            data.toByteArray());
  }

  public List<CpuProfiler.CpuProfilerData> getCpuDataByRequest(CpuProfiler.CpuDataRequest request) {
    List<CpuProfiler.CpuProfilerData> cpuData = new ArrayList<>();
    try {
      ResultSet results =
        executeQuery(CpuStatements.QUERY_CPU_DATA, request.getProcessId(), request.getSession(), request.getStartTimestamp(),
                     request.getEndTimestamp());
      while (results.next()) {
        CpuProfiler.CpuProfilerData.Builder data = CpuProfiler.CpuProfilerData.newBuilder();
        data.mergeFrom(results.getBytes(DATA_COLUMN));
        cpuData.add(data.build());
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      getLogger().error(ex);
    }
    return cpuData;
  }

  public void insertActivities(int appId,
                               Common.Session session,
                               int tid,
                               String name,
                               List<CpuProfiler.GetThreadsResponse.ThreadActivity> activities) {
    for (CpuProfiler.GetThreadsResponse.ThreadActivity activity : activities) {
      // TODO: optimize it by adding the states in batches
      execute(CpuStatements.INSERT_THREAD_ACTIVITY, appId, session, tid, activity.getTimestamp(), activity.getNewState().toString(), name);
    }
  }

  public void insertSnapshot(long appId,
                             Common.Session session,
                             long timestamp,
                             List<CpuProfiler.GetThreadsResponse.ThreadSnapshot.Snapshot> snapshots) {
    // For now, insert it as activity. TODO: differentiate the concepts of snapshot and activity
    for (CpuProfiler.GetThreadsResponse.ThreadSnapshot.Snapshot snapshot : snapshots) {
      execute(CpuStatements.INSERT_THREAD_ACTIVITY,
              appId, session, snapshot.getTid(), timestamp, snapshot.getState().toString(), snapshot.getName());
    }
  }

  public List<CpuProfiler.GetThreadsResponse.Thread> getThreadsDataByRequest(CpuProfiler.GetThreadsRequest request) {
    // Use a TreeMap to preserve the threads sorting order (by tid)
    Map<Integer, CpuProfiler.GetThreadsResponse.Thread.Builder> threads = new TreeMap<>();
    try {
      ResultSet activities = executeQuery(CpuStatements.QUERY_THREAD_ACTIVITIES,
                                          // Used as the timestamp of the states that happened before the request
                                          request.getStartTimestamp(),
                                          request.getProcessId(),
                                          request.getSession(),
                                          // Used to get the the states that happened before the request
                                          request.getStartTimestamp(),
                                          request.getProcessId(),
                                          request.getSession(),
                                          // The start and end timestamps below are used to get the activities that
                                          // happened in the interval (start, end]
                                          request.getStartTimestamp(),
                                          request.getEndTimestamp());
      while (activities.next()) {
        // Thread id should be the first column
        int tid = activities.getInt(1);
        if (!threads.containsKey(tid)) {
          // Thread name should be the second column
          CpuProfiler.GetThreadsResponse.Thread.Builder thread = createThreadBuilder(tid, activities.getString(2));
          threads.put(tid, thread);
        }
        // State should be the third column
        CpuProfiler.GetThreadsResponse.State state = CpuProfiler.GetThreadsResponse.State.valueOf(activities.getString(3));
        // Timestamp should be the fourth column
        CpuProfiler.GetThreadsResponse.ThreadActivity.Builder activity =
          CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder().setNewState(state).setTimestamp(activities.getLong(4));
        threads.get(tid).addActivities(activity.build());
      }
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }

    // Add all threads that should be included in the response.
    List<CpuProfiler.GetThreadsResponse.Thread> cpuData = new ArrayList<>();
    for (CpuProfiler.GetThreadsResponse.Thread.Builder thread : threads.values()) {
      cpuData.add(thread.build());
    }
    return cpuData;
  }

  public List<CpuProfiler.TraceInfo> getTraceByRequest(CpuProfiler.GetTraceInfoRequest request) {
    List<CpuProfiler.TraceInfo> traceInfo = new ArrayList<>();
    try {
      ResultSet results =
        executeQuery(CpuStatements.QUERY_TRACE_INFO, request.getSession(), request.getToTimestamp(), request.getFromTimestamp(),
                     request.getFromTimestamp());
      while (results.next()) {
        CpuProfiler.TraceInfo.Builder data = CpuProfiler.TraceInfo.newBuilder();
        data.setTraceId(results.getInt(TRACE_ID_COLUMN));
        data.setFromTimestamp(results.getLong(FROM_TIMESTAMP_COLUMN));
        data.setToTimestamp(results.getLong(TO_TIMESTAMP_COLUMN));
        traceInfo.add(data.build());
      }
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
    return traceInfo;
  }

  public ByteString getTraceData(int traceId, Common.Session session) {
    try {
      ResultSet results = executeQuery(CpuStatements.FIND_TRACE_DATA, traceId, session);
      return ByteString.copyFrom(results.getBytes(DATA_COLUMN));
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
    return null;
  }

  public void insertTrace(CpuProfiler.TraceInfo trace, Common.Session session, ByteString data) {
    execute(CpuStatements.INSERT_TRACE_DATA, trace.getTraceId(), session, trace.getFromTimestamp(), trace.getToTimestamp(),
            data.toByteArray());
  }

  private static CpuProfiler.GetThreadsResponse.Thread.Builder createThreadBuilder(int tid, String name) {
    CpuProfiler.GetThreadsResponse.Thread.Builder thread = CpuProfiler.GetThreadsResponse.Thread.newBuilder();
    thread.setTid(tid);
    thread.setName(name);
    return thread;
  }
}
