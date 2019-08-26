/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.cpu.analysis;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * This class represents a collection of objects to be analyzed. This can be an entire capture, or a series of selected objects. This model
 * is meant to be a container for a collection of {@link CpuAnalysisTabModel}s. Each {@link CpuAnalysisTabModel} handles which tabs are
 * added and what data they show. The {@link CpuAnalysisModel}'s counterpart is the {@link CpuAnalysisPanel}
 */
public class CpuAnalysisModel {
  private final String myName;
  private final List<CpuAnalysisTabModel> myTabs = new ArrayList<>();

  public CpuAnalysisModel(@NotNull String name) {
    myName = name;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public List<CpuAnalysisTabModel> getTabs() {
    return myTabs;
  }
}