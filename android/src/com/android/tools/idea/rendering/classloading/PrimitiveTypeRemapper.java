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
  public static float remapFloat(Object source, boolean isStatic, String methodName, float value) {
    return (Float)ConstantRemapperManager.remapAny(source, isStatic, methodName, value);
  }

  public static double remapDouble(Object source, boolean isStatic, String methodName, double value) {
    return (Double)ConstantRemapperManager.remapAny(source, isStatic, methodName, value);
  }

  public static short remapShort(Object source, boolean isStatic, String methodName, short value) {
    return (Short)ConstantRemapperManager.remapAny(source, isStatic, methodName, value);
  }

  public static int remapInt(Object source, boolean isStatic, String methodName, int value) {
    return (Integer)ConstantRemapperManager.remapAny(source, isStatic, methodName, value);
  }

  public static long remapLong(Object source, boolean isStatic, String methodName, long value) {
    return (Long)ConstantRemapperManager.remapAny(source, isStatic, methodName, value);
  }

  public static char remapChar(Object source, boolean isStatic, String methodName, char value) {
    return (Character)ConstantRemapperManager.remapAny(source, isStatic, methodName, value);
  }
}
