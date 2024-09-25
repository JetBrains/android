/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.python.run;

import com.google.common.collect.ImmutableSet;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.TypeEvalContext;

/** Utilities class for identifying python test psi elements. */
public class PyTestUtils {

  private static final ImmutableSet<String> PY_UNIT_TEST_CLASSES =
      ImmutableSet.of("unittest.TestCase", "unittest.case.TestCase");

  public static boolean isTestFile(PyFile file) {
    for (PyClass cls : file.getTopLevelClasses()) {
      if (isTestClass(cls)) {
        return true;
      }
    }
    for (PyFunction cls : file.getTopLevelFunctions()) {
      if (isTestFunction(cls)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isTestClass(PyClass pyClass) {
    final TypeEvalContext contextToUse =
        TypeEvalContext.userInitiated(pyClass.getProject(), pyClass.getContainingFile());
    for (PyClassLikeType type : pyClass.getAncestorTypes(contextToUse)) {
      if (type != null && PY_UNIT_TEST_CLASSES.contains(type.getClassQName())) {
        return true;
      }
    }
    String name = pyClass.getName();
    return name != null && name.endsWith("Test");
  }

  public static boolean isTestFunction(PyFunction fn) {
    String name = fn.getName();
    return name != null && name.startsWith("test");
  }
}
