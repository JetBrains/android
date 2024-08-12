/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run.test.smrunner;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Locate android test classes / methods for test UI navigation. */
public class BlazeAndroidTestLocator implements SMTestLocator {

  public static final BlazeAndroidTestLocator INSTANCE = new BlazeAndroidTestLocator();

  private BlazeAndroidTestLocator() {}

  @Override
  public List<Location> getLocation(
      String protocol, String path, Project project, GlobalSearchScope scope) {
    if (!protocol.equals(SmRunnerUtils.GENERIC_SUITE_PROTOCOL)) {
      return ImmutableList.of();
    }
    String[] split = path.split(SmRunnerUtils.TEST_NAME_PARTS_SPLITTER);
    List<PsiClass> classes = findClasses(project, scope, split[0]);
    if (classes.isEmpty()) {
      return ImmutableList.of();
    }
    if (split.length <= 1) {
      return classes.stream().map(PsiLocation::new).collect(Collectors.toList());
    }

    String methodName = split[1];
    List<Location> results = new ArrayList<>();
    for (PsiClass psiClass : classes) {
      PsiMethod method = findTestMethod(psiClass, methodName);
      if (method != null) {
        results.add(new PsiLocation<>(method));
      }
    }
    return results.isEmpty() ? ImmutableList.of(new PsiLocation<>(classes.get(0))) : results;
  }

  private static List<PsiClass> findClasses(
      Project project, GlobalSearchScope scope, String className) {
    PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(className, scope);
    if (psiClass != null) {
      return ImmutableList.of(psiClass);
    }
    // handle unqualified class names
    return Arrays.stream(PsiShortNamesCache.getInstance(project).getClassesByName(className, scope))
        .filter(JUnitUtil::isTestClass)
        .collect(Collectors.toList());
  }

  private static PsiMethod findTestMethod(PsiClass psiClass, String methodName) {
    final PsiMethod[] methods = psiClass.findMethodsByName(methodName, true);

    if (methods.length == 0) {
      return null;
    }
    if (methods.length == 1) {
      return methods[0];
    }
    for (PsiMethod method : methods) {
      if (method.getParameterList().getParametersCount() == 0) {
        return method;
      }
    }
    return methods[0];
  }
}
