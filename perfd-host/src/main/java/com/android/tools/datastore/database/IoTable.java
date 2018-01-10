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
import com.android.tools.profiler.proto.IoProfiler;
import com.android.tools.profiler.protobuf3jarjar.InvalidProtocolBufferException;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class IoTable extends DataStoreTable<IoTable.IoStatements> {

  public enum IoStatements {
    INSERT_IO_DATA,
    QUERY_IO_DATA,
    INSERT_SPEED_DATA,
    QUERY_SPEED_DATA,
  }

  private static Logger getLogger() {
    return Logger.getInstance(IoTable.class);
  }

  @Override
  public void initialize(@NotNull Connection connection) {
    super.initialize(connection);
    try {
      createTable("Io_Files_Data", "Session INTEGER NOT NULL", "FileSessionId INTEGER NOT NULL",
                  "StartTime INTEGER", "EndTime INTEGER", "Data BLOB");
      createTable("Io_Speed_Data", "Session INTEGER NOT NULL", "EndTime INTEGER", "Type INTEGER",
                  "Speed INTEGER");

      createUniqueIndex("Io_Files_Data", "Session", "FileSessionId");
      createUniqueIndex("Io_Speed_Data", "Session", "EndTime", "Type");
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  @Override
  public void prepareStatements() {
    try {
      createStatement(IoStatements.INSERT_IO_DATA,
                      "INSERT OR REPLACE INTO Io_Files_Data (Session, FileSessionId, StartTime, EndTime, Data) VALUES (?, ?, ?, ?, ?)");
      createStatement(IoStatements.QUERY_IO_DATA,
                      "SELECT Data FROM Io_Files_Data WHERE Session = ? AND (EndTime > ? OR EndTime = -1) AND StartTime <= ?");

      createStatement(IoStatements.INSERT_SPEED_DATA,
                      "INSERT OR IGNORE INTO Io_Speed_Data (Session, EndTime, Type, Speed) VALUES (?, ?, ?, ?)");
      createStatement(IoStatements.QUERY_SPEED_DATA,
                      "SELECT EndTime, Speed FROM Io_Speed_Data WHERE Session = ? AND Type = ? AND EndTime > ? AND EndTime <= ?");
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  public void insert(Common.Session session, IoProfiler.FileSession fileSession) {
    execute(IoStatements.INSERT_IO_DATA, session.getSessionId(), fileSession.getIoSessionId(), fileSession.getStartTimestamp(),
            fileSession.getEndTimestamp(), fileSession.toByteArray());
  }

  public void insert(Common.Session session, long endTimestamp, IoProfiler.IoSpeedData speedData) {
    execute(IoStatements.INSERT_SPEED_DATA, session.getSessionId(), endTimestamp, speedData.getTypeValue(), speedData.getSpeed());
  }

  public List<IoProfiler.FileSession> getFileData(Common.Session session, long startTimestamp, long endTimestamp) {
    final int fileSessionColumn = 1;
    try {
      List<IoProfiler.FileSession> fileData = new ArrayList<>();
      ResultSet results = executeQuery(IoStatements.QUERY_IO_DATA, session.getSessionId(), startTimestamp, endTimestamp);
      while (results.next()) {
        byte[] data = results.getBytes(fileSessionColumn);
        if (data != null) {
          IoProfiler.FileSession.Builder builder = IoProfiler.FileSession.newBuilder();
          builder.mergeFrom(data);
          fileData.add(builder.build());
        }
      }
      return fileData;
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      getLogger().error(ex);
    }
    return null;
  }

  public List<IoProfiler.IoSpeedData> getSpeedData(Common.Session session,
                                                   long startTimestamp,
                                                   long endTimestamp,
                                                   IoProfiler.IoType type) {
    final int endTimeColumn = 1;
    final int speedColumn = 2;
    try {
      List<IoProfiler.IoSpeedData> speedData = new ArrayList<>();
      ResultSet results =
        executeQuery(IoStatements.QUERY_SPEED_DATA, session.getSessionId(), type.getNumber(), startTimestamp, endTimestamp);
      while (results.next()) {
        IoProfiler.IoSpeedData.Builder speedDataBuilder = IoProfiler.IoSpeedData.newBuilder();
        speedDataBuilder.setEndTimestamp(results.getLong(endTimeColumn));
        speedDataBuilder.setType(type);
        speedDataBuilder.setSpeed(results.getLong(speedColumn));
        speedData.add(speedDataBuilder.build());
      }
      return speedData;
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
    return null;
  }
}
