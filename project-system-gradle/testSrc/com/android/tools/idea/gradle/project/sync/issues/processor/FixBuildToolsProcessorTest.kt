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
package com.android.tools.idea.gradle.project.sync.issues.processor

import com.android.SdkConstants
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil.getGradleBuildFile
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.collect.ImmutableList
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewBundle
import org.junit.Test

class FixBuildToolsProcessorTest : AndroidGradleTestCase() {
  @Test
  fun testRemoveUsageViewDescriptor() {
    val processor = FixBuildToolsProcessor(project, ImmutableList.of(), "77.7.7", false, true)
    val usageDescriptor = processor.createUsageViewDescriptor(UsageInfo.EMPTY_ARRAY)
    assertEquals("Values to remove " + UsageViewBundle.getReferencesString(1, 1),
                 usageDescriptor.getCodeReferencesText(1, 1))
    assertEquals("Remove Android Build Tools Versions", usageDescriptor.processedElementsHeader)
  }

  @Test
  fun testUpdateUsageViewDescriptor() {
    val processor = FixBuildToolsProcessor(project, ImmutableList.of(), "77.7.7", false, false)
    val usageDescriptor = processor.createUsageViewDescriptor(UsageInfo.EMPTY_ARRAY)
    assertEquals("Values to update " + UsageViewBundle.getReferencesString(1, 1),
                 usageDescriptor.getCodeReferencesText(1, 1))
    assertEquals("Update Android Build Tools Versions", usageDescriptor.processedElementsHeader)
  }

  @Test
  fun testFindUsages() {
    loadSimpleApplication()
    val module = getModule("app")
    val file = getGradleBuildFile(module)!!

    val processor = FixBuildToolsProcessor(project, ImmutableList.of(file), "77.7.7", false, false)
    val usages = processor.findUsages()
    assertSize(1, usages)
    assertEquals('"' + SdkConstants.CURRENT_BUILD_TOOLS_VERSION + '"', usages[0].element!!.text)
  }

  @Test
  fun testPerformRefactoring() {
    loadSimpleApplication()
    val module = getModule("app")
    val file = getGradleBuildFile(module)!!

    val processor = FixBuildToolsProcessor(project, ImmutableList.of(file), "77.7.7", false, false)
    val usages = processor.findUsages()
    runWriteCommandAction(project) {
      processor.performRefactoring(usages)
    }

    assertTrue(String(file.contentsToByteArray()).contains("buildToolsVersion '77.7.7'"))
  }

  @Test
  fun testSyncAfterRefactor() {
    loadSimpleApplication()
    val module = getModule("app")
    val file = getGradleBuildFile(module)!!

    val processor = FixBuildToolsProcessor(project, ImmutableList.of(file), "77.7.7", true, false)
    val usages = processor.findUsages()
    var synced = false
    GradleSyncState.subscribe(project, object : GradleSyncListener {
      override fun syncSucceeded(project: Project) {
        synced = true
      }
    })

    runWriteCommandAction(project) {
      processor.performRefactoring(usages)
    }

    assertTrue(synced)
  }
}