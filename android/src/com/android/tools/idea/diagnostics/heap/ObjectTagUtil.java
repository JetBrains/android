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

import static com.android.tools.idea.diagnostics.heap.HeapTraverseNode.minDepthKindFromByte;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ObjectTagUtil {
  static final int INVALID_OBJECT_ID = -1;
  static final int INVALID_OBJECT_DEPTH = -1;

  /*
    Object tags have the following structure (in right-most bit order):
    8bits - current iteration id (used for validation of below fields)
    1bit - visited
    32bits - postorder number
    17bits - depth
    1bit - is owned by component exceeding limits
    4bit - (if the above is set) index of the component
    1bit - optimal depth kind
   */
  private static final long CURRENT_ITERATION_ID_MASK = 0xFF;
  private static final long CURRENT_ITERATION_VISITED_MASK = 0x100;
  private static final long CURRENT_ITERATION_OBJECT_ID_MASK = 0x1FFFFFFFE00L;

  // 8(current iteration id mask) + 1(visited bit)
  private static final int CURRENT_ITERATION_OBJECT_ID_OFFSET = 9;


  // 8(current iteration id mask) + 1(visited bit) + 32(object id)
  private static final int CURRENT_ITERATION_OBJECT_DEPTH_OFFSET = 41;
  private static final long CURRENT_ITERATION_OBJECT_DEPTH_MASK = 0x3FFFE0000000000L;


  private static final long OBJECT_OWNED_BY_EXCEEDED_COMPONENT_BIT_MASK = 0x400000000000000L;

  // 8(current iteration id mask) + 1(visited bit) + 32(object id) + 17(depth) + 1(is owned by exceeding component bit)
  private static final int OBJECT_OWNING_EXCEEDED_COMPONENT_OFFSET = 59;

  static final int EXCEEDED_COMPONENTS_LIMIT = 16; // 4 bits reserved for exceeded cluster id;
  private static final long OBJECT_OWNING_EXCEEDED_COMPONENT_MASK = 0x7800000000000000L;
  private static final int DEPTH_KIND_OFFSET = 63;
  private static final long DEPTH_KIND_MASK = 0x8000000000000000L;

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

  static int getDepth(long tag, short currentIterationId) {
    if (!isTagFromTheCurrentIteration(tag, currentIterationId)) {
      return INVALID_OBJECT_DEPTH;
    }
    return (int)((tag & CURRENT_ITERATION_OBJECT_DEPTH_MASK) >> CURRENT_ITERATION_OBJECT_DEPTH_OFFSET);
  }

  @Nullable
  static HeapTraverseNode.MinDepthKind getDepthKind(long tag, short currentIterationId) {
    if (!isTagFromTheCurrentIteration(tag, currentIterationId)) {
      return null;
    }
    return minDepthKindFromByte((byte)((tag & DEPTH_KIND_MASK) >>> DEPTH_KIND_OFFSET));
  }

  static void setObjectId(@NotNull final Object obj, long tag, int newObjectId, short currentIterationId) {
    tag &= ~CURRENT_ITERATION_OBJECT_ID_MASK;
    tag |= (long)newObjectId << CURRENT_ITERATION_OBJECT_ID_OFFSET;
    tag &= ~CURRENT_ITERATION_ID_MASK;
    tag |= currentIterationId;
    MemoryReportJniHelper.setObjectTag(obj, tag);
  }

  // method unsets the visited bit, sets up passed object id and depth
  static long constructTag(int objectId, int depth, HeapTraverseNode.MinDepthKind minDepthKind, short currentIterationId,
                           boolean isOwnedByExceededComponent,
                           int currentExceededClusterIndex) {
    assert 0 <= currentExceededClusterIndex;
    assert currentExceededClusterIndex < 32;

    long tag = 0;
    tag |= ((long)objectId << CURRENT_ITERATION_OBJECT_ID_OFFSET) & CURRENT_ITERATION_OBJECT_ID_MASK;
    tag |= ((long)depth << CURRENT_ITERATION_OBJECT_DEPTH_OFFSET) & CURRENT_ITERATION_OBJECT_DEPTH_MASK;
    if (isOwnedByExceededComponent) {
      tag |= OBJECT_OWNED_BY_EXCEEDED_COMPONENT_BIT_MASK;
    }
    tag |= ((long)currentExceededClusterIndex << OBJECT_OWNING_EXCEEDED_COMPONENT_OFFSET) & OBJECT_OWNING_EXCEEDED_COMPONENT_MASK;
    if (minDepthKind != HeapTraverseNode.MinDepthKind.DEFAULT) {
      tag |= ((long)minDepthKind.getValue() << DEPTH_KIND_OFFSET) & DEPTH_KIND_MASK;
    }
    tag |= currentIterationId;
    return tag;
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

  public static boolean isOwnedByExceededComponent(long tag) {
    return (tag & OBJECT_OWNED_BY_EXCEEDED_COMPONENT_BIT_MASK) != 0;
  }

  public static byte getOwningExceededClusterIndex(long tag) {
    return(byte)((tag & OBJECT_OWNING_EXCEEDED_COMPONENT_MASK) >>> OBJECT_OWNING_EXCEEDED_COMPONENT_OFFSET);
  }
}
