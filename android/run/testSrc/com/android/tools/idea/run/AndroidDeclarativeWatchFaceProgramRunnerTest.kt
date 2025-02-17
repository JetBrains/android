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
package com.android.tools.idea.run

import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.run.configuration.AndroidDeclarativeWatchFaceConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.testFramework.ProjectRule
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AndroidDeclarativeWatchFaceProgramRunnerTest {

  @get:Rule
  val projectRule = ProjectRule()

  private lateinit var declarativeWatchFaceRunConfiguration: RunConfiguration
  private lateinit var programRunner: AndroidDeclarativeWatchFaceProgramRunner

  private var isSyncInProgress = false
  private var isSyncNeeded = false

  @Before
  fun setup() {
    val factory = AndroidDeclarativeWatchFaceConfigurationType().configurationFactories[0]
    declarativeWatchFaceRunConfiguration = factory.createTemplateConfiguration(projectRule.project)

    val syncManager = object: ProjectSystemSyncManager {
      override fun requestSyncProject(reason: ProjectSystemSyncManager.SyncReason) = throw IllegalStateException("not implemented")
      override fun getLastSyncResult() = throw IllegalStateException("not implemented")

      override fun isSyncInProgress() = isSyncInProgress

      override fun isSyncNeeded() = isSyncNeeded
    }

    programRunner = AndroidDeclarativeWatchFaceProgramRunner { syncManager }
  }

  @Test
  fun `cannot run as debug`() {
    assertFalse(programRunner.canRun(DefaultDebugExecutor.EXECUTOR_ID, declarativeWatchFaceRunConfiguration))
  }

  @Test
  fun `cannot run if sync is in progress`() {
    isSyncInProgress = true
    assertFalse(programRunner.canRun(DefaultRunExecutor.EXECUTOR_ID, declarativeWatchFaceRunConfiguration))
  }

  @Test
  fun `cannot run if sync is needed`() {
    isSyncNeeded = true
    assertFalse(programRunner.canRun(DefaultRunExecutor.EXECUTOR_ID, declarativeWatchFaceRunConfiguration))
  }

  @Test
  fun `cannot run on non-AndroidDeclarativeWatchFace run configurations`() {
    val runConfiguration = AndroidRunConfigurationType.getInstance().factory.createTemplateConfiguration(projectRule.project)
    assertFalse(programRunner.canRun(DefaultRunExecutor.EXECUTOR_ID, runConfiguration))
  }

  @Test
  fun `can run on AndroidDeclarativeWatchFace run configuration`() {
    assertTrue(programRunner.canRun(DefaultRunExecutor.EXECUTOR_ID, declarativeWatchFaceRunConfiguration))
  }
}