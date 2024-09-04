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

import com.android.SdkConstants
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker.Companion.getInstance
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.project.sync.issues.processor.FixBuildToolsProcessor
import com.android.tools.idea.gradle.project.sync.requestProjectSync
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.collect.ImmutableList
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_QF_BUILD_TOOLS_VERSION_CHANGED
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import org.junit.Test

class FixBuildToolsProcessorIntegrationTest : AndroidGradleTestCase() {
  @Test
  fun testFindUsages() {
    loadSimpleApplication()
    val module = getModule("app")
    val file = GradleProjectSystemUtil.getGradleBuildFile(module)!!

    val processor = FixBuildToolsProcessor(project, ImmutableList.of(file), "77.7.7", false, false)
    val usages = processor.findUsages()
    assertSize(1, usages)
    assertEquals('"' + SdkConstants.CURRENT_BUILD_TOOLS_VERSION + '"', usages[0].element!!.text)
  }

  @Test
  fun testPerformRefactoring() {
    loadSimpleApplication()
    val module = getModule("app")
    val file = GradleProjectSystemUtil.getGradleBuildFile(module)!!

    val processor = FixBuildToolsProcessor(project, ImmutableList.of(file), "77.7.7", false, false)
    val usages = processor.findUsages()
    WriteCommandAction.runWriteCommandAction(project) {
      processor.performRefactoring(usages)
    }

    assertTrue(String(file.contentsToByteArray()).contains("buildToolsVersion '77.7.7'"))
  }

  @Test
  fun testSyncAfterRefactor() {
    loadSimpleApplication()
    val module = getModule("app")
    val file = GradleProjectSystemUtil.getGradleBuildFile(module)!!

    val processor = FixBuildToolsProcessor(project, ImmutableList.of(file), "77.7.7", false, false)
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
    getInstance().requestProjectSync(project, TRIGGER_QF_BUILD_TOOLS_VERSION_CHANGED)

    assertTrue(synced)
  }
}