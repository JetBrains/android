/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.transport.faketransport;

import com.android.tools.idea.transport.TransportService;
import com.android.tools.profiler.proto.Common;

import com.android.tools.profiler.proto.EventProfiler.ActivityDataResponse;
import com.android.tools.profiler.proto.EventProfiler.EventDataRequest;
import com.android.tools.profiler.proto.EventProfiler.EventStartRequest;
import com.android.tools.profiler.proto.EventProfiler.EventStartResponse;
import com.android.tools.profiler.proto.EventProfiler.EventStopRequest;
import com.android.tools.profiler.proto.EventProfiler.EventStopResponse;
import com.android.tools.profiler.proto.EventProfiler.SystemDataResponse;
import com.android.tools.profiler.proto.EventServiceGrpc;
import com.android.tools.profiler.proto.MemoryProfiler.ListDumpInfosRequest;
import com.android.tools.profiler.proto.MemoryProfiler.ListHeapDumpInfosResponse;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryRequest;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryStartRequest;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryStartResponse;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryStopRequest;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryStopResponse;
import com.android.tools.idea.io.grpc.BindableService;
import com.android.tools.idea.io.grpc.stub.StreamObserver;
import com.android.tools.profiler.proto.Trace;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class FakeGrpcServer extends FakeGrpcChannel {
  /**
   * Mapping from sessions being profiled to the number of profilers that are monitoring it.
   */
  private final Map<Long, Integer> myProfiledProcesses;

  /**
   * A test should use createFakeGrpcServer() to obtain an instance of FakeGrpcServer, not calling the constructor directly.
   */
  private FakeGrpcServer(String name, BindableService... services) {
    super(name, services);
    myProfiledProcesses = new HashMap<>(7);
  }

  /**
   * @return a new instance of FakeGrpcServer ready for a test to use.
   */
  @NotNull
  public static FakeGrpcServer createFakeGrpcServer(String name, BindableService transportService, BindableService profilerService) {
    FakeGrpcServer server =
      new FakeGrpcServer(name, transportService, profilerService);
    // Set the links between the services and the server.
    TransportService.setTestChannelName(server.getName());
    return server;
  }

  /**
   * A convenience method for creating a fake GRPC server when you don't care about
   * profiler-specific services.
   * <p>
   * Note: The transport service was refactored out of the profiler service, which is why many
   * original tests use them, but they are not required.
   */
  @NotNull
  public static FakeGrpcServer createFakeGrpcServer(String name, BindableService transportService) {
    return createFakeGrpcServer(name, transportService, transportService);
  }

  /**
   * @return the number of processes currently being profiled.
   */
  public int getProfiledProcessCount() {
    return myProfiledProcesses.keySet().size();
  }

  private synchronized void addProfiledProcess(Common.Session session) {
    long sessionId = session.getSessionId();
    int profilerCount = myProfiledProcesses.getOrDefault(sessionId, 0);
    myProfiledProcesses.put(sessionId, profilerCount + 1);
  }

  private synchronized void removeProfiledProcess(Common.Session session) {
    long sessionId = session.getSessionId();
    Integer profilerCount = myProfiledProcesses.get(sessionId);
    if (profilerCount != null) {
      if (profilerCount > 1) {
        myProfiledProcesses.replace(sessionId, profilerCount - 1);
      }
      else {
        myProfiledProcesses.remove(sessionId);
      }
    }
  }

}
