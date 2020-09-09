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
package com.android.tools.idea.appinspection.inspectors.workmanager.model

import androidx.work.inspection.WorkManagerInspectorProtocol.CancelWorkCommand
import androidx.work.inspection.WorkManagerInspectorProtocol.Command
import androidx.work.inspection.WorkManagerInspectorProtocol.Data
import androidx.work.inspection.WorkManagerInspectorProtocol.DataEntry
import androidx.work.inspection.WorkManagerInspectorProtocol.Event
import androidx.work.inspection.WorkManagerInspectorProtocol.TrackWorkManagerCommand
import androidx.work.inspection.WorkManagerInspectorProtocol.WorkAddedEvent
import androidx.work.inspection.WorkManagerInspectorProtocol.WorkInfo
import androidx.work.inspection.WorkManagerInspectorProtocol.WorkRemovedEvent
import androidx.work.inspection.WorkManagerInspectorProtocol.WorkUpdatedEvent
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WorkManagerInspectorClientTest {

  private class FakeAppInspectorMessenger(
    override val scope: CoroutineScope,
    private val singleRawCommandResponse: ByteArray = ByteArray(0)
  ) : AppInspectorMessenger {
    lateinit var rawDataSent: ByteArray
    override suspend fun sendRawCommand(rawData: ByteArray): ByteArray {
      rawDataSent = rawData
      return singleRawCommandResponse
    }

    override val rawEventFlow = emptyFlow<ByteArray>()
  }

  /**
   * A fake class to track the triggering events of worksChangedListener .
   */
  private class Listener(client: WorkManagerInspectorClient) {
    var count = 0

    init {
      client.addWorksChangedListener { count += 1 }
    }

    fun consume() = count.apply { count = 0 }
  }

  private lateinit var executor: ExecutorService
  private lateinit var scope: CoroutineScope
  private lateinit var messenger: FakeAppInspectorMessenger
  private lateinit var client: WorkManagerInspectorClient
  private lateinit var listener: Listener

  @Before
  fun setUp() {
    executor = Executors.newSingleThreadExecutor()
    scope = CoroutineScope(executor.asCoroutineDispatcher() + SupervisorJob())
    messenger = FakeAppInspectorMessenger(scope)
    client = WorkManagerInspectorClient(messenger, scope)
    listener = Listener(client)
  }

  @After
  fun tearDown() {
    scope.cancel()
    executor.shutdownNow()
  }

  @Test
  fun startTrackingWorkManager_messengerReceivesCommand() = runBlocking {
    val command = Command.newBuilder().setTrackWorkManager(TrackWorkManagerCommand.getDefaultInstance()).build()
    // This scope is single threaded so the following block should occur after sending commands.
    scope.launch { assertThat(messenger.rawDataSent).isEqualTo(command.toByteArray()) }.join()
  }

  @Test
  fun addNewWork() = runBlocking {
    val id = "Test"
    val workInfo = WorkInfo.newBuilder()
      .setId(id)
      .build()

    sendWorkAddedEvent(workInfo)
    assertThat(client.getWorkInfoCount()).isEqualTo(1)
    assertThat(client.getWorkInfoOrNull(0)!!.id).isEqualTo(id)
    assertThat(client.indexOfFirstWorkInfo { it.id == id }).isEqualTo(0)
    assertThat(listener.consume()).isGreaterThan(0)
  }

  @Test
  fun removeWorks() = runBlocking {
    val id1 = "Test1"
    val id2 = "Test2"
    val workInfo1 = WorkInfo.newBuilder()
      .setId(id1)
      .build()
    val workInfo2 = WorkInfo.newBuilder()
      .setId(id2)
      .build()

    sendWorkAddedEvent(workInfo1)
    sendWorkAddedEvent(workInfo2)
    assertThat(client.getWorkInfoCount()).isEqualTo(2)

    sendWorkRemovedEvent(id2)
    assertThat(client.getWorkInfoCount()).isEqualTo(1)
    assertThat(client.getWorkInfoOrNull(0)!!.id).isEqualTo(id1)

    sendWorkRemovedEvent(id1)
    assertThat(client.getWorkInfoCount()).isEqualTo(0)
  }

  @Test
  fun updateWork() = runBlocking {
    val id = "Test"
    val workInfo = WorkInfo.newBuilder()
      .setId(id)
      .build()
    sendWorkAddedEvent(workInfo)

    val workStateUpdatedEvent = WorkUpdatedEvent.newBuilder()
      .setId(id)
      .setState(WorkInfo.State.ENQUEUED)
      .build()
    client.handleEvent(workStateUpdatedEvent.toEvent().toByteArray())
    assertThat(listener.consume()).isGreaterThan(0)
    assertThat(client.getWorkInfoCount()).isEqualTo(1)
    assertThat(client.getWorkInfoOrNull(0)!!.state).isEqualTo(WorkInfo.State.ENQUEUED)

    val data = Data.newBuilder()
      .addEntries(DataEntry.newBuilder().setKey("key").setValue("value").build())
      .build()
    val workDataUpdatedEvent = WorkUpdatedEvent.newBuilder()
      .setId(id)
      .setData(data)
      .build()
    client.handleEvent(workDataUpdatedEvent.toEvent().toByteArray())
    assertThat(client.getWorkInfoCount()).isEqualTo(1)
    assertThat(client.getWorkInfoOrNull(0)!!.data).isEqualTo(data)
    assertThat(listener.consume()).isGreaterThan(0)

    val runAttemptCount = 1
    val workRunAttemptCountUpdatedEvent = WorkUpdatedEvent.newBuilder()
      .setId(id)
      .setRunAttemptCount(runAttemptCount)
      .build()
    client.handleEvent(workRunAttemptCountUpdatedEvent.toEvent().toByteArray())
    assertThat(client.getWorkInfoCount()).isEqualTo(1)
    assertThat(client.getWorkInfoOrNull(0)!!.runAttemptCount).isEqualTo(runAttemptCount)
    assertThat(listener.consume()).isGreaterThan(0)

    val scheduleRequestedAt = 10L
    val workScheduleRequestedAtUpdatedEvent = WorkUpdatedEvent.newBuilder()
      .setId(id)
      .setScheduleRequestedAt(scheduleRequestedAt)
      .build()
    client.handleEvent(workScheduleRequestedAtUpdatedEvent.toEvent().toByteArray())
    assertThat(client.getWorkInfoCount()).isEqualTo(1)
    assertThat(client.getWorkInfoOrNull(0)!!.scheduleRequestedAt).isEqualTo(scheduleRequestedAt)
    assertThat(listener.consume()).isGreaterThan(0)
  }

  @Test
  fun filterWork() = runBlocking {
    val id1 = "Test1"
    val id2 = "Test2"
    val id3 = "Test3"
    val tag1 = "TagForTest1"
    val tag2 = "TagForTest2AndTest3"
    val workInfo1 = WorkInfo.newBuilder()
      .setId(id1)
      .addTags(tag1)
      .build()
    val workInfo2 = WorkInfo.newBuilder()
      .setId(id2)
      .addTags(tag2)
      .build()
    val workInfo3 = WorkInfo.newBuilder()
      .setId(id3)
      .addTags(tag2)
      .build()

    sendWorkAddedEvent(workInfo1)
    sendWorkAddedEvent(workInfo2)
    sendWorkAddedEvent(workInfo3)
    assertThat(client.filterTag).isNull()
    assertThat(client.getWorkInfoCount()).isEqualTo(3)
    assertThat(client.getAllTags()).containsAllOf(tag1, tag2)
    assertThat(listener.consume()).isGreaterThan(0)

    client.filterTag = tag1
    assertThat(client.getWorkInfoCount()).isEqualTo(1)
    assertThat(client.getAllTags()).containsAllOf(tag1, tag2)
    assertThat(client.getWorkInfoOrNull(0)!!.id).isEqualTo(id1)
    assertThat(listener.consume()).isGreaterThan(0)

    client.filterTag = tag2
    assertThat(client.getWorkInfoCount()).isEqualTo(2)
    assertThat(client.getAllTags()).containsAllOf(tag1, tag2)
    assertThat(listOf(client.getWorkInfoOrNull(0)!!.id, client.getWorkInfoOrNull(1)!!.id)).containsAllOf(id2, id3)
    assertThat(listener.consume()).isGreaterThan(0)

    client.filterTag = null
    assertThat(client.getWorkInfoCount()).isEqualTo(3)
    assertThat(listener.consume()).isGreaterThan(0)
  }

  @Test
  fun getWorkChain() = runBlocking {
    val id1 = "Test1"
    val id2 = "Test2"
    val id3 = "Test3"
    val uniqueName = "name"
    // TODO(b/165789713): Return work chain ids with topological order.
    val workInfo1 = WorkInfo.newBuilder()
      .setId(id1)
      .addNames(uniqueName)
      .build()
    val workInfo2 = WorkInfo.newBuilder()
      .setId(id2)
      .addNames(uniqueName)
      .build()
    val workInfo3 = WorkInfo.newBuilder()
      .setId(id3)
      .build()

    sendWorkAddedEvent(workInfo1)
    sendWorkAddedEvent(workInfo2)
    sendWorkAddedEvent(workInfo3)

    val worksWithName = client.getWorkChain(workInfo1.id)
    assertThat(worksWithName.size).isEqualTo(2)
    assertThat(worksWithName).containsAllOf(id1, id2)
    assertThat(listener.consume()).isGreaterThan(0)

    val worksWithoutName = client.getWorkChain(workInfo3.id)
    assertThat(worksWithoutName.size).isEqualTo(1)
    assertThat(worksWithoutName[0]).isEqualTo(id3)
    assertThat(listener.consume()).isEqualTo(0)
  }

  @Test
  fun cancelWork() = runBlocking {
    val id = "Test"
    val workInfo = WorkInfo.newBuilder()
      .setId(id)
      .build()

    sendWorkAddedEvent(workInfo)
    assertThat(listener.consume()).isGreaterThan(0)

    client.cancelWorkById(id)
    val cancelWorkCommand = CancelWorkCommand.newBuilder().setId(id).build()
    val command = Command.newBuilder().setCancelWork(cancelWorkCommand).build()

    // This scope is single threaded so the following block should occur after sending commands.
    scope.launch {
      assertThat(messenger.rawDataSent).isEqualTo(command.toByteArray())
      assertThat(listener.consume()).isEqualTo(0)
    }.join()
  }

  private fun sendWorkAddedEvent(workInfo: WorkInfo) {
    val workAddedEvent = WorkAddedEvent.newBuilder().setWork(workInfo).build()
    val event = Event.newBuilder().setWorkAdded(workAddedEvent).build()
    client.handleEvent(event.toByteArray())
  }

  private fun sendWorkRemovedEvent(id: String) {
    val workRemovedEvent = WorkRemovedEvent.newBuilder().setId(id).build()
    val event = Event.newBuilder().setWorkRemoved(workRemovedEvent).build()
    client.handleEvent(event.toByteArray())
  }

  private fun WorkUpdatedEvent.toEvent() = Event.newBuilder().setWorkUpdated(this).build()
}
