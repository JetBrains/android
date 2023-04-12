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
package com.android.tools.idea.insights.ui

import ai.grazie.utils.mpp.runBlocking
import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.insights.PLACEHOLDER_CONNECTION
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.VARIANT1
import com.android.tools.idea.insights.VARIANT2
import com.android.tools.idea.insights.VariantConnection
import com.android.tools.idea.testing.ui.FakeActionPopupMenu
import com.google.common.truth.Truth
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.replaceService
import com.intellij.util.ui.LafIconLookup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.ArgumentMatchers
import org.mockito.Mockito

private val CHECKMARK_ICON = LafIconLookup.getIcon("checkmark")

class AppInsightsModuleSelectorTest {
  private val applicationRule = ApplicationRule()
  private val testRootDisposable = DisposableRule()

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(applicationRule).around(testRootDisposable)

  private lateinit var fakePopup: FakeActionPopupMenu
  private lateinit var moduleSelector: AppInsightsModuleSelector
  private lateinit var actionButton: ActionButtonWithText

  @Before
  fun setUp() {
    val mockActionManager = MockitoKt.mock<ActionManagerImpl>()

    ApplicationManager.getApplication()
      .replaceService(ActionManager::class.java, mockActionManager, testRootDisposable.disposable)

    Mockito.doAnswer { invocation ->
        fakePopup = FakeActionPopupMenu(invocation.getArgument(1))
        fakePopup
      }
      .whenever(ActionManager.getInstance())
      .createActionPopupMenu(ArgumentMatchers.anyString(), MockitoKt.any())

    Mockito.`when`(
        mockActionManager.createActionToolbar(
          ArgumentMatchers.anyString(),
          MockitoKt.any(),
          ArgumentMatchers.anyBoolean()
        )
      )
      .thenCallRealMethod()

    Mockito.`when`(
        mockActionManager.createActionToolbar(
          ArgumentMatchers.anyString(),
          MockitoKt.any(),
          ArgumentMatchers.anyBoolean(),
          ArgumentMatchers.anyBoolean()
        )
      )
      .thenCallRealMethod()
  }

  @Test
  fun `choose configured app`() =
    runBlocking(AndroidDispatchers.uiThread) {
      // Prepare
      val connectionFlow =
        MutableStateFlow(Selection(VARIANT1, listOf(VARIANT1, PLACEHOLDER_CONNECTION)))
      setUpUi(connectionFlow)

      // Act
      val event = TestActionEvent()
      moduleSelector.update(event)

      // Assert
      Truth.assertThat(event.presentation.text).isEqualTo("app1 › variant1: [project1] app1")
    }

  @Test
  fun `choose unconfigured app`() =
    runBlocking(AndroidDispatchers.uiThread) {
      // Prepare
      val connectionFlow =
        MutableStateFlow(
          Selection(PLACEHOLDER_CONNECTION, listOf(VARIANT1, PLACEHOLDER_CONNECTION))
        )
      setUpUi(connectionFlow)

      // Act
      val event = TestActionEvent()
      moduleSelector.update(event)

      // Assert
      Truth.assertThat(event.presentation.text).isEqualTo("app3 › (no connection)")
    }

  @Test
  fun `open selector shows correct items for single configured app case`() =
    runBlocking(AndroidDispatchers.uiThread) {
      // Prepare
      val connectionFlow =
        MutableStateFlow(Selection(VARIANT1, listOf(VARIANT1, PLACEHOLDER_CONNECTION)))
      setUpUi(connectionFlow)

      // Act
      actionButton.click()

      // Assert
      val actions = fakePopup.getActions()
      Truth.assertThat(actions.size).isEqualTo(4)
      Truth.assertThat(actions.contents())
        .containsExactly(
          CHECKMARK_ICON to
            "app1", // configured app1, since only one app, flattened items would be injected (i.e.
          // not a popup group )
          Separator.getInstance(),
          null to "Not linked to Firebase", // text separator
          null to "app3" // unconfigured app3
        )

      // Check submenu of "app1"
      (actions[0] as ActionGroup).apply {
        Truth.assertThat(isPopup).isFalse()

        val actionItem = getChildren(null).single() as ToggleAction
        Truth.assertThat(actionItem.isSelected(TestActionEvent())).isTrue()
        Truth.assertThat(actionItem.templateText).isEqualTo("app1 › variant1: [project1] app1")
      }
    }

  @Test
  fun `open selector shows correct items for multiple configured apps case`() =
    runBlocking(AndroidDispatchers.uiThread) {
      // Prepare
      val connectionFlow =
        MutableStateFlow(Selection(VARIANT1, listOf(VARIANT1, VARIANT2, PLACEHOLDER_CONNECTION)))
      setUpUi(connectionFlow)

      // Act
      actionButton.click()

      // Assert
      val actions = fakePopup.getActions()
      Truth.assertThat(actions.size).isEqualTo(5)
      Truth.assertThat(actions.contents())
        .containsExactly(
          CHECKMARK_ICON to "app1", // configured app1, variant based connections are in its submenu
          null to "app2", // configured app1, variant based connections are in its submenu
          Separator.getInstance(),
          null to "Not linked to Firebase", // text separator
          null to "app3" // unconfigured app3
        )

      // Check submenu of "app1"
      (actions[0] as ActionGroup).apply {
        Truth.assertThat(isPopup).isTrue()

        val actionItem = getChildren(null).single() as ToggleAction
        Truth.assertThat(actionItem.isSelected(TestActionEvent())).isTrue()
        Truth.assertThat(actionItem.templateText).isEqualTo("variant1: [project1] app1")
      }

      // Check submenu of "app2"
      (actions[1] as ActionGroup).apply {
        Truth.assertThat(isPopup).isTrue()

        val actionItem = getChildren(null).single() as ToggleAction
        Truth.assertThat(actionItem.isSelected(TestActionEvent())).isFalse()
        Truth.assertThat(actionItem.templateText).isEqualTo("variant2: [project2] app2")
      }
    }

  @Test
  fun `open selector shows correct items for no configured app case`() =
    runBlocking(AndroidDispatchers.uiThread) {
      // Prepare
      val connectionFlow =
        MutableStateFlow(Selection(PLACEHOLDER_CONNECTION, listOf(PLACEHOLDER_CONNECTION)))
      setUpUi(connectionFlow)

      // Act
      actionButton.click()

      // Assert
      val actions = fakePopup.getActions()
      Truth.assertThat(actions.size).isEqualTo(3)
      Truth.assertThat(actions.contents())
        .containsExactly(
          Separator.getInstance(),
          null to "Not linked to Firebase", // text separator
          null to "app3" // unconfigured app3
        )

      // Check if "app1" is previously selected.
      (actions[2] as ToggleAction).apply {
        Truth.assertThat(isSelected(TestActionEvent())).isTrue()
      }
    }

  private fun List<AnAction>.contents() = map {
    (it as? Separator) ?: (it.templatePresentation.icon to it.templateText)
  }

  private fun setUpUi(connectionFlow: StateFlow<Selection<VariantConnection>>) {
    moduleSelector =
      AppInsightsModuleSelector(
        "Module Selector",
        null,
        null,
        connectionFlow,
        onSelect = {},
      )

    val actionGroup = DefaultActionGroup().apply { add(moduleSelector) }

    val toolbar = ActionManager.getInstance().createActionToolbar("AppInsights", actionGroup, true)
    toolbar.updateActionsImmediately()

    actionButton = toolbar.component.getComponent(0) as ActionButtonWithText
  }
}
