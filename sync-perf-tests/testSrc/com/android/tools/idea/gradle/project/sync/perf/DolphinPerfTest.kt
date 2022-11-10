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

class DolphinPerfTestV2: AbstractGradleSyncPerfTestCase() {
  override val relativePath: String = DOLPHIN_PROJECT_ANDROID_ROOT
  override val projectName: String = "Dolphin_V2"
  override val useModelV2: Boolean = true

  @Before
  override fun setUp() {
    super.setUp()
    val dolphinSource: File = projectRule.resolveTestDataPath(DOLPHIN_PROJECT_ROOT)
    val ideaProjectDolphinSource = File(FileUtil.toSystemDependentName(projectRule.project.basePath!!), "native")
    FileUtil.copyDir(dolphinSource, ideaProjectDolphinSource)
  }
}