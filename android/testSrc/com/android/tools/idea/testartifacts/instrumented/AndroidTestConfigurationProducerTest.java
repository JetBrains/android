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

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.io.Files;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;

import com.intellij.psi.PsiMethod;
import java.io.File;
import java.nio.charset.StandardCharsets;

import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createConfigurationFromPsiElement;
import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromClass;
import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromDirectory;
import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromFile;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_KOTLIN;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_ONLY_MODULE;
import static com.google.common.truth.Truth.assertThat;

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
  }

  public void testCannotCreateAndroidTestConfigurationFromJUnitTestClass() throws Exception {
    loadSimpleApplication();
    assertNull(createAndroidTestConfigurationFromClass(getProject(), "google.simpleapplication.UnitTest"));
  }

  public void testCanCreateAndroidTestConfigurationFromAndroidTestDirectory() throws Exception {
    loadSimpleApplication();
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromDirectory(getProject(), "app/src/androidTest/java");
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
    assertThat(androidRunConfig.INSTRUMENTATION_RUNNER_CLASS).isEqualTo("android.support.test.runner.AndroidJUnitRunner");
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
    assertThat(runConfig.INSTRUMENTATION_RUNNER_CLASS).isEqualTo("android.support.test.runner.AndroidJUnitRunner");
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
    assertThat(runConfig.INSTRUMENTATION_RUNNER_CLASS).isEqualTo("android.support.test.runner.AndroidJUnitRunner");
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
    assertThat(runConfig.INSTRUMENTATION_RUNNER_CLASS).isEqualTo("android.support.test.runner.AndroidJUnitRunner");
    assertThat(runConfig.PACKAGE_NAME).isEmpty();
    assertThat(runConfig.CLASS_NAME).isEmpty();
    assertThat(runConfig.METHOD_NAME).isEmpty();
  }

  public void testCanCreateAndroidTestConfigurationFromFromTestOnlyModule() throws Exception {
    loadProject(TEST_ONLY_MODULE, "test");
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromClass(getProject(), "com.example.android.app.ExampleTest");
    assertNotNull(runConfig);
    assertEmpty(runConfig.checkConfiguration(myAndroidFacet));
  }

  public void testRuntimeQualifiedNameIsUsed() throws Exception {
    loadSimpleApplication();

    File projectDir = VfsUtilCore.virtualToIoFile(myFixture.getProject().getBaseDir());
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
}
