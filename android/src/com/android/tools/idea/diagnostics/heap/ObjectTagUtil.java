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
package com.android.tools.idea.diagnostics.heap;

import org.jetbrains.annotations.NotNull;

public class ObjectTagUtil {
  static final int INVALID_OBJECT_ID = -1;

  /*
    Object tags have the following structure (in right-most bit order):
    8bits - current iteration id (used for validation of below fields)
    1bit - visited
    32bits - topological order id
   */
  private static final long CURRENT_ITERATION_ID_MASK = 0xFF;
  private static final long CURRENT_ITERATION_VISITED_MASK = 0x100;
  private static final long CURRENT_ITERATION_OBJECT_ID_MASK = 0x1FFFFFFFE00L;

  private static final int CURRENT_ITERATION_OBJECT_ID_OFFSET = 9;
  // 8(current iteration id mask) + 1(visited mask)

  static int getObjectId(long tag, short currentIterationId) {
    if (!isTagFromTheCurrentIteration(tag, currentIterationId)) {
      return INVALID_OBJECT_ID;
    }
    return (int)((tag & CURRENT_ITERATION_OBJECT_ID_MASK) >> CURRENT_ITERATION_OBJECT_ID_OFFSET);
  }

  static boolean wasVisited(long tag, short currentIterationId) {
    if (!isTagFromTheCurrentIteration(tag, currentIterationId)) {
      return false;
    }
    return (tag & CURRENT_ITERATION_VISITED_MASK) != 0;
  }

  static void setObjectId(@NotNull final Object obj, long tag, int newObjectId, short currentIterationId) {
    tag &= ~CURRENT_ITERATION_OBJECT_ID_MASK;
    tag |= (long)newObjectId << CURRENT_ITERATION_OBJECT_ID_OFFSET;
    tag &= ~CURRENT_ITERATION_ID_MASK;
    tag |= currentIterationId;
    MemoryReportJniHelper.setObjectTag(obj, tag);
  }

  static long markVisited(@NotNull final Object obj, long tag, short currentIterationId) {
    tag &= ~CURRENT_ITERATION_VISITED_MASK;
    tag |= CURRENT_ITERATION_VISITED_MASK;
    tag &= ~CURRENT_ITERATION_ID_MASK;
    tag |= currentIterationId;
    MemoryReportJniHelper.setObjectTag(obj, tag);
    return tag;
  }

  /**
   * Checks that the passed tag was set during the current traverse.
   */
  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private static boolean isTagFromTheCurrentIteration(long tag, short currentIterationId) {
    return (tag & CURRENT_ITERATION_ID_MASK) == currentIterationId;
  }
}
