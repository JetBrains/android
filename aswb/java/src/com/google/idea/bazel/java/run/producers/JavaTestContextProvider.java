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
package com.google.idea.bazel.java.run.producers;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.dependencies.TestSize;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.run.producers.CommandComponent;
import com.google.idea.blaze.base.run.producers.RunConfigurationContext;
import com.google.idea.blaze.base.run.producers.TargetSpecification;
import com.google.idea.blaze.base.run.producers.TestContextProvider;
import com.google.idea.blaze.base.run.producers.TestFilterComponent;
import com.google.idea.blaze.base.run.producers.TestFilterSyntax;
import com.google.idea.blaze.base.run.producers.TestSelector;
import com.google.idea.blaze.base.run.producers.UnifiedRunContext;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Currently handles only non-abstract java test methods / single test class. */
class JavaTestContextProvider implements TestContextProvider {

  @Nullable
  static UnifiedRunContext fromClassAndMethod(PsiClass testClass, @Nullable PsiMethod method) {
    if (method != null) {
      return fromClassAndMethods(testClass, ImmutableList.of(method));
    }
    return fromClass(testClass);
  }

  @Nullable
  @Override
  public RunConfigurationContext getTestContext(ConfigurationContext context) {
    Location<?> location = context.getLocation();
    if (location != null && location.getPsiElement() != null) {
      PsiFile file = location.getPsiElement().getContainingFile();
      if (file != null && file.getName().endsWith(".kt")) {
        return null;
      }
    }
    final List<PsiMethod> selectedMethods = TestMethodSelectionUtil.getSelectedMethods(context);
    if (selectedMethods != null && !selectedMethods.isEmpty()) {
      if (isKotlin(selectedMethods.get(0).getContainingClass())) {
        return null;
      }
      return fromSelectedMethods(selectedMethods);
    }
    PsiElement locationElement = context.getPsiLocation();
    if (locationElement instanceof PsiClass) {
      PsiClass testClass = (PsiClass) locationElement;
      if (!isKotlin(testClass)) {
        return fromClass(testClass);
      }
    }
    if (locationElement instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) locationElement;
      PsiClass testClass = method.getContainingClass();
      if (testClass != null && !isKotlin(testClass)) {
        return fromClassAndMethod(testClass, method);
      }
    }
    PsiMethod method = getTestMethod(context);
    if (method != null) {
      PsiClass testClass = method.getContainingClass();
      if (testClass != null && !isKotlin(testClass)) {
        return fromClassAndMethod(testClass, method);
      }
    }
    PsiClass testClass = getSelectedTestClass(context);
    if (testClass == null || isKotlin(testClass)) {
      return null;
    }
    return fromClass(testClass);
  }

  private static PsiMethod getTestMethod(ConfigurationContext context) {
    Location<?> location = context.getLocation();
    if (location == null) {
      return null;
    }
    Location<PsiMethod> methodLocation = ProducerUtils.getTestMethod(location);
    return methodLocation != null ? methodLocation.getPsiElement() : null;
  }

  private static boolean isKotlin(@Nullable PsiClass psiClass) {
    if (psiClass == null) {
      return false;
    }
    PsiFile file = psiClass.getContainingFile();
    return file != null && file.getName().endsWith(".kt");
  }

  @Nullable
  private static UnifiedRunContext fromClass(PsiClass testClass) {
    String qualifiedName = testClass.getQualifiedName();
    if (qualifiedName == null) {
      return null;
    }
    Set<PsiClass> innerClasses = ProducerUtils.getInnerTestClasses(testClass);
    Set<PsiClass> allClasses = new HashSet<>(innerClasses);
    allClasses.add(testClass);

    TestFilterSyntax version =
        allClasses.stream().anyMatch(ProducerUtils::isJUnit4Class)
            ? TestFilterSyntax.JUNIT_4
            : TestFilterSyntax.JUNIT_3;

    List<TestSelector> testSelectors = new ArrayList<>();
    testSelectors.add(new TestSelector(qualifiedName, null, null, version));
    for (PsiClass inner : innerClasses) {
      if (inner.getQualifiedName() != null) {
        testSelectors.add(new TestSelector(inner.getQualifiedName(), null, null, version));
      }
    }

    TestSize testSize = TestSizeFinder.getTestSize(testClass);
    TargetSpecification targetSpec = getTargetSpecification(testClass, testSize);
    if (targetSpec == null) {
      return null;
    }

    return new UnifiedRunContext(
        testClass,
        targetSpec,
        new TestFilterComponent(testSelectors, false),
        new CommandComponent(BlazeCommandName.TEST, Collections.emptyList()));
  }

  @Nullable
  private static UnifiedRunContext fromSelectedMethods(List<PsiMethod> selectedMethods) {
    selectedMethods.sort(Comparator.comparing(PsiMethod::getName));
    PsiMethod firstMethod = selectedMethods.get(0);
    PsiClass containingClass = firstMethod.getContainingClass();
    if (containingClass == null) {
      return null;
    }
    for (PsiMethod method : selectedMethods) {
      if (!containingClass.equals(method.getContainingClass())) {
        return null;
      }
    }
    return fromClassAndMethods(containingClass, selectedMethods);
  }

  @Nullable
  private static UnifiedRunContext fromClassAndMethods(
      PsiClass containingClass, List<PsiMethod> selectedMethods) {
    String qualifiedName = containingClass.getQualifiedName();
    if (qualifiedName == null) {
      return null;
    }
    PsiMethod firstMethod = selectedMethods.get(0);
    TestFilterSyntax version =
        ProducerUtils.isJUnit4Class(containingClass)
            ? TestFilterSyntax.JUNIT_4
            : TestFilterSyntax.JUNIT_3;

    List<TestSelector> testSelectors =
        selectedMethods.stream()
            .map(m -> new TestSelector(qualifiedName, m.getName(), null, version))
            .collect(Collectors.toList());

    TestSize testSize = TestSizeFinder.getTestSize(firstMethod);
    TargetSpecification targetSpec = getTargetSpecification(containingClass, testSize);
    if (targetSpec == null) {
      return null;
    }

    List<String> extraFlags =
        selectedMethods.size() == 1
            ? Collections.singletonList(BlazeFlags.DISABLE_TEST_SHARDING)
            : Collections.emptyList();

    return new UnifiedRunContext(
        firstMethod,
        targetSpec,
        new TestFilterComponent(testSelectors, false),
        new CommandComponent(BlazeCommandName.TEST, extraFlags));
  }

  /**
   * Constructs a deferred {@link TargetSpecification.PendingResolution} for the given test class.
   *
   * <p>Target resolution across reverse dependencies and heuristics is deferred to Stage 2.5 in
   * background coroutines to ensure Stage 1 context extraction does not block the UI thread.
   */
  @Nullable
  private static TargetSpecification getTargetSpecification(
      PsiClass testClass, @Nullable TestSize testSize) {
    VirtualFile vf =
        testClass.getContainingFile() != null
            ? testClass.getContainingFile().getVirtualFile()
            : null;
    if (vf != null) {
      return new TargetSpecification.PendingResolution(
          new File(vf.getPath()), testSize, RuleType.TEST);
    }
    return null;
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
}
