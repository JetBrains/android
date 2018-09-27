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
package com.android.tools.adtui.model.filter;

import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * The {@code FilterHandler} handles the filter content changes and update the match count results.
 */
public abstract class FilterHandler {
  private ArrayList<Consumer<FilterResult>> myMatchCountResultListeners = new ArrayList<>();
  private Filter myFilter = Filter.EMPTY_FILTER;

  public final void setFilter(@NotNull Filter filter) {
    myFilter = filter;
    FilterResult result = applyFilter(filter);
    // Disable result when filter is empty.
    if (filter.isEmpty() && result.isFilterEnabled()) {
      result = new FilterResult(result.getMatchCount(), false);
    }
    FilterResult finalResult = result;
    myMatchCountResultListeners.forEach(action -> action.consume(finalResult));
  }

  /**
   * Add listener that responds to match count result changes.
   */
  public final void addMatchCountResultListener(@NotNull Consumer<FilterResult> listener) {
    myMatchCountResultListeners.add(listener);
  }

  public final Filter getFilter() {
    return myFilter;
  }

  public final void refreshFilterContent() {
    setFilter(myFilter);
  }

  /**
   * Apply the new filter and return the matching result.
   */
  @NotNull
  protected abstract FilterResult applyFilter(@NotNull Filter filter);
}
