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

import com.android.sdklib.AndroidVersion
import com.android.testutils.TestUtils
import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.Sdks
import com.android.tools.idea.welcome.config.FirstRunWizardMode
import com.android.tools.idea.welcome.config.InstallerData
import com.android.tools.idea.welcome.config.installerData
import com.android.tools.idea.welcome.install.FirstRunWizardDefaults
import com.android.tools.idea.welcome.wizard.deprecated.FirstRunWizardHost
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.table.JBTable
import junit.framework.Assert.assertFalse
import junit.framework.TestCase
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import java.io.File
import java.util.concurrent.TimeUnit
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
  fun welcomeStep_shownWhenInstallTypeNewInstall() {
    val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL)

    val welcomeLabel = checkNotNull(fakeUi.findComponent<JLabel> { it.text.contains("Once the setup wizard completes") })
    assertTrue(fakeUi.isShowing(welcomeLabel))
  }

  @Test
  fun welcomeStep_showsWelcomeMessageForUsersWithNoExistingSdks() {
    deleteSdks()
    val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL)

    val welcomeLabel = checkNotNull(fakeUi.findComponent<JLabel> { it.text.contains("Welcome! This wizard will set up") })
    assertTrue(fakeUi.isShowing(welcomeLabel))
  }

  @Test
  fun welcomeStep_showsWelcomeBackMessageForExistingUsers() {
    val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL)

    val welcomeLabel = checkNotNull(fakeUi.findComponent<JLabel> { it.text.contains("Welcome back! This setup wizard will") })
    assertTrue(fakeUi.isShowing(welcomeLabel))
  }

  @Test
  fun installTypeStep_shownWhenNewInstallAndDefaultSdkPathSpecified() {
    val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL)

    checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Next") }).doClick()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val installTypeLabel = checkNotNull(fakeUi.findComponent<JLabel>{ it.text.equals("Install Type") })
    assertTrue(fakeUi.isShowing(installTypeLabel))
  }

  @Test
  fun sdkComponentsStep_skippedWhenNewInstallAndStandardInstallTypeChosen() {
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
  fun sdkComponentsStep_shownWhenNewInstallAndCustomInstallTypeChosen() {
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
  fun sdkComponentsStep_skippedWhenInstallHandoffModeAndInstallerDataSdkPathValid() {
    val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL)

    // Click 'next' on welcome screen
    checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Next") }).doClick()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  @Test
  fun sdkComponentsStep_showsComponentsToInstall() {
    val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL)
    navigateToSdkComponentsStep(fakeUi)

    val tableModel = checkNotNull(fakeUi.findComponent<JBTable>()).model
    assertThat(tableModel.rowCount).isGreaterThan(0)

    val components = mutableListOf<String>()
    for (i in 0..< tableModel.rowCount) {
      components.add((tableModel.getValueAt(i, 0) as Pair<*, *>).first.toString())
    }
    assertTrue(components.any { it.contains("Android SDK")})
    assertTrue(components.any { it.contains("Android SDK Platform")})
    assertTrue(components.any { it.matches("^Android \\d.*".toRegex()) })
    assertTrue(components.any { it.contains("Android Virtual Device")})
  }

  @Test
  fun sdkComponentsStep_sdkPathPointsToExistingSdk() {
    val sdkPath = TestUtils.getSdk().toFile()
    createLatestSdk(sdkPath)
    mockStatic(FirstRunWizardDefaults::class.java).use {
      val mode = FirstRunWizardMode.NEW_INSTALL
      `when`(FirstRunWizardDefaults.getInitialSdkLocation(mode)).thenReturn(sdkPath)

      val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL)
      navigateToSdkComponentsStep(fakeUi)

      val warningLabel = checkNotNull(fakeUi.findComponent<JLabel>{ it.text.contains("An existing Android SDK was detected") })
      assertTrue(fakeUi.isShowing(warningLabel))

      val sdkPathLabel = checkNotNull(fakeUi.findComponent<ExtendableTextField>{ it.text.equals(sdkPath.absolutePath) })
      assertTrue(fakeUi.isShowing(sdkPathLabel))
    }
  }

  @Test
  fun sdkComponentsStep_sdkPathWarning() {
    val sdkPath = TestUtils.getSdk().toFile()
    createLatestSdk(sdkPath)
    mockStatic(FirstRunWizardDefaults::class.java).use {
      `when`(FirstRunWizardDefaults.getInitialSdkLocation(FirstRunWizardMode.NEW_INSTALL)).thenReturn(sdkPath)
      val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL)
      navigateToSdkComponentsStep(fakeUi)

      val sdkPathLabel = checkNotNull(fakeUi.findComponent<TextFieldWithBrowseButton>{ it.text == sdkPath.absolutePath })
      val pathWithWhitespace = FileUtil.createTempDirectory("sdk dir", null)
      sdkPathLabel.text = pathWithWhitespace.absolutePath

      val warningLabel = checkNotNull(fakeUi.findComponent<JLabel>{ it.text.contains("should not contain whitespace, as this can cause problems with the NDK tools.") })
      assertTrue(fakeUi.isShowing(warningLabel))
    }
  }

  @Test
  fun sdkComponentsStep_sdkPathChanged() {
    val sdkPath = TestUtils.getSdk().toFile()
    createLatestSdk(sdkPath)
    mockStatic(FirstRunWizardDefaults::class.java).use {
      val mode = FirstRunWizardMode.NEW_INSTALL
      `when`(FirstRunWizardDefaults.getInitialSdkLocation(mode)).thenReturn(sdkPath)

      val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL)
      navigateToSdkComponentsStep(fakeUi)

      val warningLabel = checkNotNull(fakeUi.findComponent<JLabel>{ it.text.contains("An existing Android SDK was detected") })
      assertTrue(fakeUi.isShowing(warningLabel))

      val sdkPathLabel = checkNotNull(fakeUi.findComponent<TextFieldWithBrowseButton>{ it.text == sdkPath.absolutePath })
      assertTrue(fakeUi.isShowing(sdkPathLabel))

      // Add listener to the loading panel to allow us to wait for loading to complete
      val loadingPanel = checkNotNull(fakeUi.findComponent<JBLoadingPanel>())
      val nextButton = checkNotNull(fakeUi.findComponent<JButton>{it.text.contains("Next") })

      // Change path
      val newSdkPath = FileUtil.createTempDirectory("sdk", null)
      sdkPathLabel.text = newSdkPath.absolutePath

      waitForCondition(2, TimeUnit.SECONDS) { loadingPanel.isLoading }
      assertFalse(nextButton.isEnabled)

      waitForCondition(10, TimeUnit.SECONDS) { !loadingPanel.isLoading }
      assertTrue(nextButton.isEnabled)
    }
  }

  @Test
  fun licenseStep_refreshedWhenSdkPathChanged() {
    // TODO - will implement when migrating the license step
  }

  private fun deleteSdks() {
    val sdks = AndroidSdks.getInstance().allAndroidSdks
    for (sdk in sdks) {
      WriteAction.runAndWait<RuntimeException> {
        ProjectJdkTable.getInstance().removeJdk(sdk!!)
      }
    }
  }

  private fun createLatestSdk(sdkPath: File) {
    createSdk(sdkPath, Sdks.getLatestAndroidPlatform())
  }

  private fun createSdk(sdkPath: File, version: AndroidVersion) {
    Sdks.allowAccessToSdk(projectRule.testRootDisposable)
    val target = Sdks.findAndroidTarget(sdkPath, version)

    val sdk  = AndroidSdks.getInstance().create(target, sdkPath, "Test SDK", true /* add roots */)
    TestCase.assertNotNull(sdk)
    IdeSdks.removeJdksOn(projectRule.testRootDisposable)
  }

  private fun createWizard(wizardMode: FirstRunWizardMode, sdkPath: File? = null): FakeUi {
    installerData = InstallerData(sdkPath ?: IdeSdks.getInstance().getAndroidSdkPath(), true, "timestamp", "1234")

    val welcomeScreen = if (isTestingLegacyWizard == true) FirstRunWizardHost(wizardMode) else StudioFirstRunWelcomeScreen(wizardMode)
    Disposer.register(projectRule.testRootDisposable, welcomeScreen)
    return FakeUi(welcomeScreen.welcomePanel, createFakeWindow = true)
  }

  private fun navigateToSdkComponentsStep(fakeUi: FakeUi) {
    // Click 'next' on welcome screen
    checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Next") }).doClick()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Click 'Custom' radio button then 'Next'
    checkNotNull(fakeUi.findComponent<JRadioButton> { it.text.contains("Custom") }).doClick()
    checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Next") }).doClick()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }
}