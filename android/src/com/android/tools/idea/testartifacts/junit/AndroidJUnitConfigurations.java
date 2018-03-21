/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.testartifacts.junit;

import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.gradle.util.GradleProjects;
import com.intellij.execution.*;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit.PatternConfigurationProducer;
import com.intellij.execution.junit.TestObject;
import com.intellij.openapi.module.Module;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Common code for the {@link RunConfigurationProducer}s in Android JUnit configurations.
 */
public class AndroidJUnitConfigurations {
  public static boolean shouldUseAndroidJUnitConfigurations(@NotNull ConfigurationFromContext self,
                                                            @NotNull ConfigurationFromContext other) {
    RunConfiguration androidConfiguration = self.getConfiguration();
    RunConfiguration otherConfiguration = other.getConfiguration();
    if (androidConfiguration instanceof ModuleBasedConfiguration &&
        (otherConfiguration instanceof JUnitConfiguration) &&
        !(otherConfiguration instanceof AndroidJUnitConfiguration)) {
      Module module = ((ModuleBasedConfiguration)androidConfiguration).getConfigurationModule().getModule();
      if (module != null && GradleProjects.isIdeaAndroidModule(module)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isFromContext(@NotNull JUnitConfiguration junitConfiguration,
                                      @NotNull ConfigurationContext context,
                                      @NotNull ConfigurationFactory configurationFactory) {
    if (RunConfigurationProducer.getInstance(PatternConfigurationProducer.class).isMultipleElementsSelected(context)) {
      return false;
    }

    Location contextLocation = context.getLocation();
    if (contextLocation == null) {
      return false;
    }

    PsiElement leaf = contextLocation.getPsiElement();
    Location<PsiMethod> methodLocation = getTestMethodLocation(leaf);
    PsiClass testClass = getTestClass(leaf);
    TestObject testObject = junitConfiguration.getTestObject();

    if (!testObject.isConfiguredByElement(junitConfiguration, testClass, methodLocation == null ? null : methodLocation.getPsiElement(), null, null)) {
      return false;
    }

    return settingsMatchTemplate(junitConfiguration, context, configurationFactory);
  }

  private static boolean settingsMatchTemplate(@NotNull JUnitConfiguration junitConfiguration,
                                               @NotNull ConfigurationContext configurationContext,
                                               @NotNull ConfigurationFactory configurationFactory) {
    RunConfiguration predefinedConfiguration = configurationContext.getOriginalConfiguration(AndroidJUnitConfigurationType.getInstance());

    if (predefinedConfiguration != null && predefinedConfiguration instanceof CommonJavaRunConfigurationParameters) {
      String vmParameters = ((CommonJavaRunConfigurationParameters)predefinedConfiguration).getVMParameters();
      if (vmParameters != null && !junitConfiguration.getVMParameters().equals(vmParameters)) {
        return false;
      }
    }

    RunnerAndConfigurationSettings template = RunManager.getInstance(junitConfiguration.getProject()).getConfigurationTemplate(configurationFactory);
    Module predefinedModule = ((ModuleBasedConfiguration)template.getConfiguration()).getConfigurationModule().getModule();
    Module configurationModule = junitConfiguration.getConfigurationModule().getModule();
    Module contextModule = configurationContext.getLocation() == null ? null : configurationContext.getLocation().getModule();

    return configurationModule == contextModule || configurationModule == predefinedModule;
  }

  @Nullable
  private static PsiClass getTestClass(@NotNull PsiElement leaf) {
    PsiClass psiClass = AndroidPsiUtils.getPsiParentOfType(leaf, PsiClass.class, false);
    if (psiClass != null && JUnitUtil.isTestClass(psiClass)) {
      return psiClass;
    }
    return null;
  }

  @Nullable
  private static Location<PsiMethod> getTestMethodLocation(@NotNull PsiElement leaf) {
    PsiMethod method = AndroidPsiUtils.getPsiParentOfType(leaf, PsiMethod.class, false);
    if (method != null) {
      Location<PsiMethod> methodLocation = PsiLocation.fromPsiElement(method);
      if (methodLocation != null && JUnitUtil.isTestMethod(methodLocation, false)) {
        return methodLocation;
      }
    }
    return null;
  }
}
