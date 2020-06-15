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

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Relevant metadata about how much data matches a specified {@link Filter}.
 */
public class FilterResult {
  private final int myMatchCount;
  private final int myTotalCount;
  private final boolean myIsFilterEnabled;

  public FilterResult() {
    this(0, 0, false);
  }

  public FilterResult(int matchCount, int totalCount, boolean isFilterEnabled) {
    assert matchCount >= 0;
    myMatchCount = matchCount;
    myTotalCount = totalCount;
    myIsFilterEnabled = isFilterEnabled;
  }

  /**
   * @return number of elements that match the filter.
   */
  public int getMatchCount() {
    return myMatchCount;
  }

  /**
   * @return total number of elements traversed. For feature tracking only.
   */
  public int getTotalCount() {
    return myTotalCount;
  }

  /**
   * @return true if and only if the match count result is valid.
   */
  public boolean isFilterEnabled() {
    return myIsFilterEnabled;
  }

  /**
   * Combine this instance with another instance.
   *
   * @return a new instance w/ match count combined, total count combined and {@link #isFilterEnabled()} set to true if either one's filter
   * is enabled.
   */
  public FilterResult combine(@NotNull FilterResult anotherResult) {
    return new FilterResult(getMatchCount() + anotherResult.getMatchCount(),
                            getTotalCount() + anotherResult.getTotalCount(),
                            isFilterEnabled() || anotherResult.isFilterEnabled());
  }

  @Override
  public boolean equals(Object object) {
    if (object instanceof FilterResult) {
      FilterResult result = (FilterResult)object;
      if (myIsFilterEnabled) {
        return result.myIsFilterEnabled && result.myMatchCount == myMatchCount && result.myTotalCount == myTotalCount;
      }
      return !result.myIsFilterEnabled;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return myIsFilterEnabled ? Objects.hash(myMatchCount, myTotalCount) : -1;
  }
}
