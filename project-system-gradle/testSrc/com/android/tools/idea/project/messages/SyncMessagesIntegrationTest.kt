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
package com.android.tools.idea.project.messages

import com.android.tools.idea.gradle.project.build.output.BuildOutputErrorsListener
import com.android.tools.idea.gradle.project.sync.GradleSyncListenerWithRoot
import com.android.tools.idea.gradle.project.sync.GradleSyncNeededReason
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages
import com.android.tools.idea.project.hyperlink.SyncMessageFragment
import com.android.tools.idea.project.hyperlink.SyncMessageHyperlink
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncQuickFix
import com.intellij.build.BuildProgressListener
import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.internal.DummySyncViewManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.Project
import com.intellij.testFramework.replaceService
import com.intellij.util.ThreeState
import com.intellij.util.messages.MessageBusConnection
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * This test is for asserting that SyncMessage is converted to BuildEvent in SyncView as expected.
 */
class SyncMessagesIntegrationTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  val syncViewEvents = mutableListOf<BuildEvent>()

  @Before
  fun setUp() {
    projectRule.project.replaceService(
      SyncViewManager::class.java,
      object : DummySyncViewManager(projectRule.project) {
        override fun onEvent(buildId: Any, event: BuildEvent) {
          syncViewEvents.add(event)
        }
      },
      projectRule.testRootDisposable
    )

    val taskId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, projectRule.project)

    projectRule.project.replaceService(
      GradleSyncState::class.java,
      object : GradleSyncState {
        override val isSyncInProgress: Boolean = false
        override val externalSystemTaskId: ExternalSystemTaskId? = taskId
        override val lastSyncFinishedTimeStamp: Long = -1
        override val lastSyncedGradleVersion: GradleVersion? = null
        override fun lastSyncFailed(): Boolean = false
        override fun isSyncNeeded(): ThreeState = ThreeState.NO
        override fun getSyncNeededReason(): GradleSyncNeededReason? = null
        override fun subscribe(project: Project, listener: GradleSyncListenerWithRoot, disposable: Disposable): MessageBusConnection {
          error("Not supported in unit test mode")
        }
      },
      projectRule.testRootDisposable
    )
  }

  @Test
  fun multilineIssueDescription() {
    val message = SyncMessage(
      "group",
      MessageType.WARNING,
      "Line1", "Line2"
    ).apply {
      add(object : SyncMessageHyperlink("url", "url text") {
        override fun execute(project: Project) = Unit
        override val quickFixIds: List<GradleSyncQuickFix> = emptyList()
      })
    }

    GradleSyncMessages.getInstance(projectRule.project).report(message)

    Truth.assertThat(syncViewEvents).hasSize(1)
    syncViewEvents.first().let {
      Truth.assertThat(it).isInstanceOf(MessageEvent::class.java)
      Truth.assertThat(it).isInstanceOf(BuildIssueEvent::class.java)
      Truth.assertThat(it.message).isEqualTo("Line1")
      val issue = (it as BuildIssueEvent).issue
      Truth.assertThat(issue.title).isEqualTo("Line1")
      Truth.assertThat(issue.description).isEqualTo("""
        Line1
        Line2
        <a href="url">url text</a>
      """.trimIndent())
    }
  }
}