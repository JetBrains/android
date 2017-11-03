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

public class CpuThreadInfo {
  /** Thread id */
  private final int myId;

  /** Thread name */
  private final String myName;

  public CpuThreadInfo(int threadId, @NonNull String name) {
    myId = threadId;
    myName = name;
  }

  public int getId() {
    return myId;
  }

  @NotNull
  public String getName() {
    return myName;
  }
}
