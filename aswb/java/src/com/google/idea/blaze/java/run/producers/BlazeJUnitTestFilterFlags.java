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

package com.google.idea.blaze.java.run.producers;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.java.run.producers.JUnitParameterizedClassHeuristic.ParameterizedTestInfo;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.execution.Location;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Utilities for building test filter flags for JUnit tests. */
public final class BlazeJUnitTestFilterFlags {

  /** A version of JUnit to generate test filter flags for. */
  public enum JUnitVersion {
    JUNIT_3,
    JUNIT_4
  }

  /**
   * Builds the JUnit test filter corresponding to the given class.<br>
   * Returns null if no class name can be found.
   */
  @Nullable
  public static String testFilterForClass(PsiClass psiClass) {
    return testFilterForClassAndMethods(psiClass, ImmutableList.of());
  }

  /**
   * Builds the JUnit test filter corresponding to the given class and methods.<br>
   * Returns null if no class name can be found.
   */
  @Nullable
  public static String testFilterForClassAndMethods(
      PsiClass psiClass, Collection<PsiMethod> methods) {
    JUnitVersion version =
        JUnitUtil.isJUnit4TestClass(psiClass) ? JUnitVersion.JUNIT_4 : JUnitVersion.JUNIT_3;
    ParameterizedTestInfo parameterizedTestInfo =
        JUnitParameterizedClassHeuristic.getParameterizedTestInfo(psiClass);
    return testFilterForClassAndMethods(
        psiClass,
        version,
        extractMethodFilters(psiClass, methods, parameterizedTestInfo),
        parameterizedTestInfo);
  }

  /** Runs all parameterized versions of methods. */
  private static List<String> extractMethodFilters(
      PsiClass psiClass,
      Collection<PsiMethod> methods,
      ParameterizedTestInfo parameterizedTestInfo) {
    // standard org.junit.runners.Parameterized class requires no per-test annotations
    String testSuffixRegex = getTestSuffixRegex(psiClass, parameterizedTestInfo);
    return methods.stream()
        .map((method) -> methodFilter(method, testSuffixRegex))
        .sorted()
        .collect(Collectors.toList());
  }

  @Nullable
  private static String getTestSuffixRegex(
      PsiClass testClass, ParameterizedTestInfo parameterizedTestInfo) {

    if (parameterizedTestInfo != null) {
      return parameterizedTestInfo.testMethodSuffixRegex();
    }
    if (PsiMemberParameterizedLocation.getParameterizedLocation(testClass, null) != null) {
      return JUnitParameterizedClassHeuristic.STANDARD_JUNIT_TEST_SUFFIX;
    }
    return null;
  }

  private static String methodFilter(PsiMethod method, @Nullable String testSuffixRegex) {
    if (testSuffixRegex != null) {
      return method.getName() + testSuffixRegex;
    } else if (AnnotationUtil.findAnnotation(method, "Parameters") != null) {
      // Supports @junitparams.Parameters, an annotation that applies to an individual test method
      return method.getName() + JUnitParameterizedClassHeuristic.STANDARD_JUNIT_TEST_SUFFIX;
    } else {
      return method.getName();
    }
  }

  @Nullable
  public static String testFilterForClassesAndMethods(
      Map<PsiClass, Collection<Location<?>>> methodsPerClass) {
    if (methodsPerClass.isEmpty()) {
      return null;
    }
    // Note: this could be incorrect if there are no JUnit4 classes in this sample, but some in the
    // java_test target they're run from.
    JUnitVersion version =
        hasJUnit4Test(methodsPerClass.keySet()) ? JUnitVersion.JUNIT_4 : JUnitVersion.JUNIT_3;
    return testFilterForClassesAndMethods(methodsPerClass, version);
  }

  @Nullable
  public static String testFilterForClassesAndMethods(
      Map<PsiClass, Collection<Location<?>>> methodsPerClass, JUnitVersion version) {
    List<String> classFilters = new ArrayList<>();
    for (Entry<PsiClass, Collection<Location<?>>> entry : methodsPerClass.entrySet()) {
      ParameterizedTestInfo parameterizedTestInfo =
          JUnitParameterizedClassHeuristic.getParameterizedTestInfo(entry.getKey());
      String filter =
          testFilterForClassAndMethods(
              entry.getKey(),
              version,
              extractMethodFilters(entry.getValue()),
              parameterizedTestInfo);
      if (filter == null) {
        return null;
      }
      classFilters.add(filter);
    }
    classFilters.sort(String::compareTo);
    return version == JUnitVersion.JUNIT_4
        ? String.join("|", classFilters)
        : String.join(",", classFilters);
  }

  /** Only runs specified parameterized versions, where relevant. */
  private static List<String> extractMethodFilters(Collection<Location<?>> methods) {
    return methods.stream()
        .map(BlazeJUnitTestFilterFlags::testFilterForLocation)
        .sorted()
        .collect(Collectors.toList());
  }

  private static String testFilterForLocation(Location<?> location) {
    PsiElement psi = location.getPsiElement();
    assert (psi instanceof PsiMethod);
    String methodName = ((PsiMethod) psi).getName();
    if (location instanceof PsiMemberParameterizedLocation) {
      return methodName
          + StringUtil.escapeToRegexp(
              ((PsiMemberParameterizedLocation) location).getParamSetName());
    }
    return methodName;
  }

  private static boolean hasJUnit4Test(Collection<PsiClass> classes) {
    for (PsiClass psiClass : classes) {
      if (JUnitUtil.isJUnit4TestClass(psiClass)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Builds the JUnit test filter corresponding to the given class and methods.<br>
   * Returns null if no class name can be found.
   */
  @Nullable
  private static String testFilterForClassAndMethods(
      PsiClass psiClass,
      JUnitVersion version,
      List<String> methodFilters,
      ParameterizedTestInfo parameterizedTestInfo) {
    String className = psiClass.getQualifiedName();
    if (className == null) {
      return null;
    }
    return testFilterForClassAndMethods(className, version, methodFilters, parameterizedTestInfo);
  }

  /**
   * Builds the blaze test_filter flag for JUnit tests. Excludes the "--test_filter" component of
   * the flag, so that multiple test classes can be combined.
   */
  @VisibleForTesting
  static String testFilterForClassAndMethods(
      String className,
      JUnitVersion jUnitVersion,
      List<String> methodFilters,
      ParameterizedTestInfo parameterizedTestInfo) {
    StringBuilder output = new StringBuilder(className);
    if (parameterizedTestInfo != null && parameterizedTestInfo.testClassSuffixRegex() != null) {
      output.append(parameterizedTestInfo.testClassSuffixRegex());
    }
    String methodNamePattern = concatenateMethodNames(methodFilters, jUnitVersion);
    if (Strings.isNullOrEmpty(methodNamePattern)) {
      if (jUnitVersion == JUnitVersion.JUNIT_4) {
        output.append('#');
      }
      return output.toString();
    }
    output.append('#').append(methodNamePattern);
    // JUnit 4 test filters are regexes, and must be terminated to avoid matching
    // unintended classes/methods. JUnit 3 test filters do not need or support this syntax.
    if (jUnitVersion == JUnitVersion.JUNIT_3) {
      return output.toString();
    }
    output.append('$');
    return output.toString();
  }

  @Nullable
  private static String concatenateMethodNames(
      List<String> methodNames, JUnitVersion jUnitVersion) {
    if (methodNames.isEmpty()) {
      return null;
    }
    if (methodNames.size() == 1) {
      return methodNames.get(0);
    }
    return jUnitVersion == JUnitVersion.JUNIT_4
        ? String.format("(%s)", String.join("|", methodNames))
        : String.join(",", methodNames);
  }

  private BlazeJUnitTestFilterFlags() {}
}
