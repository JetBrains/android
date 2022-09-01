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

import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

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
}
