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
package com.android.tools.idea.gradle.util;

import static com.android.SdkConstants.DOT_KTS;
import static com.android.tools.idea.testing.TestProjectPaths.KOTLIN_GRADLE_DSL;

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.TestModuleUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jetbrains.annotations.NotNull;

public class GradleUtilAndroidGradleTest extends AndroidGradleTestCase {
  public void testGetGradleBuildFileFromAppModule() throws Exception {
    loadSimpleApplication();
    verifyBuildFile(TestModuleUtil.findAppModule(getProject()), "app", "build.gradle");
  }

  public void testGetGradleBuildFileFromProjectModule() throws Exception {
    loadSimpleApplication();
    verifyBuildFile(TestModuleUtil.findModule(getProject(), getProject().getName()), "build.gradle");
  }

  public void testHasKtsBuildFilesKtsBasedProject() throws Exception {
    loadProject(KOTLIN_GRADLE_DSL);
    assertTrue(GradleUtil.projectBuildFilesTypes(getProject()).contains(DOT_KTS));
  }

  public void testHasKtsBuildFilesGroovyBasedProject() throws Exception {
    loadSimpleApplication();
    assertFalse(GradleUtil.projectBuildFilesTypes(getProject()).contains(DOT_KTS));
  }

  private void verifyBuildFile(@NotNull Module module, @NotNull String... expectedPath) {
    Path fullPath = Paths.get(getProject().getBasePath(), expectedPath);

    VirtualFile moduleBuildFile = GradleUtil.getGradleBuildFile(module);
    assertEquals(FileUtil.toSystemIndependentName(fullPath.toString()), moduleBuildFile.getPath());
  }
}
