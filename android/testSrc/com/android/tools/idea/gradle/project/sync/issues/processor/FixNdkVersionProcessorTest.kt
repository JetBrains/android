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
package com.android.tools.idea.gradle.project.sync.issues.processor

import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.HELLO_JNI
import com.google.common.collect.ImmutableList
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewBundle
import org.junit.Test


class FixNdkVersionProcessorTest : AndroidGradleTestCase() {
  @Test
  fun testUsageViewDescriptor() {
    val processor = FixNdkVersionProcessor(project, ImmutableList.of(), "77.7.7")
    val usageDescriptor = processor.createUsageViewDescriptor(UsageInfo.EMPTY_ARRAY)
    assertEquals("Values to update " + UsageViewBundle.getReferencesString(1, 1),
                 usageDescriptor.getCodeReferencesText(1, 1))
    assertEquals("Update Android NDK Versions", usageDescriptor.processedElementsHeader)
  }

  @Test
  fun testUpdateUsageViewDescriptor() {
    val processor = FixNdkVersionProcessor(project, ImmutableList.of(), "77.7.7")
    val usageDescriptor = processor.createUsageViewDescriptor(UsageInfo.EMPTY_ARRAY)
    assertEquals("Values to update " + UsageViewBundle.getReferencesString(1, 1),
                 usageDescriptor.getCodeReferencesText(1, 1))
    assertEquals("Update Android NDK Versions", usageDescriptor.processedElementsHeader)
  }

  @Test
  fun testFindUsages() {
    loadProject(HELLO_JNI)
    val module = getModule("app")
    val file = GradleUtil.getGradleBuildFile(module)!!

    val processor = FixNdkVersionProcessor(project, ImmutableList.of(file), "77.7.7")
    val usages = processor.findUsages()
    assertSize(1, usages)
    assertEquals("\"21.4.7075529\"", usages[0].element!!.text)
  }

  @Test
  fun testPerformRefactoring() {
    loadProject(HELLO_JNI)
    val module = getModule("app")
    val file = GradleUtil.getGradleBuildFile(module)!!

    val processor = FixNdkVersionProcessor(project, ImmutableList.of(file), "77.7.7")
    val usages = processor.findUsages()
    var synced = false
    GradleSyncState.subscribe(project, object : GradleSyncListener {
      override fun syncFailed(project: Project, errorMessage: String) {
        // It fails with 77.7.7.
        synced = true
      }
    })

    WriteCommandAction.runWriteCommandAction(project) {
      processor.performRefactoring(usages)
    }

    assertTrue(String(file.contentsToByteArray()).contains("ndkVersion '77.7.7'"))
    assertTrue(synced)
  }

}
