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
import com.intellij.openapi.util.SystemInfo;

import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromClass;
import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromDirectory;

/**
 * Test for {@link AndroidTestConfigurationProducer}
 */
public class AndroidTestConfigurationProducerTest extends AndroidGradleTestCase {

  @Override
  protected boolean shouldRunTest() {
    // Do not run tests on Windows (see http://b.android.com/222904)
    return !SystemInfo.isWindows && super.shouldRunTest();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    loadSimpleApplication();
  }

  public void ignore_testCanCreateAndroidTestConfigurationFromAndroidTestClass() throws Exception {
    assertNotNull(createAndroidTestConfigurationFromClass(getProject(), "google.simpleapplication.ApplicationTest"));
  }

  public void ignore_testCannotCreateAndroidTestConfigurationFromJUnitTestClass() throws Exception {
    assertNull(createAndroidTestConfigurationFromClass(getProject(), "google.simpleapplication.UnitTest"));
  }

  public void ignore_testCanCreateAndroidTestConfigurationFromAndroidTestDirectory() throws Exception {
    assertNotNull(createAndroidTestConfigurationFromDirectory(getProject(), "app/src/androidTest/java"));
  }

  public void ignore_testCannotCreateAndroidTestConfigurationFromJUnitTestDirectory() throws Exception {
    assertNull(createAndroidTestConfigurationFromDirectory(getProject(), "app/src/test/java"));
  }
}
