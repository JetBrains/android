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

import java.util.ArrayList;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link Filter} with some additional support for firing listeners on event changes.
 */
public class FilterModel {
  private final ArrayList<Consumer<FilterResult>> myMatchResultListeners = new ArrayList<>();

  @Nullable private FilterHandler myHandler;
  @NotNull Filter myFilter = Filter.EMPTY_FILTER;
  private FilterResult myResult = FilterResult.EMPTY_RESULT;

  public void setFilter(@NotNull Filter filter) {
    myFilter = filter;
    notifyFilterChange();
  }

  @NotNull
  public Filter getFilter() {
    return myFilter;
  }

  private void setMatchCountResult(@NotNull FilterResult result) {
    if (!myResult.equals(result)) {
      myResult = result;
      myMatchResultListeners.forEach(consumer -> consumer.accept(result));
    }
  }

  public void setFilterHandler(@NotNull FilterHandler handler) {
    myHandler = handler;
    handler.addMatchCountResultListener(result -> setMatchCountResult(result));
  }

  /**
   * Add listener that responds to match count result changes
   */
  public void addMatchResultListener(@NotNull Consumer<FilterResult> listener) {
    myMatchResultListeners.add(listener);
  }

  private void notifyFilterChange() {
    if (myHandler != null) {
      myHandler.setFilter(myFilter);
    }
  }
}
