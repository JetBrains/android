/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.wizard;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;

/**
 * Utility class for common import UI code.
 */
public class ImportUIUtil {
  /**
   * Formats a message picking the format string depending on number of arguments
   *
   * @param values values that will be used as format argument.
   * @param oneElementMessage message when only one value is in the list. Should accept one string argument.
   * @param twoOrThreeElementsMessage message format when there's 2 or 3 values. Should accept two string arguments.
   * @param moreThenThreeElementsMessage message format for over 3 values. Should accept one string and one number.
   * @return formatted message string
   */
  public static String formatElementListString(Iterable<String> values,
                                               String oneElementMessage,
                                               String twoOrThreeElementsMessage,
                                               String moreThenThreeElementsMessage) {
    int size = Iterables.size(values);
    if (size <= 1) { // If there's 0 elements, some error happened
      return String.format(oneElementMessage, Iterables.getFirst(values, "<validation error>"));
    }
    else if (size <= 3) {
      return String.format(twoOrThreeElementsMessage, atMostTwo(values, size), Iterables.getLast(values));
    }
    else {
      return String.format(moreThenThreeElementsMessage, atMostTwo(values, size), size - 2);
    }
  }

  private static String atMostTwo(Iterable<String> names, int size) {
    return Joiner.on(", ").join(Iterables.limit(names, Math.min(size - 1, 2)));
  }

  private ImportUIUtil() {
    // Do nothing
  }
}
