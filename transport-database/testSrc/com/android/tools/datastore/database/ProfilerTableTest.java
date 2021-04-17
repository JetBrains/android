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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.Profiler.GetSessionsResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class ProfilerTableTest extends DatabaseTest<ProfilerTable> {
  @Override
  @NotNull
  protected ProfilerTable createTable() {
    return new ProfilerTable();
  }

  @Override
  @NotNull
  protected List<Consumer<ProfilerTable>> getTableQueryMethodsForVerification() {
    List<Consumer<ProfilerTable>> methodCalls = new ArrayList<>();

    methodCalls.add((table) -> assertThat(table.getSessionById(-1)).isEqualTo(Common.Session.getDefaultInstance()));
    methodCalls
      .add((table) -> assertThat(table.getSessionMetaData(-1)).isEqualTo(Profiler.GetSessionMetaDataResponse.getDefaultInstance()));
    methodCalls.add((table) -> assertThat(table.getSessions()).isEqualTo(GetSessionsResponse.getDefaultInstance()));
    methodCalls.add((table) -> table
      .insertOrUpdateSession(Common.Session.getDefaultInstance(), "Name", 0, "x86", false,
                             Common.SessionMetaData.SessionType.UNSPECIFIED));
    methodCalls.add((table) -> table.updateSessionEndTime(0, 0));
    methodCalls.add((table) -> table.deleteSession(-1));
    return methodCalls;
  }

  @Test
  public void testInsertAndGetSessions() {
    List<Common.Session> sessions = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      long startTime = 40 + i;
      String sessionName = Integer.toString(60 + i);
      Common.Session session = Common.Session
        .newBuilder()
        .setSessionId(10 + i)
        .setStreamId(20 + i)
        .setPid(30 + i)
        .setStartTimestamp(startTime)
        .setEndTimestamp(Long.MAX_VALUE)
        .build();

      getTable().insertOrUpdateSession(session, sessionName, startTime, "x86", true, Common.SessionMetaData.SessionType.FULL);
      sessions.add(session);
    }

    GetSessionsResponse response = getTable().getSessions();
    assertThat(response.getSessionsCount()).isEqualTo(10);
    for (int i = 0; i < 10; i++) {
      assertThat(response.getSessions(i)).isEqualTo(sessions.get(i));
    }

    for (int i = 0; i < 10; i++) {
      Common.Session session = sessions.get(i).toBuilder().setEndTimestamp(50 + i).build();
      getTable().updateSessionEndTime(session.getSessionId(), session.getEndTimestamp());
      sessions.set(i, session);
    }

    response = getTable().getSessions();
    assertThat(response.getSessionsCount()).isEqualTo(10);
    for (int i = 0; i < 10; i++) {
      assertThat(response.getSessions(i)).isEqualTo(sessions.get(i));
    }
  }

  @Test
  public void testGetSessionById() {
    List<Common.Session> sessions = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      long startTime = 40 + i;
      String sessionName = Integer.toString(60 + i);
      Common.Session session = Common.Session
        .newBuilder()
        .setSessionId(10 + i)
        .setStreamId(20 + i)
        .setPid(30 + i)
        .setStartTimestamp(startTime)
        .setEndTimestamp(Long.MAX_VALUE)
        .build();

      getTable().insertOrUpdateSession(session, sessionName, startTime, "x86", true, Common.SessionMetaData.SessionType.FULL);
      sessions.add(session);
    }

    for (int i = 0; i < 2; i++) {
      assertThat(getTable().getSessionById(sessions.get(i).getSessionId())).isEqualTo(sessions.get(i));
    }

    // Test the invalid case.
    assertThat(getTable().getSessionById(-1)).isEqualTo(Common.Session.getDefaultInstance());
  }

  @Test
  public void testDeleteSession() {
    List<Common.Session> sessions = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      long startTime = 40 + i;
      String sessionName = Integer.toString(60 + i);
      Common.Session session = Common.Session
        .newBuilder()
        .setSessionId(10 + i)
        .setStreamId(20 + i)
        .setPid(30 + i)
        .setStartTimestamp(startTime)
        .setEndTimestamp(Long.MAX_VALUE)
        .build();

      getTable().insertOrUpdateSession(session, sessionName, startTime, "x86", true, Common.SessionMetaData.SessionType.FULL);
      sessions.add(session);
    }

    for (int i = 0; i < 2; i++) {
      assertThat(getTable().getSessionById(sessions.get(i).getSessionId())).isEqualTo(sessions.get(i));
      getTable().deleteSession(sessions.get(i).getSessionId());
    }

    for (int i = 0; i < 2; i++) {
      assertThat(getTable().getSessionById(sessions.get(i).getSessionId())).isEqualTo(Common.Session.getDefaultInstance());
    }
  }

  @Test
  public void testInsertAndGetSessionMetaData() {
    List<Common.SessionMetaData> metaDatas = new ArrayList<>();
    Random rand = new Random();
    for (int i = 0; i < 10; i++) {
      long sessionId = 10 + i;
      long startTime = 40 + i;
      boolean useJvmti = rand.nextBoolean();
      String sessionName = Integer.toString(60 + i);
      Common.Session session = Common.Session.newBuilder().setSessionId(sessionId).build();
      Common.SessionMetaData metaData = Common.SessionMetaData
        .newBuilder()
        .setSessionId(sessionId)
        .setStartTimestampEpochMs(startTime)
        .setSessionName(sessionName)
        .setProcessAbi("x86")
        .setJvmtiEnabled(useJvmti)
        .setType(Common.SessionMetaData.SessionType.FULL)
        .build();

      getTable()
        .insertOrUpdateSession(session, sessionName, startTime, "x86", useJvmti,
                               Common.SessionMetaData.SessionType.FULL);
      metaDatas.add(metaData);
    }

    for (int i = 0; i < 10; i++) {
      Common.SessionMetaData data = metaDatas.get(i);
      Profiler.GetSessionMetaDataResponse response = getTable().getSessionMetaData(data.getSessionId());
      assertThat(response.getData()).isEqualTo(data);
    }
  }
}