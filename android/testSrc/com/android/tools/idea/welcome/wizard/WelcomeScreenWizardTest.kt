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
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.welcome.config.FirstRunWizardMode
import com.android.tools.idea.welcome.config.InstallerData
import com.android.tools.idea.welcome.config.installerData
import com.android.tools.idea.welcome.wizard.deprecated.FirstRunWizardHost
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters
import java.io.File
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JRadioButton
import kotlin.test.assertNull

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
  fun welcomeStepShownWhenInstallTypeNewInstall() {
    val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL)

    val welcomeLabel = checkNotNull(fakeUi.findComponent<JLabel> { it.text.contains("Once the setup wizard completes") })
    assertTrue(fakeUi.isShowing(welcomeLabel))
  }

  @Test
  fun welcomeStepShowsWelcomeMessageForUsersWithNoExistingSDKs() {
    deleteSdks()
    val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL)

    val welcomeLabel = checkNotNull(fakeUi.findComponent<JLabel> { it.text.contains("Welcome! This wizard will set up") })
    assertTrue(fakeUi.isShowing(welcomeLabel))
  }

  @Test
  fun welcomeStepShowsWelcomeBackMessageForExistingUsers() {
    val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL)

    val welcomeLabel = checkNotNull(fakeUi.findComponent<JLabel> { it.text.contains("Welcome back! This setup wizard will") })
    assertTrue(fakeUi.isShowing(welcomeLabel))
  }

  @Test
  fun installTypeStepShownWhenNewInstallAndDefaultSdkPathSpecified() {
    val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL)

    checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Next") }).doClick()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val installTypeLabel = checkNotNull(fakeUi.findComponent<JLabel>{ it.text.equals("Install Type") })
    assertTrue(fakeUi.isShowing(installTypeLabel))
  }

  @Test
  fun sdkComponentsStepSkippedWhenNewInstallAndStandardInstallTypeChosen() {
    val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL)

    // Click 'next' on welcome screen
    checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Next") }).doClick()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Click 'next' on 'Install Type' screen - 'Standard' is selected by default
    checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Next") }).doClick()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val sdkComponentsLabel = fakeUi.findComponent<JLabel>{ it.text.equals("SDK Components Setup") }
    assertNull(sdkComponentsLabel)
  }

  @Test
  fun sdkComponentsStepShownWhenNewInstallAndCustomInstallTypeChosen() {
    val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL)

    // Click 'next' on welcome screen
    checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Next") }).doClick()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Click 'Custom' radio button then 'Next'
    checkNotNull(fakeUi.findComponent<JRadioButton> { it.text.contains("Custom") }).doClick()
    checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Next") }).doClick()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val sdkComponentsLabel = checkNotNull(fakeUi.findComponent<JLabel>{ it.text.equals("SDK Components Setup") })
    assertTrue(fakeUi.isShowing(sdkComponentsLabel))
  }

  @Test
  fun sdkComponentsStepSkippedWhenInstallHandoffModeAndInstallerDataSdkPathValid() {
    val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL)

    // Click 'next' on welcome screen
    checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Next") }).doClick()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  @Test
  fun missingSDKComponentStepShownWhenInstallTypeMissingSDK() {
    val fakeUi = createWizard(FirstRunWizardMode.MISSING_SDK)

    val missingSdkLabel = checkNotNull(fakeUi.findComponent<JLabel> { it.text.contains("No Android SDK found") })
    assertTrue(fakeUi.isShowing(missingSdkLabel))
  }

  private fun deleteSdks() {
    val sdks = AndroidSdks.getInstance().allAndroidSdks
    for (sdk in sdks) {
      WriteAction.runAndWait<RuntimeException> {
        ProjectJdkTable.getInstance().removeJdk(sdk!!)
      }
    }
  }

  private fun createWizard(wizardMode: FirstRunWizardMode, sdkPath: File? = null): FakeUi {
    installerData = InstallerData(sdkPath ?: IdeSdks.getInstance().getAndroidSdkPath(), true, "timestamp", "1234")

    val welcomeScreen = if (isTestingLegacyWizard == true) FirstRunWizardHost(wizardMode) else StudioFirstRunWelcomeScreen(wizardMode)
    Disposer.register(projectRule.testRootDisposable, welcomeScreen)
    return FakeUi(welcomeScreen.welcomePanel, createFakeWindow = true)
  }
}