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
 * Model for {@link CpuAnalysisTab}. Each tab has unique rendering requirements as such this container holds a list of data that can be
 * used for the tab. An example of a tab that uses the list may be the Flame chart with multiselect or in the "Full Trace".
 *
 * @param <T> type of the analysis object, e.g. {@link com.android.tools.profilers.cpu.CpuThreadTrackModel}.
 */
public class CpuAnalysisTabModel<T> {
  /**
   * The order the enum values are defined determine the order they are displayed to ensure consistent ordering across the board.
   */
  public enum Type {
    /**
     * Summary tab used to display high level information about the current capture / selection.
     */
    SUMMARY("Summary"),
    TOP_DOWN("Top Down"),
    FLAME_CHART("Flame Chart"),
    BOTTOM_UP("Bottom Up"),
    EVENTS("Events"),
    OCCURRENCES("Occurrences"),
    LOGS("Logs");

    /**
     * The display name to show in the tab header.
     */
    @NotNull
    public String getName() {
      return myName;
    }

    private final String myName;

    Type(@NotNull String name) {
      myName = name;
    }
  }

  private final Type myTabType;
  private final List<T> myDataSeries;

  public CpuAnalysisTabModel(@NotNull Type tabType) {
    myTabType = tabType;
    myDataSeries = new ArrayList<>();
  }

  @NotNull
  public Type getTabType() {
    return myTabType;
  }

  @NotNull
  public List<T> getDataSeries() {
    return myDataSeries;
  }
}