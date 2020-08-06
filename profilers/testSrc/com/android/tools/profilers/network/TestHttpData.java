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
package com.android.tools.profilers.network;

import com.android.tools.profilers.network.httpdata.HttpData;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/**
 * Helper class for creating {@link HttpData} and {@link HttpData.Builder}s that are test friendly.
 * For example, they work with seconds (rather than microseconds) and they make sure a bunch of
 * sensible values are included by default.
 * <p>
 * These values are exposed either via public constants for methods that start with the word
 * {@code fake}, in case a test needs to test against them.
 * <p>
 * Because a request's upload, download, and end timestamps have a special meaning if set to 0,
 * this class requires that the start timestamp at least be greater than 0, for clarity.
 */
public final class TestHttpData {

  public static final HttpData.JavaThread FAKE_THREAD = new HttpData.JavaThread(0, "Main Thread");
  public static final List<HttpData.JavaThread> FAKE_THREAD_LIST = Collections.singletonList(FAKE_THREAD);
  public static final int FAKE_RESPONSE_CODE = 302;
  public static final String FAKE_RESPONSE_DESCRIPTION = "Found";
  public static final String FAKE_CONTENT_TYPE = "image/jpeg";

  private TestHttpData() {
    // disabled - static methods only
  }

  public static String fakeUrl(long id) {
    return "http://example.com/" + id;
  }

  public static int fakeContentSize(long id) {
    return (int)(id * 100);
  }

  // TODO remove once we remove the legacy pipeline codebase.
  public static String fakeStackTraceId(String trace) {
    return Integer.toString(trace.hashCode());
  }

  public static String fakeStackTrace(long id) {
    return String.format(
      "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java)\n" +
      "com.example.android.displayingbitmaps.util.AsyncTask$2.call(AsyncTask.java:%d)", id);
  }

  @NotNull
  public static String fakeResponseFields(long id, String contentType) {
    return String
      .format("status line = HTTP/1.1 %d %s\nContent-Type = %s;\nconnId = %d\n Content-Length = %d\n", FAKE_RESPONSE_CODE,
              FAKE_RESPONSE_DESCRIPTION, contentType, id, fakeContentSize(id));
  }

  @NotNull
  public static String fakeResponseFields(long id) {
    return fakeResponseFields(id, FAKE_CONTENT_TYPE);
  }

  @NotNull
  public static String fakeRequestPayloadId(long id) {
    return "requestPayload" + id;
  }

  @NotNull
  public static String fakeResponsePayloadId(long id) {
    return "responsePayload" + id;
  }

  @NotNull
  public static HttpData.Builder newBuilder(long id) {
    return initHttpDataBuilder(newBuilder(id, 1, 1));
  }


  @NotNull
  public static HttpData.Builder newBuilder(long id, long startS, long endS) {
    return initHttpDataBuilder(newBuilder(id, startS, startS, endS, endS, endS));
  }

  @NotNull
  public static HttpData.Builder newBuilder(long id, long startS, long endS, HttpData.JavaThread initialThread) {
    return initHttpDataBuilder(newBuilder(id, startS, startS, endS, endS, endS, initialThread));
  }

  @NotNull
  public static HttpData.Builder newBuilder(long id,
                                            long requestStartSec,
                                            long requestCompleteSec,
                                            long responseStartSec,
                                            long responseCompleteSec,
                                            long connectionEndSec) {
    return initHttpDataBuilder(
      newBuilder(id, requestStartSec, requestCompleteSec, responseStartSec, responseCompleteSec, connectionEndSec, FAKE_THREAD));
  }

  @NotNull
  public static HttpData.Builder newBuilder(long id,
                                            long requestStartSec,
                                            long requestCompleteSec,
                                            long responseStartSec,
                                            long responseCompleteSec,
                                            long connectionEndSec,
                                            HttpData.JavaThread initialThread) {
    if (requestStartSec <= 0) {
      throw new IllegalArgumentException("startS should be a positive (>= 0) value");
    }

    return initHttpDataBuilder(new HttpData.Builder(id,
                                                    TimeUnit.SECONDS.toMicros(requestStartSec),
                                                    TimeUnit.SECONDS.toMicros(requestCompleteSec),
                                                    TimeUnit.SECONDS.toMicros(responseStartSec),
                                                    TimeUnit.SECONDS.toMicros(responseCompleteSec),
                                                    TimeUnit.SECONDS.toMicros(connectionEndSec),
                                                    Collections.singletonList(initialThread)));
  }

  @NotNull
  private static HttpData.Builder initHttpDataBuilder(@NotNull HttpData.Builder builder) {
    // Create sample data so we can read its values (the builder itself doesn't have or need
    // getters)
    HttpData temp = builder.build();
    long id = temp.getId();

    builder.setTrace(fakeStackTrace(id));
    builder.setUrl(fakeUrl(id));
    if (temp.getRequestCompleteTimeUs() != 0) {
      builder.setRequestPayloadId(fakeRequestPayloadId(id));
    }
    if (temp.getResponseCompleteTimeUs() != 0) {
      builder.setResponsePayloadId(fakeResponsePayloadId(id));
      builder.setResponseFields(fakeResponseFields(id));
    }

    return builder;
  }
}
