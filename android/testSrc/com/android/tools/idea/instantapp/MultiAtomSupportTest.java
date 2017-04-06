/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.ImmutableList;
import org.junit.Ignore;

import static com.android.tools.idea.instantapp.AIAProjectStructureAssertions.*;
import static com.android.tools.idea.testing.TestProjectPaths.MULTI_ATOM;
import static com.android.tools.idea.testing.TestProjectPaths.NESTED_MULTI_ATOM;

@Ignore("http://b/35788310")
public class MultiAtomSupportTest extends AndroidGradleTestCase {
  public void testFake() {
  }

  public void /*test*/LoadMultiAtomProject() throws Exception {
    loadProject(MULTI_ATOM);

    assertModuleIsValidAIAApp(getModule("apk"), ImmutableList.of(":feature1lib", ":feature2lib"));
    assertModuleIsValidAIALibrary(getModule("feature1lib"), ImmutableList.of(":baselib"));
    assertModuleIsValidAIALibrary(getModule("feature2lib"), ImmutableList.of(":baselib"));
    assertModuleIsValidAIALibrary(getModule("baselib"), ImmutableList.of());
    assertModuleIsValidAIAInstantApp(getModule("iapk"), "baseatom",
                                     ImmutableList.of(":feature1atom", ":feature2atom", ":baseatom" /*See http://b/34154264*/));
    assertModuleIsValidAIASplit(getModule("feature1atom"), "baseatom", ImmutableList.of(":feature1lib"), ImmutableList.of(":baseatom"));
    assertModuleIsValidAIASplit(getModule("feature2atom"), "baseatom", ImmutableList.of(":feature2lib"), ImmutableList.of(":baseatom"));
    assertModuleIsValidAIABaseSplit(getModule("baseatom"), ImmutableList.of(":baselib"));

    // Until http://b/34154473 is fixed the following fails as source generation / building can not complete without errors
    // generateSources();
    // Project project = getProject();
    // assertFileHasNoErrors(project, new File("feature1lib/src/main/java/com/google/android/instantapps/samples/multiatom/feature1lib/Feature1Activity.java"));
    // assertFileHasNoErrors(project, new File("feature2lib/src/main/java/com/google/android/instantapps/samples/multiatom/feature2lib/Feature2Activity.java"));
  }

  public void /*test*/LoadNestedMultiAtomProject() throws Exception {
    loadProject(NESTED_MULTI_ATOM);

    assertModuleIsValidAIAApp(getModule("apk"), ImmutableList.of(":lib:feature1", ":lib:feature2"));
    assertModuleIsValidAIALibrary(getModule("lib-feature1"), ImmutableList.of(":lib:base"));
    assertModuleIsValidAIALibrary(getModule("lib-feature2"), ImmutableList.of(":lib:base"));
    assertModuleIsValidAIALibrary(getModule("lib-base"), ImmutableList.of());
    assertModuleIsValidAIAInstantApp(getModule("iapk"), "base",
                                     ImmutableList.of(":atom:feature1", ":atom:feature2", ":atom:base" /*See http://b/34154264*/));
    assertModuleIsValidAIASplit(getModule("atom-feature1"), "base", ImmutableList.of(":lib:feature1"), ImmutableList.of(":atom:base"));
    assertModuleIsValidAIASplit(getModule("atom-feature2"), "base", ImmutableList.of(":lib:feature2"), ImmutableList.of(":atom:base"));
    assertModuleIsValidAIABaseSplit(getModule("atom-base"), ImmutableList.of(":lib:base"));

    // Until http://b/34154473 is fixed the following fails as source generation / building can not complete without errors
    // generateSources();
    // Project project = getProject();
    // assertFileHasNoErrors(project, new File("feature1lib/src/main/java/com/google/android/instantapps/samples/multiatom/feature1lib/Feature1Activity.java"));
    // assertFileHasNoErrors(project, new File("feature2lib/src/main/java/com/google/android/instantapps/samples/multiatom/feature2lib/Feature2Activity.java"));
  }
}
