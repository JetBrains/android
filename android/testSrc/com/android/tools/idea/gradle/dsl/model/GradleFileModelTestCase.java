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
package com.android.tools.idea.gradle.dsl.model;

import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.intellij.openapi.util.io.FileUtil.ensureCanCreateFile;
import static com.intellij.openapi.util.io.FileUtil.writeToFile;
import static org.fest.assertions.Assertions.assertThat;

public abstract class GradleFileModelTestCase extends PlatformTestCase {
  protected File mySettingsFile;
  protected File myBuildFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    String basePath = myProject.getBasePath();
    assertNotNull(basePath);
    File projectBasePath = new File(basePath);
    assertThat(projectBasePath).isDirectory();
    mySettingsFile = new File(projectBasePath, FN_SETTINGS_GRADLE);
    assertThat(ensureCanCreateFile(mySettingsFile));

    File moduleFilePath = new File(myModule.getModuleFilePath());
    File moduleDirPath = moduleFilePath.getParentFile();
    assertThat(moduleDirPath).isDirectory();
    myBuildFile = new File(moduleDirPath, FN_BUILD_GRADLE);
    assertTrue(ensureCanCreateFile(myBuildFile));
  }

  @Override
  protected void checkForSettingsDamage(@NotNull List<Throwable> exceptions) { }

  protected void writeToSettingsFile(@NotNull String text) throws IOException {
    writeToFile(mySettingsFile, text);
  }

  protected void writeToBuildFile(@NotNull String text) throws IOException {
    writeToFile(myBuildFile, text);
  }

  @NotNull
  protected GradleSettingsModel getGradleSettingsModel() {
    GradleSettingsModel settingsModel = GradleSettingsModel.get(myProject);
    assertNotNull(settingsModel);
    return settingsModel;
  }

  @NotNull
  protected GradleBuildModel getGradleBuildModel() {
    GradleBuildModel buildModel = GradleBuildModel.get(myModule);
    assertNotNull(buildModel);
    return buildModel;
  }
}
