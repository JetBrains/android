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
import com.android.tools.idea.tests.gui.framework.fixture.BuildVariantsToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.google.common.collect.Multimap;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class BuildVariantsTest extends GuiTestCase {
  @Test
  public void testSwitchVariantWithFlavor() throws IOException {
    IdeFrameFixture projectFrame = openProject("Flavoredlib");
    String moduleName = "app";

    BuildVariantsToolWindowFixture buildVariants = projectFrame.getBuildVariantsWindow();
    buildVariants.select(moduleName, "flavor1Release");

    String generatedSourceDirPath = "app/build/generated/source/";

    Multimap<JpsModuleSourceRootType, String> appSourceFolders = projectFrame.getSourceFolderRelativePaths(moduleName);
    Collection<String> sourceFolders = appSourceFolders.get(JavaSourceRootType.SOURCE);
    assertThat(sourceFolders).contains(generatedSourceDirPath + "r/flavor1/release",
                                       generatedSourceDirPath + "aidl/flavor1/release",
                                       generatedSourceDirPath + "buildConfig/flavor1/release",
                                       generatedSourceDirPath + "rs/flavor1/release",
                                       "app/src/flavor1Release/aidl",
                                       "app/src/flavor1Release/java",
                                       "app/src/flavor1Release/jni",
                                       "app/src/flavor1Release/rs");

    Module appModule = projectFrame.getModule(moduleName);
    AndroidFacet androidFacet = AndroidFacet.getInstance(appModule);
    assertNotNull(androidFacet);

    JpsAndroidModuleProperties androidFacetProperties = androidFacet.getProperties();
    assertEquals("assembleFlavor1Release", androidFacetProperties.ASSEMBLE_TASK_NAME);
    // 'release' variant does not have 'test' artifact.
    assertEquals("", androidFacetProperties.ASSEMBLE_TEST_TASK_NAME);

    buildVariants.select(moduleName, "flavor1Debug");

    appSourceFolders = projectFrame.getSourceFolderRelativePaths(moduleName);
    sourceFolders = appSourceFolders.get(JavaSourceRootType.SOURCE);
    assertThat(sourceFolders).contains(generatedSourceDirPath + "r/flavor1/debug",
                                       generatedSourceDirPath + "aidl/flavor1/debug",
                                       generatedSourceDirPath + "buildConfig/flavor1/debug",
                                       generatedSourceDirPath + "rs/flavor1/debug",
                                       "app/src/flavor1Debug/aidl",
                                       "app/src/flavor1Debug/java",
                                       "app/src/flavor1Debug/jni",
                                       "app/src/flavor1Debug/rs");

    assertEquals("assembleFlavor1Debug", androidFacetProperties.ASSEMBLE_TASK_NAME);
    // Verifies that https://code.google.com/p/android/issues/detail?id=83077 is not a bug.
    assertEquals("assembleFlavor1DebugTest", androidFacetProperties.ASSEMBLE_TEST_TASK_NAME);
  }
}
