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
package com.android.tools.profilers.cpu.systemtrace;

import com.android.tools.profilers.cpu.CpuThreadInfo;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a slice of thread, which essentially means a period of time a thread doesn't change state. For example:
 *  t1 |  Running  | Sleeping |                   | Running |
 *  t2              |  Sleeping  |
 *
 *  Thread t1 has three slices (Running, Sleeping and Running), while t2 has one (Sleeping).
 */
public class CpuThreadSliceInfo extends CpuThreadInfo {
  public static final CpuThreadSliceInfo NULL_THREAD = new CpuThreadSliceInfo(0, "", 0, "", 0);

  /**
   * User space process ID, in kernel space this is the TGID.
   */
  private final int myProcessId;

  /**
   * User space process name.
   */
  private final String myProcessName;

  /**
   * Duration of the slice in microseconds.
   */
  private final long myDurationUs;

  public CpuThreadSliceInfo(int threadId, @NotNull String name, int processId, @NotNull String processName, long durationUs) {
    super(threadId, name);
    myProcessId = processId;
    myProcessName = processName;
    myDurationUs = durationUs;
  }

  public CpuThreadSliceInfo(int threadId, @NotNull String name, int processId, @NotNull String processName) {
    this(threadId, name, processId, processName, 0);
  }

  public int getProcessId() {
    return myProcessId;
  }

  @NotNull
  public String getProcessName() {
    return myProcessName;
  }

  public long getDurationUs() {
    return myDurationUs;
  }

  @Override
  public boolean isMainThread() {
    return myProcessId == getId();
  }
}
