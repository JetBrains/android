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

import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromDirectory;
import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createJUnitConfigurationFromDirectory;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_SAME_NAME_CLASSES;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.testartifacts.instrumented.AndroidTestConsoleProperties;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.android.tools.idea.testartifacts.junit.AndroidJUnitConfiguration;
import com.android.tools.idea.testartifacts.junit.AndroidJUnitConfigurationType;
import com.android.tools.idea.testartifacts.junit.AndroidTestPackage;
import com.android.tools.idea.projectsystem.TestArtifactSearchScopes;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.TestProjectPaths;
import com.intellij.execution.ConfigurationUtil;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for eventual conflicts between {@link AndroidTestRunConfiguration} and {@link AndroidJUnitConfiguration}
 */
//TODO(karimai): Migrate this test when both Instrumented tests and unit tests use GRADLE.
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

    AndroidJUnitConfiguration jUnitConfiguration = createJUnitConfigurationFromDirectory(getProject(), "app/src/test/java");
    RunConfiguration androidTestRunConfiguration = createAndroidTestConfigurationFromDirectory(getProject(), "app/src/androidTest/java");

    assertNotNull(jUnitConfiguration);
    assertNotNull(androidTestRunConfiguration);

    SMTRunnerConsoleProperties jUnitProperties = jUnitConfiguration.createTestConsoleProperties(executor);
    SMTRunnerConsoleProperties androidTestProperties = new AndroidTestConsoleProperties(androidTestRunConfiguration, executor);

    PsiClass[] jUnitClasses = JavaPsiFacade.getInstance(getProject()).findClasses(commonTestClassName, jUnitProperties.getScope());
    PsiClass[] aTestClasses = JavaPsiFacade.getInstance(getProject()).findClasses(commonTestClassName, androidTestProperties.getScope());

    assertSize(1, jUnitClasses);
    assertSize(1, aTestClasses);
    assertNotSame(jUnitClasses[0], aTestClasses[0]);
  }

  public void testCorrectJUnitConfigurationAllInPackageModule() throws Exception {
    loadSimpleApplication();
    checkClassesInAllInPackage(TestSearchScope.SINGLE_MODULE, "google.simpleapplication");
  }

  public void testCorrectJUnitConfigurationAllInPackageProject() throws Exception {
    loadSimpleApplication();
    checkClassesInAllInPackage(TestSearchScope.WHOLE_PROJECT, "google.simpleapplication");
  }

  public void testAllInProject() throws Exception {
    loadProject(TestProjectPaths.UNIT_TESTING);
    Set<PsiClass> testClasses = getClassesToTest(TestSearchScope.WHOLE_PROJECT, "", getModule("app"));
    assertThat(testClasses.stream().map(PsiClass::getQualifiedName).collect(Collectors.toSet())).containsExactly(
      "com.example.app.AppJavaUnitTest",
      "com.example.app.AppKotlinUnitTest",
      "com.example.javalib.JavaLibJavaTest",
      "com.example.javalib.JavaLibKotlinTest",
      "com.example.util_lib.UtilLibJavaTest",
      "com.example.util_lib.UtilLibKotlinTest"
    );

  }

  public void testAcrossModuleBoundaries() throws Exception {
    loadProject(TestProjectPaths.UNIT_TESTING);
    Set<PsiClass> testClasses = getClassesToTest(TestSearchScope.MODULE_WITH_DEPENDENCIES, "", getModule("app"));
    assertThat(testClasses.stream().map(PsiClass::getQualifiedName).collect(Collectors.toSet())).containsExactly(
      "com.example.app.AppJavaUnitTest",
      "com.example.app.AppKotlinUnitTest",
      "com.example.javalib.JavaLibJavaTest",
      "com.example.javalib.JavaLibKotlinTest",
      "com.example.util_lib.UtilLibJavaTest",
      "com.example.util_lib.UtilLibKotlinTest"
    );
  }

  private void checkClassesInAllInPackage(@NotNull TestSearchScope type,
                                          @SuppressWarnings("SameParameterValue") @NotNull String packageName) {
    Module module = getModule("app");
    Set<PsiClass> myClasses = getClassesToTest(type, packageName, module);

    assertSize(1, myClasses);
    TestArtifactSearchScopes scopes = TestArtifactSearchScopes.getInstance(module);
    assertNotNull(scopes);
    assertTrue(scopes.isUnitTestSource(myClasses.iterator().next().getContainingFile().getVirtualFile()));
  }

  @NotNull
  private Set<PsiClass> getClassesToTest(@NotNull TestSearchScope type, @NotNull String packageName, @NotNull Module module) {
    assertNotNull(module);

    AndroidJUnitConfiguration configuration = createAllInPackageRunConfiguration(module, packageName, type);
    AndroidTestPackage testPackage =
      new AndroidTestPackage(configuration,
                             ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), configuration).build());
    Set<PsiClass> myClasses = new HashSet<>();
    ConfigurationUtil.findAllTestClasses(testPackage.getClassFilter(configuration.getPersistentData()), module, myClasses);
    return myClasses;
  }

  @NotNull
  private AndroidJUnitConfiguration createAllInPackageRunConfiguration(Module module, String packageName, TestSearchScope type) {
    AndroidJUnitConfiguration configuration =
      new AndroidJUnitConfiguration(getProject(), AndroidJUnitConfigurationType.getInstance().getConfigurationFactories()[0]);
    configuration.getPersistentData().TEST_OBJECT = JUnitConfiguration.TEST_PACKAGE;
    configuration.getPersistentData().PACKAGE_NAME = packageName;
    configuration.getPersistentData().setScope(type);
    configuration.setModule(module);
    return configuration;
  }
}
