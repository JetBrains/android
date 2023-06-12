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
package com.android.tools.idea.testartifacts.instrumented.testsuite.actions

import com.android.annotations.concurrency.UiThread
import com.android.tools.concurrency.AndroidIoManager
import com.android.tools.idea.testartifacts.instrumented.testsuite.export.getTestStartTime
import com.intellij.execution.TestStateStorage
import com.intellij.execution.testframework.sm.TestHistoryConfiguration
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.concurrentMapOf
import java.io.File
import java.util.concurrent.ExecutorService

/**
 * Customized import test group action which supports additional test format
 * such as UTP test results.
 */
class ImportTestGroup(
  private val backgroundExecutor: ExecutorService = AndroidIoManager.getInstance().getBackgroundDiskIoExecutor()
) : com.intellij.execution.testframework.sm.runner.history.actions.ImportTestsGroup() {

  private data class IntelliJStandardTestHistoryTimestamp(
    val lastModifiedTime: Long,
    val testStartTime: Long,
  )

  private val timestampMap: MutableMap<File, IntelliJStandardTestHistoryTimestamp> = concurrentMapOf()

  // TODO(b/244935095): remove when getChildren() no longer expects/requires @UiThread
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  @UiThread
  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val project = e?.project ?: return EMPTY_ARRAY
    val actions: MutableMap<Long, AnAction> = sortedMapOf( compareByDescending { it } )
    getUtpTestHistoryActions(project).associateTo(actions) { it }
    getIntelliJStandardTestHistoryActions(project).associateTo(actions) { it }
    return actions.values.toTypedArray()
  }

  @UiThread
  private fun getIntelliJStandardTestHistoryActions(project: Project): Sequence<Pair<Long, AnAction>> {
    val testHistoryRoot = TestStateStorage.getTestHistoryRoot(project)
    return TestHistoryConfiguration.getInstance(project).files.asSequence()
      .map { fileName: String? -> File(testHistoryRoot, fileName) }
      .filter { file: File -> file.exists() }
      .map {
        val lastModifiedTime = it.lastModified()
        val timestamp = timestampMap[it]
        if (timestamp?.lastModifiedTime != lastModifiedTime) {
          // If timestamp entry is not available yet, we use file's last modified timestamp
          // for now until we retrieve actual test start time from the XML file asynchronously
          // so that we don't block UI thread.
          updateTimestampMapForFileAsync(it)
          Pair(lastModifiedTime, ImportTestsFromHistoryAction(project, it))
        } else {
          Pair(timestamp.testStartTime, ImportTestsFromHistoryAction(project, it))
        }
      }
  }

  private fun getUtpTestHistoryActions(project: Project): Sequence<Pair<Long, AnAction>> {
    return sequence {
      val connectedDeviceAction = createImportUtpResultActionFromAndroidGradlePluginOutput(project)
      yieldAll(connectedDeviceAction.map { Pair(it.timestamp, it.action) })

      val managedDeviceActions = createImportGradleManagedDeviceUtpResults(project)
      yieldAll(managedDeviceActions.map { Pair(it.timestamp, it.action) })
    }
  }

  private fun updateTimestampMapForFileAsync(file: File) {
    backgroundExecutor.submit {
      timestampMap[file] = IntelliJStandardTestHistoryTimestamp(file.lastModified(), getTestStartTime(file))
    }
  }
}