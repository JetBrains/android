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
import com.android.tools.profiler.proto.EnergyProfiler;
import com.android.tools.profiler.protobuf3jarjar.InvalidProtocolBufferException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class EnergyTable extends DataStoreTable<EnergyTable.EventStatements> {
  @Override
  public void initialize(@NotNull Connection connection) {
    super.initialize(connection);
    try {
      createTable("Energy_Sample", "Session INTEGER NOT NULL", "Timestamp INTEGER NOT NULL", "Sample BLOB NOT NULL");
      createTable("Energy_Event", "Session INTEGER NOT NULL", "Timestamp INTEGER NOT NULL", "Event BLOB NOT NULL");
      createUniqueIndex("Energy_Sample", "Session", "Timestamp");
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  @Override
  public void prepareStatements() {
    try {
      createStatement(EventStatements.INSERT_SAMPLE, "INSERT OR REPLACE INTO Energy_Sample (Session, Timestamp, Sample) values (?, ?, ?)");
      createStatement(EventStatements.INSERT_EVENT, "INSERT OR REPLACE INTO Energy_Event (Session, Timestamp, Event) values (?, ?, ?)");
      createStatement(EventStatements.QUERY_SAMPLE,
                      "SELECT Sample from Energy_Sample WHERE Session = ? AND Timestamp >= ? AND Timestamp < ?;");
      createStatement(EventStatements.QUERY_EVENT,
                      "SELECT Event from Energy_Event WHERE Session = ? AND Timestamp >= ? AND Timestamp < ?;");
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  public void insertOrReplace(@NotNull Common.Session session, @NotNull EnergyProfiler.EnergySample sample) {
    execute(EventStatements.INSERT_SAMPLE, session.getSessionId(), sample.getTimestamp(), sample.toByteArray());
  }

  public void insertOrReplace(@NotNull Common.Session session, @NotNull EnergyProfiler.EnergyEvent event) {
    execute(EventStatements.INSERT_EVENT, session.getSessionId(), event.getTimestamp(), event.toByteArray());
  }

  /**
   * @return The list of matching samples given the {@code request} parameter, or {@code null} if there's a SQL-related error.
   */
  @Nullable
  public List<EnergyProfiler.EnergySample> findSamples(EnergyProfiler.EnergyRequest request) {
    try {
      ResultSet results = executeQuery(EventStatements.QUERY_SAMPLE, request.getSession().getSessionId(), request.getStartTimestamp(),
                                       request.getEndTimestamp());
      return getSamplesFromResultSet(results);
    }
    catch (SQLException ex) {
      onError(ex);
    }
    return null;
  }

  /**
   * @return The list of matching events given the {@code request} parameter, or {@code null} if there's a SQL-related error.
   */
  @Nullable
  public List<EnergyProfiler.EnergyEvent> findEvents(EnergyProfiler.EnergyRequest request) {
    try {
      ResultSet results = executeQuery(EventStatements.QUERY_EVENT, request.getSession().getSessionId(), request.getStartTimestamp(),
                                       request.getEndTimestamp());
      return getEventsFromResultSet(results);
    }
    catch (SQLException ex) {
      onError(ex);
    }
    return null;
  }

  public enum EventStatements {
    INSERT_SAMPLE,
    QUERY_SAMPLE,
    INSERT_EVENT,
    QUERY_EVENT,
  }

  @NotNull
  private static List<EnergyProfiler.EnergySample> getSamplesFromResultSet(@NotNull ResultSet results) {
    List<EnergyProfiler.EnergySample> samples = new ArrayList<>();
    try {
      while (results.next()) {
        EnergyProfiler.EnergySample.Builder sampleBuilder = EnergyProfiler.EnergySample.newBuilder();
        sampleBuilder.mergeFrom(results.getBytes(1));
        samples.add(sampleBuilder.build());
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      onError(ex);
    }
    return samples;
  }

  @NotNull
  private static List<EnergyProfiler.EnergyEvent> getEventsFromResultSet(@NotNull ResultSet results) {
    List<EnergyProfiler.EnergyEvent> events = new ArrayList<>();
    try {
      while (results.next()) {
        EnergyProfiler.EnergyEvent.Builder eventBuilder = EnergyProfiler.EnergyEvent.newBuilder();
        eventBuilder.mergeFrom(results.getBytes(1));
        events.add(eventBuilder.build());
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      onError(ex);
    }
    return events;
  }
}
