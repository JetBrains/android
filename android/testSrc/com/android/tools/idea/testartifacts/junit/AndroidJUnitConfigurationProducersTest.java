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
package com.android.tools.idea.testartifacts.junit;

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.util.SystemInfo;

import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromFile;
import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createJUnitConfigurationFromClass;
import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createJUnitConfigurationFromDirectory;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_KOTLIN;


/**
 * Tests for all the {@link AndroidJUnitConfigurationProducer}s
 */
public class AndroidJUnitConfigurationProducersTest extends AndroidGradleTestCase {

  @Override
  protected boolean shouldRunTest() {
    // Do not run tests on Windows (see http://b.android.com/222904)
    return !SystemInfo.isWindows && super.shouldRunTest();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  public void testCanCreateJUnitConfigurationFromJUnitTestClass() throws Exception {
    loadSimpleApplication();
    assertNotNull(createJUnitConfigurationFromClass(getProject(), "google.simpleapplication.UnitTest"));
  }

  public void testCannotCreateJUnitConfigurationFromAndroidTestClass() throws Exception {
    loadSimpleApplication();
    assertNull(createJUnitConfigurationFromClass(getProject(), "google.simpleapplication.ApplicationTest"));
  }

  public void testCanCreateJUnitConfigurationFromJUnitTestDirectory() throws Exception {
    loadSimpleApplication();
    assertNotNull(createJUnitConfigurationFromDirectory(getProject(), "app/src/test/java"));
  }

  public void testCannotCreateJUnitConfigurationFromAndroidTestDirectory() throws Exception {
    loadSimpleApplication();
    assertNull(createJUnitConfigurationFromDirectory(getProject(), "app/src/androidTest/java"));
  }

  public void testCannotCreateJUnitConfigurationFromAndroidTestClassKotlin() throws Exception {
    loadProject(TEST_ARTIFACTS_KOTLIN);
    assertNull(createAndroidTestConfigurationFromFile(
      getProject(), "app/src/androidTest/java/com/example/android/kotlin/ExampleInstrumentedTest.kt"));
  }

  public void testCanCreateJUnitConfigurationFromJUnitTestDirectoryKotlin() throws Exception {
    loadProject(TEST_ARTIFACTS_KOTLIN);
    assertNotNull(createJUnitConfigurationFromDirectory(getProject(), "app/src/test/java"));
  }

  public void testCannotCreateJUnitConfigurationFromAndroidTestDirectoryKotlin() throws Exception {
    loadProject(TEST_ARTIFACTS_KOTLIN);
    assertNull(createJUnitConfigurationFromDirectory(getProject(), "app/src/androidTest/java"));
  }
}
