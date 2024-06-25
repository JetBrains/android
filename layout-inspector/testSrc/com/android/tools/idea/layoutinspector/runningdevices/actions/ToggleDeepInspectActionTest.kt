/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.runningdevices.actions

import com.android.tools.adtui.actions.createTestActionEvent
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.pipeline.AbstractInspectorClient
import com.android.tools.idea.layoutinspector.pipeline.DisconnectedClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.TreeLoader
import com.android.tools.idea.layoutinspector.properties.PropertiesProvider
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import java.nio.file.Path
import org.junit.Rule
import org.junit.Test

class ToggleDeepInspectActionTest {
  @get:Rule val projectRule = ProjectRule()
  @get:Rule val disposableRule = DisposableRule()

  @Test
  fun testActionClick() {
    var isSelected = false
    val toggleDeepInspectAction =
      ToggleDeepInspectAction(
        isSelected = { isSelected },
        setSelected = { isSelected = !isSelected },
        isRendering = { true },
        connectedClientProvider = { DisconnectedClient },
      )

    toggleDeepInspectAction.actionPerformed(createTestActionEvent(toggleDeepInspectAction))
    assertThat(isSelected).isTrue()

    toggleDeepInspectAction.actionPerformed(createTestActionEvent(toggleDeepInspectAction))
    assertThat(isSelected).isFalse()
  }

  @Test
  fun testTitleAndDescription() {
    val toggleDeepInspectAction =
      ToggleDeepInspectAction(
        isSelected = { false },
        setSelected = {},
        isRendering = { true },
        connectedClientProvider = { DisconnectedClient },
      )

    val event = createTestActionEvent(toggleDeepInspectAction)
    toggleDeepInspectAction.update(event)
    assertThat(event.presentation.text).isEqualTo("Toggle Deep Inspect")
    assertThat(event.presentation.description)
      .isEqualTo("Enable Deep Inspect to select components by clicking on the device.")
  }

  @Test
  fun testDeepInspectActionIsDisabledWhenClientIsNotConnected() {
    val inspectorClient =
      FakeInspectorClient(
        projectRule.project,
        MODERN_DEVICE.createProcess(),
        disposableRule.disposable,
      )
    val toggleDeepInspectAction =
      ToggleDeepInspectAction(
        isSelected = { false },
        setSelected = {},
        isRendering = { true },
        connectedClientProvider = { inspectorClient },
      )

    val event = createTestActionEvent(toggleDeepInspectAction)
    toggleDeepInspectAction.update(event)

    assertThat(event.presentation.isEnabled).isFalse()

    inspectorClient.state = InspectorClient.State.CONNECTED
    toggleDeepInspectAction.update(event)

    assertThat(event.presentation.isEnabled).isTrue()
  }

  @Test
  fun testDeepInspectActionIsDisabledWhenNotRendering() {
    val inspectorClient =
      FakeInspectorClient(
        projectRule.project,
        MODERN_DEVICE.createProcess(),
        disposableRule.disposable,
      )
    var isRendering = true
    val toggleDeepInspectAction =
      ToggleDeepInspectAction(
        isSelected = { false },
        setSelected = {},
        isRendering = { isRendering },
        connectedClientProvider = { inspectorClient },
      )

    val event = createTestActionEvent(toggleDeepInspectAction)

    inspectorClient.state = InspectorClient.State.CONNECTED

    toggleDeepInspectAction.update(event)
    assertThat(event.presentation.isEnabled).isTrue()

    isRendering = false

    toggleDeepInspectAction.update(event)
    assertThat(event.presentation.isEnabled).isFalse()

    isRendering = true

    toggleDeepInspectAction.update(event)
    assertThat(event.presentation.isEnabled).isTrue()
  }
}

private open class FakeInspectorClient(
  project: Project,
  process: ProcessDescriptor,
  parentDisposable: Disposable,
) :
  AbstractInspectorClient(
    DynamicLayoutInspectorAttachToProcess.ClientType.APP_INSPECTION_CLIENT,
    project,
    NotificationModel(project),
    process,
    DisconnectedClient.stats,
    AndroidCoroutineScope(parentDisposable),
    parentDisposable,
  ) {
  override suspend fun startFetching() = throw NotImplementedError()

  override suspend fun stopFetching() = throw NotImplementedError()

  override fun refresh() = throw NotImplementedError()

  override suspend fun saveSnapshot(path: Path) = throw NotImplementedError()

  override suspend fun doConnect() {}

  override suspend fun doDisconnect() {}

  override val capabilities
    get() = throw NotImplementedError()

  override val treeLoader: TreeLoader
    get() = throw NotImplementedError()

  override val inLiveMode: Boolean
    get() = false

  override val provider: PropertiesProvider
    get() = throw NotImplementedError()
}
