/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.dependencies.TestSize;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.TestTargetHeuristic;
import com.google.idea.blaze.base.run.producers.RunConfigurationContext;
import com.google.idea.blaze.base.run.producers.TestContext;
import com.google.idea.blaze.base.run.producers.TestContext.BlazeFlagsModification;
import com.google.idea.blaze.base.run.producers.TestContextProvider;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Currently handles only a non-abstract java test methods / single test class. */
class JavaTestContextProvider implements TestContextProvider {

  @Nullable
  static TestContext fromClassAndMethod(PsiClass testClass, @Nullable PsiMethod method) {
    if (method != null) {
      return fromClassAndMethods(testClass, ImmutableList.of(method));
    }
    return fromClass(testClass);
  }

  @Nullable
  @Override
  public RunConfigurationContext getTestContext(ConfigurationContext context) {
    final List<PsiMethod> selectedMethods = TestMethodSelectionUtil.getSelectedMethods(context);
    if (selectedMethods != null && !selectedMethods.isEmpty()) {
      return fromSelectedMethods(selectedMethods);
    }
    // otherwise look for a single selected class
    PsiClass testClass = getSelectedTestClass(context);
    if (testClass == null || testClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return null;
    }
    return fromClass(testClass);
  }

  @Nullable
  private static TestContext fromClass(PsiClass testClass) {
    String testFilter = getTestFilterForClass(testClass);
    if (testFilter == null) {
      return null;
    }
    TestSize testSize = TestSizeFinder.getTestSize(testClass);
    ListenableFuture<TargetInfo> target =
        TestTargetHeuristic.targetFutureForPsiElement(testClass, testSize);
    if (target == null) {
      return null;
    }
    return TestContext.builder(testClass, ExecutorType.FAST_DEBUG_SUPPORTED_TYPES)
        .setTarget(target)
        .setTestFilter(testFilter)
        .setDescription(testClass.getName())
        .build();
  }

  @Nullable
  private static TestContext fromSelectedMethods(List<PsiMethod> selectedMethods) {
    // Sort so multiple configurations created with different selection orders are the same.
    selectedMethods.sort(Comparator.comparing(PsiMethod::getName));
    PsiMethod firstMethod = selectedMethods.get(0);
    PsiClass containingClass = firstMethod.getContainingClass();
    if (containingClass == null || containingClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return null;
    }
    for (PsiMethod method : selectedMethods) {
      if (!containingClass.equals(method.getContainingClass())) {
        return null;
      }
    }
    return fromClassAndMethods(containingClass, selectedMethods);
  }

  private static TestContext fromClassAndMethods(
      PsiClass containingClass, List<PsiMethod> selectedMethods) {
    PsiMethod firstMethod = selectedMethods.get(0);
    String testFilter =
        BlazeJUnitTestFilterFlags.testFilterForClassAndMethods(containingClass, selectedMethods);
    if (testFilter == null) {
      return null;
    }
    TestSize testSize = TestSizeFinder.getTestSize(firstMethod);
    ListenableFuture<TargetInfo> target =
        TestTargetHeuristic.targetFutureForPsiElement(containingClass, testSize);
    if (target == null) {
      return null;
    }
    String methodNames =
        selectedMethods.stream().map(PsiMethod::getName).collect(Collectors.joining(","));
    String description = String.format("%s.%s", containingClass.getName(), methodNames);
    return TestContext.builder(firstMethod, ExecutorType.FAST_DEBUG_SUPPORTED_TYPES)
        .setTarget(target)
        .setTestFilter(testFilter)
        // test sharding disabled when manually selecting methods (typically only 1)
        .addBlazeFlagsModification(
            BlazeFlagsModification.addFlagIfNotPresent(BlazeFlags.DISABLE_TEST_SHARDING))
        .setDescription(description)
        .build();
  }

  @Nullable
  private static PsiClass getSelectedTestClass(ConfigurationContext context) {
    Location<?> location = context.getLocation();
    if (location == null) {
      return null;
    }
    location = JavaExecutionUtil.stepIntoSingleClass(location);
    if (location == null) {
      return null;
    }
    if (JUnitConfigurationUtil.isMultipleElementsSelected(context)) {
      return null;
    }
    return ProducerUtils.getTestClass(location);
  }

  @Nullable
  private static String getTestFilterForClass(PsiClass testClass) {
    Set<PsiClass> innerTestClasses = ProducerUtils.getInnerTestClasses(testClass);
    if (innerTestClasses.isEmpty()) {
      return BlazeJUnitTestFilterFlags.testFilterForClass(testClass);
    }
    innerTestClasses.add(testClass);
    Map<PsiClass, Collection<Location<?>>> methodsPerClass =
        innerTestClasses.stream().collect(Collectors.toMap(c -> c, c -> ImmutableList.of()));
    return BlazeJUnitTestFilterFlags.testFilterForClassesAndMethods(methodsPerClass);
  }
}
