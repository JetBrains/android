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
package com.android.tools.idea.gradle.project.sync.perf;

import static com.android.tools.idea.gradle.project.sync.perf.TestProjectPaths.DOLPHIN_PROJECT_ANDROID_ROOT;
import static com.android.tools.idea.gradle.project.sync.perf.TestProjectPaths.DOLPHIN_PROJECT_ROOT;

import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import org.jetbrains.annotations.NotNull;

/**
 * Measure performance for IDEA sync using the dolphin project.
 */
public class DolphinFullPerfTest extends GradleSyncPerfTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    File dolphinSource = resolveTestDataPath(DOLPHIN_PROJECT_ROOT);
    // The dolphin project place the Android project under <project root>/Source/Android. So the IDEA project root does not contain native
    // source code. Therefore we manually copy all the native source code to a subfolder 'native' under the IDEA project root. This works
    // because the diff patch (setupForSyncTest) we applied already changed build.gradle to refer to the CMakeLists.txt under this "native"
    // directory.
    File ideaProjectDolphinSource = new File(FileUtil.toSystemDependentName(getProject().getBasePath()), "native");
    FileUtil.copyDir(dolphinSource, ideaProjectDolphinSource);
  }

  @NotNull
  @Override
  public String getProjectName() {
    return "Dolphin";
  }

  @NotNull
  @Override
  public String getRelativePath() {
    return DOLPHIN_PROJECT_ANDROID_ROOT;
  }

  @Override
  protected boolean useSingleVariantSyncInfrastructure() {
    return false;
  }
}
