/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION_PRE30;
import static com.google.common.truth.Truth.assertThat;

import java.util.List;

public class AndroidRunConfigurationGradleOldAgpTest extends AndroidRunConfigurationGradleTestCase {
  public void testErrorIfGradlePluginVersionIsOutdated() throws Exception {
    loadProject(SIMPLE_APPLICATION_PRE30, "app", "4.5", "3.0.0");

    // Verifies there is a validation error (since bundle tasks are not available)
    myRunConfiguration.DEPLOY = true;
    myRunConfiguration.DEPLOY_APK_FROM_BUNDLE = true;
    List<ValidationError> errors = myRunConfiguration.checkConfiguration(myAndroidFacet);
    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).getMessage()).isEqualTo("This option requires a newer version of the Android Gradle Plugin");
  }
}
