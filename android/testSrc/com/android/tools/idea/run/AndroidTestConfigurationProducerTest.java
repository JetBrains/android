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
package com.android.tools.idea.run;

import com.android.tools.idea.run.testing.AndroidTestRunConfiguration;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.MapDataContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.vfs.VfsUtilCore.findRelativeFile;

// TODO The test failed in https://android-jenkins.corp.google.com/job/studio-master-dev-test/1265 but passed from IDEA
/**
 * Test for {@link com.android.tools.idea.run.testing.AndroidTestConfigurationProducer}
 */
public class AndroidTestConfigurationProducerTest extends AndroidGradleTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    loadProject("guiTests/SimpleApplication", false);
  }

  public void /*test*/CanCreateConfigurationFromFromAndroidTestClass() throws Exception {
    assertNotNull(createConfigurationFromClass("google.simpleapplication.ApplicationTest"));
  }

  public void /*test*/CannotCreateConfigurationFromFromUnitTestClass() throws Exception {
    assertNull(createConfigurationFromClass("google.simpleapplication.UnitTest"));
  }

  public void /*test*/CanCreateConfigurationFromFromAndroidTestDirectory() throws Exception {
    assertNotNull(createConfigurationFromDirectory("app/src/androidTest/java"));
  }

  public void /*test*/CannotCreateConfigurationFromFromUnitTestDirectory() throws Exception {
    assertNull(createConfigurationFromDirectory("app/src/test/java"));
  }

  @Nullable
  private AndroidTestRunConfiguration createConfigurationFromClass(@NotNull String qualifiedName) {
    PsiElement element = JavaPsiFacade.getInstance(getProject()).findClass(qualifiedName,
                                                                           GlobalSearchScope.projectScope(getProject()));
    assertNotNull(element);
    return createConfiguration(element);
  }

  @Nullable
  private AndroidTestRunConfiguration createConfigurationFromDirectory(@NotNull String directory) {
    VirtualFile virtualFile = findRelativeFile(directory, getProject().getBaseDir());
    assertNotNull(virtualFile);
    PsiElement element = PsiManager.getInstance(getProject()).findDirectory(virtualFile);
    assertNotNull(element);
    return createConfiguration(element);
  }

  @Nullable
  private AndroidTestRunConfiguration createConfiguration(@NotNull PsiElement psiElement) {
    ConfigurationContext context = createContext(psiElement);
    RunnerAndConfigurationSettings settings = context.getConfiguration();
    if (settings == null) {
      return null;
    }
    RunConfiguration configuration = settings.getConfiguration();
    if (configuration instanceof AndroidTestRunConfiguration) {
      return (AndroidTestRunConfiguration)configuration;
    }
    return null;
  }

  @NotNull
  private ConfigurationContext createContext(@NotNull PsiElement psiElement) {
    MapDataContext dataContext = new MapDataContext();
    dataContext.put(CommonDataKeys.PROJECT, getProject());
    if (LangDataKeys.MODULE.getData(dataContext) == null) {
      dataContext.put(LangDataKeys.MODULE, ModuleUtilCore.findModuleForPsiElement(psiElement));
    }
    dataContext.put(Location.DATA_KEY, PsiLocation.fromPsiElement(psiElement));
    return ConfigurationContext.getFromContext(dataContext);
  }
}
