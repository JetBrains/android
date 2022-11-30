/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.tools.idea.diagnostics.heap.HeapSnapshotTraverse.HeapSnapshotPresentationConfig.SizePresentationStyle;

import com.android.tools.idea.diagnostics.hprof.util.HeapReportUtils;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HeapTraverseUtil {

  static boolean isArrayOfPrimitives(@NotNull final Class<?> type) {
    return type.isArray() && HeapTraverseUtil.isPrimitive(type.getComponentType());
  }

  /**
   * Check that passed class was initialized.
   */
  public static void processMask(long mask, @NotNull final Consumer<Integer> p) {
    int trailingZeros = Long.numberOfTrailingZeros(mask);
    mask >>= Long.numberOfTrailingZeros(mask);
    for (int i = trailingZeros; mask != 0; i++, mask >>= 1) {
      if ((mask & 1) != 0) {
        p.accept(i);
      }
    }
  }

  public static boolean isPrimitive(@NotNull Class<?> type) {
    return type.isPrimitive();
  }

  @NotNull
  public static String getObjectsSizePresentation(long bytes,
                                                  SizePresentationStyle style) {
    if (style == SizePresentationStyle.BYTES) {
      return String.format(Locale.US, "%d bytes", bytes);
    }
    return HeapReportUtils.INSTANCE.toShortStringAsCount(bytes);
  }

  @NotNull
  public static String getObjectsStatsPresentation(ObjectsStatistics statistics,
                                                   SizePresentationStyle style) {
    return String.format(Locale.US, "%s/%d objects",
                         getObjectsSizePresentation(statistics.getTotalSizeInBytes(), style), statistics.getObjectsCount());
  }

  @Nullable
  static Object getFieldValue(@NotNull Object object, @NotNull String fieldName) {
    try {
      Field field = object.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      return field.get(object);
    }
    catch (ReflectiveOperationException e) {
      throw new Error(e); // Should not happen unless there is a bug in this class.
    }
  }
}
