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
import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createConfigurationFromPsiElement;
import static com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration.TEST_CLASS;
import static com.android.tools.idea.testing.TestProjectPaths.DYNAMIC_APP;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_KOTLIN;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_KOTLIN_MULTIPLATFORM;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_ONLY_MODULE;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.projectsystem.ModuleSystemUtil;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.io.Files;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.PlatformTestUtil;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.jetbrains.android.facet.AndroidFacet;

/**
 * Test for {@link AndroidTestConfigurationProducer}
 */
public class AndroidTestConfigurationProducerTest extends AndroidGradleTestCase {

  @Override
  protected boolean shouldRunTest() {
    // Do not run tests on Windows (see http://b.android.com/222904)
    return !SystemInfo.isWindows && super.shouldRunTest();
  }

  public void testCanCreateAndroidTestConfigurationFromAndroidTestClass() throws Exception {
    loadSimpleApplication();
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromClass(getProject(), "google.simpleapplication.ApplicationTest");
    assertNotNull(runConfig);
    assertEmpty(runConfig.checkConfiguration(myAndroidFacet));
    assertEquals(runConfig.CLASS_NAME, "google.simpleapplication.ApplicationTest");
    assertEquals(runConfig.TESTING_TYPE, TEST_CLASS);
  }

  public void testCannotCreateAndroidTestConfigurationFromJUnitTestClass() throws Exception {
    loadSimpleApplication();
    assertNull(createAndroidTestConfigurationFromClass(getProject(), "google.simpleapplication.UnitTest"));
  }

  public void testCanCreateAndroidTestConfigurationFromAndroidTestSubDirectory() throws Exception {
    loadSimpleApplication();
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromDirectory(getProject(), "app/src/androidTest/java");
    assertNotNull(runConfig);
    assertEmpty(runConfig.checkConfiguration(myAndroidFacet));
  }

  public void testCanCreateAndroidTestConfigurationFromAndroidTestDirectory() throws Exception {
    loadSimpleApplication();
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromDirectory(getProject(), "app/src/androidTest");
    assertNotNull(runConfig);
    assertEmpty(runConfig.checkConfiguration(myAndroidFacet));
  }

  public void testCannotCreateAndroidTestConfigurationFromJUnitTestDirectory() throws Exception {
    loadSimpleApplication();
    assertNull(createAndroidTestConfigurationFromDirectory(getProject(), "app/src/test/java"));
  }

  public void testConfigIsNotCreatedFromJUnitTestClassKotlin() throws Exception {
    loadProject(TEST_ARTIFACTS_KOTLIN);
    assertNull(createAndroidTestConfigurationFromClass(
      getProject(), "com.example.android.kotlin.ExampleUnitTest"));
  }

  public void testConfigIsNotCreatedFromJUnitTestFileKotlin() throws Exception {
    loadProject(TEST_ARTIFACTS_KOTLIN);
    assertNull(createAndroidTestConfigurationFromFile(
      getProject(), "app/src/test/java/com/example/android/kotlin/ExampleUnitTest.kt"));
  }

  public void testConfigIsNotCreatedFromJUnitTestDirectoryKotlin() throws Exception {
    loadProject(TEST_ARTIFACTS_KOTLIN);
    assertNull(createAndroidTestConfigurationFromDirectory(getProject(), "app/src/test/java"));
  }

  public void testMethodTestIsCreatedKotlin() throws Exception {
    loadProject(TEST_ARTIFACTS_KOTLIN);
    PsiMethod[] methods = myFixture.findClass("com.example.android.kotlin.ExampleInstrumentedTest")
      .findMethodsByName("useAppContext", false);
    assertThat(methods).hasLength(1);
    RunConfiguration runConfig = createConfigurationFromPsiElement(getProject(), methods[0]);
    assertThat(runConfig).isInstanceOf(AndroidTestRunConfiguration.class);
    AndroidTestRunConfiguration androidRunConfig = (AndroidTestRunConfiguration) runConfig;
    assertEmpty(androidRunConfig.checkConfiguration(myAndroidFacet));
    assertThat(androidRunConfig.TESTING_TYPE).isEqualTo(AndroidTestRunConfiguration.TEST_METHOD);
    assertThat(androidRunConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty();
    assertThat(androidRunConfig.PACKAGE_NAME).isEmpty();
    assertThat(androidRunConfig.CLASS_NAME).isEqualTo("com.example.android.kotlin.ExampleInstrumentedTest");
    assertThat(androidRunConfig.METHOD_NAME).isEqualTo("useAppContext");
  }

  public void testClassTestIsCreatedKotlin() throws Exception {
    loadProject(TEST_ARTIFACTS_KOTLIN);
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromClass(
      getProject(), "com.example.android.kotlin.ExampleInstrumentedTest");
    assertNotNull(runConfig);
    assertEmpty(runConfig.checkConfiguration(myAndroidFacet));
    assertThat(runConfig.TESTING_TYPE).isEqualTo(AndroidTestRunConfiguration.TEST_CLASS);
    assertThat(runConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty();
    assertThat(runConfig.PACKAGE_NAME).isEmpty();
    assertThat(runConfig.CLASS_NAME).isEqualTo("com.example.android.kotlin.ExampleInstrumentedTest");
    assertThat(runConfig.METHOD_NAME).isEmpty();
  }

  public void testAllInPackageTestIsCreatedKotlin() throws Exception {
    loadProject(TEST_ARTIFACTS_KOTLIN);
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromDirectory(
      getProject(), "app/src/androidTest/java/com/example/android/kotlin");
    assertNotNull(runConfig);
    assertEmpty(runConfig.checkConfiguration(myAndroidFacet));
    assertThat(runConfig.TESTING_TYPE).isEqualTo(AndroidTestRunConfiguration.TEST_ALL_IN_PACKAGE);
    assertThat(runConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty();
    assertThat(runConfig.PACKAGE_NAME).isEqualTo("com.example.android.kotlin");
    assertThat(runConfig.CLASS_NAME).isEmpty();
    assertThat(runConfig.METHOD_NAME).isEmpty();
  }

  public void testAllInModuleTestIsCreatedKotlin() throws Exception {
    loadProject(TEST_ARTIFACTS_KOTLIN);
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromDirectory(
      getProject(), "app/src/androidTest/java");
    assertNotNull(runConfig);
    assertEmpty(runConfig.checkConfiguration(myAndroidFacet));
    assertThat(runConfig.TESTING_TYPE).isEqualTo(AndroidTestRunConfiguration.TEST_ALL_IN_MODULE);
    assertThat(runConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty();
    assertThat(runConfig.PACKAGE_NAME).isEmpty();
    assertThat(runConfig.CLASS_NAME).isEmpty();
    assertThat(runConfig.METHOD_NAME).isEmpty();
  }

  public void testAllInDirectoryTestIsCreatedKotlin() throws Exception {
    loadProject(TEST_ARTIFACTS_KOTLIN);
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromDirectory(
      getProject(), "app/src/androidTest");
    assertNotNull(runConfig);
    assertEmpty(runConfig.checkConfiguration(myAndroidFacet));
    assertThat(runConfig.TESTING_TYPE).isEqualTo(AndroidTestRunConfiguration.TEST_ALL_IN_MODULE);
    assertThat(runConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty();
    assertThat(runConfig.PACKAGE_NAME).isEmpty();
    assertThat(runConfig.CLASS_NAME).isEmpty();
    assertThat(runConfig.METHOD_NAME).isEmpty();
    assertThat(runConfig.TEST_NAME_REGEX).isEmpty();
    assertThat(runConfig.suggestedName()).isEqualTo("All Tests");
  }

  public void testSingleParameterizedTestIsCreatedKotlin() throws Exception {
    loadProject(TEST_ARTIFACTS_KOTLIN);
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromMethod(
      getProject(), "com.example.android.kotlin.ParameterizedTest", "exampleParameterizedTest");
    assertNotNull(runConfig);
    assertEmpty(runConfig.checkConfiguration(myAndroidFacet));
    assertThat(runConfig.TESTING_TYPE).isEqualTo(AndroidTestRunConfiguration.TEST_ALL_IN_MODULE);
    assertThat(runConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty();
    assertThat(runConfig.PACKAGE_NAME).isEmpty();
    assertThat(runConfig.CLASS_NAME).isEqualTo("com.example.android.kotlin.ParameterizedTest");
    assertThat(runConfig.METHOD_NAME).isEqualTo("exampleParameterizedTest");
    assertThat(runConfig.TEST_NAME_REGEX).isEqualTo("com.example.android.kotlin.ParameterizedTest.exampleParameterizedTest\\[.*\\]");
    assertThat(runConfig.suggestedName()).isEqualTo("exampleParameterizedTest()");
  }

  public void testCanCreateAndroidTestConfigurationFromFromTestOnlyModule() throws Exception {
    loadProject(TEST_ONLY_MODULE);
    AndroidFacet mainTestFacet = AndroidFacet.getInstance(ModuleSystemUtil.getMainModule(getModule("test")));
    assertNotNull(mainTestFacet);
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromClass(getProject(), "com.example.android.app.ExampleTest");
    assertNotNull(runConfig);
    assertEmpty(runConfig.checkConfiguration(mainTestFacet));
    assertEquals(runConfig.CLASS_NAME, "com.example.android.app.ExampleTest");
    assertEquals(runConfig.TESTING_TYPE, TEST_CLASS);
  }

  public void testCanCreateAndroidTestConfigurationFromFromDynamicFeatureModule() throws Exception {
    loadProject(DYNAMIC_APP);
    AndroidFacet mainTestFacet = AndroidFacet.getInstance(ModuleSystemUtil.getMainModule(getModule("feature1")));
    assertNotNull(mainTestFacet);
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromClass(getProject(), "com.example.feature1.ExampleInstrumentedTest");
    assertNotNull(runConfig);
    assertEmpty(runConfig.checkConfiguration(mainTestFacet));
    assertEquals(runConfig.CLASS_NAME, "com.example.feature1.ExampleInstrumentedTest");
    assertEquals(runConfig.TESTING_TYPE, TEST_CLASS);
  }

  public void testCanCreateAndroidTestConfigurationWhenOriginalConfigExists() throws Exception {
    loadProject(TEST_ARTIFACTS_KOTLIN);

    MapDataContext dataContext = new MapDataContext();
    dataContext.put(CommonDataKeys.PROJECT, getProject());

    PsiElement element = JavaPsiFacade.getInstance(getProject()).findClass(
      "com.example.android.kotlin.ExampleInstrumentedTest",
      GlobalSearchScope.projectScope(getProject()));
    assertNotNull(element);
    dataContext.put(Location.DATA_KEY, PsiLocation.fromPsiElement(element));

    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    dataContext.put(PlatformCoreDataKeys.MODULE, module);

    // This test really is concerned with the case where the context data has an original
    // configuration with module information in it, to simulate the SMTRunnerConsole context
    // when right-clicking on a test result.
    AndroidTestRunConfiguration original =
      new AndroidTestRunConfiguration(getProject(), AndroidTestRunConfigurationType.getInstance().getFactory());
    original.setModule(module);
    dataContext.put(RunConfiguration.DATA_KEY, original);

    ConfigurationContext context = ConfigurationContext.getFromContext(dataContext);
    assertNotNull(context.getOriginalConfiguration(AndroidTestRunConfigurationType.getInstance()));

    AndroidTestConfigurationProducer producer = new AndroidTestConfigurationProducer();
    ConfigurationFromContext runConfig = producer.createConfigurationFromContext(context);
    assertNotNull(runConfig);
  }

  public void testRuntimeQualifiedNameIsUsed() throws Exception {
    loadSimpleApplication();

    File projectDir = VfsUtilCore.virtualToIoFile(PlatformTestUtil.getOrCreateProjectBaseDir(myFixture.getProject()));
    File newTestFile = new File(projectDir, "app/src/androidTest/java/google/simpleapplication/SomeTest.java");
    Files.createParentDirs(newTestFile);
    Files.write(
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
      "}",
      newTestFile,
      StandardCharsets.UTF_8);

    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(newTestFile);

    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromClass(getProject(), "google.simpleapplication.SomeTest.InnerClassTest");
    assertEquals("google.simpleapplication.SomeTest$InnerClassTest", runConfig.CLASS_NAME);
  }

  public void testCreateAndroidAndroidTestKotlinMultiplatformFromSubDirectory() throws Exception {
    loadProject(TEST_ARTIFACTS_KOTLIN_MULTIPLATFORM);
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromDirectory(
      getProject(), "module2/src/androidAndroidTest/kotlin");
    assertNotNull(runConfig);
    assertEmpty(runConfig.checkConfiguration(myAndroidFacet));
    assertThat(runConfig.TESTING_TYPE).isEqualTo(AndroidTestRunConfiguration.TEST_ALL_IN_MODULE);
    assertThat(runConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty();
    assertThat(runConfig.PACKAGE_NAME).isEmpty();
    assertThat(runConfig.CLASS_NAME).isEmpty();
    assertThat(runConfig.METHOD_NAME).isEmpty();
  }

  public void testCreateAndroidAndroidTestKotlinMultiplatformFromDirectory() throws Exception {
    loadProject(TEST_ARTIFACTS_KOTLIN_MULTIPLATFORM);
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromDirectory(
      getProject(), "module2/src/androidAndroidTest");
    assertNotNull(runConfig);
    assertEmpty(runConfig.checkConfiguration(myAndroidFacet));
    assertThat(runConfig.TESTING_TYPE).isEqualTo(AndroidTestRunConfiguration.TEST_ALL_IN_MODULE);
    assertThat(runConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty();
    assertThat(runConfig.PACKAGE_NAME).isEmpty();
    assertThat(runConfig.CLASS_NAME).isEmpty();
    assertThat(runConfig.METHOD_NAME).isEmpty();
  }

  public void testCreateAndroidAndroidTestKotlinMultiplatfomFromClass() throws Exception {
    loadProject(TEST_ARTIFACTS_KOTLIN_MULTIPLATFORM);
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromClass(
      getProject(), "ExampleInstrumentedTest");
    assertNotNull(runConfig);
    assertEmpty(runConfig.checkConfiguration(myAndroidFacet));
    assertThat(runConfig.TESTING_TYPE).isEqualTo(TEST_CLASS);
    assertThat(runConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty();
    assertThat(runConfig.PACKAGE_NAME).isEmpty();
    assertThat(runConfig.CLASS_NAME).isEqualTo("ExampleInstrumentedTest");
    assertThat(runConfig.METHOD_NAME).isEmpty();
  }
}
