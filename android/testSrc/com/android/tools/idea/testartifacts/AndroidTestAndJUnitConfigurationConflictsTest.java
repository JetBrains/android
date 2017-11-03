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
package com.android.tools.idea.testartifacts;

import com.android.tools.idea.testartifacts.instrumented.AndroidTestConsoleProperties;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.android.tools.idea.testartifacts.junit.AndroidJUnitConfiguration;
import com.android.tools.idea.testartifacts.junit.AndroidJUnitConfigurationType;
import com.android.tools.idea.testartifacts.junit.AndroidTestPackage;
import com.android.tools.idea.testartifacts.scopes.TestArtifactSearchScopes;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.execution.CantRunException;
import com.intellij.execution.ConfigurationUtil;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromDirectory;
import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createJUnitConfigurationFromDirectory;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_SAME_NAME_CLASSES;

/**
 * Tests for eventual conflicts between {@link AndroidTestRunConfiguration} and {@link AndroidJUnitConfiguration}
 */
public class AndroidTestAndJUnitConfigurationConflictsTest extends AndroidGradleTestCase {
  // See http://b.android.com/215255
  public void testConfigurationsAreDifferent() throws Exception {
    loadSimpleApplication();
    if (SystemInfo.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    RunConfiguration androidTestRunConfiguration = createAndroidTestConfigurationFromDirectory(getProject(), "app/src/androidTest/java");
    RunConfiguration jUnitConfiguration = createJUnitConfigurationFromDirectory(getProject(), "app/src/test/java");

    assertNotNull(jUnitConfiguration);
    assertNotNull(androidTestRunConfiguration);

    assertNotSame(androidTestRunConfiguration, jUnitConfiguration);
  }

  public void testDoubleClickRedirection() throws Exception {
    String commonTestClassName = "google.testartifacts.ExampleTest";
    loadProject(TEST_ARTIFACTS_SAME_NAME_CLASSES);

    Executor executor = DefaultRunExecutor.getRunExecutorInstance();

    RunConfiguration jUnitConfiguration = createJUnitConfigurationFromDirectory(getProject(), "app/src/test/java");
    RunConfiguration androidTestRunConfiguration = createAndroidTestConfigurationFromDirectory(getProject(), "app/src/androidTest/java");

    assertNotNull(jUnitConfiguration);
    assertNotNull(androidTestRunConfiguration);

    SMTRunnerConsoleProperties jUnitProperties = ((AndroidJUnitConfiguration)jUnitConfiguration).createTestConsoleProperties(executor);
    SMTRunnerConsoleProperties androidTestProperties = new AndroidTestConsoleProperties(androidTestRunConfiguration, executor);

    PsiClass[] jUnitClasses = JavaPsiFacade.getInstance(getProject()).findClasses(commonTestClassName, jUnitProperties.getScope());
    PsiClass[] aTestClasses = JavaPsiFacade.getInstance(getProject()).findClasses(commonTestClassName, androidTestProperties.getScope());

    assertSize(1, jUnitClasses);
    assertSize(1, aTestClasses);
    assertNotSame(jUnitClasses[0], aTestClasses[0]);
  }

  private void checkClassesInAllInPackage(TestSearchScope type) throws CantRunException {
    Module module = ModuleManager.getInstance(myFixture.getProject()).findModuleByName("app");
    assertNotNull(module);

    AndroidJUnitConfiguration configuration = createConfiguration(getProject(), "google.simpleapplication", module);
    configuration.getPersistentData().setScope(type);

    AndroidTestPackage testPackage = new AndroidTestPackage(configuration, ExecutionEnvironmentBuilder.create(
      DefaultRunExecutor.getRunExecutorInstance(), configuration).build());
    Set<PsiClass> myClasses = new HashSet<>();
    ConfigurationUtil.findAllTestClasses(testPackage.getClassFilter(configuration.getPersistentData()), module, myClasses);

    assertSize(1, myClasses);
    TestArtifactSearchScopes scopes = TestArtifactSearchScopes.get(module);
    assertNotNull(scopes);
    assertTrue(scopes.isUnitTestSource(myClasses.iterator().next().getContainingFile().getVirtualFile()));
  }

  public void testCorrectJUnitConfigurationAllInPackageModule() throws Exception {
    loadSimpleApplication();
    checkClassesInAllInPackage(TestSearchScope.SINGLE_MODULE);
  }

  public void testCorrectJUnitConfigurationAllInPackageProject() throws Exception {
    loadSimpleApplication();
    checkClassesInAllInPackage(TestSearchScope.WHOLE_PROJECT);
  }

  private static AndroidJUnitConfiguration createConfiguration(@NotNull Project project,
                                                               @NotNull String packageQualifiedName,
                                                               @NotNull Module module) {
    AndroidJUnitConfiguration configuration =
      new AndroidJUnitConfiguration("", project, AndroidJUnitConfigurationType.getInstance().getConfigurationFactories()[0]);
    configuration.getPersistentData().TEST_OBJECT = JUnitConfiguration.TEST_PACKAGE;
    configuration.getPersistentData().PACKAGE_NAME = packageQualifiedName;
    configuration.getPersistentData().setScope(TestSearchScope.WHOLE_PROJECT);
    configuration.setModule(module);
    return configuration;
  }
}
