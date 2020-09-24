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
 * Utility class used by the {@link LiveLiteralsMethodVisitor} to handle the auto-boxing of primitives.
 * Constants are always primitives but both ASM and our constant management use boxed types. This class defines a number
 * of methods that allow to do the unboxing correctly when called from user code.
 */
@SuppressWarnings("ConstantConditions")
public class PrimitiveTypeRemapper {
  public static float remapFloat(Object source, String fileName, int offset, float value) {
    return (Float)ConstantRemapperManager.remapAny(source, fileName, offset, value);
  }

  public static double remapDouble(Object source, String fileName, int offset, double value) {
    return (Double)ConstantRemapperManager.remapAny(source, fileName, offset, value);
  }

  public static short remapShort(Object source, String fileName, int offset, short value) {
    return (Short)ConstantRemapperManager.remapAny(source, fileName, offset, value);
  }

  public static int remapInt(Object source, String fileName, int offset, int value) {
    return (Integer)ConstantRemapperManager.remapAny(source, fileName, offset, value);
  }

  public static long remapLong(Object source, String fileName, int offset, long value) {
    return (Long)ConstantRemapperManager.remapAny(source, fileName, offset, value);
  }

  public static char remapChar(Object source, String fileName, int offset, char value) {
    return (Character)ConstantRemapperManager.remapAny(source, fileName, offset, value);
  }

  public static boolean remapBoolean(Object source, String fileName, int offset, boolean value) {
    return (Boolean)ConstantRemapperManager.remapAny(source, fileName, offset, value);
  }
}
