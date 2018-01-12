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

import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.android.tools.idea.testartifacts.junit.AndroidJUnitConfiguration;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.MapDataContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.vfs.VfsUtilCore.findRelativeFile;
import static org.junit.Assert.assertNotNull;

/**
 * Collection of utility methods for testing {@link AndroidTestRunConfiguration}s, {@link AndroidJUnitConfiguration}s and their interactions
 */
public class TestConfigurationTesting {
  @Nullable
  public static AndroidTestRunConfiguration createAndroidTestConfigurationFromClass(@NotNull Project project, @NotNull String qualifiedName) {
    return createConfigurationFromClass(project, qualifiedName, AndroidTestRunConfiguration.class);
  }

  @Nullable
  public static AndroidJUnitConfiguration createJUnitConfigurationFromClass(@NotNull Project project, @NotNull String qualifiedName) {
    return createConfigurationFromClass(project, qualifiedName, AndroidJUnitConfiguration.class);
  }

  @Nullable
  public static AndroidJUnitConfiguration createJUnitConfigurationFromDirectory(@NotNull Project project, @NotNull String directory) {
    return createConfigurationFromDirectory(project, directory, AndroidJUnitConfiguration.class);
  }

  @Nullable
  public static AndroidTestRunConfiguration createAndroidTestConfigurationFromDirectory(@NotNull Project project, @NotNull String directory) {
    return createConfigurationFromDirectory(project, directory, AndroidTestRunConfiguration.class);
  }

  @Nullable
  public static AndroidJUnitConfiguration createJUnitConfigurationFromFile(@NotNull Project project, @NotNull String file) {
    return createConfigurationFromFile(project, file, AndroidJUnitConfiguration.class);
  }

  @Nullable
  public static AndroidTestRunConfiguration createAndroidTestConfigurationFromFile(@NotNull Project project, @NotNull String file) {
    return createConfigurationFromFile(project, file, AndroidTestRunConfiguration.class);
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
    VirtualFile virtualFile = findRelativeFile(directory, project.getBaseDir());
    assertNotNull(virtualFile);
    PsiElement element = PsiManager.getInstance(project).findDirectory(virtualFile);
    assertNotNull(element);
    RunConfiguration runConfiguration = createConfigurationFromPsiElement(project, element);
    return expectedType.isInstance(runConfiguration) ? expectedType.cast(runConfiguration) : null;
  }

  @Nullable
  private static <T extends RunConfiguration> T createConfigurationFromFile(@NotNull Project project,
                                                                            @NotNull String file,
                                                                            @NotNull Class<T> expectedType) {
    VirtualFile virtualFile = findRelativeFile(file, project.getBaseDir());
    assertNotNull(virtualFile);
    PsiElement element = PsiManager.getInstance(project).findFile(virtualFile);
    assertNotNull(element);
    RunConfiguration runConfiguration = createConfigurationFromPsiElement(project, element);
    return expectedType.isInstance(runConfiguration) ? expectedType.cast(runConfiguration) : null;
  }

  @Nullable
  private static RunConfiguration createConfigurationFromPsiElement(@NotNull Project project, @NotNull PsiElement psiElement) {
    ConfigurationContext context = createContext(project, psiElement);
    RunnerAndConfigurationSettings settings = context.getConfiguration();
    if (settings == null) {
      return null;
    }
    RunConfiguration configuration = settings.getConfiguration();
    if (configuration instanceof AndroidTestRunConfiguration || configuration instanceof AndroidJUnitConfiguration) {
      return configuration;
    }
    return null;
  }

  @NotNull
  public static ConfigurationContext createContext(@NotNull Project project, @NotNull PsiElement psiElement) {
    MapDataContext dataContext = new MapDataContext();
    dataContext.put(CommonDataKeys.PROJECT, project);
    if (LangDataKeys.MODULE.getData(dataContext) == null) {
      dataContext.put(LangDataKeys.MODULE, ModuleUtilCore.findModuleForPsiElement(psiElement));
    }
    dataContext.put(Location.DATA_KEY, PsiLocation.fromPsiElement(psiElement));
    return ConfigurationContext.getFromContext(dataContext);
  }
}
