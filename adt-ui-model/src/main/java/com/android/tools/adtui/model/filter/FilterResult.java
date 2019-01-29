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

/*
 * Relevant metadata about how much data matches a specified {@link Filter}"
 */
public class FilterResult {
  private final int myMatchCount;
  private final boolean myIsFilterEnabled;

  public FilterResult(int count, boolean isFilterEnabled) {
    assert count >= 0;
    myMatchCount = count;
    myIsFilterEnabled = isFilterEnabled;
  }

  public int getMatchCount() {
    return myMatchCount;
  }

  /*
   * Returns true if and only if the match count result is valid.
   */
  public boolean isFilterEnabled() {
    return myIsFilterEnabled;
  }

  @Override
  public boolean equals(Object object) {
    if (object instanceof FilterResult) {
      FilterResult result = (FilterResult)object;
      if (myIsFilterEnabled) {
        return result.myIsFilterEnabled && result.myMatchCount == myMatchCount;
      }
      return !result.myIsFilterEnabled;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return myIsFilterEnabled ? myMatchCount : -1;
  }
}
