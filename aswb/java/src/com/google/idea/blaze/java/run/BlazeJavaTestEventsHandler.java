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
package com.google.idea.blaze.java.run;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.smrunner.BlazeTestEventsHandler;
import com.google.idea.blaze.base.run.smrunner.BlazeXmlSchema.TestSuite;
import com.google.idea.blaze.java.run.producers.BlazeJUnitTestFilterFlags;
import com.google.idea.blaze.java.sync.source.JavaLikeLanguage;
import com.intellij.execution.Location;
import com.intellij.execution.testframework.JavaTestLocator;
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
public class BlazeJavaTestEventsHandler implements BlazeTestEventsHandler {

  private static final ImmutableSet<Kind> HANDLED_KINDS = JavaLikeLanguage.getAllHandledTestKinds();
  private static final char TEST_CASE_SEPARATOR = '/';

  @Override
  public boolean handlesKind(@Nullable Kind kind) {
    return HANDLED_KINDS.contains(kind);
  }

  /** Overridden to support parameterized tests, which use nested test_suite XML elements. */
  @Override
  public boolean ignoreSuite(Label label, @Nullable Kind kind, TestSuite suite) {
    if (suite.testSuites.isEmpty()) {
      return false;
    }
    for (TestSuite child : suite.testSuites) {
      // target/class names are fully-qualified; unqualified names denote parameterized methods
      if (child.name != null && !child.name.contains(".")) {
        return false;
      }
    }
    return true;
  }

  @Override
  public SMTestLocator getTestLocator() {
    return JavaTestLocator.INSTANCE;
  }

  @Override
  public String suiteLocationUrl(Label label, @Nullable Kind kind, String name) {
    return JavaTestLocator.SUITE_PROTOCOL + URLUtil.SCHEME_SEPARATOR + name;
  }

  @Override
  public String testLocationUrl(
      Label label,
      @Nullable Kind kind,
      String parentSuite,
      String name,
      @Nullable String classname) {
    if (classname == null) {
      return null;
    }
    String classComponent = JavaTestLocator.TEST_PROTOCOL + URLUtil.SCHEME_SEPARATOR + classname;
    String parameterComponent = extractParameterComponent(name);
    if (parameterComponent != null) {
      return classComponent + TEST_CASE_SEPARATOR + parentSuite + parameterComponent;
    }
    return classComponent + TEST_CASE_SEPARATOR + name;
  }

  @Nullable
  private static String extractParameterComponent(String name) {
    if (name.startsWith("[") && name.contains("]")) {
      return name.substring(0, name.indexOf(']') + 1);
    }
    return null;
  }

  @Override
  public String suiteDisplayName(Label label, @Nullable Kind kind, String rawName) {
    String name = StringUtil.trimEnd(rawName, '.');
    int lastPointIx = name.lastIndexOf('.');
    return lastPointIx != -1 ? name.substring(lastPointIx + 1) : name;
  }

  @Nullable
  @Override
  public String getTestFilter(Project project, List<Location<?>> testLocations) {
    Map<PsiClass, Collection<Location<?>>> failedClassesAndMethods = new HashMap<>();
    for (Location<?> location : testLocations) {
      appendTest(failedClassesAndMethods, location);
    }
    String filter =
        BlazeJUnitTestFilterFlags.testFilterForClassesAndMethods(failedClassesAndMethods);
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
