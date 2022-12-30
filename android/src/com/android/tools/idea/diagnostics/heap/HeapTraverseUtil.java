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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ReflectionUtil;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

public final class HeapTraverseUtil {
  // An object has overhead for header and v-table entry
  private static final long ESTIMATED_OBJECT_OVERHEAD = 16;

  // An array has additional overhead for the array size
  private static final long ESTIMATED_ARRAY_OVERHEAD = 8;

  private static final long REFERENCE_SIZE = 4;

  private static final Method Unsafe_shouldBeInitialized;

  private static final Logger LOG = Logger.getInstance(HeapTraverseUtil.class);

  static {
    Method shouldBeInitialized;
    try {
      shouldBeInitialized = ReflectionUtil.getDeclaredMethod(Class.forName("sun.misc.Unsafe"), "shouldBeInitialized", Class.class);
    }
    catch (ClassNotFoundException ignored) {
      shouldBeInitialized = null;
    }
    Unsafe_shouldBeInitialized = shouldBeInitialized;
  }

  private static long sizeOfPrimitive(Class<?> type) {
    if (!type.isPrimitive()) return REFERENCE_SIZE;
    if (type == Boolean.TYPE) return 1;
    if (type == Byte.TYPE) return 1;
    if (type == Character.TYPE) return 2;
    if (type == Short.TYPE) return 2;
    if (type == Integer.TYPE) return 4;
    if (type == Long.TYPE) return 8;
    if (type == Float.TYPE) return 4;
    if (type == Double.TYPE) return 8;
    throw new IllegalArgumentException("Size computation: unknown type: " + type.getName());
  }

  static boolean isArrayOfPrimitives(@NotNull final Class<?> type) {
    return type.isArray() && HeapTraverseUtil.isPrimitive(type.getComponentType());
  }

  /**
   * Check that passed class was initialized.
   */
  static boolean isInitialized(@NotNull final Class<?> type) {
    if (Unsafe_shouldBeInitialized == null) return false;
    boolean isInitialized = false;
    try {
      isInitialized = !(Boolean)Unsafe_shouldBeInitialized.invoke(ReflectionUtil.getUnsafe(), type);
    }
    catch (Exception e) {
      LOG.warn(e);
    }
    return isInitialized;
  }

  private static long arraySize(@NotNull final Object obj) {
    if (!obj.getClass().isArray()) {
      return 0;
    }
    int componentCount = Array.getLength(obj);
    long arraySize = componentCount * sizeOfPrimitive(obj.getClass().getComponentType());
    // The expression: (size + 7) & ~7 adds bytes to fill a full word (8 bytes)
    return ESTIMATED_ARRAY_OVERHEAD + ((arraySize + 7) & ~7);
  }

  public static long sizeOf(@NotNull final Object obj, @NotNull final FieldCache fieldCache) {
    long arraySize = arraySize(obj);
    return ESTIMATED_OBJECT_OVERHEAD + fieldCache.getInstanceFields(obj.getClass()).length * REFERENCE_SIZE + arraySize;
  }

  public static void processMask(int mask, @NotNull final Consumer<Integer> p) {
    int trailingZeros = Integer.numberOfTrailingZeros(mask);
    mask >>= Integer.numberOfTrailingZeros(mask);
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
