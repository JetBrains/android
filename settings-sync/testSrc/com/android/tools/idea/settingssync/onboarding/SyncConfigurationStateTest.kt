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
package com.android.tools.idea.settingssync.onboarding

import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.flags.junit.FlagRule
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import com.google.gct.login2.LoginFeature
import com.google.gct.login2.PreferredUser
import com.google.gct.login2.ui.onboarding.compose.GoogleSignInWizard
import com.google.gct.wizard.FakeController
import com.google.gct.wizard.WizardPage
import com.google.gct.wizard.WizardState
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.waitUntil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

internal const val USER_EMAIL = "test@test.com"

class SyncConfigurationStateTest {
  private val applicationRule = ApplicationRule()
  private val disposableRule = DisposableRule()
  private val composeTestRule = createStudioComposeTestRule()
  private val flagRule = FlagRule(StudioFlags.SETTINGS_SYNC_ENABLED, true)
  private val dispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val rules =
    RuleChain.outerRule(applicationRule)
      .around(flagRule)
      .around(disposableRule)
      .around(composeTestRule)

  @Before
  fun setup() {
    ExtensionTestUtil.maskExtensions(
      LoginFeature.Companion.EP_NAME,
      listOf(feature),
      disposableRule.disposable,
      false,
    )
  }

  private suspend fun initWizard(
    pages: List<WizardPage>,
    state: WizardState,
    scope: CoroutineScope,
  ) {
    val controller = FakeController(pages, state, scope)

    composeTestRule.setContent { controller.CurrentComposablePage() }

    waitUntil { controller.currentPage != null }
    assertThat(controller.currentPage).isEqualTo(pages[0])
  }

  @Test
  fun `check default`() =
    runTest(dispatcher) {
      // Prepare
      val wizardState =
        WizardState().apply {
          // Make sure we won't skip the page
          getOrCreateState { GoogleSignInWizard.SignInState() }
            .apply { signedInUser = PreferredUser.User(email = USER_EMAIL) }
        }

      initWizard(pages = listOf(ChooseCategoriesStepPage()), wizardState, this)

      // Verify
      val syncUIState = wizardState.getOrCreateState { SyncConfigurationState() }
      syncUIState.syncCategoryStates.checkSynCategories(
        listOf(
          NodeState(name = "UI settings", state = ToggleableState.On),
          NodeState(name = "Editor font", state = ToggleableState.On),
          NodeState(name = "Keymaps", state = ToggleableState.On),
          NodeState(name = "Code settings", state = ToggleableState.On),
          NodeState(name = "Plugins", state = ToggleableState.On),
          NodeState(name = "Bundled plugins", state = ToggleableState.On),
          NodeState(name = "Tools", state = ToggleableState.On),
          NodeState(name = "System settings", state = ToggleableState.On),
        )
      )
      assertThat(syncUIState.pushOrPull).isEqualTo(PushOrPull.NOT_SPECIFIED)
      assertThat(syncUIState.configurationOption)
        .isEqualTo(SyncConfigurationOption.CONFIGURE_NEW_ACCOUNT)
    }

  @Test
  fun `unselect Keymaps category`() =
    runTest(dispatcher) {
      // Prepare
      val wizardState =
        WizardState().apply {
          // Make sure we won't skip the page
          getOrCreateState { GoogleSignInWizard.SignInState() }
            .apply { signedInUser = PreferredUser.User(email = USER_EMAIL) }
        }

      initWizard(pages = listOf(ChooseCategoriesStepPage()), wizardState, this)

      // Action
      composeTestRule.onNodeWithText("Keymaps").assertIsDisplayed().performClick()

      // Verify
      val syncUIState = wizardState.getOrCreateState { SyncConfigurationState() }
      syncUIState.syncCategoryStates.checkSynCategories(
        listOf(
          NodeState(name = "UI settings", state = ToggleableState.On),
          NodeState(name = "Editor font", state = ToggleableState.On),
          NodeState(name = "Keymaps", state = ToggleableState.Off),
          NodeState(name = "Code settings", state = ToggleableState.On),
          NodeState(name = "Plugins", state = ToggleableState.On),
          NodeState(name = "Bundled plugins", state = ToggleableState.On),
          NodeState(name = "Tools", state = ToggleableState.On),
          NodeState(name = "System settings", state = ToggleableState.On),
        )
      )
      assertThat(syncUIState.pushOrPull).isEqualTo(PushOrPull.NOT_SPECIFIED)
      assertThat(syncUIState.configurationOption)
        .isEqualTo(SyncConfigurationOption.CONFIGURE_NEW_ACCOUNT)
    }
}

internal data class NodeState(val name: String, val state: ToggleableState)

internal fun List<CheckboxNode>.checkSynCategories(expected: List<NodeState>) {
  val current =
    flatMap { listOf(it) + it.children }
      .map { node -> NodeState(name = node.label, state = node.isCheckedState) }

  assertThat(current).isEqualTo(expected)
}
