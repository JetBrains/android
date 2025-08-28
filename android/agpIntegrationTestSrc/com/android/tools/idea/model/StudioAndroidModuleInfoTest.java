/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.model;

import static com.android.tools.idea.testing.TestProjectPaths.MODULE_INFO_FLAVORS;
import static com.android.tools.idea.testing.TestProjectPaths.MODULE_INFO_GRADLE_ONLY;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor;
import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.android.tools.module.AndroidModuleInfo;
import org.jetbrains.android.facet.AndroidFacet;
import org.junit.Rule;
import org.junit.Test;

public class StudioAndroidModuleInfoTest {
  @Rule
  public AndroidGradleProjectRule projectRule = new AndroidGradleProjectRule();

  private final AgpVersionSoftwareEnvironmentDescriptor softwareEnvironment = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT;

  @Test
  public void testGradleOnly() {
    projectRule.loadProject(MODULE_INFO_GRADLE_ONLY, softwareEnvironment);
    AndroidFacet myAndroidFacet = projectRule.androidFacet(":");
    assertThat(myAndroidFacet).isNotNull();
    AndroidModuleInfo androidModuleInfo = StudioAndroidModuleInfo.getInstance(myAndroidFacet);
    assertThat(androidModuleInfo.getMinSdkVersion().getApiLevel()).isEqualTo(17);
    assertThat(androidModuleInfo.getTargetSdkVersion().getApiLevel()).isEqualTo(Integer.parseInt(softwareEnvironment.getTargetSdk()));
    assertThat(androidModuleInfo.getPackageName()).isEqualTo("from.gradle");
  }

  @Test
  public void testFlavors() {
    projectRule.loadProject(MODULE_INFO_FLAVORS, softwareEnvironment);
    AndroidFacet myAndroidFacet = projectRule.androidFacet(":");
    assertThat(myAndroidFacet).isNotNull();

    AndroidModuleInfo androidModuleInfo = StudioAndroidModuleInfo.getInstance(myAndroidFacet);
    assertThat(androidModuleInfo.getMinSdkVersion().getApiLevel()).isEqualTo(14);
    assertThat(androidModuleInfo.getTargetSdkVersion().getApiLevel()).isEqualTo(Integer.parseInt(softwareEnvironment.getTargetSdk()));
    assertThat(androidModuleInfo.getPackageName()).isEqualTo("com.example.free.debug");
  }
}
