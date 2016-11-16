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
package com.android.tools.idea.run.testing;

import com.android.tools.idea.gradle.testing.TestArtifactSearchScopes;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.util.SystemInfo;

import static com.android.tools.idea.run.testing.TestConfigurationTestUtil.createAndroidConfigurationFromDirectory;
import static com.android.tools.idea.run.testing.TestConfigurationTestUtil.createJUnitConfigurationFromDirectory;

/**
 * Tests for creation of test run configurations in Android Gradle projects
 */
public class CreateTestConfigurationTest extends AndroidGradleTestCase {
  // See http://b.android.com/215255
  public void testConfigurationsAreDifferent() throws Exception {
    loadSimpleApplication();
    TestArtifactSearchScopes.initializeScopes(getProject());
    if (SystemInfo.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    RunConfiguration androidTestRunConfiguration = createAndroidConfigurationFromDirectory(getProject(), "app/src/androidTest/java");
    RunConfiguration jUnitConfiguration = createJUnitConfigurationFromDirectory(getProject(), "app/src/test/java");

    assertNotNull(jUnitConfiguration);
    assertNotNull(androidTestRunConfiguration);

    assertNotSame(androidTestRunConfiguration, jUnitConfiguration);
  }
}
