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

import static com.android.tools.idea.testing.TestProjectPaths.DYNAMIC_APP;
import static com.google.common.truth.Truth.assertThat;

import java.util.List;

public class AndroidRunConfigurationGradleTest extends AndroidRunConfigurationGradleTestCase {
  public void testNoErrorIfGradlePluginVersionIsUpToDate() throws Exception {
    loadProject(DYNAMIC_APP);
    myRunConfiguration.DEPLOY = true;
    myRunConfiguration.DEPLOY_APK_FROM_BUNDLE = true;
    List<ValidationError> errors = myRunConfiguration.checkConfiguration(myAndroidFacet);
    assertThat(errors).isEmpty();
  }
}
