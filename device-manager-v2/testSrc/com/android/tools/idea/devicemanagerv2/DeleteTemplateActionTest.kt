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
package com.android.tools.idea.devicemanagerv2

import com.android.adblib.utils.createChildScope
import com.android.tools.analytics.UsageTrackerRule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DeviceManagerEvent
import com.intellij.testFramework.ApplicationRule
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify

class DeleteTemplateActionTest {
  @get:Rule val applicationRule = ApplicationRule()
  @get:Rule val usageTrackerRule = UsageTrackerRule()

  @Test
  fun deleteHandle(): Unit = runTest {
    val handleScope = createChildScope()
    val template = FakeDeviceTemplate("A")
    val handle = FakeDeviceHandle(handleScope, sourceTemplate = template)

    val actionEvent =
      actionEvent(dataContext(deviceRowData = DeviceRowData.create(handle, emptyList())))

    DeleteTemplateAction().update(actionEvent)

    assertThat(actionEvent.presentation.isEnabled).isFalse()
    assertThat(actionEvent.presentation.isVisible).isFalse()

    handleScope.cancel()
  }

  @Test
  fun deleteTemplate(): Unit = runTest {
    val template = FakeDeviceTemplate("A")

    val actionEvent =
      actionEvent(
        dataContext(deviceRowData = DeviceRowData.create(template), coroutineScope = this)
      )

    DeleteTemplateAction().update(actionEvent)

    assertThat(actionEvent.presentation.isEnabled).isTrue()
    assertThat(actionEvent.presentation.isVisible).isTrue()

    DeleteTemplateAction().actionPerformed(actionEvent)

    advanceUntilIdle()

    verify(template.deleteAction).delete()
    assertThat(usageTrackerRule.deviceManagerEventKinds())
      .containsExactly(DeviceManagerEvent.EventKind.PHYSICAL_DELETE_ACTION)
  }
}
