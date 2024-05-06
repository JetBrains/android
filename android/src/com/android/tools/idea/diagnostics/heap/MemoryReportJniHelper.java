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

public class MemoryReportJniHelper {
  /**
   * Returns a JVM TI tag of the passed object.
   */
  static native long getObjectTag(@NotNull final Object obj);

  /**
   * Sets a JVM TI object tag for a passed object.
   */
  static native void setObjectTag(@NotNull final Object obj, long newTag);

  /**
   * Checks that JVM TI agent has a capability to tag objects.
   */
  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  static native boolean canTagObjects();

  /**
   * @return an array of class objects initialized by the JVM.
   */
  public static native Class<?>[] getClasses();

  /**
   * @return an estimated size of the passed object in bytes.
   */
  static native long getObjectSize(@NotNull final Object obj);

  /**
   * Checks if class was initialized by the JVM.
   */
  static native boolean isClassInitialized(@NotNull final Class<?> classToCheck);

  /**
   * Checks if class was initialized by the JVM.
   */
  static native Object[] getClassStaticFieldsValues(@NotNull final Class<?> classToCheck);

}
