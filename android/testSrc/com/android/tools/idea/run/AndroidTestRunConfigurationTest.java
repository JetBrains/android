/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run;

import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromClass;
import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_APP_AND_LIB_DEPENDENCY;
import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_APP_AND_LIB_DEPENDENCY_NO_LIB_MANIFEST;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.AndroidGradleTestUtilsKt;
import java.util.List;
import java.util.stream.Collectors;

public class AndroidTestRunConfigurationTest extends AndroidGradleTestCase {

  public void testCannotRunLibTestsInReleaseBuild() throws Exception {
    loadProject(PROJECT_WITH_APP_AND_LIB_DEPENDENCY);

    AndroidRunConfigurationBase androidTestRunConfiguration =
      createAndroidTestConfigurationFromClass(getProject(), "com.example.projectwithappandlib.lib.ExampleInstrumentedTest");
    assertNotNull(androidTestRunConfiguration);

    List<ValidationError> errors = androidTestRunConfiguration.validate(null);
    assertThat(errors).hasSize(0);

    AndroidGradleTestUtilsKt.switchVariant(getProject(), ":app", "basicRelease");
    errors = androidTestRunConfiguration.validate(null);
    assertThat(errors).isNotEmpty();
    assertThat(errors.stream().map(ValidationError::getMessage).collect(Collectors.toList()))
      .contains("Module 'testCannotRunLibTestsInReleaseBuild.lib.androidTest' doesn't exist in project");
  }

  public void testCanRunLibTestsInDebugBuildWithNoAndroidManifest() throws Exception {
    loadProject(PROJECT_WITH_APP_AND_LIB_DEPENDENCY_NO_LIB_MANIFEST);

    AndroidRunConfigurationBase androidTestRunConfiguration =
      createAndroidTestConfigurationFromClass(getProject(), "com.example.projectwithappandlib.lib.ExampleInstrumentedTest");
    assertNotNull(androidTestRunConfiguration);

    List<ValidationError> errors = androidTestRunConfiguration.validate(null);
    assertThat(errors).hasSize(0);
  }
}
