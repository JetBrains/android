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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromClass;
import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromDirectory;
import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromFile;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_KOTLIN;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_ONLY_MODULE;

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

  public void testCannotCreateAndroidTestConfigurationFromJUnitTestClassWithKotlin() throws Exception {
    loadProject(TEST_ARTIFACTS_KOTLIN);
    assertNull(createAndroidTestConfigurationFromFile(
      getProject(), "app/src/test/java/com/example/android/kotlin/ExampleUnitTest.kt"));
  }

  public void testCanCreateAndroidTestConfigurationFromAndroidTestDirectoryWithKotlin() throws Exception {
    loadProject(TEST_ARTIFACTS_KOTLIN);
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromDirectory(getProject(), "app/src/androidTest/java");
    assertNotNull(runConfig);
    assertEmpty(runConfig.checkConfiguration(myAndroidFacet));
  }

  public void testCannotCreateAndroidTestConfigurationFromJUnitTestDirectoryWithKotlin() throws Exception {
    loadProject(TEST_ARTIFACTS_KOTLIN);
    assertNull(createAndroidTestConfigurationFromDirectory(getProject(), "app/src/test/java"));
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
      "import android.app.Application;\n" +
      "import android.test.ApplicationTestCase;\n" +
      "\n" +
      "public class SomeTest {\n" +
      "  public static class InnerClassTest extends ApplicationTestCase<Application> {\n" +
      "    \n" +
      "  }\n" +
      "}",
      newTestFile,
      StandardCharsets.UTF_8);

    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(newTestFile);

    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromClass(getProject(), "google.simpleapplication.SomeTest.InnerClassTest");
    assertEquals(runConfig.CLASS_NAME, "google.simpleapplication.SomeTest$InnerClassTest");
  }
}
