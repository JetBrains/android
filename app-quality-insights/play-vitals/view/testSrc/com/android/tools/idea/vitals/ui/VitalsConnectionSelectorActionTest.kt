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
package com.android.tools.idea.vitals.ui

import com.android.tools.idea.insights.Selection
import com.android.tools.idea.vitals.TEST_CONNECTION_1
import com.android.tools.idea.vitals.TEST_CONNECTION_2
import com.android.tools.idea.vitals.datamodel.VitalsConnection
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TestActionEvent.createTestEvent
import java.awt.Point
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class VitalsConnectionSelectorActionTest {
  @get:Rule val applicationRule = ApplicationRule()

  @Test
  fun `action updates title when selection changes`() = runTest {
    val stateFlow = MutableStateFlow(Selection<VitalsConnection>(null, emptyList()))
    val action = VitalsConnectionSelectorAction(stateFlow, this, {}, { Point() })
    val event = createTestEvent()
    action.update(event)
    assertThat(event.presentation.text).isEqualTo("No apps available")

    stateFlow.value = Selection(TEST_CONNECTION_1, listOf(TEST_CONNECTION_1, TEST_CONNECTION_2))
    action.update(event)
    assertThat(event.presentation.text)
      .isEqualTo("${TEST_CONNECTION_1.displayName} [${TEST_CONNECTION_1.appId}]")
  }
}
