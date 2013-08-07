/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.run.testing;

import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit.JavaRuntimeConfigurationProducerBase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.android.run.AndroidRunConfigurationType;
import org.jetbrains.android.run.TargetSelectionMode;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidTestConfigurationProducer extends JavaRuntimeConfigurationProducerBase implements Cloneable {
  private PsiElement mySourceElement;

  public AndroidTestConfigurationProducer() {
    super(AndroidTestRunConfigurationType.getInstance());
  }

  @Override
  public PsiElement getSourceElement() {
    return mySourceElement;
  }

  @Nullable
  @Override
  protected RunnerAndConfigurationSettings findExistingByElement(Location location,
                                                                 @NotNull RunnerAndConfigurationSettings[] existingConfigurations,
                                                                 ConfigurationContext context) {
    final Module contextModule = AndroidUtils.getAndroidModule(context);
    if (contextModule == null) return null;
    if (location == null) return null;

    location = JavaExecutionUtil.stepIntoSingleClass(location);
    if (location == null) return null;

    PsiElement element = location.getPsiElement();
    PsiPackage psiPackage = checkPackage(element);
    String packageName = psiPackage == null ? null : psiPackage.getQualifiedName();

    PsiClass elementClass = PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
    String className = elementClass == null ? null : elementClass.getQualifiedName();

    PsiMethod elementMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    String methodName = elementMethod == null ? null : elementMethod.getName();

    for (RunnerAndConfigurationSettings settings : existingConfigurations) {
      if (settings.getConfiguration() instanceof AndroidTestRunConfiguration) {
        AndroidTestRunConfiguration config = (AndroidTestRunConfiguration)settings.getConfiguration();
        Module moduleInConfig = config.getConfigurationModule().getModule();
        if (!Comparing.equal(contextModule, moduleInConfig)) {
          continue;
        }

        switch (config.TESTING_TYPE) {
          case AndroidTestRunConfiguration.TEST_ALL_IN_MODULE:
            if (psiPackage != null && packageName.isEmpty()) {
              return settings;
            }
            break;
          case AndroidTestRunConfiguration.TEST_ALL_IN_PACKAGE:
            if (packageName != null && packageName.equals(config.PACKAGE_NAME)) {
              return settings;
            }
            break;
          case AndroidTestRunConfiguration.TEST_CLASS:
            if (className != null && className.equals(config.CLASS_NAME)) {
              return settings;
            }
          case AndroidTestRunConfiguration.TEST_METHOD:
            if (methodName != null && methodName.equals(config.METHOD_NAME)) {
              return settings;
            }
        }
      }
    }
    return null;
  }

  @Override
  @Nullable
  protected RunnerAndConfigurationSettings createConfigurationByElement(Location location, ConfigurationContext context) {
    if (AndroidUtils.getAndroidModule(context) == null) return null;
    if (location == null) return null;

    location = JavaExecutionUtil.stepIntoSingleClass(location);
    if (location == null) return null;

    PsiElement element = location.getPsiElement();
    RunnerAndConfigurationSettings settings = createAllInPackageConfiguration(element, context);
    if (settings != null) return settings;

    settings = createMethodConfiguration(element, context);
    if (settings != null) return settings;

    return createClassConfiguration(element, context);
  }

  @Nullable
  private RunnerAndConfigurationSettings createAllInPackageConfiguration(PsiElement element, ConfigurationContext context) {
    PsiPackage p = checkPackage(element);
    if (p != null) {
      final String packageName = p.getQualifiedName();
      RunnerAndConfigurationSettings settings =
        createConfiguration(p, context, packageName.length() > 0
                                        ? AndroidTestRunConfiguration.TEST_ALL_IN_PACKAGE
                                        : AndroidTestRunConfiguration.TEST_ALL_IN_MODULE);
      if (settings == null) return null;
      AndroidTestRunConfiguration configuration = (AndroidTestRunConfiguration)settings.getConfiguration();
      configuration.PACKAGE_NAME = packageName;
      configuration.setGeneratedName();
      return settings;
    }
    return null;
  }

  @Nullable
  private RunnerAndConfigurationSettings createClassConfiguration(PsiElement element, ConfigurationContext context) {
    PsiClass elementClass = PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
    while (elementClass != null) {
      if (JUnitUtil.isTestClass(elementClass)) {
        RunnerAndConfigurationSettings settings =
          createConfiguration(elementClass, context, AndroidTestRunConfiguration.TEST_CLASS);
        if (settings == null) return null;
        AndroidTestRunConfiguration configuration = (AndroidTestRunConfiguration)settings.getConfiguration();
        configuration.CLASS_NAME = elementClass.getQualifiedName();
        configuration.setGeneratedName();
        return settings;
      }
      elementClass = PsiTreeUtil.getParentOfType(elementClass, PsiClass.class);
    }
    return null;
  }

  @Nullable
  private RunnerAndConfigurationSettings createMethodConfiguration(PsiElement element, ConfigurationContext context) {
    PsiMethod elementMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    while (elementMethod != null) {
      if (isTestMethod(elementMethod)) {
        PsiClass c = elementMethod.getContainingClass();
        assert c != null;
        RunnerAndConfigurationSettings settings =
          createConfiguration(elementMethod, context, AndroidTestRunConfiguration.TEST_METHOD);
        if (settings == null) return null;
        AndroidTestRunConfiguration configuration = (AndroidTestRunConfiguration)settings.getConfiguration();
        configuration.CLASS_NAME = c.getQualifiedName();
        configuration.METHOD_NAME = elementMethod.getName();
        configuration.setGeneratedName();
        return settings;
      }
      elementMethod = PsiTreeUtil.getParentOfType(elementMethod, PsiMethod.class);
    }
    return null;
  }

  @Nullable
  private RunnerAndConfigurationSettings createConfiguration(PsiElement element, ConfigurationContext context, int testingType) {
    Module module = AndroidUtils.getAndroidModule(context);
    if (module == null) return null;
    mySourceElement = element;
    RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(element.getProject(), context);
    AndroidTestRunConfiguration configuration = (AndroidTestRunConfiguration)settings.getConfiguration();
    configuration.TESTING_TYPE = testingType;
    setupConfigurationModule(context, configuration);

    final TargetSelectionMode targetSelectionMode = AndroidUtils
      .getDefaultTargetSelectionMode(module, AndroidTestRunConfigurationType.getInstance(), AndroidRunConfigurationType.getInstance());
    
    if (targetSelectionMode != null) {
      configuration.setTargetSelectionMode(targetSelectionMode);
    }

    return settings;
  }

  private static boolean isTestMethod(PsiMethod method) {
    PsiClass testClass = method.getContainingClass();
    if (testClass != null && JUnitUtil.isTestClass(testClass)) {
      return new JUnitUtil.TestMethodFilter(testClass).value(method);
    }
    return false;
  }

  @Override
  public int compareTo(Object o) {
    return PREFERED;
  }
}
