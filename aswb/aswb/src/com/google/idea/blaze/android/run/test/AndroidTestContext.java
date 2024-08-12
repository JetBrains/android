/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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

package com.google.idea.blaze.android.run.test;

import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.google.common.base.Strings;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.TestTargetHeuristic;
import com.google.idea.blaze.base.run.producers.RunConfigurationContext;
import com.google.idea.blaze.java.AndroidBlazeRules.RuleTypes;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import java.util.Objects;
import javax.annotation.Nullable;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtNamedFunction;

/**
 * {@link RunConfigurationContext} for android test targets. Provides run configuration for Java and
 * Kotlin sources consumed by 'android_test' and 'android_instrumentation_test' targets.
 */
abstract class AndroidTestContext implements RunConfigurationContext {
  private final TargetInfo target;

  private AndroidTestContext(TargetInfo targetInfo) {
    this.target = targetInfo;
  }

  @Override
  public boolean setupRunConfiguration(BlazeCommandRunConfiguration config) {
    config.setTargetInfo(target);
    BlazeAndroidTestRunConfigurationState configState =
        config.getHandlerStateIfType(BlazeAndroidTestRunConfigurationState.class);
    if (configState == null) {
      return false;
    }
    String className = getFullyQualifiedClassName();
    if (className == null) {
      return false;
    }
    configState.setClassName(className);

    String methodName = getMethodName();
    int testingType =
        methodName != null
            ? AndroidTestRunConfiguration.TEST_METHOD
            : AndroidTestRunConfiguration.TEST_CLASS;

    configState.setTestingType(testingType);
    configState.setMethodName(methodName);

    config.setGeneratedName();
    return true;
  }

  @Override
  public boolean matchesRunConfiguration(BlazeCommandRunConfiguration config) {
    BlazeAndroidTestRunConfigurationState configState =
        config.getHandlerStateIfType(BlazeAndroidTestRunConfigurationState.class);
    if (configState == null) {
      return false;
    }

    String methodName = getMethodName();
    int testingType =
        methodName != null
            ? AndroidTestRunConfiguration.TEST_METHOD
            : AndroidTestRunConfiguration.TEST_CLASS;

    return configState.getTestingType() == testingType
        && Objects.equals(
            Strings.emptyToNull(configState.getClassName()), getFullyQualifiedClassName())
        && Objects.equals(Strings.emptyToNull(configState.getMethodName()), methodName);
  }

  /**
   * Returns the fully qualified class name of the test for which config is being generated. Returns
   * null if the name could not be computed.
   */
  @Nullable
  protected abstract String getFullyQualifiedClassName();

  /**
   * Returns the name of the method being tested. Returns null if there is no associated method or
   * if the name could not be computed
   */
  @Nullable
  protected abstract String getMethodName();

  /** Returns AndroidTestContext specific for Java PSI */
  @Nullable
  static AndroidTestContext fromClassAndMethod(PsiClass psiClass, @Nullable PsiMethod psiMethod) {
    TargetInfo target = getTargetInfo(psiClass);
    if (target == null) {
      return null;
    }

    if (!RuleTypes.ANDROID_TEST.getKind().equals(target.getKind())
        && !RuleTypes.ANDROID_INSTRUMENTATION_TEST.getKind().equals(target.getKind())) {
      return null;
    }

    return new JavaAndroidTestContext(psiClass, psiMethod, target);
  }

  /** Returns AndroidTestContext specific for Kotlin PSI */
  @Nullable
  static AndroidTestContext fromClassAndMethod(
      KtClass ktClass, @Nullable KtNamedFunction ktFunction) {
    TargetInfo target = getTargetInfo(ktClass);
    if (target == null) {
      return null;
    }

    if (!RuleTypes.ANDROID_TEST.getKind().equals(target.getKind())
        && !RuleTypes.ANDROID_INSTRUMENTATION_TEST.getKind().equals(target.getKind())) {
      return null;
    }

    return new KotlinAndroidTestContext(ktClass, ktFunction, target);
  }

  /**
   * Returns the target info corresponding to the given class.
   *
   * <p>NOTE: This method currently synchronous which might cause freezes if calculating the target
   * takes a long time. AndroidTestContext needs to be updated to handle futures properly to prevent
   * these freezes.
   */
  @Nullable
  private static TargetInfo getTargetInfo(PsiElement klass) {
    // Using deprecated `testTargetForPsiElement` because AndroidTestContext cannot correctly handle
    // `targetFutureForPsiElement` and immediately calling `.get()` to the future returned by
    // `targetFutureForPsiElement` can result in a deadlock (see b/189640193#comment6).
    // TODO(b/189874361): Adapt AndroidTestContext to support targetFutureForPsiElement
    return TestTargetHeuristic.testTargetForPsiElement(klass, null);
  }

  /** AndroidTestContext for extracting fqcn and method name from Java PSI */
  private static class JavaAndroidTestContext extends AndroidTestContext {
    private final PsiClass psiClass;
    private final PsiMethod psiMethod;

    private JavaAndroidTestContext(
        PsiClass psiClass, @Nullable PsiMethod psiMethod, TargetInfo targetInfo) {
      super(targetInfo);
      this.psiClass = psiClass;
      this.psiMethod = psiMethod;
    }

    @Nullable
    @Override
    protected String getFullyQualifiedClassName() {
      return ReadAction.compute(psiClass::getQualifiedName);
    }

    @Override
    @Nullable
    protected String getMethodName() {
      return psiMethod == null ? null : ReadAction.compute(psiMethod::getName);
    }

    @Override
    public PsiElement getSourceElement() {
      return psiMethod != null ? psiMethod : psiClass;
    }
  }

  /** AndroidTestContext for extracting fqcn and method name from Kotlin PSI */
  private static class KotlinAndroidTestContext extends AndroidTestContext {
    private final KtClass ktClass;
    @Nullable private final KtNamedFunction ktFunction;

    private KotlinAndroidTestContext(
        KtClass ktClass, @Nullable KtNamedFunction ktFunction, TargetInfo targetInfo) {
      super(targetInfo);
      this.ktClass = ktClass;
      this.ktFunction = ktFunction;
    }

    @Nullable
    @Override
    protected String getFullyQualifiedClassName() {
      return ReadAction.compute(
          () -> {
            FqName fqName = ktClass.getFqName();
            return fqName == null ? null : fqName.toString();
          });
    }

    @Nullable
    @Override
    protected String getMethodName() {
      return ktFunction == null ? null : ReadAction.compute(ktFunction::getName);
    }

    @Override
    public PsiElement getSourceElement() {
      return ktFunction != null ? ktFunction : ktClass;
    }
  }
}
