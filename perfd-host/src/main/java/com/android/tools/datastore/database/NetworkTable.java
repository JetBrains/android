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
import com.android.tools.profiler.proto.NetworkProfiler;
import com.google.protobuf3jarjar.InvalidProtocolBufferException;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class NetworkTable extends DataStoreTable<NetworkTable.NetworkStatements> {
  public enum NetworkStatements {
    INSERT_NETWORK_DATA,
    QUERY_NETWORK_DATA_BY_TYPE,
    QUERY_NETWORK_DATA,
    QUERY_COMMON_CONNECTION_DATA,
    FIND_CONNECTION_DATA,
    INSERT_CONNECTION_DATA
  }

  private static final Map<NetworkProfiler.NetworkProfilerData.DataCase, Integer> DATACASE_REQUEST_TYPE_MAP = new HashMap<>();
  private static final int REQUEST_COLUMN = 2;
  private static final int RESPONSE_COLUMN = 3;
  private static final int REQUEST_BODY_COLUMN = 4;
  private static final int RESPONSE_BODY_COLUMN = 5;
  private static final int THREADS_COLUMN = 6;

  static {
    DATACASE_REQUEST_TYPE_MAP
      .put(NetworkProfiler.NetworkProfilerData.DataCase.SPEED_DATA, NetworkProfiler.NetworkDataRequest.Type.SPEED.getNumber());
    DATACASE_REQUEST_TYPE_MAP
      .put(NetworkProfiler.NetworkProfilerData.DataCase.CONNECTION_DATA, NetworkProfiler.NetworkDataRequest.Type.CONNECTIONS.getNumber());
    DATACASE_REQUEST_TYPE_MAP.put(NetworkProfiler.NetworkProfilerData.DataCase.CONNECTIVITY_DATA,
                                  NetworkProfiler.NetworkDataRequest.Type.CONNECTIVITY.getNumber());
  }

  private static Logger getLogger() {
    return Logger.getInstance(NetworkTable.class);
  }

  @Override
  public void initialize(@NotNull Connection connection) {
    super.initialize(connection);
    try {
      createTable("Network_Data", "Id INTEGER NOT NULL", "Type INTEGER NOT NULL", "EndTime INTEGER", "Data BLOB");
      createTable("Network_Connection", "ProcessId INTEGER NOT NULL", "Session INTEGER NOT NULL", "Id INTEGER NOT NULL",
                  "StartTime INTEGER",
                  "EndTime INTEGER",
                  "ConnectionData BLOB", "RequestData BLOB", "ResponseData BLOB", "RequestBodyData BLOB", "ResponseBodyData BLOB", "ThreadsData BLOB",
                  "PRIMARY KEY(ProcessId, Id)");
      createUniqueIndex("Network_Data", "Id", "Type", "EndTime");
      createUniqueIndex("Network_Connection", "ProcessId", "Session", "Id");
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  @Override
  public void prepareStatements() {
    try {
      createStatement(NetworkStatements.INSERT_NETWORK_DATA,
                      "INSERT OR IGNORE INTO Network_Data (Id, Type, EndTime, Data) VALUES (?, ?, ?, ?)");
      createStatement(NetworkStatements.QUERY_NETWORK_DATA_BY_TYPE,
                      "SELECT Data FROM Network_Data WHERE (Id = ? OR Id = ?) AND Type = ? AND EndTime > ? AND EndTime <= ?");
      createStatement(NetworkStatements.QUERY_NETWORK_DATA,
                      "SELECT Data FROM Network_Data WHERE (Id = ? OR Id = ? ) AND EndTime > ? AND EndTime <= ?");

      createStatement(NetworkStatements.QUERY_COMMON_CONNECTION_DATA,
                      "SELECT ConnectionData FROM Network_Connection WHERE ProcessId = ? AND Session = ? AND (EndTime > ? OR EndTime = 0) AND StartTime <= ?");
      createStatement(NetworkStatements.FIND_CONNECTION_DATA,
                      "SELECT ConnectionData, RequestData, ResponseData, RequestBodyData, ResponseBodyData, ThreadsData FROM Network_Connection WHERE Id = ? AND Session = ?");
      createStatement(NetworkStatements.INSERT_CONNECTION_DATA,
                      "INSERT OR REPLACE INTO Network_Connection (ProcessId, Session, Id, StartTime, EndTime, ConnectionData, RequestData, ResponseData, RequestBodyData, ResponseBodyData, ThreadsData) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  public List<NetworkProfiler.HttpConnectionData> getNetworkConnectionDataByRequest(NetworkProfiler.HttpRangeRequest request) {
    List<NetworkProfiler.HttpConnectionData> datas = new ArrayList<>();
    try {
      ResultSet results = executeQuery(NetworkStatements.QUERY_COMMON_CONNECTION_DATA, request.getProcessId(), request.getSession(),
                                       request.getStartTimestamp(),
                                       request.getEndTimestamp());
      while (results.next()) {
        NetworkProfiler.HttpConnectionData.Builder data = NetworkProfiler.HttpConnectionData.newBuilder();
        data.mergeFrom(results.getBytes(1));
        datas.add(data.build());
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      getLogger().error(ex);
    }
    return datas;
  }

  public List<NetworkProfiler.NetworkProfilerData> getNetworkDataByRequest(NetworkProfiler.NetworkDataRequest request) {
    List<NetworkProfiler.NetworkProfilerData> datas = new ArrayList<>();
    ResultSet results;
    try {
      if (request.getType() == NetworkProfiler.NetworkDataRequest.Type.ALL) {
        results =
          executeQuery(NetworkStatements.QUERY_NETWORK_DATA, request.getProcessId(), Common.AppId.ANY_VALUE, request.getStartTimestamp(),
                       request.getEndTimestamp());
      }
      else {
        results = executeQuery(NetworkStatements.QUERY_NETWORK_DATA_BY_TYPE, request.getProcessId(), Common.AppId.ANY_VALUE,
                               request.getType().getNumber(),
                               request.getStartTimestamp(), request.getEndTimestamp());
      }
      while (results.next()) {
        NetworkProfiler.NetworkProfilerData.Builder data = NetworkProfiler.NetworkProfilerData.newBuilder();
        data.mergeFrom(results.getBytes(1));
        datas.add(data.build());
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      getLogger().error(ex);
    }
    return datas;
  }

  public void insert(int appId, NetworkProfiler.NetworkProfilerData data) {
    execute(NetworkStatements.INSERT_NETWORK_DATA, appId, DATACASE_REQUEST_TYPE_MAP.get(data.getDataCase()),
            data.getBasicInfo().getEndTimestamp(), data.toByteArray());
  }

  public NetworkProfiler.HttpDetailsResponse getHttpDetailsResponseById(long connId,
                                                                        Common.Session session,
                                                                        NetworkProfiler.HttpDetailsRequest.Type type) {
    NetworkProfiler.HttpDetailsResponse.Builder responseBuilder = NetworkProfiler.HttpDetailsResponse.newBuilder();
    try {
      ResultSet results = executeQuery(NetworkStatements.FIND_CONNECTION_DATA, connId, session);
      if (results.next()) {
        Optional<Integer> column = columnFor(type);
        if (column.isPresent()) {
          byte[] responseBytes = results.getBytes(column.get());
          if (responseBytes != null) {
            responseBuilder.mergeFrom(responseBytes);
          }
          return responseBuilder.build();
        }
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      getLogger().error(ex);
    }
    return null;
  }

  private Optional<Integer> columnFor(NetworkProfiler.HttpDetailsRequest.Type type) {
    switch (type) {
      case REQUEST:
        return Optional.of(REQUEST_COLUMN);
      case RESPONSE:
        return Optional.of(RESPONSE_COLUMN);
      case REQUEST_BODY:
        return Optional.of(REQUEST_BODY_COLUMN);
      case RESPONSE_BODY:
        return Optional.of(RESPONSE_BODY_COLUMN);
      case ACCESSING_THREADS:
        return Optional.of(THREADS_COLUMN);
      case UNSPECIFIED:
      case UNRECOGNIZED:
        return Optional.empty();
    }
    throw new AssertionError(type);
  }

  public void insertOrReplace(int processId,
                              Common.Session session,
                              NetworkProfiler.HttpDetailsResponse request,
                              NetworkProfiler.HttpDetailsResponse response,
                              NetworkProfiler.HttpDetailsResponse requestBody,
                              NetworkProfiler.HttpDetailsResponse responseBody,
                              NetworkProfiler.HttpDetailsResponse threads,
                              NetworkProfiler.HttpConnectionData connection) {
    long id = connection.getConnId();
    long startTime = connection.getStartTimestamp();
    long endTime = connection.getEndTimestamp();
    byte[] commonData = connection.toByteArray();
    byte[] requestData = request == null ? null : request.toByteArray();
    byte[] responseData = response == null ? null : response.toByteArray();
    byte[] requestBodyData = requestBody == null ? null : requestBody.toByteArray();
    byte[] responseBodyData = responseBody == null ? null : responseBody.toByteArray();
    byte[] threadsData = threads == null ? null : threads.toByteArray();
    execute(NetworkStatements.INSERT_CONNECTION_DATA, processId, session, id, startTime, endTime, commonData, requestData, responseData,
            requestBodyData, responseBodyData, threadsData);
  }
}
