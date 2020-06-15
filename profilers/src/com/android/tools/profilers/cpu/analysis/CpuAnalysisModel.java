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

import java.util.Locale;
import java.util.SortedMap;
import java.util.TreeMap;
import org.jetbrains.annotations.NotNull;

/**
 * This model is meant to be a container for a collection of {@link CpuAnalysisTabModel}s. Each {@link CpuAnalysisTabModel} handles which
 * tabs are added and what data they show. The {@link CpuAnalysisModel}'s counterpart is the {@link CpuAnalysisPanel}.
 *
 * @param <T> type of the analysis object, e.g. {@link com.android.tools.profilers.cpu.CpuThreadTrackModel}.
 */
public class CpuAnalysisModel<T> {
  private final String myName;
  private final String myMultiSelectNameFormat;
  private int myMultiSelectItemCount;

  /**
   * Map of tab type (e.g. summary) to tab model. The keys are sorted by the enum natural order (the order the enums are defined) so that
   * the tabs are displayed in a consistent order.
   */
  private final SortedMap<CpuAnalysisTabModel.Type, CpuAnalysisTabModel<T>> myTabModels = new TreeMap<>();

  public CpuAnalysisModel(@NotNull String name) {
    this(name, name);
  }

  /**
   * @param name                  model name to display in analysis panel.
   * @param multiSelectNameFormat format string to use when multiple items are selected for analysis ("%d" will be replaced by the item
   *                              count).
   */
  public CpuAnalysisModel(@NotNull String name, @NotNull String multiSelectNameFormat) {
    myName = name;
    myMultiSelectItemCount = 1; // Default assumes one things is selected when the model is constructed.
    myMultiSelectNameFormat = multiSelectNameFormat;
  }

  /**
   * @return model name to display. If the model has merged with other models, the multi-select name will be displayed.
   */
  @NotNull
  public String getName() {
    return myMultiSelectItemCount < 2 ? myName : String.format(Locale.getDefault(), myMultiSelectNameFormat, myMultiSelectItemCount);
  }

  /**
   * Adds a tab model to the analysis model. Only one tab model per type can be present.
   */
  public void addTabModel(CpuAnalysisTabModel<T> tabModel) {
    myTabModels.put(tabModel.getTabType(), tabModel);
  }

  /**
   * @return all tab models for iteration. They are sorted by the {@link CpuAnalysisTabModel.Type} enum natural order (the order the enums
   * are defined) so that the tabs are displayed in a consistent order.
   */
  @NotNull
  public Iterable<CpuAnalysisTabModel<T>> getTabModels() {
    return myTabModels.values();
  }

  /**
   * @return number of tab models.
   */
  public int getTabSize() {
    return myTabModels.size();
  }

  /**
   * Note: random access has liner time complexity due to storing data in a {@link SortedMap}. Use {@link #getTabModels()} for iterating
   * over all tab models.
   */
  @NotNull
  public CpuAnalysisTabModel<T> getTabModelAt(int index) throws IndexOutOfBoundsException {
    if (index >= myTabModels.size()) {
      throw new IndexOutOfBoundsException();
    }
    int count = 0;
    for (CpuAnalysisTabModel<T> tabModel : myTabModels.values()) {
      if (count == index) {
        return tabModel;
      }
      ++count;
    }
    // Should never happen because the index check makes sure the n-th element is guaranteed to be available.
    throw new IllegalStateException();
  }

  /**
   * Merges with another model of the type {@link T}.
   * <p>
   * The tabs will be the union of the both analysis models. For tabs present in both models, the data series will be combined.
   *
   * @param anotherModel another analysis model to merge with.
   * @return this instance
   */
  @NotNull
  public CpuAnalysisModel<T> mergeWith(@NotNull CpuAnalysisModel<T> anotherModel) {
    anotherModel.myTabModels.forEach((type, tabModel) -> {
      if (myTabModels.containsKey(type)) {
        // If tab type is already present, merge the data series.
        myTabModels.get(type).getDataSeries().addAll(tabModel.getDataSeries());
      }
      else {
        // Otherwise add tab model.
        myTabModels.put(type, tabModel);
      }
    });
    ++myMultiSelectItemCount;
    return this;
  }
}