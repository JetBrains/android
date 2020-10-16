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
package com.android.tools.idea.appinspection.inspectors.workmanager.view

import androidx.work.inspection.WorkManagerInspectorProtocol.CallStack
import androidx.work.inspection.WorkManagerInspectorProtocol.Constraints
import androidx.work.inspection.WorkManagerInspectorProtocol.WorkInfo
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.service.TestAppInspectionIdeServices
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.swing.JLabel
import javax.swing.JPanel

class OutputDataProviderTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private lateinit var executor: ExecutorService
  private lateinit var scope: CoroutineScope
  private lateinit var ideServices: AppInspectionIdeServices

  @Before
  fun setUp() {
    executor = Executors.newSingleThreadExecutor()
    scope = CoroutineScope(executor.asCoroutineDispatcher() + SupervisorJob())
    ideServices = TestAppInspectionIdeServices()
  }

  @After
  fun tearDown() {
    scope.cancel()
    executor.shutdownNow()
  }

  @Test
  fun convertEmptyCallStack() {
    val component = EnqueuedAtProvider(ideServices, scope).convert(CallStack.getDefaultInstance())
    assertThat(component.componentCount).isEqualTo(3)
    assertThat((component.getComponent(0) as JLabel).text).isEqualTo("Unavailable")
  }

  @Test
  fun convertEmptyTagList() {
    val emptyTagList = WorkInfo.getDefaultInstance().tagsList
    val component = StringListProvider.convert(emptyTagList) as JLabel
    assertThat(component.text).isEqualTo("None")
  }

  @Test
  fun convertFullConstrains() {
    val fullConstraints = Constraints.newBuilder().apply {
      requiredNetworkType = Constraints.NetworkType.UNMETERED
      requiresCharging = true
      requiresBatteryNotLow = true
      requiresDeviceIdle = true
      requiresStorageNotLow = true
    }.build()
    val component = ConstraintProvider.convert(fullConstraints) as JPanel
    assertThat(component.componentCount).isEqualTo(5)
  }

  @Test
  fun convertEmptyData() {
    val unfinishedWorkInfo = WorkInfo.newBuilder().setState(WorkInfo.State.RUNNING).build()
    val unfinishedLabel = OutputDataProvider.convert(unfinishedWorkInfo) as JLabel
    assertThat(unfinishedLabel.text).isEqualTo("Awaiting data...")

    val finishedWorkInfo = WorkInfo.newBuilder().setState(WorkInfo.State.FAILED).build()
    val finishedLabel = OutputDataProvider.convert(finishedWorkInfo) as JLabel
    assertThat(finishedLabel.text).isEqualTo("null")
  }
}
