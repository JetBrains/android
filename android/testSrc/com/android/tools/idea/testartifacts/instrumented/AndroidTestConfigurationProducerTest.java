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

import com.android.tools.idea.run.editor.AndroidRunConfigurationEditor;
import com.android.tools.idea.run.editor.TestRunParameters;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestConfigurationProducer;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.MapDataContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static com.android.tools.idea.testing.TestProjectPaths.RUN_CONFIG_RUNNER_ARGUMENTS;
import static com.intellij.openapi.vfs.VfsUtilCore.findRelativeFile;

/**
 * Test for {@link AndroidTestConfigurationProducer}
 */
public class AndroidTestConfigurationProducerTest extends AndroidGradleTestCase {

  public void testCanCreateConfigurationFromFromAndroidTestClass() throws Exception {
    loadSimpleApplication();
    if (SystemInfo.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    assertNotNull(createConfigurationFromClass("google.simpleapplication.ApplicationTest"));
  }

  // Test skipped because a memory leak when calling AndroidRunConfigurationEditor<AndroidTestRunConfiguration>.getConfigurationEditor()
  public void /*test*/RunnerComponentsHiddenWhenGradleProject() throws Exception {
    loadSimpleApplication();
    if (SystemInfo.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    AndroidTestRunConfiguration androidTestRunConfiguration = createConfigurationFromClass("google.simpleapplication.ApplicationTest");
    assertNotNull(androidTestRunConfiguration);
    AndroidRunConfigurationEditor<AndroidTestRunConfiguration> editor =
      (AndroidRunConfigurationEditor<AndroidTestRunConfiguration>)androidTestRunConfiguration.getConfigurationEditor();

    TestRunParameters testRunParameters = (TestRunParameters)editor.getConfigurationSpecificEditor();
    testRunParameters.resetFrom(androidTestRunConfiguration);
    assertFalse("Runner component is visible in a Gradle project", testRunParameters.getRunnerComponent().isVisible());
  }

  public void testRunnerArgumentsSet() throws Exception {
    loadProject(RUN_CONFIG_RUNNER_ARGUMENTS);
    Map<String, String> expectedArguments = new HashMap<>();
    expectedArguments.put("size", "medium");
    expectedArguments.put("foo", "bar");

    Map<String, String> runnerArguments = AndroidTestRunConfiguration.getRunnerArguments(myAndroidFacet);
    assertEquals(expectedArguments, runnerArguments);
  }

  public void testCannotCreateConfigurationFromFromUnitTestClass() throws Exception {
    loadSimpleApplication();
    assertNull(createConfigurationFromClass("google.simpleapplication.UnitTest"));
  }

  public void testCanCreateConfigurationFromFromAndroidTestDirectory() throws Exception {
    loadSimpleApplication();
    if (SystemInfo.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    assertNotNull(createConfigurationFromDirectory("app/src/androidTest/java"));
  }

  public void testCannotCreateConfigurationFromFromUnitTestDirectory() throws Exception {
    loadSimpleApplication();
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
