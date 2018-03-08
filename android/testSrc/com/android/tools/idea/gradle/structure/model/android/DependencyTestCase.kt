/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.android

import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.AndroidGradleTests.getLocalRepositories
import com.android.tools.idea.testing.TestProjectPaths.PSD_SAMPLE_REPO
import com.intellij.util.PathUtil.toSystemDependentName
import org.jetbrains.android.AndroidTestBase
import java.io.File

abstract class DependencyTestCase : AndroidGradleTestCase() {
  override fun updateVersionAndDependencies(projectRoot: File) {
    val localRepositories = getLocalRepositories()
    val testRepositoryPath = File(AndroidTestBase.getTestDataPath(), toSystemDependentName(PSD_SAMPLE_REPO)).absolutePath!!
    val repositories = """
      maven {
        name 'test'
        url 'file:$testRepositoryPath'
      }
      $localRepositories
      """
    AndroidGradleTests.updateGradleVersions(projectRoot, repositories, null)
  }
}