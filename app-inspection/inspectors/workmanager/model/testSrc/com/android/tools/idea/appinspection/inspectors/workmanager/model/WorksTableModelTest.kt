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

import androidx.work.inspection.WorkManagerInspectorProtocol.Data
import androidx.work.inspection.WorkManagerInspectorProtocol.DataEntry
import androidx.work.inspection.WorkManagerInspectorProtocol.Event
import androidx.work.inspection.WorkManagerInspectorProtocol.WorkAddedEvent
import androidx.work.inspection.WorkManagerInspectorProtocol.WorkInfo
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorksTableModel.Column
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.swing.SwingUtilities

class WorksTableModelTest {
  private lateinit var executor: ExecutorService
  private lateinit var scope: CoroutineScope
  private lateinit var messenger: AppInspectorMessenger
  private lateinit var client: WorkManagerInspectorClient
  private lateinit var model: WorksTableModel

  @Before
  fun setUp() {
    executor = Executors.newSingleThreadExecutor()
    scope = CoroutineScope(executor.asCoroutineDispatcher() + SupervisorJob())
    messenger = object : AppInspectorMessenger {
      override suspend fun sendRawCommand(rawData: ByteArray) = ByteArray(0)
      override val eventFlow = emptyFlow<ByteArray>()
      override val scope = this@WorksTableModelTest.scope
    }
    client = WorkManagerInspectorClient(messenger, scope)
    model = WorksTableModel(client)
    model.addTableModelListener {
      // It will cause problems if we ever trigger a listener not on the UI thread,
      // so we assert here to verify we don't break this in future refactorings.
      assertThat(SwingUtilities.isEventDispatchThread()).isTrue()
    }
  }

  @After
  fun tearDown() {
    scope.cancel()
    executor.shutdownNow()
  }

  @Test
  fun initializeModel() {
    assertThat(model.columnCount).isEqualTo(6)
    val columnArray = Column.values()
    for (index in 0 until 6) {
      assertThat(model.getColumnName(index) == columnArray[index].name)
    }
  }

  @Test
  fun updateModelWithClientChange() {
    val data = Data.newBuilder()
      .addEntries(DataEntry.newBuilder().setKey("k").setValue("v").build())
      .build()
    val workInfo1 = WorkInfo.newBuilder()
      .setId("ID1")
      .setWorkerClassName("package1.package2.ClassName1")
      .setState(WorkInfo.State.ENQUEUED)
      .setScheduleRequestedAt(3L)
      .setRunAttemptCount(1)
      .setData(data)
      .build()

    sendWorkAddedEvent(workInfo1)
    assertThat(model.rowCount).isEqualTo(1)
    assertThat(model.getValueAt(0, Column.ORDER.ordinal)).isEqualTo(1)
    assertThat(model.getValueAt(0, Column.CLASS_NAME.ordinal)).isEqualTo("ClassName1")
    assertThat(model.getValueAt(0, Column.STATE.ordinal)).isEqualTo(1)
    assertThat(model.getValueAt(0, Column.TIME_STARTED.ordinal)).isEqualTo(3L)
    assertThat(model.getValueAt(0, Column.RUN_ATTEMPT_COUNT.ordinal)).isEqualTo(1)
    assertThat(model.getValueAt(0, Column.DATA.ordinal)).isEqualTo(WorkInfo.State.ENQUEUED to data)
  }

  private fun sendWorkAddedEvent(workInfo: WorkInfo) {
    val workAddedEvent = WorkAddedEvent.newBuilder().setWork(workInfo).build()
    val event = Event.newBuilder().setWorkAdded(workAddedEvent).build()
    client.handleEvent(event.toByteArray())
  }
}
