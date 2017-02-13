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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetworkTable extends DatastoreTable<NetworkTable.NetworkStatements> {

  public enum NetworkStatements {
    INSERT_NETWORK_DATA,
    QUERY_NETWORK_DATA_BY_TYPE,
    QUERY_NETWORK_DATA,
    QUERY_COMMON_CONNECTION_DATA,
    FIND_CONNECTION_DATA,
    INSERT_CONNECTION_DATA
  }

  private static final Map<NetworkProfiler.NetworkProfilerData.DataCase, Integer> DATACASE_REQUEST_TYPE_MAP = new HashMap<>();
  private static final int INVALID_COLUMN = 0;
  private static final int BODY_COLUMN = 2;
  private static final int REQUEST_COLUMN = 3;
  private static final int RESPONSE_COLUMN = 4;


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
  public void initialize(Connection connection) {
    super.initialize(connection);
    try {
      createTable("Network_Data", "Id INTEGER NOT NULL", "Type INTEGER NOT NULL", "EndTime INTEGER", "Data BLOB");
      createTable("Network_Connection", "ProcessId INTEGER NOT NULL", "Id INTEGER NOT NULL", "StartTime INTEGER", "EndTime INTEGER",
                  "ConnectionData BLOB", "BodyData BLOB", "RequestData BLOB", "ResponseData BLOB", "PRIMARY KEY(ProcessId, Id)");
      createIndex("Network_Connection", "ProcessId", "Id");
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  @Override
  public void prepareStatements(Connection connection) {
    try {
      createStatement(NetworkStatements.INSERT_NETWORK_DATA, "INSERT INTO Network_Data (Id, Type, EndTime, Data) VALUES (?, ?, ?, ?)");
      createStatement(NetworkStatements.QUERY_NETWORK_DATA_BY_TYPE,
                      "SELECT Data FROM Network_Data WHERE (Id = ? OR Id = ?) AND Type = ? AND EndTime > ? AND EndTime <= ?");
      createStatement(NetworkStatements.QUERY_NETWORK_DATA,
                      "SELECT Data FROM Network_Data WHERE (Id = ? OR Id = ? ) AND EndTime > ? AND EndTime <= ?");

      createStatement(NetworkStatements.QUERY_COMMON_CONNECTION_DATA,
                      "SELECT ConnectionData FROM Network_Connection WHERE ProcessId = ? AND (EndTime > ? OR EndTime = 0) AND StartTime <= ?");
      createStatement(NetworkStatements.FIND_CONNECTION_DATA,
                      "SELECT ConnectionData, BodyData, RequestData, ResponseData FROM Network_Connection WHERE Id = ?");
      createStatement(NetworkStatements.INSERT_CONNECTION_DATA,
                      "INSERT OR REPLACE INTO Network_Connection (ProcessId, Id, StartTime, EndTime, ConnectionData, BodyData, RequestData, ResponseData) values (?, ?, ?, ?, ?, ?, ?, ?)");
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  public List<NetworkProfiler.HttpConnectionData> getNetworkConnectionDataByRequest(NetworkProfiler.HttpRangeRequest request) {
    List<NetworkProfiler.HttpConnectionData> datas = new ArrayList<>();
    ResultSet results = executeQuery(NetworkStatements.QUERY_COMMON_CONNECTION_DATA, request.getProcessId(), request.getStartTimestamp(),
                                     request.getEndTimestamp());
    try {
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
    if (request.getType() == NetworkProfiler.NetworkDataRequest.Type.ALL) {
      results = executeQuery(NetworkStatements.QUERY_NETWORK_DATA, request.getProcessId(), Common.AppId.ANY_VALUE, request.getStartTimestamp(),
                             request.getEndTimestamp());
    }
    else {
      results = executeQuery(NetworkStatements.QUERY_NETWORK_DATA_BY_TYPE, request.getProcessId(), Common.AppId.ANY_VALUE,
                             request.getType().getNumber(),
                             request.getStartTimestamp(), request.getEndTimestamp());
    }
    try {
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

  public NetworkProfiler.HttpDetailsResponse getHttpDetailsResponseById(long connId, NetworkProfiler.HttpDetailsRequest.Type type) {
    NetworkProfiler.HttpDetailsResponse.Builder responseBuilder = NetworkProfiler.HttpDetailsResponse.newBuilder();
    ResultSet results = executeQuery(NetworkStatements.FIND_CONNECTION_DATA, connId);
    try {
      if (results.next()) {
        int column = INVALID_COLUMN;
        switch (type) {
          case REQUEST:
            column = REQUEST_COLUMN;
            break;
          case RESPONSE:
            column = RESPONSE_COLUMN;
            break;
          case RESPONSE_BODY:
            column = BODY_COLUMN;
            break;

        }
        if (column != INVALID_COLUMN) {
          byte[] responseBytes = results.getBytes(column);
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

  public void insertOrReplace(int processId, NetworkProfiler.HttpDetailsResponse request, NetworkProfiler.HttpDetailsResponse response, NetworkProfiler.HttpDetailsResponse body, NetworkProfiler.HttpConnectionData data) {
    long id = data.getConnId();
    long startTime = data.getStartTimestamp();
    long endTime = data.getEndTimestamp();
    byte[] commonData = data.toByteArray();
    byte[] responseData = response == null ? null : response.toByteArray();
    byte[] requestData = request == null ? null : request.toByteArray();
    byte[] bodyData = body == null ? null : body.toByteArray();
    execute(NetworkStatements.INSERT_CONNECTION_DATA, processId, id, startTime, endTime, commonData, bodyData, requestData, responseData);
  }
}
