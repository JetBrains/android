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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.python.run.PyTestUtils;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/** Locate python test classes / methods for test UI navigation. */
public final class BlazePythonTestLocator implements SMTestLocator {

  public static final BlazePythonTestLocator INSTANCE = new BlazePythonTestLocator();

  static final String PY_TESTCASE_PREFIX = "__main__.";

  private BlazePythonTestLocator() {}

  // Super method uses raw Location. Check super method again after #api212.
  @SuppressWarnings("rawtypes")
  @Override
  public List<Location> getLocation(
      String protocol, String path, Project project, GlobalSearchScope scope) {
    if (protocol.equals(SmRunnerUtils.GENERIC_SUITE_PROTOCOL)) {
      return findTestClass(project, scope, path);
    }
    if (protocol.equals(SmRunnerUtils.GENERIC_TEST_PROTOCOL)) {
      path = StringUtil.trimStart(path, PY_TESTCASE_PREFIX);
      String[] components = path.split("\\.|::");
      if (components.length < 2) {
        return ImmutableList.of();
      }
      return findTestMethod(
          project, scope, components[components.length - 2], components[components.length - 1]);
    }
    return ImmutableList.of();
  }

  // Super method of getLocation() uses raw Location. Check super method again after #api212.
  @SuppressWarnings("rawtypes")
  private static List<Location> findTestMethod(
      Project project, GlobalSearchScope scope, String className, @Nullable String methodName) {
    List<Location> results = new ArrayList<>();
    if (methodName == null) {
      return findTestClass(project, scope, className);
    }
    for (PyClass pyClass : PyClassNameIndex.find(className, project, scope)) {
      ProgressManager.checkCanceled();
      if (PyTestUtils.isTestClass(pyClass)) {
        PyFunction method = findMethod(pyClass, methodName);
        if (method != null && PyTestUtils.isTestFunction(method)) {
          results.add(new PsiLocation<>(project, method));
        }
        results.add(new PsiLocation<>(project, pyClass));
      }
    }
    return results;
  }

  // Super method of getLocation() uses raw Location. Check super method again after #api212.
  @SuppressWarnings("rawtypes")
  private static List<Location> findTestClass(
      Project project, GlobalSearchScope scope, String className) {
    List<Location> results = new ArrayList<>();
    for (PyClass pyClass : PyClassNameIndex.find(className, project, scope)) {
      ProgressManager.checkCanceled();
      if (PyTestUtils.isTestClass(pyClass)) {
        results.add(new PsiLocation<>(project, pyClass));
      }
    }
    return results;
  }

  @Nullable
  private static PyFunction findMethod(PyClass pyClass, String methodName) {
    PyFunction method = pyClass.findMethodByName(methodName, true, null);
    if (method != null) {
      return method;
    }
    return Arrays.stream(PyParameterizedNameConverter.EP_NAME.getExtensions())
        .map(converter -> converter.toFunctionName(methodName))
        .map(name -> pyClass.findMethodByName(name, true, null))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }
}
