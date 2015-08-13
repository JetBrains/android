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
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import junit.framework.TestCase;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.util.DistributionLocator;
import org.gradle.util.GradleVersion;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.android.exportSignedPackage.ExportSignedPackageWizard;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ExportSignedPackageTest extends TestCase {
  @NonNls private static final String BASE_PATH = "testData/projects/signapk/";

  /**
   * Returns the {@link com.android.builder.model.AndroidProject} given the gradle project root.
   * Note that this works only single module projects (only one build.gradle)
   */
  @Nullable
  private static AndroidProject getAndroidProject(String projectPath) {
    File androidPlugin = new File(AndroidTestBase.getAndroidPluginHome());
    File projectDir = new File(androidPlugin, BASE_PATH + projectPath);
    GradleConnector connector = GradleConnector.newConnector();
    connector.forProjectDirectory(projectDir);
    connector.useDistribution(new DistributionLocator().getDistributionFor(GradleVersion.version("2.2.1")));

    AndroidProject model = null;
    ProjectConnection connection = connector.connect();
    try {
      model = connection.getModel(AndroidProject.class);
    } finally {
      connection.close();
    }

    return model;
  }

  public void testNoFlavors() {
    AndroidProject androidProject = getAndroidProject("no_flavors");
    assertNotNull(androidProject);

    // debug and release
    assertEquals(2, androidProject.getVariants().size());

    List<String> assembleTasks = ExportSignedPackageWizard.getAssembleTasks("", androidProject, "release", Collections.<String>emptyList());
    assertEquals(1, assembleTasks.size());
    assertEquals(":assembleRelease", assembleTasks.get(0));
  }

  public void testFlavors() {
    AndroidProject androidProject = getAndroidProject("multiflavor");
    assertNotNull(androidProject);

    // (free,pro) x (arm,x86) x (debug,release) = 8
    assertEquals(8, androidProject.getVariants().size());

    Set<String> assembleTasks =
      Sets.newHashSet(ExportSignedPackageWizard.getAssembleTasks("", androidProject, "release", Lists.newArrayList("pro-x86", "free-arm")));
    assertEquals(2, assembleTasks.size());
    assertTrue(assembleTasks.contains(":assembleProX86Release"));
    assertTrue(assembleTasks.contains(":assembleFreeArmRelease"));
  }
}
