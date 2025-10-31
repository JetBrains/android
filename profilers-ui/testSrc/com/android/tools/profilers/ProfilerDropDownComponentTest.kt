/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.profilers

import com.android.tools.adtui.swing.FakeUi
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.RuleChain
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class ProfilerDropDownComponentTest {

  @get:Rule
  val rule = RuleChain(
    ApplicationRule(),
    // EdtRule is required for @RunsInEdt to work.
    EdtRule()
  )

  enum class TestEnum { A, B, C }

  @Test
  fun componentIsCreated() {
    val flow = MutableStateFlow(selectionOf(TestEnum.A))
    val component = ProfilerDropDownComponent<TestEnum>("Test", "Desc", null, flow, null, {})
    assertThat(component).isNotNull()
    assertThat(component.dropDownAction).isNotNull()
  }

  @Test
  @RunsInEdt
  fun selectionChangesText() {
    val flow = MutableStateFlow(selectionOf(TestEnum.A))
    val component = ProfilerDropDownComponent<TestEnum>("Test", "Desc", null, flow, null, {})
    val fakeUi = FakeUi(component)

    val action = component.dropDownAction
    val event = TestActionEvent.createTestEvent()

    action.update(event)
    assertThat(event.presentation.text).isEqualTo("A")

    flow.value = flow.value.select(TestEnum.B)
    fakeUi.layoutAndDispatchEvents() // To trigger update
    action.update(event)
    assertThat(event.presentation.text).isEqualTo("B")
  }

  @Test
  @RunsInEdt
  fun clickingItemChangesSelection() {
    var selected: TestEnum? = null
    val flow = MutableStateFlow(selectionOf(TestEnum.A))
    val component = ProfilerDropDownComponent<TestEnum>("Test", "Desc", null, flow, null, { selected = it })

    // Simulate click on item B
    selectItem(component, TestEnum.B)

    assertThat(selected).isEqualTo(TestEnum.B)
  }
}