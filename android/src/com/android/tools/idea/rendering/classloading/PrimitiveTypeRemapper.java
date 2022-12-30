/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.rendering.classloading;

/**
 * Utility class used by the {@link LiveLiteralsTransformKt} to handle the auto-boxing of primitives.
 * Constants are always primitives but both ASM and our constant management use boxed types. This class defines a number
 * of methods that allow to do the unboxing correctly when called from user code.
 */
@SuppressWarnings("ConstantConditions")
public final class PrimitiveTypeRemapper {
  public static float remapFloat(Object source, String fileName, int offset, float value) {
    Object constant = ComposePreviewConstantRemapper.remapAny(source, fileName, offset, value);
    if (constant instanceof Number) {
      return ((Number)constant).floatValue();
    }

    // Return default value
    return value;
  }

  public static double remapDouble(Object source, String fileName, int offset, double value) {
    Object constant = ComposePreviewConstantRemapper.remapAny(source, fileName, offset, value);
    if (constant instanceof Number) {
      return ((Number)constant).doubleValue();
    }

    // Return default value
    return value;
  }

  public static short remapShort(Object source, String fileName, int offset, short value) {
    Object constant = ComposePreviewConstantRemapper.remapAny(source, fileName, offset, value);
    if (constant instanceof Number) {
      return ((Number)constant).shortValue();
    }

    // Return default value
    return value;
  }

  public static int remapInt(Object source, String fileName, int offset, int value) {
    Object constant = ComposePreviewConstantRemapper.remapAny(source, fileName, offset, value);
    if (constant instanceof Number) {
      return ((Number)constant).intValue();
    }

    // Return default value
    return value;
  }

  public static long remapLong(Object source, String fileName, int offset, long value) {
    Object constant = ComposePreviewConstantRemapper.remapAny(source, fileName, offset, value);
    if (constant instanceof Number) {
      return ((Number)constant).longValue();
    }

    // Return default value
    return value;
  }

  public static byte remapByte(Object source, String fileName, int offset, byte value) {
    Object constant = ComposePreviewConstantRemapper.remapAny(source, fileName, offset, value);
    if (constant instanceof Number) {
      return ((Number)constant).byteValue();
    }

    // Return default value
    return value;
  }

  public static char remapChar(Object source, String fileName, int offset, char value) {
    Object constant = ComposePreviewConstantRemapper.remapAny(source, fileName, offset, value);
    if (constant instanceof Character) {
      return (Character)constant;
    }

    // Return default value
    return value;
  }

  public static boolean remapBoolean(Object source, String fileName, int offset, boolean value) {
    Object constant = ComposePreviewConstantRemapper.remapAny(source, fileName, offset, value);
    if (constant instanceof Boolean) {
      return (Boolean)constant;
    }

    // Return default value
    return value;
  }
}
