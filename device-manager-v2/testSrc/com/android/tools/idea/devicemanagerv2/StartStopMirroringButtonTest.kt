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
package com.android.tools.idea.devicemanagerv2

import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.waitForCondition
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.streaming.MirroringHandle
import com.android.tools.idea.streaming.MirroringManager
import com.android.tools.idea.streaming.MirroringState
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.components.service
import com.intellij.testFramework.ProjectRule
import icons.StudioIcons
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Tests for StartStopMirroringButton. */
class StartStopMirroringButtonTest {

  @get:Rule val projectRule = ProjectRule()

  private val project
    get() = projectRule.project

  private val mirroringManager
    get() = project.service<MirroringManager>()

  @Test
  fun testBasicFunctionality() = runBlocking {
    val deviceHandle = mock<DeviceHandle>()
    whenever(deviceHandle.scope).thenReturn(AndroidCoroutineScope(project))
    val button = StartStopMirroringButton(deviceHandle, project)
    assertThat(button.isVisible).isFalse()

    val activator = FakeMirroringHandle(MirroringState.INACTIVE)
    mirroringManager.mirroringHandles.value = mapOf(deviceHandle to activator)
    waitForCondition(1, TimeUnit.SECONDS) { button.baseIcon == StudioIcons.Avd.START_MIRROR }
    assertThat(button.isVisible).isTrue()
    assertThat(button.isEnabled).isTrue()

    assertThat(activator.toggleCount).isEqualTo(0)
    button.doClick()
    assertThat(activator.toggleCount).isEqualTo(1)

    val deactivator = FakeMirroringHandle(MirroringState.ACTIVE)
    mirroringManager.mirroringHandles.value = mapOf(deviceHandle to deactivator)
    waitForCondition(1, TimeUnit.SECONDS) { button.baseIcon == StudioIcons.Avd.STOP_MIRROR }
    assertThat(button.isVisible).isTrue()
    assertThat(button.isEnabled).isTrue()

    assertThat(deactivator.toggleCount).isEqualTo(0)
    button.doClick()
    assertThat(deactivator.toggleCount).isEqualTo(1)

    mirroringManager.mirroringHandles.value = mapOf()
    waitForCondition(1, TimeUnit.SECONDS) { !button.isVisible }
  }

  private class FakeMirroringHandle(override val mirroringState: MirroringState) : MirroringHandle {
    var toggleCount: Int = 0

    override fun toggleMirroring() {
      toggleCount++
    }
  }
}
