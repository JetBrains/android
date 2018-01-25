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

package com.android.tools.idea.testartifacts.instrumented;

import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.run.AndroidRunConfigurationType;
import com.android.tools.idea.run.TargetSelectionMode;
import com.android.tools.idea.testartifacts.scopes.TestArtifactSearchScopes;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.junit.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_TEST;
import static com.intellij.psi.util.PsiTreeUtil.getParentOfType;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidTestConfigurationProducer extends JavaRunConfigurationProducerBase<AndroidTestRunConfiguration> {

  public AndroidTestConfigurationProducer() {
    super(AndroidTestRunConfigurationType.getInstance());
  }

  private boolean setupAllInPackageConfiguration(AndroidTestRunConfiguration configuration,
                                                 PsiElement element,
                                                 ConfigurationContext context,
                                                 Ref<PsiElement> sourceElement) {
    final PsiPackage p = JavaRuntimeConfigurationProducerBase.checkPackage(element);
    if (p == null) {
      return false;
    }
    final String packageName = p.getQualifiedName();
    setupConfiguration(configuration, p, context, sourceElement);
    configuration.TESTING_TYPE = !packageName.isEmpty()
                                 ? AndroidTestRunConfiguration.TEST_ALL_IN_PACKAGE
                                 : AndroidTestRunConfiguration.TEST_ALL_IN_MODULE;
    configuration.PACKAGE_NAME = packageName;
    configuration.setGeneratedName();
    return true;
  }

  private boolean setupClassConfiguration(AndroidTestRunConfiguration configuration,
                                          PsiElement element,
                                          ConfigurationContext context,
                                          Ref<PsiElement> sourceElement) {
    PsiClass elementClass = AndroidPsiUtils.getPsiParentOfType(element, PsiClass.class, false);

    while (elementClass != null) {
      if (JUnitUtil.isTestClass(elementClass)) {
        setupConfiguration(configuration, elementClass, context, sourceElement);
        configuration.TESTING_TYPE = AndroidTestRunConfiguration.TEST_CLASS;
        configuration.CLASS_NAME = JavaExecutionUtil.getRuntimeQualifiedName(elementClass);
        configuration.setGeneratedName();
        return true;
      }
      elementClass = getParentOfType(elementClass, PsiClass.class);
    }
    return false;
  }

  private boolean setupMethodConfiguration(AndroidTestRunConfiguration configuration,
                                                  PsiElement element,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    PsiMethod elementMethod = AndroidPsiUtils.getPsiParentOfType(element, PsiMethod.class, false);

    while (elementMethod != null) {
      if (isTestMethod(elementMethod)) {
        PsiClass c = elementMethod.getContainingClass();
        setupConfiguration(configuration, elementMethod, context, sourceElement);
        assert c != null;
        configuration.TESTING_TYPE = AndroidTestRunConfiguration.TEST_METHOD;
        configuration.CLASS_NAME = JavaExecutionUtil.getRuntimeQualifiedName(c);
        configuration.METHOD_NAME = elementMethod.getName();
        configuration.setGeneratedName();
        return true;
      }
      elementMethod = getParentOfType(elementMethod, PsiMethod.class);
    }
    return false;
  }

  private void setupConfiguration(AndroidTestRunConfiguration configuration,
                                  PsiElement element,
                                  ConfigurationContext context,
                                  Ref<PsiElement> sourceElement) {
    final Module module = AndroidUtils.getAndroidModule(context);

    if (module == null) {
      return;
    }
    sourceElement.set(element);
    setupConfigurationModule(context, configuration);

    final TargetSelectionMode targetSelectionMode = AndroidUtils
      .getDefaultTargetSelectionMode(module, AndroidTestRunConfigurationType.getInstance(), AndroidRunConfigurationType.getInstance());

    if (targetSelectionMode != null) {
      configuration.getDeployTargetContext().setTargetSelectionMode(targetSelectionMode);
    }
  }

  private static boolean isTestMethod(PsiMethod method) {
    PsiClass testClass = method.getContainingClass();
    if (testClass != null && JUnitUtil.isTestClass(testClass)) {
      return new JUnitUtil.TestMethodFilter(testClass).value(method);
    }
    return false;
  }

  @Override
  protected boolean setupConfigurationFromContext(AndroidTestRunConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    Module module = AndroidUtils.getAndroidModule(context);
    if (module == null) {
      return false;
    }

    Location location = context.getLocation();

    if (location == null) {
      return false;
    }
    location = JavaExecutionUtil.stepIntoSingleClass(location);

    if (location == null) {
      return false;
    }
    final PsiElement element = location.getPsiElement();

    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return false;
    }

    // TODO: Resolve direct AndroidGradleModel dep (b/22596984)
    AndroidModuleModel androidModel = AndroidModuleModel.get(facet);

    if (androidModel != null) {
      // Only suggest the android test run configuration if it makes sense for the selected test artifact.
      if (facet.getConfiguration().getProjectType() != PROJECT_TYPE_TEST && androidModel.getSelectedVariant().getAndroidTestArtifact() == null) {
        return false;
      }

      TestArtifactSearchScopes testScopes = TestArtifactSearchScopes.get(module);
      if (testScopes == null) {
        return false;
      }
      VirtualFile virtualFile = null;
      if (element instanceof PsiDirectory) {
        virtualFile = ((PsiDirectory)element).getVirtualFile();
      } else {
        PsiFile psiFile = element.getContainingFile();
        if (psiFile != null) {
          virtualFile = psiFile.getVirtualFile();
        }
      }
      if (virtualFile == null) {
        return false;
      }
      if (!testScopes.isAndroidTestSource(virtualFile)) {
        return false;
      }
    }

    setupInstrumentationTestRunner(configuration, facet);
    if (setupAllInPackageConfiguration(configuration, element, context, sourceElement)) {
      return true;
    }
    if (setupMethodConfiguration(configuration, element, context, sourceElement)) {
      return true;
    }
    return setupClassConfiguration(configuration, element, context, sourceElement);
  }

  private static void setupInstrumentationTestRunner(@NotNull AndroidTestRunConfiguration configuration, @NotNull AndroidFacet facet) {
    if (!GradleProjectInfo.getInstance(configuration.getProject()).isBuildWithGradle()) {
      configuration.INSTRUMENTATION_RUNNER_CLASS = StringUtil.notNullize(AndroidTestRunConfiguration.findInstrumentationRunner(facet));
    }
  }

  @Override
  public boolean isConfigurationFromContext(AndroidTestRunConfiguration configuration, ConfigurationContext context) {
    Location location = context.getLocation();
    Module contextModule = AndroidUtils.getAndroidModule(context);

    if (contextModule == null || location == null) {
      return false;
    }
    location = JavaExecutionUtil.stepIntoSingleClass(location);

    if (location == null) {
      return false;
    }

    PsiElement element = location.getPsiElement();
    VirtualFile directoryOrFile = element instanceof PsiDirectory ? ((PsiDirectory)element).getVirtualFile()
                                                       : element.getContainingFile().getVirtualFile();
    TestArtifactSearchScopes testScopes = TestArtifactSearchScopes.get(contextModule);
    if (directoryOrFile != null && testScopes != null && !testScopes.isAndroidTestSource(directoryOrFile)) {
      return false;
    }

    PsiPackage psiPackage = JavaRuntimeConfigurationProducerBase.checkPackage(element);
    String packageName = psiPackage == null ? null : psiPackage.getQualifiedName();

    PsiClass elementClass = AndroidPsiUtils.getPsiParentOfType(element, PsiClass.class, false);
    String className = elementClass == null ? null : elementClass.getQualifiedName();

    PsiMethod elementMethod = AndroidPsiUtils.getPsiParentOfType(element, PsiMethod.class, false);
    String methodName = elementMethod == null ? null : elementMethod.getName();
    Module moduleInConfig = configuration.getConfigurationModule().getModule();

    if (!Comparing.equal(contextModule, moduleInConfig)) {
      return false;
    }
    switch (configuration.TESTING_TYPE) {
      case AndroidTestRunConfiguration.TEST_ALL_IN_MODULE:
        return psiPackage != null && packageName.isEmpty();

      case AndroidTestRunConfiguration.TEST_ALL_IN_PACKAGE:
        return packageName != null && packageName.equals(configuration.PACKAGE_NAME);

      case AndroidTestRunConfiguration.TEST_CLASS:
        return elementMethod == null && className != null && className.equals(configuration.CLASS_NAME);

      case AndroidTestRunConfiguration.TEST_METHOD:
        return methodName != null && methodName.equals(configuration.METHOD_NAME) &&
               className != null && className.equals(configuration.CLASS_NAME);
    }
    return false;
  }

  @Override
  public boolean shouldReplace(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    Project project = self.getConfiguration().getProject();
    if (!GradleProjectInfo.getInstance(project).isBuildWithGradle()) {
      return false;
    }
    // If we decided the context is for an instrumentation test (see {@link #setupConfigurationFromContext}), it should replace
    // other test configurations, as they won't work anyway. These are either JUnitConfiguration or AndroidJUnit configuration,
    // both produced by JUnitConfigurationType (AndroidJUnitConfigurationType is a child of JUnitConfigurationType)
    return other.getConfigurationType() instanceof JUnitConfigurationType;
  }
}
