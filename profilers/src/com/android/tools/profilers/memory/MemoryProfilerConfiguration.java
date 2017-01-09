/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.profilers.memory;

import org.jetbrains.annotations.NotNull;

/**
 * A class that holds user changeable values that need to persist across sessions.
 */
public class MemoryProfilerConfiguration {
  public enum ClassGrouping {
    NO_GROUPING("Flat Classes List"),
    GROUP_BY_PACKAGE("Group by Packages");

    @NotNull
    String myLabel;

    ClassGrouping(@NotNull String label) {
      myLabel = label;
    }

    @Override
    public String toString() {
      return myLabel;
    }
  }

  @NotNull private final MemoryProfilerStage myStage;

  private ClassGrouping myClassGrouping = ClassGrouping.NO_GROUPING;

  MemoryProfilerConfiguration(@NotNull MemoryProfilerStage stage) {
    myStage = stage;
  }

  public void setClassGrouping(ClassGrouping classGrouping) {
    if (myClassGrouping != classGrouping) {
      myClassGrouping = classGrouping;
      myStage.getAspect().changed(MemoryProfilerAspect.CLASS_GROUPING);
    }
  }

  @NotNull
  public ClassGrouping getClassGrouping() {
    return myClassGrouping;
  }
}
