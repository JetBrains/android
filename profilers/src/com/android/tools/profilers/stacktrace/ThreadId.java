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
package com.android.tools.profilers.stacktrace;

import com.android.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;

public class ThreadId {
  public static final ThreadId INVALID_THREAD_ID = new ThreadId(-1);
  @VisibleForTesting static final String DISPLAY_FORMAT = "<Thread %s>";
  private static final String UNKNOWN_THREAD_NAME = "Unknown";

  private final String myThreadName;
  private final String myDisplayName;

  public ThreadId(int threadId) {
    this(threadId == -1 ? UNKNOWN_THREAD_NAME : Integer.toString(threadId));
  }

  public ThreadId(@NotNull String threadName) {
    myThreadName = threadName.isEmpty() ? UNKNOWN_THREAD_NAME : threadName;
    myDisplayName = String.format(DISPLAY_FORMAT, myThreadName);
  }

  @Override
  public int hashCode() {
    return myThreadName.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof ThreadId && ((ThreadId)obj).myThreadName.equals(myThreadName);
  }

  @Override
  public String toString() {
    return myDisplayName;
  }
}
