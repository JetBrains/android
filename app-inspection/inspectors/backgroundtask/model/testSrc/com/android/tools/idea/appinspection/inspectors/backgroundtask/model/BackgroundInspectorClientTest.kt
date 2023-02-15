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
import androidx.work.inspection.WorkManagerInspectorProtocol.Data
import androidx.work.inspection.WorkManagerInspectorProtocol.DataEntry
import androidx.work.inspection.WorkManagerInspectorProtocol.Event
import androidx.work.inspection.WorkManagerInspectorProtocol.TrackWorkManagerCommand
import androidx.work.inspection.WorkManagerInspectorProtocol.WorkAddedEvent
import androidx.work.inspection.WorkManagerInspectorProtocol.WorkInfo
import androidx.work.inspection.WorkManagerInspectorProtocol.WorkRemovedEvent
import androidx.work.inspection.WorkManagerInspectorProtocol.WorkUpdatedEvent
import backgroundtask.inspection.BackgroundTaskInspectorProtocol
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.BackgroundTaskEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.WorkEntry
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class BackgroundInspectorClientTest {

  private class FakeAppInspectorMessenger(
    override val scope: CoroutineScope,
    private val singleRawCommandResponse: ByteArray = ByteArray(0)
  ) : AppInspectorMessenger {
    lateinit var rawDataSent: ByteArray
    override suspend fun sendRawCommand(rawData: ByteArray): ByteArray {
      rawDataSent = rawData
      return singleRawCommandResponse
    }

    override val eventFlow = emptyFlow<ByteArray>()
  }

  /** A fake class to cache latest background task entry updates. */
  private class Listener(client: BackgroundTaskInspectorClient) {
    private lateinit var type: EntryUpdateEventType
    private lateinit var entry: BackgroundTaskEntry

    init {
      client.addEntryUpdateEventListener { type, entry ->
        this.type = type
        this.entry = entry
      }
    }

    /** Waits for the single threaded scope to update its listeners and consumes the latest one. */
    fun consume(listener: EntryUpdateEventListener) = listener(type, entry)
  }

  private lateinit var scope: CoroutineScope
  private lateinit var backgroundTaskInspectorMessenger: FakeAppInspectorMessenger
  private lateinit var workManagerInspectorMessenger: FakeAppInspectorMessenger
  private lateinit var client: BackgroundTaskInspectorClient
  private lateinit var listener: Listener

  @Before
  fun setUp() {
    scope = CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher() + SupervisorJob())
    backgroundTaskInspectorMessenger = FakeAppInspectorMessenger(scope)
    workManagerInspectorMessenger = FakeAppInspectorMessenger(scope)
    client =
      BackgroundTaskInspectorClient(
        backgroundTaskInspectorMessenger,
        WmiMessengerTarget.Resolved(workManagerInspectorMessenger),
        scope,
        StubBackgroundTaskInspectorTracker()
      )
    listener = Listener(client)
  }

  @After
  fun tearDown() {
    scope.cancel()
  }

  @Test
  fun startTrackingBackgroundTaskEvents_messengersSendCorrectCommands() = runBlocking {
    val workCommand =
      WorkManagerInspectorProtocol.Command.newBuilder()
        .setTrackWorkManager(TrackWorkManagerCommand.getDefaultInstance())
        .build()
    workManagerInspectorMessenger.sendRawCommand(workCommand.toByteArray())
    val backgroundTaskCommand =
      BackgroundTaskInspectorProtocol.Command.newBuilder()
        .setTrackBackgroundTask(
          BackgroundTaskInspectorProtocol.TrackBackgroundTaskCommand.getDefaultInstance()
        )
        .build()
    backgroundTaskInspectorMessenger.sendRawCommand(backgroundTaskCommand.toByteArray())

    assertThat(workManagerInspectorMessenger.rawDataSent).isEqualTo(workCommand.toByteArray())
    assertThat(backgroundTaskInspectorMessenger.rawDataSent)
      .isEqualTo(backgroundTaskCommand.toByteArray())
  }

  @Test
  fun addNewWork() = runBlocking {
    val id = "Test"
    val workInfo = WorkInfo.newBuilder().setId(id).build()

    sendWorkAddedEvent(workInfo)
    listener.consume { type, entry ->
      assertThat(type).isEqualTo(EntryUpdateEventType.ADD)
      assertThat((entry as WorkEntry).getWorkInfo()).isEqualTo(workInfo)
    }
  }

  @Test
  fun removeWorks() {
    val id1 = "Test1"
    val id2 = "Test2"
    val workInfo1 = WorkInfo.newBuilder().setId(id1).build()
    val workInfo2 = WorkInfo.newBuilder().setId(id2).build()

    sendWorkAddedEvent(workInfo1)
    sendWorkAddedEvent(workInfo2)

    sendWorkRemovedEvent(id2)
    listener.consume { type, entry ->
      assertThat(type).isEqualTo(EntryUpdateEventType.REMOVE)
      assertThat((entry as WorkEntry).getWorkInfo()).isEqualTo(workInfo2)
      assertThat(client.getEntry(workInfo2.id)).isNull()
    }

    sendWorkRemovedEvent(id1)
    listener.consume { type, entry ->
      assertThat(type).isEqualTo(EntryUpdateEventType.REMOVE)
      assertThat((entry as WorkEntry).getWorkInfo()).isEqualTo(workInfo1)
      assertThat(client.getEntry(workInfo1.id)).isNull()
    }
  }

  @Test
  fun updateWork() {
    val id = "Test"
    val workInfo = WorkInfo.newBuilder().setId(id).build()
    sendWorkAddedEvent(workInfo)

    val workStateUpdatedEvent =
      WorkUpdatedEvent.newBuilder().setId(id).setState(WorkInfo.State.ENQUEUED).build()
    client.handleEvent(
      EventWrapper(EventWrapper.Case.WORK, workStateUpdatedEvent.toEvent().toByteArray())
    )

    listener.consume { type, entry ->
      assertThat(type).isEqualTo(EntryUpdateEventType.UPDATE)
      assertThat(entry.id).isEqualTo(id)
      assertThat((client.getEntry(entry.id) as WorkEntry).getWorkInfo().state)
        .isEqualTo(WorkInfo.State.ENQUEUED)
    }

    val data =
      Data.newBuilder()
        .addEntries(DataEntry.newBuilder().setKey("key").setValue("value").build())
        .build()
    val workDataUpdatedEvent = WorkUpdatedEvent.newBuilder().setId(id).setData(data).build()
    client.handleEvent(
      EventWrapper(EventWrapper.Case.WORK, workDataUpdatedEvent.toEvent().toByteArray())
    )
    listener.consume { type, entry ->
      assertThat(type).isEqualTo(EntryUpdateEventType.UPDATE)
      assertThat(entry.id).isEqualTo(id)
      assertThat((client.getEntry(entry.id) as WorkEntry).getWorkInfo().data).isEqualTo(data)
    }

    val runAttemptCount = 1
    val workRunAttemptCountUpdatedEvent =
      WorkUpdatedEvent.newBuilder().setId(id).setRunAttemptCount(runAttemptCount).build()

    client.handleEvent(
      EventWrapper(EventWrapper.Case.WORK, workRunAttemptCountUpdatedEvent.toEvent().toByteArray())
    )
    listener.consume { type, entry ->
      assertThat(type).isEqualTo(EntryUpdateEventType.UPDATE)
      assertThat(entry.id).isEqualTo(id)
      assertThat((client.getEntry(entry.id) as WorkEntry).getWorkInfo().runAttemptCount)
        .isEqualTo(runAttemptCount)
    }

    val scheduleRequestedAt = 10L
    val workScheduleRequestedAtUpdatedEvent =
      WorkUpdatedEvent.newBuilder().setId(id).setScheduleRequestedAt(scheduleRequestedAt).build()
    client.handleEvent(
      EventWrapper(
        EventWrapper.Case.WORK,
        workScheduleRequestedAtUpdatedEvent.toEvent().toByteArray()
      )
    )
    listener.consume { type, entry ->
      assertThat(type).isEqualTo(EntryUpdateEventType.UPDATE)
      assertThat(entry.id).isEqualTo(id)
      assertThat((client.getEntry(entry.id) as WorkEntry).getWorkInfo().scheduleRequestedAt)
        .isEqualTo(scheduleRequestedAt)
    }
  }

  @Test
  fun getWorkChain() {
    /**
     * Constructs a a dependency graph that looks like:
     * ```
     *      work1  work2
     *     /    \   /  \
     * ```
     * work3 work4 work5
     * ```
     *          /   \
     *       work6  work7    work8
     * ```
     */
    val workIdList = (1..8).map { "work${it}" }
    val dependencyList =
      listOf(Pair(1, 3), Pair(1, 4), Pair(2, 4), Pair(2, 5), Pair(4, 6), Pair(4, 7)).map {
        Pair("work${it.first}", "work${it.second}")
      }
    val workInfoList =
      workIdList.map { id ->
        WorkInfo.newBuilder()
          .setId(id)
          .addAllPrerequisites(dependencyList.filter { it.second == id }.map { it.first })
          .addAllDependents(dependencyList.filter { it.first == id }.map { it.second })
          .build()
      }
    for (workInfo in workInfoList) {
      sendWorkAddedEvent(workInfo)
    }

    val complexWorkChain = client.getOrderedWorkChain("work4").map { it.id }
    assertThat(complexWorkChain.size).isEqualTo(7)
    for ((from, to) in dependencyList) {
      assertThat(complexWorkChain.indexOf(from)).isLessThan(complexWorkChain.indexOf(to))
    }

    val singleWorkChain = client.getOrderedWorkChain("work8").map { it.id }
    assertThat(singleWorkChain.size).isEqualTo(1)
    assertThat(singleWorkChain[0]).isEqualTo("work8")
  }

  private fun sendWorkAddedEvent(workInfo: WorkInfo) {
    val workAddedEvent = WorkAddedEvent.newBuilder().setWork(workInfo).build()
    val event = Event.newBuilder().setWorkAdded(workAddedEvent).build()
    client.handleEvent(EventWrapper(EventWrapper.Case.WORK, event.toByteArray()))
  }

  private fun sendWorkRemovedEvent(id: String) {
    val workRemovedEvent = WorkRemovedEvent.newBuilder().setId(id).build()
    val event = Event.newBuilder().setWorkRemoved(workRemovedEvent).build()
    client.handleEvent(EventWrapper(EventWrapper.Case.WORK, event.toByteArray()))
  }

  private fun WorkUpdatedEvent.toEvent() = Event.newBuilder().setWorkUpdated(this).build()
}
