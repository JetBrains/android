/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.issues.processor.runsGradle

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.project.sync.issues.processor.UpdateCompileSdkProcessor
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.junit.Test

class UpdateCompileSdkProcessorTest : AndroidGradleTestCase() {
  private lateinit var appModule: Module
  private lateinit var buildFile: VirtualFile
  private var currentCompileSdkVersion: Int = 0

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    loadSimpleApplication()
    // setup test project module with a lower compileSdk. In the test, we update the compileSdk to latest using
    // UpdateCompileSdkProcessor.
    decrementCompileSdkVersionForTesting()
  }

  private fun decrementCompileSdkVersionForTesting() {
    appModule = getModule("app")
    val projectBuildModel: ProjectBuildModel = ProjectBuildModel.get(project)
    buildFile = GradleProjectSystemUtil.getGradleBuildFile(appModule)!!
    val androidBuildModel = projectBuildModel.getModuleBuildModel(buildFile).android()
    val existingVersion = androidBuildModel.compileSdkVersion().toString().toInt()
    currentCompileSdkVersion = existingVersion - 1
    WriteCommandAction.runWriteCommandAction(project) {
      androidBuildModel.compileSdkVersion().setValue(currentCompileSdkVersion)
      projectBuildModel.applyChanges()
    }
  }

  @Test
  fun testFindUsages() {
    val newCompileSdkVersion = currentCompileSdkVersion + 1
    val processor = UpdateCompileSdkProcessor(project, mapOf(buildFile to newCompileSdkVersion))
    val usages = processor.findUsages()
    assertSize(1, usages)
    assertEquals("""$currentCompileSdkVersion""", usages[0].element!!.text)
  }

  @Test
  fun testPerformRefactoring() {
    val newCompileSdkVersion = currentCompileSdkVersion + 1
    val processor = UpdateCompileSdkProcessor(project, mapOf(buildFile to newCompileSdkVersion))
    val usages = processor.findUsages()
    var synced = false
    GradleSyncState.subscribe(project, object : GradleSyncListener {
      override fun syncSucceeded(project: Project) {
        synced = true
      }
    })

    WriteCommandAction.runWriteCommandAction(project) {
      processor.performRefactoring(usages)
    }

    assertTrue(String(buildFile.contentsToByteArray()).contains("compileSdk $newCompileSdkVersion"))
    assertTrue(synced)
  }
}
