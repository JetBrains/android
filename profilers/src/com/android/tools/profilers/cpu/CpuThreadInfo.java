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
package com.android.tools.profilers.cpu;

import com.android.annotations.NonNull;
import org.jetbrains.annotations.NotNull;

public class CpuThreadInfo implements Comparable<CpuThreadInfo> {
  private static final String RENDER_THREAD_NAME = "RenderThread";

  /** Thread id */
  private final int myId;

  /** Thread name */
  private final String myName;

  /**
   * Whether this {@link CpuThreadInfo} contains information of a main thread.
   */
  private final boolean myIsMainThread;

  public CpuThreadInfo(int threadId, @NonNull String name, boolean isMainThread) {
    myId = threadId;
    myName = name;
    myIsMainThread = isMainThread;
  }

  public CpuThreadInfo(int threadId, @NonNull String name) {
    this(threadId, name, false);
  }

  public int getId() {
    return myId;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public boolean isMainThread() {
    return myIsMainThread;
  }

  public boolean isRenderThread() {
    return getName().equals(RENDER_THREAD_NAME);
  }

  /**
   * The main thread comes first, then render thread. The rest of threads are sorted by name, if the same, by thread ID.
   */
  @Override
  public int compareTo(@NotNull CpuThreadInfo o) {
    // Process main thread should be the first element.
    if (isMainThread()) {
      return -1;
    }
    else if (o.isMainThread()) {
      return 1;
    }

    // Render threads should be the next elements.
    boolean thisIsRenderThread = isRenderThread();
    boolean otherIsRenderThread = o.isRenderThread();
    if (thisIsRenderThread && otherIsRenderThread) {
      return getId() - o.getId();
    }
    else if (thisIsRenderThread) {
      return -1;
    }
    else if (otherIsRenderThread) {
      return 1;
    }

    // Finally the list is sorted by thread name, with conflicts sorted by thread id.
    int nameResult = getName().compareTo(o.getName());
    if (nameResult == 0) {
      return getId() - o.getId();
    }
    return nameResult;
  }

  @Override
  public String toString() {
    return getName() + ":" + getId();
  }
}
