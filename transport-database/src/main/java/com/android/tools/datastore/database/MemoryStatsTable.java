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

import static com.android.tools.datastore.database.MemoryStatsTable.MemoryStatements.INSERT_OR_REPLACE_ALLOCATIONS_INFO;
import static com.android.tools.datastore.database.MemoryStatsTable.MemoryStatements.INSERT_OR_REPLACE_HEAP_INFO;
import static com.android.tools.datastore.database.MemoryStatsTable.MemoryStatements.INSERT_SAMPLE;
import static com.android.tools.datastore.database.MemoryStatsTable.MemoryStatements.QUERY_ALLOCATION_INFO_BY_ID;
import static com.android.tools.datastore.database.MemoryStatsTable.MemoryStatements.QUERY_ALLOCATION_INFO_BY_TIME;
import static com.android.tools.datastore.database.MemoryStatsTable.MemoryStatements.QUERY_ALLOC_STATS;
import static com.android.tools.datastore.database.MemoryStatsTable.MemoryStatements.QUERY_GC_STATS;
import static com.android.tools.datastore.database.MemoryStatsTable.MemoryStatements.QUERY_HEAP_INFO_BY_TIME;
import static com.android.tools.datastore.database.MemoryStatsTable.MemoryStatements.QUERY_MEMORY;
import static com.android.tools.datastore.database.MemoryStatsTable.MemoryStatements.values;

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory.HeapDumpInfo;
import com.android.tools.profiler.proto.Memory.AllocationsInfo;
import com.android.tools.profiler.proto.MemoryProfiler.ListDumpInfosRequest;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryRequest;
import com.android.tools.idea.protobuf.GeneratedMessageV3;
import com.android.tools.idea.protobuf.InvalidProtocolBufferException;
import com.android.tools.idea.protobuf.Message;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MemoryStatsTable extends DataStoreTable<MemoryStatsTable.MemoryStatements> {

  public enum MemoryStatements {
    INSERT_SAMPLE("INSERT OR IGNORE INTO Memory_Samples (Session, Timestamp, Type, Data) VALUES (?, ?, ?, ?)"),
    QUERY_MEMORY(
      String.format(Locale.US, "SELECT Data FROM Memory_Samples WHERE Session = ? AND Type = %d AND TimeStamp > ? AND TimeStamp <= ?",
                    MemorySamplesType.MEMORY.ordinal())),
    QUERY_ALLOC_STATS(
      String.format(Locale.US, "SELECT Data FROM Memory_Samples WHERE Session = ? AND Type = %d AND TimeStamp > ? AND TimeStamp <= ?",
                    MemorySamplesType.ALLOC_STATS.ordinal())),

    // TODO: gc stats are duration data so we should account for end time. In reality this is usually sub-ms so it might not matter?
    QUERY_GC_STATS(
      String.format(Locale.US, "SELECT Data FROM Memory_Samples WHERE Session = ? AND Type = %d AND TimeStamp > ? AND TimeStamp <= ?",
                    MemorySamplesType.GC_STATS.ordinal())),

    INSERT_OR_REPLACE_HEAP_INFO(
      "INSERT OR REPLACE INTO Memory_HeapDump (Session, StartTime, EndTime, InfoData) VALUES (?, ?, ?, ?)"),
    // EndTime = UNSPECIFIED_DURATION checks for the special case where we have an ongoing duration sample
    QUERY_HEAP_INFO_BY_TIME("SELECT InfoData FROM Memory_HeapDump where Session = ? AND EndTime > ? AND StartTime <= ?"),

    INSERT_OR_REPLACE_ALLOCATIONS_INFO(
      "INSERT OR REPLACE INTO Memory_AllocationInfo (Session, StartTime, EndTime, InfoData) VALUES (?, ?, ?, ?)"),
    UPDATE_LEGACY_ALLOCATIONS_INFO_EVENTS("UPDATE Memory_AllocationInfo SET LegacyEventsData = ? WHERE Session = ? AND StartTime = ?"),
    // EndTime = UNSPECIFIED_DURATION checks for the special case where we have an ongoing duration sample
    QUERY_ALLOCATION_INFO_BY_TIME("SELECT InfoData FROM Memory_AllocationInfo WHERE Session = ? AND EndTime > ? AND StartTime <= ?"),
    QUERY_ALLOCATION_INFO_BY_ID("SELECT InfoData from Memory_AllocationInfo WHERE Session = ? AND StartTime = ?");

    @NotNull private final String mySqlStatement;

    MemoryStatements(@NotNull String sqlStatement) {
      mySqlStatement = sqlStatement;
    }

    @NotNull
    public String getStatement() {
      return mySqlStatement;
    }
  }

  private enum MemorySamplesType {
    MEMORY,
    ALLOC_STATS,
    GC_STATS
  }

  @Override
  public void initialize(@NotNull Connection connection) {
    super.initialize(connection);
    try {
      createTable("Memory_Samples", "Session INTEGER NOT NULL", "Timestamp INTEGER", "Type INTEGER",
                  "Data BLOB", "PRIMARY KEY(Session, Timestamp, Type)");
      createTable("Memory_AllocationInfo", "Session INTEGER NOT NULL", "StartTime INTEGER",
                  "EndTime INTEGER", "InfoData BLOB", "LegacyEventsData BLOB", "PRIMARY KEY(Session, StartTime)");
      createTable("Memory_HeapDump", "Session INTEGER NOT NULL", "StartTime INTEGER",
                  "EndTime INTEGER", "InfoData BLOB", "PRIMARY KEY(Session, StartTime)");
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  @Override
  public void prepareStatements() {
    try {
      for (MemoryStatements statement : values()) {
        createStatement(statement, statement.getStatement());
      }
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  @NotNull
  public MemoryData getData(@NotNull MemoryRequest request) {
    long sessionId = request.getSession().getSessionId();
    long startTime = request.getStartTime();
    long endTime = request.getEndTime();
    List<MemoryData.MemorySample> memorySamples =
      getResultsInfo(QUERY_MEMORY, sessionId, startTime, endTime, MemoryData.MemorySample.getDefaultInstance());
    List<MemoryData.AllocStatsSample> allocStatsSamples =
      getResultsInfo(QUERY_ALLOC_STATS, sessionId, startTime, endTime, MemoryData.AllocStatsSample.getDefaultInstance());
    List<MemoryData.GcStatsSample> gcStatsSamples =
      getResultsInfo(QUERY_GC_STATS, sessionId, startTime, endTime, MemoryData.GcStatsSample.getDefaultInstance());
    List<HeapDumpInfo> heapDumpSamples =
      getResultsInfo(QUERY_HEAP_INFO_BY_TIME, sessionId, startTime, endTime, HeapDumpInfo.getDefaultInstance());
    List<AllocationsInfo> allocationSamples =
      getResultsInfo(QUERY_ALLOCATION_INFO_BY_TIME, sessionId, startTime, endTime, AllocationsInfo.getDefaultInstance());
    return MemoryData
      .newBuilder()
      .addAllMemSamples(memorySamples)
      .addAllAllocStatsSamples(allocStatsSamples)
      .addAllGcStatsSamples(gcStatsSamples)
      .addAllHeapDumpInfos(heapDumpSamples)
      .addAllAllocationsInfo(allocationSamples)
      .build();
  }

  public void insertMemory(@NotNull Common.Session session, @NotNull List<MemoryData.MemorySample> samples) {
    for (MemoryData.MemorySample sample : samples) {
      execute(INSERT_SAMPLE, session.getSessionId(), sample.getTimestamp(), MemorySamplesType.MEMORY.ordinal(),
              sample.toByteArray());
    }
  }

  public void insertAllocStats(@NotNull Common.Session session, @NotNull List<MemoryData.AllocStatsSample> samples) {
    for (MemoryData.AllocStatsSample sample : samples) {
      execute(INSERT_SAMPLE, session.getSessionId(), sample.getTimestamp(), MemorySamplesType.ALLOC_STATS.ordinal(),
              sample.toByteArray());
    }
  }

  public void insertGcStats(@NotNull Common.Session session, @NotNull List<MemoryData.GcStatsSample> samples) {
    for (MemoryData.GcStatsSample sample : samples) {
      execute(INSERT_SAMPLE, session.getSessionId(), sample.getStartTime(), MemorySamplesType.GC_STATS.ordinal(),
              sample.toByteArray());
    }
  }

  /**
   * Note: this will reset the row's Status and DumpData to NOT_READY and null respectively, if an info with the same DumpId already exist.
   */
  public void insertOrReplaceHeapInfo(@NotNull Common.Session session, @NotNull HeapDumpInfo info) {
    execute(INSERT_OR_REPLACE_HEAP_INFO, session.getSessionId(), info.getStartTime(), info.getEndTime(), info.toByteArray());
  }


  public List<HeapDumpInfo> getHeapDumpInfoByRequest(@NotNull Common.Session session, @NotNull ListDumpInfosRequest request) {
    return getResultsInfo(QUERY_HEAP_INFO_BY_TIME, session.getSessionId(), request.getStartTime(), request.getEndTime(),
                          HeapDumpInfo.getDefaultInstance());
  }

  /**
   * Note: this will reset the allocation events and its raw dump byte content associated with a tracking start time if an entry already exists.
   */
  public void insertOrReplaceAllocationsInfo(@NotNull Common.Session session, @NotNull AllocationsInfo info) {
    execute(INSERT_OR_REPLACE_ALLOCATIONS_INFO, session.getSessionId(), info.getStartTime(), info.getEndTime(), info.toByteArray());
  }

  /**
   * @return the AllocationsInfo associated with the tracking start time. Null if an entry does not exist.
   */
  @Nullable
  public AllocationsInfo getAllocationsInfo(@NotNull Common.Session session, long trackingStartTime) {
    try {
      ResultSet results = executeQuery(QUERY_ALLOCATION_INFO_BY_ID, session.getSessionId(), trackingStartTime);
      if (results.next()) {
        byte[] bytes = results.getBytes(1);
        if (bytes != null) {
          return AllocationsInfo.parseFrom(bytes);
        }
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      onError(ex);
    }

    return null;
  }

  /**
   * A helper method for querying samples for MemorySample, AllocStatsSample, GcStatsSample, HeapDumpInfo and AllocationsInfo
   */
  private <T extends GeneratedMessageV3> List<T> getResultsInfo(@NotNull MemoryStatements query,
                                                                long sessionId,
                                                                long startTime,
                                                                long endTime,
                                                                @NotNull T defaultInstance) {
    List<T> datas = new ArrayList<>();
    try {
      ResultSet resultSet = executeQuery(query, sessionId, startTime, endTime);
      while (resultSet.next()) {
        Message data = defaultInstance.toBuilder().mergeFrom(resultSet.getBytes(1)).build();
        datas.add((T)data);
      }
    }
    catch (ClassCastException | InvalidProtocolBufferException | SQLException ex) {
      onError(ex);
    }
    return datas;
  }
}
