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
package com.android.tools.profilers;

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler.BeginSessionRequest;
import com.android.tools.profiler.proto.Profiler.BeginSessionResponse;
import com.android.tools.profiler.proto.Profiler.DeleteSessionRequest;
import com.android.tools.profiler.proto.Profiler.DeleteSessionResponse;
import com.android.tools.profiler.proto.Profiler.EndSessionRequest;
import com.android.tools.profiler.proto.Profiler.EndSessionResponse;
import com.android.tools.profiler.proto.Profiler.GetSessionMetaDataRequest;
import com.android.tools.profiler.proto.Profiler.GetSessionMetaDataResponse;
import com.android.tools.profiler.proto.Profiler.GetSessionsRequest;
import com.android.tools.profiler.proto.Profiler.GetSessionsResponse;
import com.android.tools.profiler.proto.Profiler.ImportSessionRequest;
import com.android.tools.profiler.proto.Profiler.ImportSessionResponse;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import io.grpc.stub.StreamObserver;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public final class FakeProfilerService extends ProfilerServiceGrpc.ProfilerServiceImplBase {
  private final Map<Long, Common.Session> mySessions;
  private final Map<Long, Common.SessionMetaData> mySessionMetaDatas;
  private final FakeTimer myTimer;
  private boolean myAttachAgentCalled;

  private Common.SessionMetaData.SessionType myLastImportedSessionType;

  /**
   * Creates a fake profiler service. If connected is true there will be a device with a process already present.
   */
  public FakeProfilerService(@NotNull FakeTimer timer) {
    myTimer = timer;
    mySessions = new HashMap<>();
    mySessionMetaDatas = new HashMap<>();
  }

  /**
   * This is a legacy helper function for test against the old profiler pipeline. Instead of creating a session via the BeginSession
   * command, a session is crafted and passed to this function. The events pipeline crafts the proper session events to determine the life
   * of a session. After this function is called a poll will need to happen to get the latest session state.
   */
  public void addSession(Common.Session session, Common.SessionMetaData metadata) {
    mySessions.put(session.getSessionId(), session);
    mySessionMetaDatas.put(session.getSessionId(), metadata);
  }

  @Override
  public void beginSession(BeginSessionRequest request, StreamObserver<BeginSessionResponse> responseObserver) {
    BeginSessionResponse.Builder builder = BeginSessionResponse.newBuilder();
    long sessionId = request.getDeviceId() ^ request.getPid();
    Common.Session session = Common.Session.newBuilder()
      .setSessionId(sessionId)
      .setStreamId(request.getDeviceId())
      .setPid(request.getPid())
      .setStartTimestamp(myTimer.getCurrentTimeNs())
      .setEndTimestamp(Long.MAX_VALUE)
      .build();
    Common.SessionMetaData metadata = Common.SessionMetaData.newBuilder()
      .setSessionId(sessionId)
      .setSessionName(request.getSessionName())
      .setStartTimestampEpochMs(request.getRequestTimeEpochMs())
      .setProcessAbi(request.getProcessAbi())
      .setJvmtiEnabled(request.getJvmtiConfig().getAttachAgent())
      .setType(Common.SessionMetaData.SessionType.FULL)
      .build();
    mySessions.put(sessionId, session);
    mySessionMetaDatas.put(sessionId, metadata);
    myAttachAgentCalled = request.getJvmtiConfig().getAttachAgent();
    builder.setSession(session);
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void importSession(ImportSessionRequest request, StreamObserver<ImportSessionResponse> responseObserver) {
    mySessions.put(request.getSession().getSessionId(), request.getSession());

    Common.Session session = request.getSession();
    long sessionId = session.getSessionId();
    Common.SessionMetaData metadata = Common.SessionMetaData.newBuilder()
      .setSessionId(sessionId)
      .setSessionName(request.getSessionName())
      .setJvmtiEnabled(false)
      .setType(request.getSessionType())
      .build();
    mySessionMetaDatas.put(sessionId, metadata);
    myAttachAgentCalled = false;
    myLastImportedSessionType = request.getSessionType();
    responseObserver.onNext(ImportSessionResponse.newBuilder().build());
    responseObserver.onCompleted();
  }

  @Override
  public void endSession(EndSessionRequest request, StreamObserver<EndSessionResponse> responseObserver) {
    assert (mySessions.containsKey(request.getSessionId()));
    Common.Session session = mySessions.get(request.getSessionId());
    // Set an arbitrary end time that is not Long.MAX_VALUE, which is reserved for indicating a session is ongoing.
    // If our session has not already ended we set an end timestamp.
    if (session.getEndTimestamp() == Long.MAX_VALUE) {
      session = session.toBuilder()
        .setEndTimestamp(session.getStartTimestamp() + 1)
        .build();
    }
    mySessions.put(session.getSessionId(), session);
    EndSessionResponse.Builder builder = EndSessionResponse.newBuilder().setSession(session);
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getSessions(GetSessionsRequest request, StreamObserver<GetSessionsResponse> responseObserver) {
    GetSessionsResponse response = GetSessionsResponse.newBuilder()
      .addAllSessions(mySessions.values())
      .build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void getSessionMetaData(GetSessionMetaDataRequest request,
                                 StreamObserver<GetSessionMetaDataResponse> responseObserver) {
    if (mySessionMetaDatas.containsKey(request.getSessionId())) {
      responseObserver.onNext(GetSessionMetaDataResponse.newBuilder().setData(mySessionMetaDatas.get(request.getSessionId())).build());
    }
    else {
      responseObserver.onNext(GetSessionMetaDataResponse.getDefaultInstance());
    }
    responseObserver.onCompleted();
  }

  @Override
  public void deleteSession(DeleteSessionRequest request, StreamObserver<DeleteSessionResponse> responseObserver) {
    mySessions.remove(request.getSessionId());
    mySessionMetaDatas.remove(request.getSessionId());
    responseObserver.onNext(DeleteSessionResponse.getDefaultInstance());
    responseObserver.onCompleted();
  }

  public boolean getAgentAttachCalled() {
    return myAttachAgentCalled;
  }

  public Common.SessionMetaData.SessionType getLastImportedSessionType() {
    return myLastImportedSessionType;
  }
}
