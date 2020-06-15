/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.perf

import com.android.tools.idea.gradle.project.sync.perf.TestProjectPaths.DOLPHIN_PROJECT_ANDROID_ROOT
import com.android.tools.idea.gradle.project.sync.perf.TestProjectPaths.DOLPHIN_PROJECT_ROOT
import com.intellij.openapi.util.io.FileUtil
import org.junit.Before
import java.io.File

class DolphinPerfTest(useSingleVariantSyncInfrastructure: Boolean, gradleVersion: String?, agpVersion: String?):
  AbstractGradleSyncPerfTestCase(useSingleVariantSyncInfrastructure, gradleVersion, agpVersion) {
  override val relativePath: String = DOLPHIN_PROJECT_ANDROID_ROOT
  override val projectName: String = "Dolphin"

  @Before
  override fun setUp() {
    super.setUp()
    // The dolphin project place the Android project under <project root>/Source/Android. So the IDEA project root does not contain native
    // source code. Therefore we manually copy all the native source code to a subfolder 'native' under the IDEA project root. This works
    // because the diff patch (setupForSyncTest) we applied already changed build.gradle to refer to the CMakeLists.txt under this "native"
    // directory.
    val dolphinSource: File = projectRule.resolveTestDataPath(DOLPHIN_PROJECT_ROOT)
    val ideaProjectDolphinSource = File(FileUtil.toSystemDependentName(projectRule.project.basePath!!), "native")
    FileUtil.copyDir(dolphinSource, ideaProjectDolphinSource)
  }
}