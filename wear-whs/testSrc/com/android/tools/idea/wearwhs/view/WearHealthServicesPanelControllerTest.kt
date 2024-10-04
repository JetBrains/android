/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.wearwhs.view

import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.wearwhs.communication.FakeDeviceManager
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.awt.RelativePoint
import java.awt.Point
import kotlinx.coroutines.CoroutineScope
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

@RunsInEdt
class WearHealthServicesPanelControllerTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()
  @get:Rule val edtRule = EdtRule()
  @get:Rule val fakePopupRule = JBPopupRule()

  private lateinit var uiScope: CoroutineScope
  private lateinit var workerScope: CoroutineScope
  private lateinit var deviceManager: FakeDeviceManager
  private lateinit var stateManager: WearHealthServicesStateManagerImpl
  private lateinit var controller: WearHealthServicesPanelController

  @Before
  fun setup() {
    uiScope = AndroidCoroutineScope(projectRule.testRootDisposable, AndroidDispatchers.uiThread)
    workerScope =
      AndroidCoroutineScope(projectRule.testRootDisposable, AndroidDispatchers.workerThread)
    deviceManager = FakeDeviceManager()

    stateManager =
      WearHealthServicesStateManagerImpl(deviceManager = deviceManager, workerScope = workerScope)
        .also { Disposer.register(projectRule.testRootDisposable, it) }
        .also { it.serialNumber = "some serial number" }
    controller =
      WearHealthServicesPanelController(
        stateManager = stateManager,
        uiScope = uiScope,
        workerScope = workerScope,
      )
  }

  @Test
  fun `test balloon shows`() {
    controller.showWearHealthServicesToolPopup(
      projectRule.testRootDisposable,
      RelativePoint(mock(), Point(0, 0)),
    )

    assertThat(fakePopupRule.fakePopupFactory.balloonCount).isEqualTo(1)
  }
}
