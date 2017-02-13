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
import com.android.tools.profiler.proto.Profiler;
import com.google.protobuf3jarjar.InvalidProtocolBufferException;
import com.intellij.openapi.diagnostic.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Class that wraps database access for profiler level services. The primary information
 * managed by this class is device/process lifetime.
 */
public class ProfilerTable extends DatastoreTable<ProfilerTable.ProfilerStatements> {
  public enum ProfilerStatements {
    INSERT_DEVICE,
    INSERT_PROCESS,
    SELECT_PROCESS,
    SELECT_DEVICE,
  }

  // Need to have a lock due to processes being updated and queried at the same time.
  // If a process is being queried, while one is being updated it will not get
  // returned in the query results, this results in the UI flickering.
  private Object myLock = new Object();

  private static Logger getLogger() {
    return Logger.getInstance(ProfilerTable.class);
  }

  @Override
  public void initialize(Connection connection) {
    super.initialize(connection);
    try {
      createTable("Profiler_Devices", "DeviceId INTEGER", "StartTime INTEGER", "EndTime INTEGER", "Data BLOB");
      createTable("Profiler_Processes", "DeviceId INTEGER", "ProcessId INTEGER", "StartTime INTEGER", "EndTime INTEGER", "Data BLOB");
      createIndex("Profiler_Devices", "DeviceId", "StartTime");
      createIndex("Profiler_Processes", "DeviceId", "ProcessId", "StartTime");
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  @Override
  public void prepareStatements(Connection connection) {
    try {
      createStatement(ProfilerStatements.INSERT_DEVICE, "INSERT OR REPLACE INTO Profiler_Devices (DeviceId, StartTime, EndTime, Data) values (?, ?, ?, ?)");
      createStatement(ProfilerStatements.INSERT_PROCESS, "INSERT OR REPLACE INTO Profiler_Processes (DeviceId, ProcessId, StartTime, EndTime, Data) values (?, ?, ?, ?, ?)");
      createStatement(ProfilerStatements.SELECT_PROCESS, "SELECT Data from Profiler_Processes WHERE DeviceId = ? AND (EndTime > ? OR EndTime = 0) AND StartTime < ?");
      createStatement(ProfilerStatements.SELECT_DEVICE, "SELECT Data from Profiler_Devices WHERE (EndTime > ? OR EndTime = 0) AND StartTime < ?");
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  public Profiler.GetDevicesResponse getDevices(Profiler.GetDevicesRequest request) {
    synchronized (myLock) {
      Profiler.GetDevicesResponse.Builder responseBuilder = Profiler.GetDevicesResponse.newBuilder();
      ResultSet results = executeQuery(ProfilerStatements.SELECT_DEVICE, Long.MIN_VALUE, Long.MAX_VALUE);
      try {
        while (results.next()) {
          responseBuilder.addDevice(Profiler.Device.parseFrom(results.getBytes(1)));
        }
      }
      catch (InvalidProtocolBufferException | SQLException ex) {
        getLogger().error(ex);
      }
      return responseBuilder.build();
    }
  }

  public Profiler.GetProcessesResponse getProcesses(Profiler.GetProcessesRequest request) {
    synchronized (myLock) {
      Profiler.GetProcessesResponse.Builder responseBuilder = Profiler.GetProcessesResponse.newBuilder();
      ResultSet results =
        executeQuery(ProfilerStatements.SELECT_PROCESS, request.getSession().toString().hashCode(), Long.MIN_VALUE, Long.MAX_VALUE);
      try {
        while (results.next()) {
          byte[] data = results.getBytes(1);
          Profiler.Process process = data == null ? Profiler.Process.getDefaultInstance() : Profiler.Process.parseFrom(data);
          responseBuilder.addProcess(process);
        }
      }
      catch (InvalidProtocolBufferException | SQLException ex) {
        getLogger().error(ex);
      }
      return responseBuilder.build();
    }
  }

  public void insertOrUpdateDevice(Profiler.Device device) {
    synchronized (myLock) {
      //TODO: Update start/end times with times polled from device.
      //End time always equals now, start time comes from device. This way if we get disconnected we still have an accurate end time.
      execute(ProfilerStatements.INSERT_DEVICE, device.getSerial().hashCode(), 0L, 0L, device.toByteArray());
    }
  }

  public void insertOrUpdateProcess(Common.Session session, Profiler.Process process) {
    synchronized (myLock) {
      //TODO: Properly set end time. Here the end time comes from the device, or is set to now, so we don't leave
      //end times open.
      execute(ProfilerStatements.INSERT_PROCESS, session.toString().hashCode(), process.getPid(), 0L, 0L, process.toByteArray());
    }
  }
}
