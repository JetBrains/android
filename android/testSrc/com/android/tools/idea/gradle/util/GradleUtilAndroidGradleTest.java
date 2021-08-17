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
import static com.android.tools.idea.testing.AndroidGradleTests.getEmbeddedJdk8Path;
import static com.android.tools.idea.testing.TestProjectPaths.KOTLIN_GRADLE_DSL;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.TestModuleUtil;
import com.android.utils.FileUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

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

  public void testJdkPathFromProjectJava8() throws Exception {
    String jdk8Path = getEmbeddedJdk8Path();
    verifyJdkPathFromProject(jdk8Path);
  }

  public void testJdkPathFromProjectJavaCurrent() throws Exception {
    @NotNull IdeSdks ideSdks = IdeSdks.getInstance();
    File jdkPath = ideSdks.getJdkPath();
    assertNotNull("Could not find path of current JDK", jdkPath);
    verifyJdkPathFromProject(jdkPath.getAbsolutePath());
  }

  private void verifyBuildFile(@NotNull Module module, @NotNull String... expectedPath) {
    String basePath = getProject().getBasePath();
    assertThat(basePath).isNotNull();
    Path fullPath = Paths.get(basePath, expectedPath);

    VirtualFile moduleBuildFile = GradleUtil.getGradleBuildFile(module);
    assertThat(moduleBuildFile).isNotNull();
    String modulePath = moduleBuildFile.getPath();
    assertThat(modulePath).isNotNull();
    assertEquals(FileUtil.toSystemIndependentName(fullPath.toString()), modulePath);
  }

  private void verifyJdkPathFromProject(@NotNull String javaPath) throws Exception{
    loadSimpleApplication();

    // Change value returned by IdeSdks.getJdkPath to Java 8
    ApplicationManager.getApplication().runWriteAction(() -> {
      File jdkPath = new File(javaPath);
      IdeSdks.getInstance().setJdkPath(jdkPath);
    });

    Project project = getProject();
    String basePath = project.getBasePath();
    assertThat(basePath).isNotNull();
    assertThat(basePath).isNotEmpty();
    String managerPath = GradleInstallationManager.getInstance().getGradleJvmPath(project, basePath);
    assertThat(managerPath).isNotNull();

    GradleExecutionSettings settings = GradleUtil.getOrCreateGradleExecutionSettings(project);
    String settingsPath = settings.getJavaHome();
    assertThat(settingsPath).isNotNull();
    assertThat(settingsPath).isNotEmpty();
    assertTrue(FileUtils.isSameFile(new File(settingsPath), new File(managerPath)));
  }
}
