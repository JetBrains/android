/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.model

import androidx.work.inspection.WorkManagerInspectorProtocol
import androidx.work.inspection.WorkManagerInspectorProtocol.CallStack
import androidx.work.inspection.WorkManagerInspectorProtocol.Constraints
import androidx.work.inspection.WorkManagerInspectorProtocol.Data
import androidx.work.inspection.WorkManagerInspectorProtocol.DataEntry
import androidx.work.inspection.WorkManagerInspectorProtocol.WorkInfo
import backgroundtask.inspection.BackgroundTaskInspectorProtocol
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.google.common.truth.Truth.assertThat
import javax.swing.tree.DefaultMutableTreeNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.emptyFlow

object BackgroundTaskInspectorTestUtils {
  private class FakeAppInspectorMessenger(override val scope: CoroutineScope) :
    AppInspectorMessenger {
    override suspend fun sendRawCommand(rawData: ByteArray): ByteArray = ByteArray(0)
    override val eventFlow = emptyFlow<ByteArray>()
  }

  val FAKE_WORK_INFO: WorkInfo =
    WorkInfo.newBuilder()
      .apply {
        id = "ID1"
        workerClassName = "package1.package2.ClassName1"
        addAllTags(listOf("tag1", "tag2"))
        state = WorkInfo.State.ENQUEUED
        scheduleRequestedAt = 0L
        runAttemptCount = 1

        val frame1 =
          CallStack.Frame.newBuilder()
            .setClassName("pkg1.Class1")
            .setFileName("File1")
            .setMethodName("method1")
            .setLineNumber(12)
            .build()
        val frame2 =
          CallStack.Frame.newBuilder()
            .setClassName("pkg2.Class2")
            .setFileName("File2")
            .setMethodName("method2")
            .setLineNumber(33)
            .build()
        callStack = CallStack.newBuilder().addAllFrames(listOf(frame1, frame2)).build()

        data =
          Data.newBuilder()
            .addEntries(DataEntry.newBuilder().setKey("k").setValue("v").build())
            .build()

        constraints =
          Constraints.newBuilder().setRequiredNetworkType(Constraints.NetworkType.CONNECTED).build()
        isPeriodic = false
        addPrerequisites("prerequisiteId")
        addDependents("dependentsId")
      }
      .build()

  fun getFakeClient(scope: CoroutineScope): BackgroundTaskInspectorClient {
    val backgroundTaskInspectorMessenger = FakeAppInspectorMessenger(scope)
    val workManagerInspectorMessenger = FakeAppInspectorMessenger(scope)
    return BackgroundTaskInspectorClient(
      backgroundTaskInspectorMessenger,
      WmiMessengerTarget.Resolved(workManagerInspectorMessenger),
      scope,
      StubBackgroundTaskInspectorTracker()
    )
  }

  fun BackgroundTaskInspectorClient.sendWorkEvent(
    map: WorkManagerInspectorProtocol.Event.Builder.() -> Unit
  ) {
    handleEvent(
      EventWrapper(
        EventWrapper.Case.WORK,
        WorkManagerInspectorProtocol.Event.newBuilder().apply(map).build().toByteArray()
      )
    )
  }

  fun BackgroundTaskInspectorClient.sendBackgroundTaskEvent(
    timestamp: Long,
    map: BackgroundTaskInspectorProtocol.BackgroundTaskEvent.Builder.() -> Unit
  ): BackgroundTaskInspectorProtocol.Event {
    val event =
      BackgroundTaskInspectorProtocol.Event.newBuilder()
        .apply {
          this.timestamp = timestamp
          backgroundTaskEventBuilder.map()
        }
        .build()
    handleEvent(EventWrapper(EventWrapper.Case.BACKGROUND_TASK, event.toByteArray()))
    return event
  }

  fun BackgroundTaskInspectorClient.sendWorkRemovedEvent(id: String) {
    sendWorkEvent { workRemovedBuilder.apply { this.id = id } }
  }

  fun BackgroundTaskInspectorClient.sendWorkAddedEvent(work: WorkInfo) {
    sendWorkEvent { workAddedBuilder.apply { this.work = work } }
  }

  private fun DefaultMutableTreeNode.getCategoryNode(type: String): BackgroundTaskCategoryNode {
    return children().asSequence().first { (it as BackgroundTaskCategoryNode).name == type } as
      BackgroundTaskCategoryNode
  }

  fun DefaultMutableTreeNode.getWorksCategoryNode() = getCategoryNode("Workers")
  fun DefaultMutableTreeNode.getAlarmsCategoryNode() = getCategoryNode("Alarms")
  fun DefaultMutableTreeNode.getJobsCategoryNode() = getCategoryNode("Jobs")
  fun DefaultMutableTreeNode.getWakeLocksCategoryNode() = getCategoryNode("WakeLocks")

  fun createJobInfoExtraWithWorkerId(id: String) = "{EXTRA_WORK_SPEC_ID=$id}"

  fun BackgroundTaskCategoryNode.assertEmptyWithMessage(message: String) {
    assertThat(childCount).isEqualTo(1)
    assertThat((firstChild as EmptyMessageNode).message).isEqualTo(message)
  }
}
