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
package com.google.idea.blaze.android.run.producers;

import com.google.idea.blaze.android.run.test.BlazeAndroidTestRunConfigurationState;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Handles the specific case where the user creates a run configuration for android instrumentation
 * tests by selecting test suites / classes / methods from the test UI tree. This producer only
 * handles android instrumentation tests run without using blaze test or mobile-install.
 */
public class BlazeFilterAndroidTestRunConfigurationProducer
    extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {

  /**
   * A data class presenting the name of a class#method test location. The method name can be empty,
   * in which case it means the test location is a class, not a method of the class.
   */
  private static class TestLocationName {
    private final String className;
    private final String methodName;

    private TestLocationName(String className, String methodName) {
      this.className = className;
      this.methodName = methodName;
    }
  }

  public BlazeFilterAndroidTestRunConfigurationProducer() {
    super(BlazeCommandRunConfigurationType.getInstance());
  }

  @Override
  protected boolean doSetupConfigFromContext(
      BlazeCommandRunConfiguration configuration,
      ConfigurationContext context,
      Ref<PsiElement> sourceElement) {

    BlazeAndroidTestRunConfigurationState handlerState =
        configuration.getHandlerStateIfType(BlazeAndroidTestRunConfigurationState.class);

    if (handlerState == null) {
      return false;
    }

    TestLocationName locationName = getTestLocationName(context);
    if (locationName == null) {
      return false;
    }

    handlerState.setClassName(locationName.className);
    handlerState.setMethodName(locationName.methodName);
    if (locationName.methodName.isEmpty()) {
      handlerState.setTestingType(BlazeAndroidTestRunConfigurationState.TEST_CLASS);
    } else {
      handlerState.setTestingType(BlazeAndroidTestRunConfigurationState.TEST_METHOD);
    }

    configuration.setGeneratedName();
    return true;
  }

  @Override
  protected boolean doIsConfigFromContext(
      BlazeCommandRunConfiguration configuration, ConfigurationContext context) {
    BlazeAndroidTestRunConfigurationState handlerState =
        configuration.getHandlerStateIfType(BlazeAndroidTestRunConfigurationState.class);

    if (handlerState == null) {
      return false;
    }

    TestLocationName locationName = getTestLocationName(context);
    if (locationName == null) {
      return false;
    }

    if (locationName.methodName.isEmpty()) {
      return (handlerState.getMethodName().isEmpty()
          && locationName.className.equals(handlerState.getClassName()));
    }

    return locationName.methodName.equals(handlerState.getMethodName());
  }

  /**
   * @return name of the test location as a class#method name pair. Returns null if the test
   *     location is not a class or a method of a class.
   */
  @Nullable
  private static TestLocationName getTestLocationName(ConfigurationContext context) {
    List<Location<?>> selectedElementLocations =
        SmRunnerUtils.getSelectedSmRunnerTreeElements(context);

    if (selectedElementLocations.isEmpty() || selectedElementLocations.size() > 1) {
      return null; // Don't support android instrumentation tests from more than one location.
    }

    PsiElement selectedElement = context.getPsiLocation();

    if (selectedElement instanceof PsiMethod) {
      PsiMethod selectedMethod = (PsiMethod) selectedElement;
      PsiClass containingClass = selectedMethod.getContainingClass();
      if (containingClass == null) {
        return null;
      }

      String qualifiedClassName = containingClass.getQualifiedName();
      if (qualifiedClassName == null) {
        return null;
      }

      return new TestLocationName(qualifiedClassName, selectedMethod.getName());

    } else if (selectedElement instanceof PsiClass) {
      PsiClass selectedClass = (PsiClass) selectedElement;

      String qualifiedClassName = selectedClass.getQualifiedName();
      if (qualifiedClassName == null) {
        return null;
      }

      return new TestLocationName(qualifiedClassName, "");
    }

    return null;
  }
}
