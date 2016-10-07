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

import com.intellij.util.Consumer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  /**
   * Invoke callback for every index of {@code needle} occurrence in {@code haystack}
   */
  public static void forEachOccurrence(@NotNull String haystack, char needle, @NotNull Consumer<Integer> callback) {
    forEachOccurrence(haystack, needle, 0, callback);
  }

  /**
   * Invoke callback for every index of {@code needle} occurrence in {@code haystack}, starting search from {@code startIndex}
   */
  public static void forEachOccurrence(@NotNull String haystack, char needle, int startIndex, @NotNull Consumer<Integer> callback) {
    int curr = haystack.indexOf(needle, startIndex);
    while (curr != -1) {
      callback.consume(curr);
      curr = haystack.indexOf(needle, curr + 1);
    }
  }

  /**
   * An analogue of StringUtil.trimEnd, but which returns null in case one string is not a suffix of another
   *
   * @return {@code haystack}, with suffix of {@code needle} dropped
   */
  @Nullable("when haystack doesn't end with needle")
  @Contract(pure = true)
  public static String trimEndOrNullize(@NotNull String haystack, @NotNull String needle) {
    if (haystack.endsWith(needle)) {
      return haystack.substring(0, haystack.length() - needle.length());
    }
    return null;
  }
}
