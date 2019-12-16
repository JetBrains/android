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
package com.android.tools.profilers.cpu;

import java.util.Comparator;
import org.jetbrains.annotations.NotNull;

/**
 * Comparator that is used to sort a list of CpuThreadinfo by number of child nodes then by default sort order.
 */
public class CaptureThreadComparator implements Comparator<CpuThreadInfo> {
  private final CpuCapture myCapture;

  public CaptureThreadComparator(@NotNull CpuCapture capture) {
    myCapture = capture;
  }

  @Override
  public int compare(CpuThreadInfo left, CpuThreadInfo right) {
    // Default sort order expects left side to return > 0. This ordering gives us smallest to largest. To invert this
    // We return < 0 when the left side is greater than the right.repo reb
    int result = myCapture.getCaptureNode(right.getId()).getChildCount() - myCapture.getCaptureNode(left.getId()).getChildCount();
    if (result == 0) {
      return left.compareTo(right);
    }
    return result;
  }
}
