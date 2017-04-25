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
package com.android.tools.idea.tests.gui.debugger;

import com.android.testutils.TestUtils;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.intellij.openapi.application.PathManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

import static com.intellij.openapi.util.io.FileUtil.toCanonicalPath;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

public class NativeDebuggerGuiTestRule extends GuiTestRule {
  /** Environment variable containing the full path to an NDK install */
  private static final String TEST_NDK_PATH_ENV = "ANDROID_NDK_HOME";
  private static final String TEST_DATA_DIR_PATH = PathManager.getHomePath() + "/../adt/idea/android-uitests/testData/debugger/";

  @Override
  @NotNull
  protected File getMasterProjectDirPath(@NotNull String projectDirName) {
    return new File(toCanonicalPath(toSystemDependentName(TEST_DATA_DIR_PATH + projectDirName)));
  }

  @Override
  protected void updateLocalProperties(@NotNull File projectPath) throws IOException {
    super.updateLocalProperties(projectPath);
    LocalProperties localProperties = new LocalProperties(projectPath);
    localProperties.setAndroidNdkPath(getAndroidNdkPath());
    localProperties.save();
  }

  @NotNull
  public static File getAndroidNdkPath() {
    return TestUtils.getNdk();
  }

}
