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
package com.google.idea.blaze.java.run.producers;

import com.google.common.annotations.VisibleForTesting;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer;
import com.google.idea.blaze.base.run.producers.TestContext;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.List;
import javax.annotation.Nullable;

/** Producer for abstract test classes/methods. */
public class BlazeJavaAbstractTestCaseConfigurationProducer
    extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {

  private static class AbstractTestLocation {
    private final PsiClass abstractClass;
    @Nullable private final PsiMethod method;

    private AbstractTestLocation(PsiClass abstractClass, @Nullable PsiMethod method) {
      this.abstractClass = abstractClass;
      this.method = method;
    }
  }

  public BlazeJavaAbstractTestCaseConfigurationProducer() {
    super(BlazeCommandRunConfigurationType.getInstance());
  }

  @Override
  protected boolean doSetupConfigFromContext(
      BlazeCommandRunConfiguration configuration,
      ConfigurationContext context,
      Ref<PsiElement> sourceElement) {
    AbstractTestLocation location = getAbstractLocation(context);
    if (location == null) {
      return false;
    }
    sourceElement.set(location.method != null ? location.method : location.abstractClass);
    configuration.setName(
        "Choose subclass for " + configName(location.abstractClass, location.method));
    configuration.setNameChangedByUser(true);
    return true;
  }

  @Nullable
  private static AbstractTestLocation getAbstractLocation(ConfigurationContext context) {
    if (!SmRunnerUtils.getSelectedSmRunnerTreeElements(context).isEmpty()) {
      // handled by a different producer
      return null;
    }
    PsiMethod method = getTestMethod(context);
    if (method != null) {
      PsiClass psiClass = method.getContainingClass();
      return hasTestSubclasses(psiClass) ? new AbstractTestLocation(psiClass, method) : null;
    }
    Location<?> location = context.getLocation();
    if (location == null) {
      return null;
    }
    location = JavaExecutionUtil.stepIntoSingleClass(location);
    if (location == null) {
      return null;
    }
    PsiClass psiClass =
        PsiTreeUtil.getParentOfType(location.getPsiElement(), PsiClass.class, false);
    return hasTestSubclasses(psiClass) ? new AbstractTestLocation(psiClass, null) : null;
  }

  private static PsiMethod getTestMethod(ConfigurationContext context) {
    PsiElement psi = context.getPsiLocation();
    if (psi instanceof PsiMethod
        && AnnotationUtil.isAnnotated((PsiMethod) psi, JUnitUtil.TEST_ANNOTATION, false)) {
      return (PsiMethod) psi;
    }
    List<PsiMethod> selectedMethods = TestMethodSelectionUtil.getSelectedMethods(context);
    return selectedMethods != null && selectedMethods.size() == 1 ? selectedMethods.get(0) : null;
  }

  private static boolean hasTestSubclasses(@Nullable PsiClass psiClass) {
    return psiClass != null && !SubclassTestChooser.findTestSubclasses(psiClass).isEmpty();
  }

  @Override
  protected boolean doIsConfigFromContext(
      BlazeCommandRunConfiguration configuration, ConfigurationContext context) {
    // this is an intermediate type -- when it's fully instantiated (via 'onFirstRun') it will be
    // recognized by a different producer.
    return false;
  }

  @Override
  public void onFirstRun(
      ConfigurationFromContext configuration,
      ConfigurationContext context,
      Runnable startRunnable) {
    chooseSubclass(configuration, context, startRunnable);
  }

  @VisibleForTesting
  static void chooseSubclass(
      ConfigurationFromContext configuration,
      ConfigurationContext context,
      Runnable startRunnable) {
    RunConfiguration config = configuration.getConfiguration();
    if (!(config instanceof BlazeCommandRunConfiguration)) {
      return;
    }
    AbstractTestLocation location = locationFromConfiguration(configuration);
    if (location == null) {
      return;
    }
    SubclassTestChooser.chooseSubclass(
        context,
        location.abstractClass,
        (psiClass) -> {
          if (psiClass != null) {
            setupContext((BlazeCommandRunConfiguration) config, psiClass, location.method);
          }
          startRunnable.run();
        });
  }

  @Nullable
  private static AbstractTestLocation locationFromConfiguration(
      ConfigurationFromContext configuration) {
    PsiElement element = configuration.getSourceElement();
    PsiMethod method = null;
    PsiClass psiClass = null;
    if (element instanceof PsiMethod) {
      method = (PsiMethod) element;
      psiClass = method.getContainingClass();
    } else if (element instanceof PsiClass) {
      psiClass = (PsiClass) element;
    }

    return hasTestSubclasses(psiClass) ? new AbstractTestLocation(psiClass, method) : null;
  }

  private static void setupContext(
      BlazeCommandRunConfiguration configuration, PsiClass subClass, @Nullable PsiMethod method) {
    TestContext testContext = JavaTestContextProvider.fromClassAndMethod(subClass, method);
    if (testContext == null) {
      return;
    }
    testContext.setupRunConfiguration(configuration);
  }

  private static String configName(PsiClass psiClass, @Nullable PsiMethod method) {
    String classPart = psiClass.getName();
    return method == null ? classPart : String.format("%s.%s", classPart, method.getName());
  }
}
