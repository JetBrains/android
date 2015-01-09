/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.navigation.model;

import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.navigation.NavigationEditor;
import com.android.tools.idea.editors.navigation.Utilities;
import com.android.tools.idea.editors.navigation.macros.Analyser;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;

import static org.junit.Assume.assumeTrue;

/**
 * Tests for NavigationEditor's parsing infrastructure.
 */
public class NavigationEditorTest extends AndroidGradleTestCase {
  private Module myModule;
  private AndroidFacet myFacet;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    assumeTrue(CAN_SYNC_PROJECTS);

    loadProject("projects/navigationEditor/masterDetail");
    assertNotNull(myAndroidFacet);
    IdeaAndroidProject gradleProject = myAndroidFacet.getIdeaAndroidProject();
    assertNotNull(gradleProject);

    Module[] modules = ModuleManager.getInstance(getProject()).getModules();

    assertTrue(modules.length == 1);
    myModule = modules[0];
    assertNotNull(myModule);

    myFacet = AndroidFacet.getInstance(myModule);
    assertNotNull(myFacet);

    addAndroidSdk(myModule, getTestSdkPath(), getPlatformDir());
    assertNotNull(AndroidPlatform.getInstance(myModule));
  }

  private NavigationModel getNavigationModel(String deviceQualifier) {
    Project project = myModule.getProject();
    VirtualFile navFile = Utilities.getNavigationFile(project.getBaseDir(), myModule.getName(), deviceQualifier,
                                                      NavigationEditor.NAVIGATION_FILE_NAME);
    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(navFile);
    Analyser analyser = new Analyser(myModule);
    return analyser.getNavigationModel(configuration);
  }

  @SuppressWarnings("TestMethodWithIncorrectSignature")
  private void testTransitionDerivation(String deviceQualifier, int expectedStateCount, int expectedTransitionCount) {
    NavigationModel model = getNavigationModel(deviceQualifier);
    assertTrue(model.getStates().size() == expectedStateCount);
    assertTrue(model.getTransitions().size() == expectedTransitionCount);
  }

// Use 'ignore' prefix instead of @Ignore annotation as we're extending the TestCase base class from JUnit3.
  public void ignoreTestTransitionDerivationForDefaultDevice() throws Exception {
    testTransitionDerivation("raw", 2, 1);
  }

  /* When a master-detail app like simplemail runs on a tablet, there is one less transition in landscape. */
  public void ignoreTestTransitionDerivationForTabletInLandscape() throws Exception {
    testTransitionDerivation("raw-sw600dp-land", 2, 0);
  }
}
