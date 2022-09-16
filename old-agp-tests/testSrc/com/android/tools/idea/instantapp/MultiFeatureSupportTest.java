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
package com.android.tools.idea.instantapp;

import static com.android.tools.idea.instantapp.AIAProjectStructureAssertions.assertModuleIsValidAIAApp;
import static com.android.tools.idea.instantapp.AIAProjectStructureAssertions.assertModuleIsValidAIABaseFeature;
import static com.android.tools.idea.instantapp.AIAProjectStructureAssertions.assertModuleIsValidAIAFeature;
import static com.android.tools.idea.instantapp.AIAProjectStructureAssertions.assertModuleIsValidAIAInstantApp;
import static com.android.tools.idea.testing.HighlightInfos.assertFileHasNoErrors;
import static com.android.tools.idea.testing.TestProjectPaths.MULTI_FEATURE;
import static com.android.tools.idea.testing.TestProjectPaths.NESTED_MULTI_FEATURE;

import com.android.testutils.junit4.OldAgpTest;
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import java.io.File;

@OldAgpTest(agpVersions = "3.5.0", gradleVersions = "5.5")
public class MultiFeatureSupportTest extends AndroidGradleTestCase {

  public void testLoadMultiAtomProject() throws Exception {
    // Use a plugin with feature support
    loadProject(MULTI_FEATURE, null, AgpVersionSoftwareEnvironmentDescriptor.AGP_35);

    assertModuleIsValidAIAApp(getModule("apk"), ImmutableList.of(":feature1", ":feature2", ":base"));
    assertModuleIsValidAIAFeature(getModule("feature1"), ImmutableList.of(":base"));
    assertModuleIsValidAIAFeature(getModule("feature2"), ImmutableList.of(":base"));
    assertModuleIsValidAIABaseFeature(getModule("base"), ImmutableList.of());
    assertModuleIsValidAIAInstantApp(getModule("instantapp"), ImmutableList.of(":feature1", ":feature2", ":base"));

    generateSources();
    Project project = getProject();
    assertFileHasNoErrors(project, new File(
      "feature1/src/main/java/com/google/android/instantapps/samples/multiatom/feature1/Feature1Activity.java"));
    assertFileHasNoErrors(project, new File(
      "feature2/src/main/java/com/google/android/instantapps/samples/multiatom/feature2/Feature2Activity.java"));
  }

  public void testLoadNestedMultiAtomProject() throws Exception {
    // Use a plugin with instant app support
    loadProject(NESTED_MULTI_FEATURE, null, AgpVersionSoftwareEnvironmentDescriptor.AGP_35);

    assertModuleIsValidAIAApp(getModule("apk"), ImmutableList.of(":feature:feature1", ":feature:feature2", ":feature:base"));
    assertModuleIsValidAIAFeature(getModule("feature1"), ImmutableList.of(":feature:base"));
    assertModuleIsValidAIAFeature(getModule("feature2"), ImmutableList.of(":feature:base"));
    assertModuleIsValidAIABaseFeature(getModule("base"), ImmutableList.of());
    assertModuleIsValidAIAInstantApp(getModule("instantapp"), ImmutableList.of(":feature:feature1", ":feature:feature2", ":feature:base"));

    generateSources();
    Project project = getProject();
    assertFileHasNoErrors(project, new File(
      "feature/feature1/src/main/java/com/google/android/instantapps/samples/multiatom/feature1/Feature1Activity.java"));
    assertFileHasNoErrors(project, new File(
      "feature/feature2/src/main/java/com/google/android/instantapps/samples/multiatom/feature2/Feature2Activity.java"));
  }
}
