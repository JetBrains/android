/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Various utilities for generating user-facing text
 */
public class AndroidTextUtils {
  // Static utility functions class
  private AndroidTextUtils() { }

  /**
   * Generate comma-separated list consisting of passed collection of items.
   * Function is parameterized by last separator so enumerations like
   * "A, B or C" and "A, B and C" could be generated from the single function.
   *
   * @param items resulting comma-separated list items
   * @param lastSeparator separator to be used instead of comma before last item
   * @return comma-separated list
   */
  @NotNull
  public static String generateCommaSeparatedList(@NotNull Collection<String> items, @NotNull String lastSeparator) {
    final int n = items.size();
    if (n == 0) {
      return "";
    }

    int i = 0;
    final StringBuilder result = new StringBuilder();
    for (String word : items) {
      result.append(word);
      if (i < n - 2) {
        result.append(", ");
      }
      else if (i == n - 2) {
        result.append(" ").append(lastSeparator).append(" ");
      }
      i++;
    }
    return result.toString();
  }
}
