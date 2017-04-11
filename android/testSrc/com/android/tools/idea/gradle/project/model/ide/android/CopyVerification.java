/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model.ide.android;

import org.jetbrains.annotations.NotNull;
import org.junit.ComparisonFailure;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

public final class CopyVerification {
  private CopyVerification() {
  }

  public static <T> void assertEqualsOrSimilar(@NotNull T original, @NotNull T copy) throws Throwable {
    for (Method methodInOriginal : original.getClass().getDeclaredMethods()) {
      String name = methodInOriginal.getName();
      if (name.startsWith("is") || (name.startsWith("get") && !name.equals("getClass"))) { // obtain all getters, except method 'getClass()'
        Method methodInCopy = copy.getClass().getMethod(name);
        Object valueInCopy;
        try {
          valueInCopy = methodInCopy.invoke(copy);
        }
        catch (InvocationTargetException e) {
          Throwable exception = e.getTargetException();
          if (exception instanceof UnusedModelMethodException) {
            continue;
          }
          throw exception != null ? exception : e;
        }
        Object valueInOriginal = methodInOriginal.invoke(original);
        if (!Objects.equals(valueInOriginal, valueInCopy)) {
          throw new ComparisonFailure(name, original.toString(), copy.toString());
        }
      }
    }
  }
}
