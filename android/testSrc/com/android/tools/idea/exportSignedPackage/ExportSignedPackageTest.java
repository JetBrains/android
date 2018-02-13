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
package com.android.tools.idea.exportSignedPackage;

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.jetbrains.android.exportSignedPackage.ExportSignedPackageWizard;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.testing.TestProjectPaths.SIGNAPK_MULTIFLAVOR;
import static com.android.tools.idea.testing.TestProjectPaths.SIGNAPK_NO_FLAVORS;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

public class ExportSignedPackageTest extends AndroidGradleTestCase {
  public void testNoFlavors() throws Exception {
    loadProject(SIGNAPK_NO_FLAVORS);
    AndroidProject androidProject = getModel().getAndroidProject();
    assertNotNull(androidProject);

    // debug and release
    assertEquals(2, androidProject.getVariants().size());

    List<String> assembleTasks = ExportSignedPackageWizard.getAssembleTasks("", androidProject, "release", Collections.<String>emptyList());
    assertEquals(1, assembleTasks.size());
    assertEquals(":assembleRelease", assembleTasks.get(0));
  }

  public void testFlavors() throws Exception {
    loadProject(SIGNAPK_MULTIFLAVOR);
    AndroidProject androidProject = getModel().getAndroidProject();
    assertNotNull(androidProject);

    // (free,pro) x (arm,x86) x (debug,release) = 8
    assertEquals(8, androidProject.getVariants().size());

    Set<String> assembleTasks =
      Sets.newHashSet(ExportSignedPackageWizard.getAssembleTasks("", androidProject, "release", Lists.newArrayList("pro-x86", "free-arm")));
    assertEquals(2, assembleTasks.size());
    assertTrue(assembleTasks.contains(":assembleProX86Release"));
    assertTrue(assembleTasks.contains(":assembleFreeArmRelease"));
  }

  public void testApkLocationCorrect() {
    // This test guarantees user is taken to the folder with the selected build type outputs
    assertEquals(toSystemDependentName("path/to/folder/release"), ExportSignedPackageWizard.getApkLocation("path/to/folder", "release").toString());
  }
}
