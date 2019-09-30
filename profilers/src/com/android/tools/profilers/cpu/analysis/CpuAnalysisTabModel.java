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

import com.android.tools.adtui.model.Range;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Model for {@link CpuAnalysisTab}. Each tab has unique rendering requirements as such this container holds a list of data that can be
 * used for the tab. An example of a tab that uses the list may be the Flame chart with multiselect or in the "Full Trace".
 */
public class CpuAnalysisTabModel<T> {
  private final String myTitle;
  private final List<T> myDataSeries = new ArrayList<>();

  public CpuAnalysisTabModel(@NotNull String title) {
    myTitle = title;
  }

  public String getTitle() {
    return myTitle;
  }

  public void addData(T data) {
    myDataSeries.add(data);
  }

  public List<T> getDataSeries() {
    return myDataSeries;
  }
}