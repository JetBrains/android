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
package com.android.tools.datastore.service;

import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.LogService;
import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.datastore.database.ProfilerTable;
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
import com.android.tools.idea.io.grpc.stub.StreamObserver;
import java.sql.Connection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Datastore layer of the ProfilerService that manages forwarding/querying of all session-related requests/data.
 */
public class ProfilerService extends ProfilerServiceGrpc.ProfilerServiceImplBase implements ServicePassThrough {
  @NotNull private final LogService myLogService;
  private final ProfilerTable myTable;
  @NotNull private final DataStoreService myService;

  public ProfilerService(@NotNull DataStoreService service,
                         @NotNull LogService logService) {
    myService = service;
    myLogService = logService;
    myTable = new ProfilerTable();
  }

  @NotNull
  private LogService.Logger getLogger() {
    return myLogService.getLogger(ProfilerService.class);
  }

  @Override
  public void beginSession(BeginSessionRequest request, StreamObserver<BeginSessionResponse> responseObserver) {
    ProfilerServiceGrpc.ProfilerServiceBlockingStub client = myService.getProfilerClient(request.getDeviceId());
    if (client == null) {
      responseObserver.onNext(BeginSessionResponse.getDefaultInstance());
    }
    else {
      BeginSessionResponse response = client.beginSession(request);
      getLogger().info("Session (ID " + response.getSession().getSessionId() + ") begins.");
      // TODO (b/67508808) re-investigate whether we should use a poller to update the session instead.
      // The downside is we will have a delay before getSessions will see the data
      myTable.insertOrUpdateSession(response.getSession(),
                                    request.getSessionName(),
                                    request.getRequestTimeEpochMs(),
                                    request.getProcessAbi(),
                                    request.getJvmtiConfig().getAttachAgent(),
                                    Common.SessionMetaData.SessionType.FULL);
      responseObserver.onNext(response);
    }
    responseObserver.onCompleted();
  }

  @Override
  public void endSession(EndSessionRequest request, StreamObserver<EndSessionResponse> responseObserver) {
    getLogger().info("Session (ID " + request.getSessionId() + ") ends.");
    ProfilerServiceGrpc.ProfilerServiceBlockingStub client = myService.getProfilerClient(request.getDeviceId());
    if (client == null) {
      myTable.updateSessionEndTime(request.getSessionId(), request.getEndTimestamp());
      Common.Session session = myTable.getSessionById(request.getSessionId());
      responseObserver.onNext(EndSessionResponse.newBuilder().setSession(session).build());
    }
    else {
      EndSessionResponse response = client.endSession(request);
      Common.Session session = response.getSession();
      // TODO (b/67508808) re-investigate whether we should use a poller to update the session instead.
      // The downside is we will have a delay before getSessions will see the data
      myTable.updateSessionEndTime(session.getSessionId(), session.getEndTimestamp());
      responseObserver.onNext(response);
    }
    responseObserver.onCompleted();
  }

  @Override
  public void getSessionMetaData(GetSessionMetaDataRequest request,
                                 StreamObserver<GetSessionMetaDataResponse> responseObserver) {
    responseObserver.onNext(myTable.getSessionMetaData(request.getSessionId()));
    responseObserver.onCompleted();
  }

  @Override
  public void getSessions(GetSessionsRequest request, StreamObserver<GetSessionsResponse> responseObserver) {
    responseObserver.onNext(myTable.getSessions());
    responseObserver.onCompleted();
  }

  @Override
  public void deleteSession(DeleteSessionRequest request, StreamObserver<DeleteSessionResponse> responseObserver) {
    // TODO (b\67509712): properly delete all data related to the session.
    myTable.deleteSession(request.getSessionId());
    responseObserver.onNext(DeleteSessionResponse.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void importSession(ImportSessionRequest request, StreamObserver<ImportSessionResponse> responseObserver) {
    myTable.insertOrUpdateSession(request.getSession(), request.getSessionName(), request.getStartTimestampEpochMs(), "", false,
                                  request.getSessionType());
    responseObserver.onNext(ImportSessionResponse.newBuilder().build());
    responseObserver.onCompleted();
  }

  @NotNull
  @Override
  public List<DataStoreService.BackingNamespace> getBackingNamespaces() {
    return Collections.singletonList(DataStoreService.BackingNamespace.DEFAULT_SHARED_NAMESPACE);
  }

  @Override
  public void setBackingStore(@NotNull DataStoreService.BackingNamespace namespace, @NotNull Connection connection) {
    assert namespace == DataStoreService.BackingNamespace.DEFAULT_SHARED_NAMESPACE;
    myTable.initialize(connection);
  }
}
