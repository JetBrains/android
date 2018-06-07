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
package com.android.tools.profilers.analytics;

import org.jetbrains.annotations.NotNull;

/**
 * Class with metadata related to filter operations, used for analytics purposes.
 */
public class FilterMetadata {
  public enum View {
    UNKNOWN_FILTER_VIEW,
    CPU_TOP_DOWN,
    CPU_BOTTOM_UP,
    CPU_FLAME_CHART,
    CPU_CALL_CHART,
    MEMORY_PACKAGE,
    MEMORY_CLASS,
    MEMORY_CALLSTACK,
    NETWORK_THREADS,
    NETWORK_CONNECTIONS,
  }

  private View myView = View.UNKNOWN_FILTER_VIEW;
  private int myFilterTextLength;
  private int myTotalElementCount;
  private int myMatchedElementCount;
  private int myFeaturesUsed;

  public void setFilterTextLength(int length) {
    myFilterTextLength = length;
  }

  public int getFilterTextLength() {
    return myFilterTextLength;
  }

  public void setTotalElementCount(int count) {
    myTotalElementCount = count;
  }

  public int getTotalElementCount() {
    return myTotalElementCount;
  }

  public void setMatchedElementCount(int count) {
    myMatchedElementCount = count;
  }

  public int getMatchedElementCount() {
    return myMatchedElementCount;
  }

  public void setFeaturesUsed(boolean isMatchCase, boolean isRegex) {
    myFeaturesUsed = isMatchCase ? 1 : 0;
    myFeaturesUsed |= isRegex ? 2 : 0;
  }

  public int getFeaturesUsed() {
    return myFeaturesUsed;
  }

  @NotNull
  public View getView() {
    return myView;
  }

  public void setView(@NotNull View view) {
    myView = view;
  }
}
