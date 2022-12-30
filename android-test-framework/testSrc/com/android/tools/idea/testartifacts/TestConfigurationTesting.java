/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.testartifacts;

import static com.intellij.openapi.vfs.VfsUtilCore.findRelativeFile;
import static org.junit.Assert.assertNotNull;

import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.truth.Truth;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.lang.jvm.JvmMethod;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Collection of utility methods for testing {@link AndroidTestRunConfiguration}s.
 */
public final class TestConfigurationTesting {
  @Nullable
  public static AndroidTestRunConfiguration createAndroidTestConfigurationFromMethod(
    @NotNull Project project, @NotNull String qualifiedName, @NotNull String methodName) {
    return createConfigurationFromMethod(project, qualifiedName, methodName, AndroidTestRunConfiguration.class);
  }

  @Nullable
  public static AndroidTestRunConfiguration createAndroidTestConfigurationFromClass(@NotNull Project project, @NotNull String qualifiedName) {
    return createConfigurationFromClass(project, qualifiedName, AndroidTestRunConfiguration.class);
  }

  @Nullable
  public static AndroidTestRunConfiguration createAndroidTestConfigurationFromDirectory(@NotNull Project project, @NotNull String directory) {
    return createConfigurationFromDirectory(project, directory, AndroidTestRunConfiguration.class);
  }

  @Nullable
  public static AndroidTestRunConfiguration createAndroidTestConfigurationFromFile(@NotNull Project project, @NotNull String file) {
    return createConfigurationFromFile(project, file, AndroidTestRunConfiguration.class);
  }

  @Nullable
  private static <T extends RunConfiguration> T createConfigurationFromMethod(@NotNull Project project,
                                                                              @NotNull String qualifiedName,
                                                                              @NotNull String methodName,
                                                                              @NotNull Class<T> expectedType) {
    PsiClass classElement = JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.projectScope(project));
    assertNotNull(classElement);
    JvmMethod[] elements = classElement.findMethodsByName(methodName);
    Truth.assertThat(elements).hasLength(1);
    RunConfiguration runConfiguration = createConfigurationFromPsiElement(project, elements[0].getSourceElement());
    return expectedType.isInstance(runConfiguration) ? expectedType.cast(runConfiguration) : null;
  }

  @Nullable
  private static <T extends RunConfiguration> T createConfigurationFromClass(@NotNull Project project,
                                                                             @NotNull String qualifiedName,
                                                                             @NotNull Class<T> expectedType) {
    PsiElement element = JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.projectScope(project));
    assertNotNull(element);
    RunConfiguration runConfiguration = createConfigurationFromPsiElement(project, element);
    return expectedType.isInstance(runConfiguration) ? expectedType.cast(runConfiguration) : null;
  }

  @Nullable
  private static <T extends RunConfiguration> T createConfigurationFromDirectory(@NotNull Project project,
                                                                                 @NotNull String directory,
                                                                                 @NotNull Class<T> expectedType) {
    PsiElement element = getPsiElement(project, directory, true);
    RunConfiguration runConfiguration = createConfigurationFromPsiElement(project, element);
    return expectedType.isInstance(runConfiguration) ? expectedType.cast(runConfiguration) : null;
  }

  @Nullable
  private static <T extends RunConfiguration> T createConfigurationFromFile(@NotNull Project project,
                                                                            @NotNull String file,
                                                                            @NotNull Class<T> expectedType) {
    PsiElement element = getPsiElement(project, file, false);
    RunConfiguration runConfiguration = createConfigurationFromPsiElement(project, element);
    return expectedType.isInstance(runConfiguration) ? expectedType.cast(runConfiguration) : null;
  }

  @NotNull
  @VisibleForTesting
  public static PsiElement getPsiElement(@NotNull Project project, @NotNull String file, boolean isDirectory) {
    VirtualFile virtualFile = findRelativeFile(file, PlatformTestUtil.getOrCreateProjectBaseDir(project));
    assertNotNull(virtualFile);
    PsiElement element = isDirectory ? PsiManager.getInstance(project).findDirectory(virtualFile)
                                     : PsiManager.getInstance(project).findFile(virtualFile);
    assertNotNull(element);
    return element;
  }

  @Nullable
  public static RunConfiguration createConfigurationFromPsiElement(@NotNull Project project, @NotNull PsiElement psiElement) {
    ConfigurationContext context = createContext(project, psiElement);
    RunnerAndConfigurationSettings settings = context.getConfiguration();
    if (settings == null) {
      return null;
    }
    // Save the run configuration in the project.
    RunManager runManager = RunManager.getInstance(project);
    runManager.addConfiguration(settings);

    RunConfiguration configuration = settings.getConfiguration();
    if (configuration instanceof AndroidTestRunConfiguration) {
      return configuration;
    }
    return null;
  }

  @NotNull
  public static ConfigurationContext createContext(@NotNull Project project, @NotNull PsiElement psiElement) {
    MapDataContext dataContext = new MapDataContext();
    dataContext.put(CommonDataKeys.PROJECT, project);
    if (PlatformCoreDataKeys.MODULE.getData(dataContext) == null) {
      dataContext.put(PlatformCoreDataKeys.MODULE, ModuleUtilCore.findModuleForPsiElement(psiElement));
    }
    dataContext.put(Location.DATA_KEY, PsiLocation.fromPsiElement(psiElement));
    return ConfigurationContext.getFromContext(dataContext, ActionPlaces.UNKNOWN);
  }
}
