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
package com.android.tools.idea.instantapp;

import com.android.tools.idea.fd.gradle.InstantRunGradleSupport;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;

import java.io.File;

import static com.android.tools.idea.fd.gradle.InstantRunGradleUtils.getIrSupportStatus;
import static com.android.tools.idea.instantapp.AIAProjectStructureAssertions.assertModuleIsValidAIABaseSplit;
import static com.android.tools.idea.instantapp.AIAProjectStructureAssertions.assertModuleIsValidAIAInstantApp;
import static com.android.tools.idea.testing.HighlightInfos.assertFileHasNoErrors;
import static com.android.tools.idea.testing.TestProjectPaths.INSTANT_APP;

public class InstantAppSupportTest extends AndroidGradleTestCase {

  public void testLoadInstantAppProject() throws Exception {
    loadProject(INSTANT_APP);
    generateSources();

    assertModuleIsValidAIAInstantApp(getModule("instant-app"), "baseatom", ImmutableList.of(":baseatom"));
    assertModuleIsValidAIABaseSplit(getModule("baseatom"), ImmutableList.of());

    Project project = getProject();
    assertFileHasNoErrors(project, new File("baseatom/src/main/java/com/example/instantapp/MainActivity.java"));
    assertFileHasNoErrors(project, new File("baseatom/src/androidTest/java/com/example/instantapp/ExampleInstrumentedTest.java"));
    assertFileHasNoErrors(project, new File("baseatom/src/test/java/com/example/instantapp/ExampleUnitTest.java"));
  }

  public void testInstantRunDisabled() throws Exception {
    loadProject(INSTANT_APP, "instant-app");

    // by definition, InstantRunGradleSupport.INSTANT_APP != InstantRunGradleSupport.SUPPORTED
    assertEquals(InstantRunGradleSupport.INSTANT_APP, getIrSupportStatus(getModel(), null));
  }
}
