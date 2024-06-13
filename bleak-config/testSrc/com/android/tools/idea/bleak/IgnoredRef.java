/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.bleak;

/**
 * Specifies a pattern to ignore should it appear in a leaktrace (shortest path from GC roots to a suspected
 * leaky object). The pattern matches if the reference at position {@code index} is from an object of class
 * {@code className}, through field {@code fieldName}. Negative indices count backwards from the end of the
 * leaktrace.
 */
public class IgnoredRef implements MainCheckIgnoreListEntry {
  private final int index;
  private final String className;
  private final String fieldName;

  public IgnoredRef(int index, String className, String fieldName) {
    this.index = index;
    this.className = className;
    this.fieldName = fieldName;
  }

  @Override
  public boolean test(LeakInfo info) {
    return info.getLeaktrace().referenceMatches(index, className, fieldName);
  }
}