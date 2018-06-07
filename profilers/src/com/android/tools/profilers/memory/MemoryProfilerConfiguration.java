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
    ARRANGE_BY_CLASS("Arrange by class"),
    ARRANGE_BY_PACKAGE("Arrange by package"),
    ARRANGE_BY_CALLSTACK("Arrange by callstack");

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

  private ClassGrouping myClassGrouping = ClassGrouping.ARRANGE_BY_CLASS;

  MemoryProfilerConfiguration(@NotNull MemoryProfilerStage stage) {
    myStage = stage;
  }

  public void setClassGrouping(ClassGrouping classGrouping) {
    if (myClassGrouping != classGrouping) {
      myClassGrouping = classGrouping;
      myStage.getStudioProfilers().getIdeServices().getFeatureTracker().trackChangeClassArrangment();
      myStage.getAspect().changed(MemoryProfilerAspect.CLASS_GROUPING);
      myStage.selectCaptureFilter(myStage.getCaptureFilter());
    }
  }

  @NotNull
  public ClassGrouping getClassGrouping() {
    return myClassGrouping;
  }
}
