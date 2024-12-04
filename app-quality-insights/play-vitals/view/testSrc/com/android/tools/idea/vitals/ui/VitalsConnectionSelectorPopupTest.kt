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

import ai.grazie.utils.text
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.ui.JListSimpleColoredComponent
import com.android.tools.idea.vitals.TEST_CONNECTION_1
import com.android.tools.idea.vitals.TEST_CONNECTION_2
import com.android.tools.idea.vitals.TEST_CONNECTION_3
import com.android.tools.idea.vitals.datamodel.VitalsConnection
import com.android.tools.idea.vitals.ui.VitalsConnectionSelectorPopup.NoAvailableAppsBanner
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TestActionEvent.createTestEvent
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.components.JBList
import com.jetbrains.rd.generator.nova.fail
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.JTextArea
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class VitalsConnectionSelectorPopupTest {

  private val applicationRule = ApplicationRule()
  private val popupRule = JBPopupRule()

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(applicationRule).around(popupRule)

  @Test
  fun `popup shows both suggested and all apps`() = runTest {
    val stateFlow =
      MutableStateFlow(
        Selection(
          TEST_CONNECTION_1,
          listOf(TEST_CONNECTION_1, TEST_CONNECTION_2, TEST_CONNECTION_3),
        )
      )
    val action = VitalsConnectionSelectorAction(stateFlow, this, {}, { Point() })
    val mouseEvent = MouseEvent(JPanel(), MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, true, 0)
    action.actionPerformed(createTestEvent(action, DataContext.EMPTY_CONTEXT, mouseEvent))

    val popup = popupRule.fakePopupFactory.getPopup<Unit>(0)
    val fakeUi = FakeUi(popup.content as VitalsConnectionSelectorPopup)
    val lists = fakeUi.findAllComponents<JBList<VitalsConnection>>()
    assertThat(lists).hasSize(2)
    assertThat(lists[0].model.getElementAt(0)).isEqualTo(TEST_CONNECTION_1)
    assertThat(lists[0].model.getElementAt(1)).isEqualTo(TEST_CONNECTION_2)
    assertThat(lists[1].model.getElementAt(0)).isEqualTo(TEST_CONNECTION_3)

    val labels =
      fakeUi.findAllComponents<SimpleColoredComponent> { it !is JListSimpleColoredComponent<*> }
    assertThat(labels).hasSize(2)
    assertThat(labels.map { it.toString() })
      .containsExactly("Suggested apps for this project", "Other apps")
  }

  @Test
  fun `popup shows empty other app when all connections are associated with a variant`() = runTest {
    val stateFlow =
      MutableStateFlow(Selection(TEST_CONNECTION_1, listOf(TEST_CONNECTION_1, TEST_CONNECTION_2)))
    val action = VitalsConnectionSelectorAction(stateFlow, this, {}, { Point() })
    val mouseEvent = MouseEvent(JPanel(), MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, true, 0)
    action.actionPerformed(createTestEvent(action, DataContext.EMPTY_CONTEXT, mouseEvent))

    val popup = popupRule.fakePopupFactory.getPopup<Unit>(0)
    val fakeUi = FakeUi(popup.content as VitalsConnectionSelectorPopup)
    val list = fakeUi.findComponent<JBList<VitalsConnection>>() ?: fail("List not found")
    assertThat(list.model.getElementAt(0)).isEqualTo(TEST_CONNECTION_1)
    assertThat(list.model.getElementAt(1)).isEqualTo(TEST_CONNECTION_2)

    val labels =
      fakeUi.findAllComponents<SimpleColoredComponent> { it !is JListSimpleColoredComponent<*> }
    assertThat(labels).hasSize(3)
    assertThat(labels.map { it.toString() })
      .containsExactly("Suggested apps for this project", "Other apps", "No apps accessible to you")
  }

  @Test
  fun `popup shows empty suggested apps when no connections are associated with a variant`() =
    runTest {
      val stateFlow = MutableStateFlow(Selection(TEST_CONNECTION_3, listOf(TEST_CONNECTION_3)))
      val action = VitalsConnectionSelectorAction(stateFlow, this, {}, { Point() })
      val mouseEvent = MouseEvent(JPanel(), MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, true, 0)
      action.actionPerformed(createTestEvent(action, DataContext.EMPTY_CONTEXT, mouseEvent))

      val popup = popupRule.fakePopupFactory.getPopup<Unit>(0)
      val fakeUi = FakeUi(popup.content as VitalsConnectionSelectorPopup)
      val list = fakeUi.findComponent<JBList<VitalsConnection>>() ?: fail("List not found")
      assertThat(list.model.getElementAt(0)).isEqualTo(TEST_CONNECTION_3)

      val labels =
        fakeUi.findAllComponents<SimpleColoredComponent> { it !is JListSimpleColoredComponent<*> }
      assertThat(labels).hasSize(3)
      assertThat(labels.map { it.toString() })
        .containsExactly("Suggested apps for this project", "No suggested apps", "All apps")
    }

  @Test
  fun `popup shows empty state message when there are no apps at all`() = runTest {
    val stateFlow = MutableStateFlow(Selection<VitalsConnection>(null, emptyList()))
    val action = VitalsConnectionSelectorAction(stateFlow, this, {}, { Point() })
    val mouseEvent = MouseEvent(JPanel(), MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, true, 0)
    action.actionPerformed(createTestEvent(action, DataContext.EMPTY_CONTEXT, mouseEvent))

    val popup = popupRule.fakePopupFactory.getPopup<Unit>(0)
    val fakeUi = FakeUi(popup.content as VitalsConnectionSelectorPopup)
    val banner = fakeUi.findComponent<NoAvailableAppsBanner>() ?: fail("Banner panel not found")
    val bannerFakeUi = FakeUi(banner)
    val textPane = bannerFakeUi.findComponent<JTextArea>() ?: fail("Text area not found")
    assertThat(textPane.text)
      .isEqualTo("Your Play Console account does not have access to Android Vitals for any app.")
  }

  @Test
  fun `search term narrows down connections in the list`() = runTest {
    val stateFlow =
      MutableStateFlow(
        Selection(
          TEST_CONNECTION_1,
          listOf(TEST_CONNECTION_1, TEST_CONNECTION_2, TEST_CONNECTION_3),
        )
      )
    val action = VitalsConnectionSelectorAction(stateFlow, this, {}, { Point() })
    val mouseEvent = MouseEvent(JPanel(), MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, true, 0)
    action.actionPerformed(createTestEvent(action, DataContext.EMPTY_CONTEXT, mouseEvent))

    val popup = popupRule.fakePopupFactory.getPopup<Unit>(0)
    val fakeUi = FakeUi(popup.content as VitalsConnectionSelectorPopup)
    val searchBox = fakeUi.findAllComponents<SearchTextField>().single()
    val lists = fakeUi.findAllComponents<JBList<VitalsConnection>>()

    searchBox.text = TEST_CONNECTION_2.displayName
    assertThat(lists[0].itemsCount).isEqualTo(1)
    assertThat(lists[0].model.getElementAt(0)).isEqualTo(TEST_CONNECTION_2)
    assertThat(lists[1].itemsCount).isEqualTo(0)

    searchBox.text = TEST_CONNECTION_3.appId
    assertThat(lists[0].itemsCount).isEqualTo(0)
    assertThat(lists[1].itemsCount).isEqualTo(1)
    assertThat(lists[1].model.getElementAt(0)).isEqualTo(TEST_CONNECTION_3)
  }
}
