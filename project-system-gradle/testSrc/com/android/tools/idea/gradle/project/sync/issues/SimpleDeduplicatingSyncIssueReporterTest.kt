/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.issues

import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.model.impl.IdeSyncIssueImpl
import com.google.common.truth.Truth
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import java.io.File

class SimpleDeduplicatingSyncIssueReporterTest : HeavyPlatformTestCase() {

  fun testShouldReport() {
    val reporter = object: SimpleDeduplicatingSyncIssueReporter() {
      override fun getSupportedIssueType(): Int = IdeSyncIssue.TYPE_GENERIC
    }

    val syncIssues = listOf(IdeSyncIssueImpl(
      severity = IdeSyncIssue.SEVERITY_WARNING,
      type = IdeSyncIssue.TYPE_GENERIC,
      data = "key",
      message = "Warning message!",
      multiLineMessage = null
    ));
    val moduleMap = mapOf<IdeSyncIssue, Module>(syncIssues[0] to module)
    val buildFileMap = mapOf<Module, VirtualFile>(module to getVirtualFile(File("")))
    Truth.assertThat(reporter.reportAll(
      syncIssues,
      moduleMap,
      buildFileMap
    )).isNotEmpty()
  }

  fun testShouldNotReport() {
    val reporter = object: SimpleDeduplicatingSyncIssueReporter() {
      override fun getSupportedIssueType(): Int = IdeSyncIssue.TYPE_GENERIC
      override fun shouldReport(project: Project): Boolean = false
    }

    val syncIssues = listOf(IdeSyncIssueImpl(
      severity = IdeSyncIssue.SEVERITY_WARNING,
      type = IdeSyncIssue.TYPE_GENERIC,
      data = "key",
      message = "Warning message!",
      multiLineMessage = null
    ));
    val moduleMap = mapOf<IdeSyncIssue, Module>(syncIssues[0] to module)
    val buildFileMap = mapOf<Module, VirtualFile>(module to getVirtualFile(File("")))
    Truth.assertThat(reporter.reportAll(
      syncIssues,
      moduleMap,
      buildFileMap
    )).isEmpty()
  }

}