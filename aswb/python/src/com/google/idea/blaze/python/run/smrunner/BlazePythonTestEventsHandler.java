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
package com.google.idea.blaze.python.run.smrunner;

import com.google.common.base.Joiner;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.execution.BlazeParametersListUtil;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.run.smrunner.BlazeTestEventsHandler;
import com.intellij.execution.Location;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/** Provides python-specific methods needed by the SM-runner test UI. */
public class BlazePythonTestEventsHandler implements BlazeTestEventsHandler {

  @Override
  public boolean handlesKind(@Nullable Kind kind) {
    return kind != null
        && kind.hasLanguage(LanguageClass.PYTHON)
        && kind.getRuleType().equals(RuleType.TEST);
  }

  @Override
  public SMTestLocator getTestLocator() {
    return BlazePythonTestLocator.INSTANCE;
  }

  @Override
  public String testDisplayName(Label label, @Nullable Kind kind, String rawName) {
    // Parameterized tests contain parentheses
    if (rawName.contains("(")) {
      return rawName;
    }
    // For non-parameterized, name will be fully-qualified classname. We only need last component.
    int lastDotIndex = rawName.lastIndexOf('.');
    return lastDotIndex != -1 ? rawName.substring(lastDotIndex + 1) : rawName;
  }

  @Nullable
  @Override
  public String getTestFilter(Project project, List<Location<?>> testLocations) {
    // python test runner parses filters of the form "class1.method1 class2.method2 ..."
    // parameterized test cases can cause the same class.method combination to be present
    // multiple times, so we use a set
    Set<String> filters = new LinkedHashSet<>();
    for (Location<?> location : testLocations) {
      String filter = getFilter(location.getPsiElement());
      if (filter != null) {
        filters.add(filter);
      }
    }
    if (filters.isEmpty()) {
      return null;
    }
    return String.format(
        "%s=%s",
        BlazeFlags.TEST_FILTER, BlazeParametersListUtil.encodeParam(Joiner.on(' ').join(filters)));
  }

  @Nullable
  private static String getFilter(PsiElement psiElement) {
    if (psiElement instanceof PyClass) {
      return ((PyClass) psiElement).getName();
    }
    if (!(psiElement instanceof PyFunction)) {
      return null;
    }
    PyClass pyClass = ((PyFunction) psiElement).getContainingClass();
    if (pyClass == null) {
      return null;
    }
    String methodName = ((PyFunction) psiElement).getName();
    String className = pyClass.getName();
    return methodName != null && className != null ? className + "." + methodName : null;
  }
}
