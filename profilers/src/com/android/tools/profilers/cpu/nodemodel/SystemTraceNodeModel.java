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
package com.android.tools.profilers.cpu.nodemodel;

import org.jetbrains.annotations.NotNull;

/**
 * This model represents a SystemTrace node, used for determining the color to draw for the
 * FlameChart, and CallChart.
 */
public class SystemTraceNodeModel implements CaptureNodeModel {
  @NotNull private final String myId;
  @NotNull private final String myName;

  public SystemTraceNodeModel(@NotNull String id, @NotNull String name) {
    myId = id;
    myName = name;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public String getFullName() {
    return myName;
  }

  @Override
  @NotNull
  public String getId() {
    return myId;
  }
}