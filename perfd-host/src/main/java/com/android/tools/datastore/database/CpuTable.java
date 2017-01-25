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

import com.android.tools.profiler.proto.CpuProfiler;
import com.google.protobuf3jarjar.ByteString;
import com.google.protobuf3jarjar.InvalidProtocolBufferException;
import com.intellij.openapi.diagnostic.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CpuTable extends DatastoreTable<CpuTable.CpuStatements> {

  private static final int DATA_COLUMN = 1;
  private static final int TRACE_ID_COLUMN = 1;
  private static final int FROM_TIMESTAMP_COLUMN = 2;
  private static final int TO_TIMESTAMP_COLUMN = 3;

  public enum CpuStatements {
    FIND_THREAD_DATA,
    INSERT_THREAD_DATA,
    QUERY_THREAD_DATA,
    INSERT_CPU_DATA,
    QUERY_CPU_DATA,
    QUERY_TRACE_INFO,
    FIND_TRACE_DATA,
    INSERT_TRACE_DATA
  }

  private Map<CpuStatements, PreparedStatement> myStatementMap = new HashMap();

  private static Logger getLogger() {
    return Logger.getInstance(CpuTable.class);
  }

  @Override
  public void initialize(Connection connection) {
    super.initialize(connection);
    try {
      createTable("Cpu_Data", "AppId INTEGER NOT NULL", "Timestamp INTEGER NOT NULL", "Data BLOB");
      createTable("Cpu_Threads", "AppId INTEGER NOT NULL", "ThreadId INTEGER NOT NULL", "StartTime INTEGER", "EndTime INTEGER", "Data BLOB");
      // TODO: PerfD needs to either make TraceId globally unique, or we need to add AppId to the Cpu_Trace table.
      createTable("Cpu_Trace", "TraceId INTEGER NOT NULL", "StartTime INTEGER", "EndTime INTEGER", "Data BLOB");
      createIndex("Cpu_Data", "AppId", "Timestamp");
      createIndex("Cpu_Threads", "AppId", "ThreadId");
      createIndex("Cpu_Trace", "TraceId");
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  @Override
  public void prepareStatements(Connection connection) {
    try {
      createStatement(CpuTable.CpuStatements.INSERT_CPU_DATA, "INSERT INTO Cpu_Data (AppId, Timestamp, Data) values (?, ?, ?)");
      createStatement(CpuTable.CpuStatements.QUERY_CPU_DATA, "SELECT Data from Cpu_Data WHERE AppId = ? AND Timestamp > ? AND Timestamp <= ? ");
      createStatement(CpuTable.CpuStatements.FIND_THREAD_DATA, "SELECT Data from Cpu_Threads WHERE AppId = ? AND ThreadId = ?");
      createStatement(CpuTable.CpuStatements.INSERT_THREAD_DATA, "INSERT OR REPLACE INTO Cpu_Threads (AppId, ThreadId, StartTime, EndTime, Data) values ( ?, ?, ?, ?, ?)");
      createStatement(CpuTable.CpuStatements.QUERY_THREAD_DATA, "SELECT Data from Cpu_Threads WHERE AppId = ? AND (StartTime <= ? AND ? <= EndTime) OR EndTime = 0;");
      createStatement(CpuTable.CpuStatements.QUERY_TRACE_INFO, "SELECT TraceId, StartTime, EndTime from Cpu_Trace WHERE (StartTime < ? AND ? <= EndTime) OR (StartTime > ? AND EndTime = 0);");
      createStatement(CpuTable.CpuStatements.FIND_TRACE_DATA, "SELECT Data from Cpu_Trace WHERE TraceId = ?");
      createStatement(CpuTable.CpuStatements.INSERT_TRACE_DATA, "INSERT INTO Cpu_Trace (TraceId, StartTime, EndTime, Data) values (?, ?, ?, ?)");
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  public void insert(CpuProfiler.CpuProfilerData data) {
    execute(CpuStatements.INSERT_CPU_DATA, data.getBasicInfo().getProcessId(), data.getBasicInfo().getEndTimestamp(), data.toByteArray());
  }

  public List<CpuProfiler.CpuProfilerData> getCpuDataByRequest(CpuProfiler.CpuDataRequest request) {
    List<CpuProfiler.CpuProfilerData> cpuData = new ArrayList();
    try {
      ResultSet results = executeQuery(CpuStatements.QUERY_CPU_DATA, request.getProcessId(), request.getStartTimestamp(), request.getEndTimestamp());
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

  public void insertOrReplace(long appId, long tid, CpuProfiler.GetThreadsResponse.Thread.Builder data) {
    int count = data.getActivitiesCount();
    long startTimestamp = 0;
    long endTimestamp = 0;
    if (count > 0) {
      startTimestamp = data.getActivities(0).getTimestamp();
      endTimestamp = data.getActivities(count - 1).getTimestamp();
    }
    execute(CpuStatements.INSERT_THREAD_DATA, appId, tid, startTimestamp, endTimestamp, data.build().toByteArray());
  }

  public CpuProfiler.GetThreadsResponse.Thread.Builder getThreadResponseByIdOrNull(long appId, long tid) {
    try {
      ResultSet results = executeQuery(CpuStatements.FIND_THREAD_DATA, appId, tid);
      if (results.next()) {
        CpuProfiler.GetThreadsResponse.Thread.Builder data = CpuProfiler.GetThreadsResponse.Thread.newBuilder();
        data.mergeFrom(results.getBytes(DATA_COLUMN));
        return data;
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      getLogger().error(ex);
    }
    return null;
  }

  public List<CpuProfiler.GetThreadsResponse.Thread.Builder> getThreadsDataByRequest(CpuProfiler.GetThreadsRequest request) {
    List<CpuProfiler.GetThreadsResponse.Thread.Builder> cpuData = new ArrayList();
    try {
      ResultSet results = executeQuery(CpuStatements.QUERY_THREAD_DATA, request.getProcessId(), request.getEndTimestamp(), request.getStartTimestamp());
      while (results.next()) {
        CpuProfiler.GetThreadsResponse.Thread.Builder data = CpuProfiler.GetThreadsResponse.Thread.newBuilder();
        data.mergeFrom(results.getBytes(DATA_COLUMN));
        cpuData.add(data);
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      getLogger().error(ex);
    }
    return cpuData;
  }

  public List<CpuProfiler.TraceInfo> getTraceByRequest(CpuProfiler.GetTraceInfoRequest request) {
    List<CpuProfiler.TraceInfo> traceInfo = new ArrayList<>();
    try {
      ResultSet results = executeQuery(CpuStatements.QUERY_TRACE_INFO, request.getToTimestamp(), request.getFromTimestamp(), request.getFromTimestamp());
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

  public ByteString getTraceData(int traceId) {
    try {
      ResultSet results = executeQuery(CpuStatements.FIND_TRACE_DATA, traceId);
      return ByteString.copyFrom(results.getBytes(DATA_COLUMN));
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
    return null;
  }

  public void insertTrace(CpuProfiler.TraceInfo trace, ByteString data) {
    execute(CpuStatements.INSERT_TRACE_DATA, trace.getTraceId(), trace.getFromTimestamp(), trace.getToTimestamp(), data.toByteArray());
  }
}
