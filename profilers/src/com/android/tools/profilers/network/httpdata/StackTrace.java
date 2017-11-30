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
package com.android.tools.profilers.network.httpdata;

import com.android.tools.profilers.network.NetworkConnectionsModel;
import com.android.tools.profilers.network.httpdata.HttpData;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.StackFrameParser;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

/**
 * A class for fetching the stack trace of the creation of a request associated with an
 * {@link HttpData} instance.
 */
public final class StackTrace {
  private final ImmutableList<CodeLocation> myLocations;
  private final String myTrace;

  public StackTrace(@NotNull NetworkConnectionsModel model, @NotNull HttpData httpData) {
    myTrace = model.requestBytes(httpData.getTraceId()).toStringUtf8();
    ImmutableList.Builder<CodeLocation> builder = new ImmutableList.Builder<>();
    for (String line: myTrace.split("\\n")) {
      if (line.trim().isEmpty()) {
        continue;
      }
      builder.add(new StackFrameParser(line).toCodeLocation());
    }
    myLocations = builder.build();
  }

  @NotNull
  public ImmutableList<CodeLocation> getCodeLocations() {
    return myLocations;
  }

  @VisibleForTesting
  public String getTrace() {
    return myTrace;
  }
}
