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

import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromClass;
import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromDirectory;
import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromFile;
import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromMethod;
import static com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration.TEST_CLASS;
import static com.android.tools.idea.testing.AndroidGradleProjectRuleKt.onEdt;
import static com.android.tools.idea.testing.TestProjectPaths.ANDROID_KOTLIN_MULTIPLATFORM;
import static com.android.tools.idea.testing.TestProjectPaths.DYNAMIC_APP;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_KOTLIN;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_KOTLIN_MULTIPLATFORM;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_ONLY_MODULE;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.testartifacts.TestConfigurationTestingUtil;
import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.android.tools.idea.testing.EdtAndroidGradleProjectRule;
import com.google.common.io.Files;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.RunsInEdt;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test for {@link AndroidTestConfigurationProducer}
 */
@RunsInEdt
public class AndroidTestConfigurationProducerTest {
  public AndroidGradleProjectRule projectRule = new AndroidGradleProjectRule();
  @Rule
  public EdtAndroidGradleProjectRule rule = onEdt(projectRule);

  @Before
  public void assumeNotWindows() {
    Assume.assumeFalse(SystemInfo.isWindows);
  }

  @Test
  public void testCanCreateAndroidTestConfigurationFromAndroidTestClass() {
    projectRule.loadProject(SIMPLE_APPLICATION);
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromClass(
      projectRule.getProject(), "google.simpleapplication.ApplicationTest");
    assertThat(runConfig).isNotNull();
    assertThat(runConfig.checkConfiguration(projectRule.androidTestAndroidFacet(":app"))).isEmpty();
    assertThat(runConfig.CLASS_NAME).isEqualTo("google.simpleapplication.ApplicationTest");
    assertThat(runConfig.TESTING_TYPE).isEqualTo(TEST_CLASS);
  }

  @Test
  public void testCannotCreateAndroidTestConfigurationFromJUnitTestClass() {
    projectRule.loadProject(SIMPLE_APPLICATION);
    assertThat(createAndroidTestConfigurationFromClass(projectRule.getProject(), "google.simpleapplication.UnitTest")).isNull();
  }

  @Test
  public void testCanCreateAndroidTestConfigurationFromAndroidTestSubDirectory() {
    projectRule.loadProject(SIMPLE_APPLICATION);
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromDirectory(
      projectRule.getProject(), "app/src/androidTest/java");
    assertThat(runConfig).isNotNull();
    assertThat(runConfig.checkConfiguration(projectRule.androidTestAndroidFacet(":app"))).isEmpty();
  }

  @Test
  public void testCanCreateAndroidTestConfigurationFromAndroidTestDirectory() {
    projectRule.loadProject(SIMPLE_APPLICATION);
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromDirectory(projectRule.getProject(), "app/src/androidTest");
    assertThat(runConfig).isNotNull();
    assertThat(runConfig.checkConfiguration(projectRule.androidTestAndroidFacet(":app"))).isEmpty();
  }

  @Test
  public void testCannotCreateAndroidTestConfigurationFromJUnitTestDirectory() {
    projectRule.loadProject(SIMPLE_APPLICATION);
    assertThat(createAndroidTestConfigurationFromDirectory(projectRule.getProject(), "app/src/test/java")).isNull();
  }

  @Test
  public void testConfigIsNotCreatedFromJUnitTestClassKotlin() {
    projectRule.loadProject(TEST_ARTIFACTS_KOTLIN);
    assertThat(createAndroidTestConfigurationFromClass(
      projectRule.getProject(), "com.example.android.kotlin.ExampleUnitTest")).isNull();
  }

  @Test
  public void testConfigIsNotCreatedFromJUnitTestFileKotlin() {
    projectRule.loadProject(TEST_ARTIFACTS_KOTLIN);
    assertThat(createAndroidTestConfigurationFromFile(
      projectRule.getProject(), "app/src/test/java/com/example/android/kotlin/ExampleUnitTest.kt")).isNull();
  }

  @Test
  public void testConfigIsNotCreatedFromJUnitTestDirectoryKotlin() {
    projectRule.loadProject(TEST_ARTIFACTS_KOTLIN);
    assertThat(createAndroidTestConfigurationFromDirectory(projectRule.getProject(), "app/src/test/java")).isNull();
  }

  @Test
  public void testMethodTestIsCreatedKotlin() {
    projectRule.loadProject(TEST_ARTIFACTS_KOTLIN);
    AndroidTestRunConfiguration androidRunConfig = createAndroidTestConfigurationFromMethod(
      projectRule.getProject(), "com.example.android.kotlin.ExampleInstrumentedTest", "useAppContext");
    assertThat(androidRunConfig).isNotNull();
    assertThat(androidRunConfig.checkConfiguration(projectRule.androidTestAndroidFacet(":app"))).isEmpty();
    assertThat(androidRunConfig.TESTING_TYPE).isEqualTo(AndroidTestRunConfiguration.TEST_METHOD);
    assertThat(androidRunConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty();
    assertThat(androidRunConfig.PACKAGE_NAME).isEmpty();
    assertThat(androidRunConfig.CLASS_NAME).isEqualTo("com.example.android.kotlin.ExampleInstrumentedTest");
    assertThat(androidRunConfig.METHOD_NAME).isEqualTo("useAppContext");
  }

  @Test
  public void testClassTestIsCreatedKotlin() {
    projectRule.loadProject(TEST_ARTIFACTS_KOTLIN);
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromClass(
      projectRule.getProject(), "com.example.android.kotlin.ExampleInstrumentedTest");
    assertThat(runConfig).isNotNull();
    assertThat(runConfig.checkConfiguration(projectRule.androidTestAndroidFacet(":app"))).isEmpty();
    assertThat(runConfig.TESTING_TYPE).isEqualTo(AndroidTestRunConfiguration.TEST_CLASS);
    assertThat(runConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty();
    assertThat(runConfig.PACKAGE_NAME).isEmpty();
    assertThat(runConfig.CLASS_NAME).isEqualTo("com.example.android.kotlin.ExampleInstrumentedTest");
    assertThat(runConfig.METHOD_NAME).isEmpty();
  }

  @Test
  public void testAllInPackageTestIsCreatedKotlin() {
    projectRule.loadProject(TEST_ARTIFACTS_KOTLIN);
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromDirectory(
      projectRule.getProject(), "app/src/androidTest/java/com/example/android/kotlin");
    assertThat(runConfig).isNotNull();
    assertThat(runConfig.checkConfiguration(projectRule.androidTestAndroidFacet(":app"))).isEmpty();
    assertThat(runConfig.TESTING_TYPE).isEqualTo(AndroidTestRunConfiguration.TEST_ALL_IN_PACKAGE);
    assertThat(runConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty();
    assertThat(runConfig.PACKAGE_NAME).isEqualTo("com.example.android.kotlin");
    assertThat(runConfig.CLASS_NAME).isEmpty();
    assertThat(runConfig.METHOD_NAME).isEmpty();
  }

  @Test
  public void testAllInModuleTestIsCreatedKotlin() {
    projectRule.loadProject(TEST_ARTIFACTS_KOTLIN);
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromDirectory(
      projectRule.getProject(), "app/src/androidTest/java");
    assertThat(runConfig).isNotNull();
    assertThat(runConfig.checkConfiguration(projectRule.androidTestAndroidFacet(":app"))).isEmpty();
    assertThat(runConfig.TESTING_TYPE).isEqualTo(AndroidTestRunConfiguration.TEST_ALL_IN_MODULE);
    assertThat(runConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty();
    assertThat(runConfig.PACKAGE_NAME).isEmpty();
    assertThat(runConfig.CLASS_NAME).isEmpty();
    assertThat(runConfig.METHOD_NAME).isEmpty();
  }

  @Test
  public void testAllInDirectoryTestIsCreatedKotlin() {
    projectRule.loadProject(TEST_ARTIFACTS_KOTLIN);
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromDirectory(
      projectRule.getProject(), "app/src/androidTest");
    assertThat(runConfig).isNotNull();
    assertThat(runConfig.checkConfiguration(projectRule.androidTestAndroidFacet(":app"))).isEmpty();
    assertThat(runConfig.TESTING_TYPE).isEqualTo(AndroidTestRunConfiguration.TEST_ALL_IN_MODULE);
    assertThat(runConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty();
    assertThat(runConfig.PACKAGE_NAME).isEmpty();
    assertThat(runConfig.CLASS_NAME).isEmpty();
    assertThat(runConfig.METHOD_NAME).isEmpty();
    assertThat(runConfig.TEST_NAME_REGEX).isEmpty();
    assertThat(runConfig.suggestedName()).isEqualTo("All Tests");
  }

  @Test
  public void testSingleParameterizedTestIsCreatedKotlin() {
    projectRule.loadProject(TEST_ARTIFACTS_KOTLIN);
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromMethod(
      projectRule.getProject(), "com.example.android.kotlin.ParameterizedTest", "exampleParameterizedTest");
    assertThat(runConfig).isNotNull();
    assertThat(runConfig.checkConfiguration(projectRule.androidTestAndroidFacet(":app"))).isEmpty();
    assertThat(runConfig.TESTING_TYPE).isEqualTo(AndroidTestRunConfiguration.TEST_ALL_IN_MODULE);
    assertThat(runConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty();
    assertThat(runConfig.PACKAGE_NAME).isEmpty();
    assertThat(runConfig.CLASS_NAME).isEqualTo("com.example.android.kotlin.ParameterizedTest");
    assertThat(runConfig.METHOD_NAME).isEqualTo("exampleParameterizedTest");
    assertThat(runConfig.TEST_NAME_REGEX).isEqualTo("com.example.android.kotlin.ParameterizedTest.exampleParameterizedTest\\[.*\\]");
    assertThat(runConfig.suggestedName()).isEqualTo("exampleParameterizedTest()");
  }

  @Test
  public void testCanCreateAndroidTestConfigurationFromFromTestOnlyModule() {
    projectRule.loadProject(TEST_ONLY_MODULE);
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromClass(
      projectRule.getProject(), "com.example.android.app.ExampleTest");
    assertThat(runConfig).isNotNull();
    assertThat(runConfig.checkConfiguration(projectRule.mainAndroidFacet(":test"))).isEmpty();
    assertThat(runConfig.CLASS_NAME).isEqualTo("com.example.android.app.ExampleTest");
    assertThat(runConfig.TESTING_TYPE).isEqualTo(TEST_CLASS);
  }

  @Test
  public void testCanCreateAndroidTestConfigurationFromFromDynamicFeatureModule() {
    projectRule.loadProject(DYNAMIC_APP);
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromClass(
      projectRule.getProject(), "com.example.feature1.ExampleInstrumentedTest");
    assertThat(runConfig).isNotNull();
    assertThat(runConfig.checkConfiguration(projectRule.mainAndroidFacet(":feature1"))).isEmpty();
    assertThat(runConfig.CLASS_NAME).isEqualTo("com.example.feature1.ExampleInstrumentedTest");
    assertThat(runConfig.TESTING_TYPE).isEqualTo(TEST_CLASS);
  }

  @Test
  public void testCanCreateAndroidTestConfigurationWhenOriginalConfigExists() {
    projectRule.loadProject(TEST_ARTIFACTS_KOTLIN);

    SimpleDataContext.Builder dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, projectRule.getProject());

    PsiElement element = JavaPsiFacade.getInstance(projectRule.getProject()).findClass(
      "com.example.android.kotlin.ExampleInstrumentedTest",
      GlobalSearchScope.projectScope(projectRule.getProject()));
    assertThat(element).isNotNull();
    dataContext.add(Location.DATA_KEY, PsiLocation.fromPsiElement(element));

    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    dataContext.add(PlatformCoreDataKeys.MODULE, module);

    // This test really is concerned with the case where the context data has an original
    // configuration with module information in it, to simulate the SMTRunnerConsole context
    // when right-clicking on a test result.
    AndroidTestRunConfiguration original =
      new AndroidTestRunConfiguration(projectRule.getProject(), AndroidTestRunConfigurationType.getInstance().getFactory());
    original.setModule(module);
    dataContext.add(RunConfiguration.DATA_KEY, original);

    ConfigurationContext context =
      ConfigurationContext.getFromContext(dataContext.build(), ActionPlaces.UNKNOWN);
    assertThat(context.getOriginalConfiguration(AndroidTestRunConfigurationType.getInstance())).isNotNull();

    AndroidTestConfigurationProducer producer = new AndroidTestConfigurationProducer();
    ConfigurationFromContext runConfig = producer.createConfigurationFromContext(context);
    assertThat(runConfig).isNotNull();
  }

  @Test
  public void testRuntimeQualifiedNameIsUsed() throws Exception {
    projectRule.loadProject(SIMPLE_APPLICATION);

    File projectDir = VfsUtilCore.virtualToIoFile(PlatformTestUtil.getOrCreateProjectBaseDir(projectRule.getProject()));
    File newTestFile = new File(projectDir, "app/src/androidTest/java/google/simpleapplication/SomeTest.java");
    Files.createParentDirs(newTestFile);
    Files.asCharSink(newTestFile, StandardCharsets.UTF_8).write(
      "package google.simpleapplication;\n" +
      "\n" +
      "import org.junit.Test;\n" +
      "import org.junit.runner.RunWith;\n" +
      "\n" +
      "public class SomeTest {\n" +
      "  @RunWith(AndroidJUnit4.class)\n" +
      "  public static class InnerClassTest {\n" +
      "    @Test\n" +
      "    public exampleTest() {\n" +
      "    }\n" +
      "    \n" +
      "  }\n" +
      "}");

    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(newTestFile);
    IndexingTestUtil.waitUntilIndexesAreReady(projectRule.getProject());
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromClass(
      projectRule.getProject(), "google.simpleapplication.SomeTest.InnerClassTest");
    assertThat(runConfig.CLASS_NAME).isEqualTo("google.simpleapplication.SomeTest$InnerClassTest");
  }

  @Test
  public void testCreateAndroidInstrumentedTestKotlinMultiplatformFromSubDirectory() {
    projectRule.loadProject(TEST_ARTIFACTS_KOTLIN_MULTIPLATFORM);
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromDirectory(
      projectRule.getProject(), "module2/src/androidInstrumentedTest/kotlin");
    assertThat(runConfig).isNotNull();
    assertThat(runConfig.checkConfiguration(projectRule.androidTestAndroidFacet(":module2"))).isEmpty();
    assertThat(runConfig.TESTING_TYPE).isEqualTo(AndroidTestRunConfiguration.TEST_ALL_IN_MODULE);
    assertThat(runConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty();
    assertThat(runConfig.PACKAGE_NAME).isEmpty();
    assertThat(runConfig.CLASS_NAME).isEmpty();
    assertThat(runConfig.METHOD_NAME).isEmpty();
  }

  @Test
  public void testCreateAndroidInstrumentedTestKotlinMultiplatformFromDirectory() {
    projectRule.loadProject(TEST_ARTIFACTS_KOTLIN_MULTIPLATFORM);
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromDirectory(
      projectRule.getProject(), "module2/src/androidInstrumentedTest");
    assertThat(runConfig).isNotNull();
    assertThat(runConfig.checkConfiguration(projectRule.androidTestAndroidFacet(":module2"))).isEmpty();
    assertThat(runConfig.TESTING_TYPE).isEqualTo(AndroidTestRunConfiguration.TEST_ALL_IN_MODULE);
    assertThat(runConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty();
    assertThat(runConfig.PACKAGE_NAME).isEmpty();
    assertThat(runConfig.CLASS_NAME).isEmpty();
    assertThat(runConfig.METHOD_NAME).isEmpty();
  }

  @Test
  public void testCreateAndroidInstrumentedTestKotlinMultiplatformFromClass() {
    projectRule.loadProject(TEST_ARTIFACTS_KOTLIN_MULTIPLATFORM);
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromClass(
      projectRule.getProject(), "ExampleInstrumentedTest");
    assertThat(runConfig).isNotNull();
    assertThat(runConfig.checkConfiguration(projectRule.androidTestAndroidFacet(":module2"))).isEmpty();
    assertThat(runConfig.TESTING_TYPE).isEqualTo(TEST_CLASS);
    assertThat(runConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty();
    assertThat(runConfig.PACKAGE_NAME).isEmpty();
    assertThat(runConfig.CLASS_NAME).isEqualTo("ExampleInstrumentedTest");
    assertThat(runConfig.METHOD_NAME).isEmpty();
  }

  @Test
  public void testCreateAndroidInstrumentedTestAndroidKotlinMultiplatformFromDirectory() {
    projectRule.loadProject(ANDROID_KOTLIN_MULTIPLATFORM);
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromDirectory(
      projectRule.getProject(), "kmpFirstLib/src/androidInstrumentedTest");
    assertThat(runConfig).isNotNull();
    assertThat(runConfig.checkConfiguration(projectRule.androidTestAndroidFacet(":kmpFirstLib"))).isEmpty();
    assertThat(runConfig.TESTING_TYPE).isEqualTo(AndroidTestRunConfiguration.TEST_ALL_IN_MODULE);
    assertThat(runConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty();
    assertThat(runConfig.PACKAGE_NAME).isEmpty();
    assertThat(runConfig.CLASS_NAME).isEmpty();
    assertThat(runConfig.METHOD_NAME).isEmpty();
  }

  @Test
  public void testCreateAndroidInstrumentedTestAndroidKotlinMultiplatformFromClass() {
    projectRule.loadProject(ANDROID_KOTLIN_MULTIPLATFORM);
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromClass(
      projectRule.getProject(), "com.example.kmpfirstlib.test.KmpAndroidFirstLibActivityTest");
    assertThat(runConfig).isNotNull();
    assertThat(runConfig.checkConfiguration(projectRule.androidTestAndroidFacet(":kmpFirstLib"))).isEmpty();
    assertThat(runConfig.TESTING_TYPE).isEqualTo(TEST_CLASS);
    assertThat(runConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty();
    assertThat(runConfig.PACKAGE_NAME).isEmpty();
    assertThat(runConfig.CLASS_NAME).isEqualTo("com.example.kmpfirstlib.test.KmpAndroidFirstLibActivityTest");
    assertThat(runConfig.METHOD_NAME).isEmpty();
  }

  @Test
  public void testCreateAndroidInstrumentedTestAndroidKotlinMultiplatformFromMethod() {
    projectRule.loadProject(ANDROID_KOTLIN_MULTIPLATFORM);
    AndroidTestRunConfiguration androidRunConfig = createAndroidTestConfigurationFromMethod(
      projectRule.getProject(), "com.example.kmpfirstlib.test.KmpAndroidFirstLibActivityTest", "testActivityThatPasses");
    assertThat(androidRunConfig).isNotNull();
    assertThat(androidRunConfig.checkConfiguration(projectRule.androidTestAndroidFacet(":kmpFirstLib"))).isEmpty();
    assertThat(androidRunConfig.TESTING_TYPE).isEqualTo(AndroidTestRunConfiguration.TEST_METHOD);
    assertThat(androidRunConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty();
    assertThat(androidRunConfig.PACKAGE_NAME).isEmpty();
    assertThat(androidRunConfig.CLASS_NAME).isEqualTo("com.example.kmpfirstlib.test.KmpAndroidFirstLibActivityTest");
    assertThat(androidRunConfig.METHOD_NAME).isEqualTo("testActivityThatPasses");
  }

  @Test
  public void testCreateAndroidAndGradleConfigurationsFromSrcDirectory() {
    projectRule.loadProject(SIMPLE_APPLICATION);
    @NotNull Project project = projectRule.getProject();
    PsiElement element = TestConfigurationTestingUtil.getPsiElement(project, "app/src", true);
    List<ConfigurationFromContext> runConfigs = TestConfigurationTestingUtil.createConfigurations(element);

    assertThat(runConfigs).isNotNull();
    assertThat(runConfigs).hasSize(2);
    List<RunConfiguration> configurations = runConfigs.stream().map(ConfigurationFromContext::getConfiguration).toList();

    // Check we have a AndroidRunConfiguration created from this context.
    AndroidTestRunConfiguration androidRunConfig =
      (AndroidTestRunConfiguration)configurations.stream().filter(it -> it instanceof AndroidTestRunConfiguration).findFirst().orElse(null);
    assertThat(androidRunConfig).isNotNull();
    assertThat(androidRunConfig.checkConfiguration(projectRule.androidTestAndroidFacet(":app"))).isEmpty();
    assertThat(androidRunConfig.TESTING_TYPE).isEqualTo(AndroidTestRunConfiguration.TEST_ALL_IN_MODULE);
    assertThat(androidRunConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty();
    assertThat(androidRunConfig.PACKAGE_NAME).isEmpty();
    assertThat(androidRunConfig.CLASS_NAME).isEmpty();
    assertThat(androidRunConfig.METHOD_NAME).isEmpty();
    assertThat(androidRunConfig.TEST_NAME_REGEX).isEmpty();

    // Check that we also have a unit test Run config created too.
    GradleRunConfiguration unitTestConfig =
      (GradleRunConfiguration)configurations.stream().filter(it -> it instanceof GradleRunConfiguration).findFirst().orElse(null);
    assertThat(unitTestConfig).isNotNull();
    assertThat(unitTestConfig.isRunAsTest()).isTrue();
  }
}
