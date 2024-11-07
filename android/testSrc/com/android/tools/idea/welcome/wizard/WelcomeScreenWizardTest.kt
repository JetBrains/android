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
package com.android.tools.idea.welcome.wizard

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JRootPane

@RunsInEdt
@RunWith(Parameterized::class)
class WelcomeScreenWizardTest {

  companion object {
    @JvmStatic
    @Parameters(name = "isTestingLegacyWizard={0}")
    fun parameters() = listOf(
      arrayOf(true),
      arrayOf(false),
    )
  }

  @Parameter
  @JvmField
  var isTestingLegacyWizard: Boolean? = null

  private val projectRule = AndroidProjectRule.withSdk().initAndroid(true)

  @get:Rule
  val chain = RuleChain(projectRule, HeadlessDialogRule(), EdtRule()) // AndroidProjectRule must get initialized off the EDT thread

  @Before
  fun setUp() {
    StudioFlags.NPW_FIRST_RUN_WIZARD.override(!isTestingLegacyWizard!!)
  }

  @After
  fun tearDown() {
    StudioFlags.NPW_FIRST_RUN_WIZARD.clearOverride()
  }

  @Test
  fun welcomeStepShowsWelcomeMessageForUsersWithNoExistingSDKs() {
    deleteSdks()
    val fakeUi = createWizard()

    val label = fakeUi.findComponent<JLabel> { it.text.contains("Welcome! This wizard will set up") }
    checkNotNull(label)
    assertThat(fakeUi.isShowing(label)).isTrue()
  }

  @Test
  fun welcomeStepsShowsWelcomeBackMessageForExistingUsers() {
    val fakeUi = createWizard()

    val label = fakeUi.findComponent<JLabel> { it.text.contains("Welcome back! This setup wizard will validate") }
    checkNotNull(label)
    assertThat(fakeUi.isShowing(label)).isTrue()
  }

  @Test
  fun welcomeStepShowsNextButton() {
    val fakeUi = createWizard()

    val button = fakeUi.findComponent<JButton> { it.text.contains("Next") }
    checkNotNull(button)
    assertThat(fakeUi.isShowing(button)).isTrue()

    button.doClick()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val installTypeLabel = checkNotNull(fakeUi.findComponent<JLabel>{ it.text.contains("Install Type") })
    assertThat(fakeUi.isShowing(installTypeLabel)).isTrue()
  }

  private fun deleteSdks() {
    val sdks = AndroidSdks.getInstance().allAndroidSdks
    for (sdk in sdks) {
      WriteAction.runAndWait<RuntimeException> {
        ProjectJdkTable.getInstance().removeJdk(sdk!!)
      }
    }
  }

  private fun createWizard(): FakeUi {
    val welcomeScreenProvider = AndroidStudioWelcomeScreenProvider()
    val welcomeScreen = welcomeScreenProvider.createWelcomeScreen(JRootPane())
    Disposer.register(projectRule.testRootDisposable, welcomeScreen)
    return FakeUi(welcomeScreen.welcomePanel, createFakeWindow = true)
  }
}