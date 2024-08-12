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

import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.smrunner.BlazeTestEventsHandler;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.java.AndroidBlazeRules;
import com.google.idea.blaze.java.run.producers.BlazeJUnitTestFilterFlags;
import com.google.idea.blaze.java.run.producers.BlazeJUnitTestFilterFlags.JUnitVersion;
import com.intellij.execution.Location;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.util.io.URLUtil;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Provides java-specific methods needed by the SM-runner test UI. */
public class BlazeAndroidTestEventsHandler implements BlazeTestEventsHandler {

  @Override
  public boolean handlesKind(@Nullable Kind kind) {
    return kind != null
        && kind.isOneOf(
            AndroidBlazeRules.RuleTypes.ANDROID_TEST.getKind(),
            AndroidBlazeRules.RuleTypes.ANDROID_INSTRUMENTATION_TEST.getKind());
  }

  @Override
  public SMTestLocator getTestLocator() {
    return BlazeAndroidTestLocator.INSTANCE;
  }

  @Override
  public String suiteLocationUrl(Label label, @Nullable Kind kind, String name) {
    return SmRunnerUtils.GENERIC_SUITE_PROTOCOL + URLUtil.SCHEME_SEPARATOR + name;
  }

  @Override
  public String testLocationUrl(
      Label label,
      @Nullable Kind kind,
      String parentSuite,
      String name,
      @Nullable String className) {
    // ignore initial value of className -- it's the test runner class.
    name = StringUtil.trimTrailing(name, '-');
    if (!name.contains("-")) {
      return SmRunnerUtils.GENERIC_SUITE_PROTOCOL + URLUtil.SCHEME_SEPARATOR + name;
    }
    int ix = name.lastIndexOf('-');
    className = name.substring(0, ix);
    String methodName = name.substring(ix + 1);
    return SmRunnerUtils.GENERIC_SUITE_PROTOCOL
        + URLUtil.SCHEME_SEPARATOR
        + className
        + SmRunnerUtils.TEST_NAME_PARTS_SPLITTER
        + methodName;
  }

  @Override
  public String testDisplayName(Label label, @Nullable Kind kind, String rawName) {
    String name = StringUtil.trimTrailing(rawName, '-');
    if (name.contains("-")) {
      int ix = name.lastIndexOf('-');
      return name.substring(ix + 1);
    }
    return name;
  }

  @Nullable
  @Override
  public String getTestFilter(Project project, List<Location<?>> testLocations) {
    Map<PsiClass, Collection<Location<?>>> failedClassesAndMethods = new HashMap<>();
    for (Location<?> location : testLocations) {
      appendTest(failedClassesAndMethods, location);
    }
    // the android test runner always runs with JUnit4
    String filter =
        BlazeJUnitTestFilterFlags.testFilterForClassesAndMethods(
            failedClassesAndMethods, JUnitVersion.JUNIT_4);
    return filter != null ? String.format("%s='%s'", BlazeFlags.TEST_FILTER, filter) : null;
  }

  private static void appendTest(Map<PsiClass, Collection<Location<?>>> map, Location<?> location) {
    PsiElement psi = location.getPsiElement();
    if (psi instanceof PsiClass) {
      map.computeIfAbsent((PsiClass) psi, k -> new HashSet<>());
      return;
    }
    if (!(psi instanceof PsiMethod)) {
      return;
    }
    PsiClass psiClass = ((PsiMethod) psi).getContainingClass();
    if (psiClass == null) {
      return;
    }
    map.computeIfAbsent(psiClass, k -> new HashSet<>()).add(location);
  }
}
