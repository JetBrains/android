/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.gradle;

import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.annotation.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.BuildVariantsToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.google.common.collect.Multimap;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BuildVariantsTest extends GuiTestCase {

  private static final String MODULE_NAME = "app";

  @Test @IdeGuiTest
  public void testSwitchVariantWithFlavor() throws IOException {
    IdeFrameFixture projectFrame = openProject("Flavoredlib");

    BuildVariantsToolWindowFixture buildVariants = projectFrame.getBuildVariantsWindow();
    buildVariants.selectVariantForModule(MODULE_NAME, "flavor1Release");

    String generatedSourceDirPath = MODULE_NAME + "/build/generated/source/";

    assertThat(getSourceFolders(projectFrame, JavaSourceRootType.SOURCE))
      .contains(generatedSourceDirPath + "r/flavor1/release", generatedSourceDirPath + "aidl/flavor1/release",
                generatedSourceDirPath + "buildConfig/flavor1/release", generatedSourceDirPath + "rs/flavor1/release",
                MODULE_NAME + "/src/flavor1Release/aidl", MODULE_NAME + "/src/flavor1Release/java", MODULE_NAME + "/src/flavor1Release/jni",
                MODULE_NAME + "/src/flavor1Release/rs");

    Module appModule = projectFrame.getModule(MODULE_NAME);
    AndroidFacet androidFacet = AndroidFacet.getInstance(appModule);
    assertNotNull(androidFacet);

    JpsAndroidModuleProperties androidFacetProperties = androidFacet.getProperties();
    assertEquals("assembleFlavor1Release", androidFacetProperties.ASSEMBLE_TASK_NAME);
    // 'release' variant does not have the _android_test_ artifact.
    assertEquals("", androidFacetProperties.ASSEMBLE_TEST_TASK_NAME);

    buildVariants.selectVariantForModule(MODULE_NAME, "flavor1Debug");

    assertThat(getSourceFolders(projectFrame, JavaSourceRootType.SOURCE))
      .contains(generatedSourceDirPath + "r/flavor1/debug", generatedSourceDirPath + "aidl/flavor1/debug",
                generatedSourceDirPath + "buildConfig/flavor1/debug", generatedSourceDirPath + "rs/flavor1/debug",
                MODULE_NAME + "/src/flavor1Debug/aidl", MODULE_NAME + "/src/flavor1Debug/java", MODULE_NAME + "/src/flavor1Debug/jni",
                MODULE_NAME + "/src/flavor1Debug/rs");

    assertEquals("assembleFlavor1Debug", androidFacetProperties.ASSEMBLE_TASK_NAME);
    // Verifies that https://code.google.com/p/android/issues/detail?id=83077 is not a bug.
    assertEquals("assembleFlavor1DebugTest", androidFacetProperties.ASSEMBLE_TEST_TASK_NAME);
  }

  /**
   * Attention: this test needs to be run with -Dandroid.unit_testing (for now) and with MAVEN_REPO pointing to a local repo with the
   * latest version of the gradle plugin.
   *
   * @see org.jetbrains.android.util.AndroidUtils#isUnitTestingSupportEnabled()
   */
  @Test @IdeGuiTest
  public void switchingTestArtifacts() throws Exception {
    final String androidTestSrc = MODULE_NAME + "/src/androidTest/java";
    final String unitTestSrc = MODULE_NAME + "/src/test/java";

    assertTrue(AndroidUtils.isUnitTestingSupportEnabled());

    IdeFrameFixture projectFrame = openProject("SimpleApplicationWithUnitTests");
    BuildVariantsToolWindowFixture buildVariants = projectFrame.getBuildVariantsWindow();
    buildVariants.activate();

    assertThat(getSourceFolders(projectFrame, JavaSourceRootType.TEST_SOURCE))
      .contains(androidTestSrc)
      .excludes(unitTestSrc);

    buildVariants.selectTestArtifact("Unit tests");

    assertThat(getSourceFolders(projectFrame, JavaSourceRootType.TEST_SOURCE))
      .contains(unitTestSrc)
      .excludes(androidTestSrc);

    myRobot.waitForIdle();
  }

  private Collection<String> getSourceFolders(IdeFrameFixture projectFrame, JavaSourceRootType source) {
    Multimap<JpsModuleSourceRootType, String> appSourceFolders = projectFrame.getSourceFolderRelativePaths(MODULE_NAME);
    return appSourceFolders.get(source);
  }
}
