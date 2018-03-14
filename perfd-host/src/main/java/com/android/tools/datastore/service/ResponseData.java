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
package com.android.tools.datastore.service;

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.CpuProfiler;
import io.grpc.stub.StreamObserver;

/**
 * Stores a response of a determined type to avoid making unnecessary queries to the database.
 *
 * Often, there is no need for querying the database to get the response corresponding to the request made.
 * For example, in the threads monitor each thread has a ThreadStateDataSeries that will call
 * {@link #getThreads(CpuProfiler.GetThreadsRequest, StreamObserver)} passing a request with the same arguments (start/end timestamp,
 * pid, and session). In these cases, we can query the database once and return a cached result for the subsequent calls.
 *
 * @param <T> type of the response stored
 */
class ResponseData<T> {
  private Common.Session mySession;
  private long myStart;
  private long myEnd;
  private T myResponse;

  ResponseData(Common.Session session, long startTimestamp, long endTimestamp, T response) {
    mySession = session;
    myStart = startTimestamp;
    myEnd = endTimestamp;
    myResponse = response;
  }

  public boolean matches(Common.Session session, long startTimestamp, long endTimestamp) {
    boolean isSessionEquals = (mySession == null && session == null) || (mySession != null && mySession.equals(session));
    return isSessionEquals && myStart == startTimestamp && myEnd == endTimestamp;
  }

  public T getResponse() {
    return myResponse;
  }

  @SuppressWarnings("unchecked")
  public static ResponseData createEmpty() {
    return new ResponseData(null, 0, 0, null);
  }
}
