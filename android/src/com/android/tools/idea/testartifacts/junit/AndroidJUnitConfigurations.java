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

import com.android.tools.idea.gradle.util.Projects;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.PatternConfigurationProducer;
import com.intellij.execution.junit.TestObject;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import static com.intellij.execution.junit.JUnitUtil.getTestClass;
import static com.intellij.execution.junit.JUnitUtil.getTestMethod;

/**
 * Common code for the {@link RunConfigurationProducer}s in Android JUnit configurations.
 */
public class AndroidJUnitConfigurations {
  public static boolean shouldUseAndroidJUnitConfigurations(@NotNull ConfigurationFromContext self,
                                                            @NotNull ConfigurationFromContext other) {
    RunConfiguration androidConfiguration = self.getConfiguration();
    if (androidConfiguration instanceof ModuleBasedConfiguration) {
      Module module = ((ModuleBasedConfiguration)androidConfiguration).getConfigurationModule().getModule();
      if (module != null && Projects.isIdeaAndroidModule(module)) {
        return true;
      }
    }
    return false;
  }

  // Copy of JUnitConfigurationProducer#isConfigurationFromContext using AndroidJUnitConfigurationType instead of JUnitConfigurationType
  public static boolean isFromContext(@NotNull JUnitConfiguration unitConfiguration,
                                      @NotNull ConfigurationContext context,
                                      @NotNull ConfigurationFactory configurationFactory) {
    if (RunConfigurationProducer.getInstance(PatternConfigurationProducer.class).isMultipleElementsSelected(context)) {
      return false;
    }
    RunConfiguration predefinedConfiguration = context.getOriginalConfiguration(AndroidJUnitConfigurationType.getInstance());
    Location contextLocation = context.getLocation();

    String paramSetName = contextLocation instanceof PsiMemberParameterizedLocation
                          ? ((PsiMemberParameterizedLocation)contextLocation).getParamSetName() : null;
    assert contextLocation != null;
    Location location = JavaExecutionUtil.stepIntoSingleClass(contextLocation);
    if (location == null) {
      return false;
    }
    PsiElement element = location.getPsiElement();
    PsiClass testClass = getTestClass(element);
    PsiMethod testMethod = getTestMethod(element, false);
    PsiPackage testPackage;
    if (element instanceof PsiPackage) {
      testPackage = (PsiPackage)element;
    }
    else {
      if (element instanceof PsiDirectory){
        testPackage = JavaDirectoryService.getInstance().getPackage(((PsiDirectory)element));
      }
      else {
        testPackage = null;
      }
    }
    PsiDirectory testDir = element instanceof PsiDirectory ? (PsiDirectory)element : null;
    RunnerAndConfigurationSettings template = RunManager.getInstance(location.getProject())
      .getConfigurationTemplate(configurationFactory);
    Module predefinedModule =
      ((JUnitConfiguration)template
        .getConfiguration()).getConfigurationModule().getModule();
    String vmParameters = predefinedConfiguration instanceof JUnitConfiguration ? ((JUnitConfiguration)predefinedConfiguration).getVMParameters() : null;

    if (vmParameters != null && !Comparing.strEqual(vmParameters, unitConfiguration.getVMParameters())) {
      return false;
    }
    if (paramSetName != null && !Comparing.strEqual(paramSetName, unitConfiguration.getProgramParameters())) {
      return false;
    }
    TestObject testobject = unitConfiguration.getTestObject();
    if (testobject != null) {
      if (testobject.isConfiguredByElement(unitConfiguration, testClass, testMethod, testPackage, testDir)) {
        Module configurationModule = unitConfiguration.getConfigurationModule().getModule();
        if (Comparing.equal(location.getModule(), configurationModule)) {
          return true;
        }
        if (Comparing.equal(predefinedModule, configurationModule)) {
          return true;
        }
      }
    }
    return false;
  }
}
