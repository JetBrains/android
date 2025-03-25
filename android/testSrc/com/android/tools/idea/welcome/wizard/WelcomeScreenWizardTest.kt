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

import com.android.flags.junit.FlagRule
import com.android.repository.api.License
import com.android.repository.api.RemotePackage
import com.android.repository.api.RepoManager
import com.android.repository.impl.meta.RepositoryPackages
import com.android.repository.testframework.FakePackage.FakeRemotePackage
import com.android.repository.testframework.FakeRepoManager
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.idea.avdmanager.HardwareAccelerationCheck
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.TestMessagesDialog
import com.android.tools.idea.util.toIoFile
import com.android.tools.idea.welcome.config.FirstRunWizardMode
import com.android.tools.idea.welcome.config.InstallerData
import com.android.tools.idea.welcome.config.installerData
import com.android.tools.idea.welcome.install.FirstRunWizardDefaults
import com.android.tools.idea.welcome.install.SdkComponentInstaller
import com.android.tools.idea.welcome.wizard.deprecated.LinuxKvmInfoStepForm
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.SetupWizardEvent
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.WelcomeScreen
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBLoadingPanelListener
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.table.JBTable
import com.intellij.ui.treeStructure.Tree
import java.awt.event.WindowAdapter
import java.awt.event.WindowListener
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JRadioButton
import javax.swing.JTextPane
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.jetbrains.eval4j.checkNull
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters
import org.mockito.MockedStatic
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.`when`
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.KInvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunsInEdt
@RunWith(Parameterized::class)
class WelcomeScreenWizardTest {

  companion object {
    @JvmStatic
    @Parameters(name = "isTestingLegacyWizard={0}")
    fun parameters() = listOf(arrayOf(true), arrayOf(false))
  }

  @Parameter @JvmField var isTestingLegacyWizard: Boolean? = null

  private val projectRule = AndroidProjectRule.withSdk().initAndroid(true)

  @get:Rule
  val chain =
    RuleChain(
      FlagRule(StudioFlags.NPW_COMPILE_SDK_VERSION, 35),
      FlagRule(StudioFlags.SDK_SETUP_MIGRATED_WIZARD_ENABLED),
      projectRule,
      HeadlessDialogRule(),
      EdtRule(),
    ) // AndroidProjectRule must get initialized off the EDT thread

  private lateinit var sdkPath: File
  private lateinit var mockFirstRunWizardDefaults: MockedStatic<FirstRunWizardDefaults>
  private lateinit var mockAndroidSdkHandler: MockedStatic<AndroidSdkHandler>
  private lateinit var fakeRepoManager: RepoManager

  @Before
  fun setUp() {
    StudioFlags.FIRST_RUN_MIGRATED_WIZARD_ENABLED.override(!isTestingLegacyWizard!!)

    val dialog = TestMessagesDialog(Messages.OK)
    TestDialogManager.setTestDialog(dialog)

    sdkPath = FileUtil.createTempDirectory("sdk", null)
    mockFirstRunWizardDefaults = mockStatic(FirstRunWizardDefaults::class.java, CALLS_REAL_METHODS)
    `when`(FirstRunWizardDefaults.getInitialSdkLocation(FirstRunWizardMode.NEW_INSTALL))
      .thenReturn(sdkPath)

    mockAndroidSdkHandler = mockStatic(AndroidSdkHandler::class.java, CALLS_REAL_METHODS)
    fakeRepoManager =
      spy(
        FakeRepoManager(
          RepositoryPackages(
            emptyList(),
            listOf(
              createFakeRemotePackageWithLicense("build-tools;33.0.1"),
              createFakeRemotePackageWithLicense("platforms;android-35"),
              createFakeRemotePackageWithLicense(
                "system-images;android-35;google_apis_playstore;arm64-v8a"
              ),
            ),
          )
        )
      )
    val sdkHandler = AndroidSdkHandler(sdkPath.toPath(), null, fakeRepoManager)
    whenever(AndroidSdkHandler.getInstance(any(), eq(sdkPath.toPath()))).thenReturn(sdkHandler)

    IdeSdks.removeJdksOn(projectRule.testRootDisposable)
  }

  @After
  fun tearDown() {
    StudioFlags.FIRST_RUN_MIGRATED_WIZARD_ENABLED.clearOverride()

    mockFirstRunWizardDefaults.close()
    mockAndroidSdkHandler.close()
  }

  @Test
  fun welcomeStep_showsWelcomeMessageForUsersWithNoExistingSdks() {
    val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL)

    val welcomeLabel =
      checkNotNull(
        fakeUi.findComponent<JLabel> { it.text.contains("Welcome! This wizard will set up") }
      )
    assertTrue(fakeUi.isShowing(welcomeLabel))
  }

  @Test
  fun welcomeStep_showsWelcomeBackMessageForExistingUsers() {
    `when`(FirstRunWizardDefaults.getInitialSdkLocation(FirstRunWizardMode.NEW_INSTALL))
      .thenReturn(getExistingSdkPath())
    val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL)

    val welcomeLabel =
      checkNotNull(
        fakeUi.findComponent<JLabel> { it.text.contains("Welcome back! This setup wizard will") }
      )
    assertTrue(fakeUi.isShowing(welcomeLabel))
  }

  @Test
  fun installTypeStep_shownWhenNewInstallAndDefaultSdkPathSpecified() {
    val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL)

    checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Next") }).doClick()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val installTypeLabel =
      checkNotNull(fakeUi.findComponent<JLabel> { it.text.equals("Install Type") })
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

    val sdkComponentsLabel = fakeUi.findComponent<JLabel> { it.text.equals("SDK Components Setup") }
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

    val sdkComponentsLabel =
      checkNotNull(fakeUi.findComponent<JLabel> { it.text.equals("SDK Components Setup") })
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
    for (i in 0..<tableModel.rowCount) {
      components.add((tableModel.getValueAt(i, 0) as Pair<*, *>).first.toString())
    }
    assertTrue(components.any { it.contains("Android SDK") })
    assertTrue(components.any { it.contains("Android SDK Platform") })
    assertTrue(components.any { it.matches("^Android \\d.*".toRegex()) })
    assertTrue(components.any { it.contains("Android Virtual Device") })
  }

  @Test
  fun sdkComponentsStep_sdkPathPointsToExistingSdk() {
    val existingSdkPath = getExistingSdkPath()
    `when`(FirstRunWizardDefaults.getInitialSdkLocation(FirstRunWizardMode.NEW_INSTALL))
      .thenReturn(existingSdkPath)
    val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL)
    navigateToSdkComponentsStep(fakeUi)

    val warningLabel =
      checkNotNull(
        fakeUi.findComponent<JLabel> { it.text.contains("An existing Android SDK was detected") }
      )
    assertTrue(fakeUi.isShowing(warningLabel))

    val sdkPathLabel =
      checkNotNull(
        fakeUi.findComponent<ExtendableTextField> { it.text.equals(existingSdkPath.absolutePath) }
      )
    assertTrue(fakeUi.isShowing(sdkPathLabel))
  }

  @Test
  fun sdkComponentsStep_sdkPathWarning() {
    val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL)
    navigateToSdkComponentsStep(fakeUi)

    val sdkPathLabel =
      checkNotNull(
        fakeUi.findComponent<TextFieldWithBrowseButton> { it.text == sdkPath.absolutePath }
      )
    val pathWithWhitespace = FileUtil.createTempDirectory("sdk dir", null)
    sdkPathLabel.text = pathWithWhitespace.absolutePath

    val warningLabel =
      checkNotNull(
        fakeUi.findComponent<JLabel> {
          it.text.contains(
            "should not contain whitespace, as this can cause problems with the NDK tools."
          )
        }
      )
    assertTrue(fakeUi.isShowing(warningLabel))
  }

  @Test
  fun sdkComponentsStep_sdkPathChanged() {
    val existingSdkPath = getExistingSdkPath()
    `when`(FirstRunWizardDefaults.getInitialSdkLocation(FirstRunWizardMode.NEW_INSTALL))
      .thenReturn(existingSdkPath)

    val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL)
    navigateToSdkComponentsStep(fakeUi)

    val warningLabel =
      checkNotNull(
        fakeUi.findComponent<JLabel> { it.text.contains("An existing Android SDK was detected") }
      )
    assertTrue(fakeUi.isShowing(warningLabel))

    val sdkPathLabel =
      checkNotNull(
        fakeUi.findComponent<TextFieldWithBrowseButton> { it.text == existingSdkPath.absolutePath }
      )
    assertTrue(fakeUi.isShowing(sdkPathLabel))

    val loadingPanel = checkNotNull(fakeUi.findComponent<JBLoadingPanel>())
    val nextButton = checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Next") })

    val canContinueLoading = CompletableFuture<Boolean>()
    whenever(
        fakeRepoManager.loadSynchronously(any(), anyOrNull(), any(), any(), any(), any(), any())
      )
      .doAnswer { kInvocationOnMock: KInvocationOnMock ->
        canContinueLoading.get()
        kInvocationOnMock.callRealMethod()
        null
      }

    // Change path
    sdkPathLabel.text = sdkPath.absolutePath

    waitForCondition(2, TimeUnit.SECONDS) { loadingPanel.isLoading }
    waitForCondition(2, TimeUnit.SECONDS) { !nextButton.isEnabled }

    canContinueLoading.complete(true)

    waitForCondition(2, TimeUnit.SECONDS) { !loadingPanel.isLoading }
    waitForCondition(2, TimeUnit.SECONDS) { nextButton.isEnabled }
  }

  @Test
  fun installSummaryStep_showsSummary() {
    val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL)
    navigateToInstallSummaryStep(fakeUi)

    val title = checkNotNull(fakeUi.findComponent<JLabel> { it.text.contains("Verify Settings") })
    assertTrue(fakeUi.isShowing(title))

    val summarySection =
      checkNotNull(fakeUi.findComponent<JTextPane> { it.text.contains("Setup Type:") })
    assertTrue(fakeUi.isShowing(summarySection))
    assertTrue(summarySection.text.contains("Custom"))
    assertTrue(summarySection.text.contains("SDK Folder:"))
    assertTrue(summarySection.text.contains(sdkPath.absolutePath))
    assertTrue(summarySection.text.contains("SDK Components to Download:"))

    val nextButton = checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Next") })
    assertTrue(fakeUi.isShowing(nextButton))
    assertTrue(nextButton.isEnabled)
  }

  @Test
  fun licenseAgreementStep_licensesMustBeAcceptedBeforeProceeding() {
    val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL)
    navigateToLicenseAgreementStep(fakeUi)

    val title = checkNotNull(fakeUi.findComponent<JLabel> { it.text.contains("License Agreement") })
    assertTrue(fakeUi.isShowing(title))

    val proceedButton =
      checkNotNull(fakeUi.findComponent<JButton> { it.text.contains(getLicenseStepNextText()) })
    assertTrue(fakeUi.isShowing(proceedButton))
    assertFalse(proceedButton.isEnabled)

    // Click accept on all licenses
    val tree = checkNotNull(fakeUi.findComponent<Tree>())
    val acceptButton =
      checkNotNull(fakeUi.findComponent<JBRadioButton> { it.text.contains("Accept") })
    for (i in 0..<tree.rowCount) {
      tree.setSelectionRow(i)
      acceptButton.doClick()
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    assertTrue(proceedButton.isEnabled)
  }

  @Test
  fun licenseStep_refreshedWhenSdkPathChanged() {
    val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL)
    navigateToLicenseAgreementStep(fakeUi)

    // Click accept on all licenses
    val tree = checkNotNull(fakeUi.findComponent<Tree>())
    val acceptButton =
      checkNotNull(fakeUi.findComponent<JBRadioButton> { it.text.contains("Accept") })
    for (i in 0..<tree.rowCount) {
      tree.setSelectionRow(i)
      acceptButton.doClick()
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    // Navigate back
    checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Previous") }).doClick()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Previous") }).doClick()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Change SDK location - need to wait for loading to finish
    val loadingPanel = checkNotNull(fakeUi.findComponent<JBLoadingPanel>())
    val loadingFinished = CompletableFuture<Boolean>()
    loadingPanel.addListener(
      object : JBLoadingPanelListener.Adapter() {
        override fun onLoadingFinish() {
          loadingFinished.complete(true)
        }
      }
    )
    val sdkPathLabel = checkNotNull(fakeUi.findComponent<TextFieldWithBrowseButton>())
    val newSdkPath = FileUtil.createTempDirectory("sdk", null)

    // Ensure we return a new mocked sdk - otherwise the licenses won't be refreshed
    val sdkHandler = AndroidSdkHandler(newSdkPath.toPath(), null, fakeRepoManager)
    whenever(AndroidSdkHandler.getInstance(any(), eq(newSdkPath.toPath()))).thenReturn(sdkHandler)

    sdkPathLabel.text = newSdkPath.absolutePath
    pumpEventsAndWaitForFuture(loadingFinished, 5, TimeUnit.SECONDS)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Navigate back to license step
    checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Next") }).doClick()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Next") }).doClick()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Check that none of the licenses are 'accepted'
    for (i in 0..<tree.rowCount) {
      tree.setSelectionRow(i)
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      assertFalse(acceptButton.isSelected)
    }

    val proceedButton =
      checkNotNull(fakeUi.findComponent<JButton> { it.text.contains(getLicenseStepNextText()) })
    assertFalse(proceedButton.isEnabled)
  }

  @Test
  fun linuxKvmInfoStep_shownOnLinux() {
    if (!SystemInfo.isLinux) {
      return
    }

    val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL)
    navigateToLinuxKvmInfoStep(fakeUi)

    val title = checkNotNull(fakeUi.findComponent<JLabel> { it.text.contains("Emulator Settings") })
    assertTrue(fakeUi.isShowing(title))

    val linkLabel =
      checkNotNull(
        fakeUi.findComponent<JEditorPane> {
          it.text.contains("Follow <a href=\"${LinuxKvmInfoStepForm.KVM_DOCUMENTATION_URL}\">")
        }
      )
    assertTrue(fakeUi.isShowing(linkLabel))
  }

  @Test
  fun progressStep_notShownIfSdkPathIsReadOnly() {
    mockStatic(Files::class.java, CALLS_REAL_METHODS).use {
      val readOnlySdk = FileUtil.createTempDirectory("readonly", null)
      `when`(FirstRunWizardDefaults.getInitialSdkLocation(FirstRunWizardMode.NEW_INSTALL))
        .thenReturn(readOnlySdk)
      whenever(Files.isWritable(readOnlySdk.toPath())).thenReturn(false)

      val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL)
      navigateToLicenseAgreementStep(fakeUi)

      // There will be no licenses to accept as there are no components to install

      if (willShowKvmStep()) {
        checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Next") }).doClick()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      }

      assertFalse(
        checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Next") }).isEnabled
      )

      val finishButton = checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Finish") })
      assertTrue(finishButton.isEnabled)
      finishButton.doClick()
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

      assertNull(fakeUi.findComponent<JLabel> { it.text.contains("Downloading Components") })
    }
  }

  @Test
  fun progressStep_cancelInstallationAndFinish() {
    val mockInstaller = mock(SdkComponentInstaller::class.java)
    val remotePackage = createFakeRemotePackageWithLicense("platforms;android-35")
    whenever(mockInstaller.getPackagesToInstall(any(), any())).thenReturn(listOf(remotePackage))

    val installerStarted = CompletableFuture<Boolean>()
    val cancelTriggered = CompletableFuture<Boolean>()
    whenever(mockInstaller.installComponents(any(), any(), anyOrNull(), any(), any(), any())).then {
      // Pause the installer to allow us to check the UI and to cancel the task
      installerStarted.complete(true)

      // Resume once cancel has been triggered
      cancelTriggered.get()
    }

    val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL, sdkComponentInstaller = mockInstaller)
    navigateToProgressStep(fakeUi)

    val progressLabel =
      checkNotNull(fakeUi.findComponent<JLabel> { it.text.contains("Downloading Components") })
    assertTrue(fakeUi.isShowing(progressLabel))

    // Details hidden by default
    fakeUi.findComponent<EditorComponentImpl>().checkNull()

    // Click 'More details' button
    val showDetailsButton =
      checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Show Details") })
    assertTrue(fakeUi.isShowing(showDetailsButton))
    showDetailsButton.doClick()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Check that console output is showing
    val consoleOutput = checkNotNull(fakeUi.findComponent<EditorComponentImpl>())
    assertTrue(fakeUi.isShowing(consoleOutput))

    // Check that the finish button is not enabled
    val finishButton = checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Finish") })
    assertTrue(fakeUi.isShowing(finishButton))
    assertFalse(finishButton.isEnabled)

    // Wait for install task to start
    installerStarted.get()

    // Check that the license has been accepted
    assertTrue(remotePackage.license!!.checkAccepted(sdkPath.toPath()))

    // Click 'Cancel' button
    val cancelButton = checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Cancel") })
    assertTrue(fakeUi.isShowing(cancelButton))
    assertTrue(cancelButton.isEnabled)
    cancelButton.doClick()
    cancelTriggered.complete(true)
    PlatformTestUtil.waitForAllBackgroundActivityToCalmDown()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Click 'Finish'
    assertTrue(finishButton.isEnabled)
    finishButton.doClick()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  @Test
  fun missingSdkComponentStep_shownWhenInstallTypeMissingSdk() {
    val fakeUi = createWizard(FirstRunWizardMode.MISSING_SDK)

    val title = checkNotNull(fakeUi.findComponent<JLabel> { it.text.contains("Missing SDK") })
    assertTrue(fakeUi.isShowing(title))

    val missingSdkLabel =
      checkNotNull(fakeUi.findComponent<JLabel> { it.text.contains("No Android SDK found") })
    assertTrue(fakeUi.isShowing(missingSdkLabel))
  }

  @Test
  fun installHandoffMode_skipsStraightToInstallingComponentsStepWhenSdkConfiguredInInstaller() {
    val mockInstaller = mock(SdkComponentInstaller::class.java)
    whenever(mockInstaller.getPackagesToInstall(any(), any()))
      .thenReturn(
        listOf(FakeRemotePackage("system-images;android-35;google_apis_playstore;arm64-v8a"))
      )

    val installHandoffData = InstallerData(sdkPath, true, "timestamp", "1234")
    val fakeUi = createWizard(FirstRunWizardMode.INSTALL_HANDOFF, mockInstaller, installHandoffData)

    val progressLabel =
      checkNotNull(fakeUi.findComponent<JLabel> { it.text.contains("Downloading Components") })
    assertTrue(fakeUi.isShowing(progressLabel))

    val finishButton = checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Finish") })
    waitForCondition(10, TimeUnit.SECONDS) { finishButton.isEnabled }
  }

  @Test
  fun installHandoffMode_startsWithSdkComponentsStepWhenSdkNotConfiguredInInstaller() {
    val installHandoffData = InstallerData(null, true, "timestamp", "1234")
    val fakeUi =
      createWizard(FirstRunWizardMode.INSTALL_HANDOFF, installHandoffData = installHandoffData)

    val title =
      checkNotNull(fakeUi.findComponent<JLabel> { it.text.contains("SDK Components Setup") })
    assertTrue(fakeUi.isShowing(title))
  }

  @Test
  fun frameNotClosed_whenUserClosesWindowAndDoesNotConfirmClose() {
    mockStatic(ConfirmFirstRunWizardCloseDialog::class.java).use { confirmCloseDialog ->
      whenever(ConfirmFirstRunWizardCloseDialog.show())
        .thenReturn(ConfirmFirstRunWizardCloseDialog.Result.DoNotClose)

      val listeners = arrayOf<WindowListener?>(object : WindowAdapter() {})
      val mockFrame = configureFrameMock(listeners)

      val welcomeScreen = createWelcomeScreen(FirstRunWizardMode.NEW_INSTALL)
      welcomeScreen
        .welcomePanel // Need to access the welcome panel to ensure the wizard is initialised
      welcomeScreen.setupFrame(mockFrame)

      val listener = listeners[0]
      assertNotNull(listener)
      listener.windowClosing(null)

      confirmCloseDialog.verify { ConfirmFirstRunWizardCloseDialog.show() }

      verify(mockFrame, times(0)).dispose()
    }
  }

  @Test
  fun frameClosed_whenUserClosesWindowAndConfirmsClose() {
    mockStatic(ConfirmFirstRunWizardCloseDialog::class.java).use { confirmCloseDialog ->
      whenever(ConfirmFirstRunWizardCloseDialog.show())
        .thenReturn(ConfirmFirstRunWizardCloseDialog.Result.Skip)

      val listeners = arrayOf<WindowListener?>(object : WindowAdapter() {})
      val mockFrame = configureFrameMock(listeners)

      val welcomeScreen = createWelcomeScreen(FirstRunWizardMode.NEW_INSTALL)
      welcomeScreen
        .welcomePanel // Need to access the welcome panel to ensure the wizard is initialised
      welcomeScreen.setupFrame(mockFrame)

      val listener = listeners[0]
      assertNotNull(listener)
      listener.windowClosing(null)

      confirmCloseDialog.verify { ConfirmFirstRunWizardCloseDialog.show() }

      verify(mockFrame, atLeastOnce()).dispose()
    }
  }

  @Test
  fun existingNonWelcomeFrameWindowListeners_areNotRemovedWhenSettingUpFrame() {
    mockStatic(ConfirmFirstRunWizardCloseDialog::class.java).use { confirmCloseDialog ->
      whenever(ConfirmFirstRunWizardCloseDialog.show())
        .thenReturn(ConfirmFirstRunWizardCloseDialog.Result.Skip)

      val listeners = arrayOf<WindowListener?>(object : WindowAdapter() {})
      val mockFrame = configureFrameMock(listeners)

      val welcomeScreen = createWelcomeScreen(FirstRunWizardMode.NEW_INSTALL)
      welcomeScreen
        .welcomePanel // Need to access the welcome panel to ensure the wizard is initialised
      welcomeScreen.setupFrame(mockFrame)

      verify(mockFrame, never()).removeWindowListener(any())
    }
  }

  @Test
  fun welcomeFrameWindowListener_removedAndWrappedByNewWindowListener() {
    mockStatic(ConfirmFirstRunWizardCloseDialog::class.java).use { confirmCloseDialog ->
      whenever(ConfirmFirstRunWizardCloseDialog.show())
        .thenReturn(ConfirmFirstRunWizardCloseDialog.Result.Skip)

      val listeners = arrayOf<WindowListener?>(object : WindowAdapter() {})
      val mockFrame = configureFrameMock(listeners)
      WelcomeFrame.setupCloseAction(mockFrame)

      val welcomeFrameListener = listeners[0]
      assertNotNull(welcomeFrameListener)
      val welcomeFrameListenerSpy = spy(welcomeFrameListener)
      listeners[0] = welcomeFrameListenerSpy

      val welcomeScreen = createWelcomeScreen(FirstRunWizardMode.NEW_INSTALL)
      welcomeScreen
        .welcomePanel // Need to access the welcome panel to ensure the wizard is initialised
      welcomeScreen.setupFrame(mockFrame)

      val listener = listeners[0]
      assertNotNull(listener)
      assertNotEquals(welcomeFrameListenerSpy, listener)

      listener.windowClosing(null)
      verify(welcomeFrameListenerSpy, never()).windowClosing(any())

      listener.windowClosed(null)
      verify(welcomeFrameListenerSpy, times(1)).windowClosed(anyOrNull())
    }
  }

  @Test
  fun usageMetricsTracked_wizardFinishedAfterSuccessfullyInstallingComponents() {
    val mockInstaller = mock(SdkComponentInstaller::class.java)
    val remotePackage = createFakeRemotePackageWithLicense("platforms;android-35")
    whenever(mockInstaller.getPackagesToInstall(any(), any())).thenReturn(listOf(remotePackage))

    val mockTracker: FirstRunWizardTracker = mock()
    val fakeUi =
      createWizard(
        FirstRunWizardMode.NEW_INSTALL,
        sdkComponentInstaller = mockInstaller,
        tracker = mockTracker,
      )
    navigateToProgressStep(fakeUi)

    // Click 'Finish'
    val finishButton = checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Finish") })
    waitForCondition(2, TimeUnit.SECONDS) { finishButton.isEnabled }
    finishButton.doClick()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    inOrder(mockTracker).apply {
      verify(mockTracker).trackWizardStarted()
      verify(mockTracker).trackStepShowing(SetupWizardEvent.WizardStep.WizardStepKind.WELCOME)
      verify(mockTracker).trackStepShowing(SetupWizardEvent.WizardStep.WizardStepKind.INSTALL_TYPE)
      verify(mockTracker)
        .trackStepShowing(SetupWizardEvent.WizardStep.WizardStepKind.SDK_COMPONENTS)
      verify(mockTracker)
        .trackStepShowing(SetupWizardEvent.WizardStep.WizardStepKind.INSTALL_SUMMARY)
      verify(mockTracker)
        .trackStepShowing(SetupWizardEvent.WizardStep.WizardStepKind.LICENSE_AGREEMENT)
      if (SystemInfo.isLinux) {
        verify(mockTracker)
          .trackStepShowing(SetupWizardEvent.WizardStep.WizardStepKind.LINUX_KVM_INFO)
      }
      verify(mockTracker).trackStepShowing(SetupWizardEvent.WizardStep.WizardStepKind.INSTALL_SDK)
      verify(mockTracker).trackWizardFinished(SetupWizardEvent.CompletionStatus.FINISHED)
    }

    verify(mockTracker, atLeastOnce())
      .trackInstallationMode(SetupWizardEvent.InstallationMode.CUSTOM)
    verify(mockTracker, never()).trackSdkInstallLocationChanged()
    verify(mockTracker)
      .trackSdkComponentsToInstall(
        listOf(
          SetupWizardEvent.SdkInstallationMetrics.SdkComponentKind.ANDROID_SDK,
          SetupWizardEvent.SdkInstallationMetrics.SdkComponentKind.ANDROID_PLATFORM,
        )
      )
    verify(mockTracker).trackInstallingComponentsStarted()
    verify(mockTracker)
      .trackInstallingComponentsFinished(
        SetupWizardEvent.SdkInstallationMetrics.SdkInstallationResult.SUCCESS
      )
  }

  @Test
  fun usageMetricsTracked_wizardFinishedAfterCancelingInstallation() {
    val mockInstaller = mock(SdkComponentInstaller::class.java)
    val remotePackage = createFakeRemotePackageWithLicense("platforms;android-35")
    whenever(mockInstaller.getPackagesToInstall(any(), any())).thenReturn(listOf(remotePackage))

    val cancelTriggered = CompletableFuture<Boolean>()
    whenever(mockInstaller.installComponents(any(), any(), anyOrNull(), any(), any(), any())).then {
      // Resume once cancel has been triggered
      cancelTriggered.get()
    }

    val mockTracker: FirstRunWizardTracker = mock()
    val fakeUi =
      createWizard(
        FirstRunWizardMode.NEW_INSTALL,
        sdkComponentInstaller = mockInstaller,
        tracker = mockTracker,
      )
    navigateToProgressStep(fakeUi)

    // Click 'Cancel' button
    val cancelButton = checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Cancel") })
    cancelButton.doClick()
    cancelTriggered.complete(true)
    PlatformTestUtil.waitForAllBackgroundActivityToCalmDown()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Click 'Finish'
    val finishButton = checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Finish") })
    finishButton.doClick()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    inOrder(mockTracker).apply {
      verify(mockTracker).trackWizardStarted()
      verify(mockTracker).trackStepShowing(SetupWizardEvent.WizardStep.WizardStepKind.INSTALL_SDK)
      verify(mockTracker).trackInstallingComponentsStarted()
      verify(mockTracker)
        .trackInstallingComponentsFinished(
          SetupWizardEvent.SdkInstallationMetrics.SdkInstallationResult.CANCELED
        )
      verify(mockTracker).trackWizardFinished(SetupWizardEvent.CompletionStatus.FINISHED)
    }
  }

  @Test
  fun usageMetricsTracked_wizardCanceled() {
    mockStatic(ConfirmFirstRunWizardCloseDialog::class.java).use { confirmCloseDialog ->
      whenever(ConfirmFirstRunWizardCloseDialog.show())
        .thenReturn(ConfirmFirstRunWizardCloseDialog.Result.Skip)
      val mockTracker: FirstRunWizardTracker = mock()
      val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL, tracker = mockTracker)
      navigateToSdkComponentsStep(fakeUi)

      val cancelButton = checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Cancel") })
      cancelButton.doClick()
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

      inOrder(mockTracker).apply {
        verify(mockTracker, atLeastOnce()).trackWizardStarted()
        verify(mockTracker).trackWizardFinished(SetupWizardEvent.CompletionStatus.CANCELED)
      }
    }
  }

  @Test
  fun usageMetricsTracked_bothForwardAndBackwardsNavigationTracked() {
    val mockTracker: FirstRunWizardTracker = mock()
    val fakeUi = createWizard(FirstRunWizardMode.NEW_INSTALL, tracker = mockTracker)
    navigateToSdkComponentsStep(fakeUi)

    val backButton = checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Previous") })
    backButton.doClick()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    inOrder(mockTracker).apply {
      verify(mockTracker).trackWizardStarted()
      verify(mockTracker).trackStepShowing(SetupWizardEvent.WizardStep.WizardStepKind.WELCOME)
      verify(mockTracker).trackStepShowing(SetupWizardEvent.WizardStep.WizardStepKind.INSTALL_TYPE)
      verify(mockTracker)
        .trackStepShowing(SetupWizardEvent.WizardStep.WizardStepKind.SDK_COMPONENTS)
      verify(mockTracker).trackStepShowing(SetupWizardEvent.WizardStep.WizardStepKind.INSTALL_TYPE)
    }
  }

  private fun getExistingSdkPath(): File {
    return AndroidSdks.getInstance().allAndroidSdks.firstOrNull()?.homeDirectory?.toIoFile()!!
  }

  private fun createWizard(
    wizardMode: FirstRunWizardMode,
    sdkComponentInstaller: SdkComponentInstaller? = null,
    installHandoffData: InstallerData? = null,
    tracker: FirstRunWizardTracker = mock(),
  ): FakeUi {
    val welcomeScreen =
      createWelcomeScreen(wizardMode, sdkComponentInstaller, installHandoffData, tracker)
    return FakeUi(welcomeScreen.welcomePanel, createFakeWindow = true)
  }

  private fun createWelcomeScreen(
    wizardMode: FirstRunWizardMode,
    sdkComponentInstaller: SdkComponentInstaller? = null,
    installHandoffData: InstallerData? = null,
    tracker: FirstRunWizardTracker = mock(),
  ): WelcomeScreen {
    if (installHandoffData != null) {
      installerData = installHandoffData
    }

    val installer = sdkComponentInstaller ?: SdkComponentInstaller()
    val welcomeScreen =
      AndroidStudioWelcomeScreenProvider()
        .createWelcomeScreen(
          useNewWizard = !isTestingLegacyWizard!!,
          wizardMode,
          installer,
          tracker,
        )

    Disposer.register(projectRule.testRootDisposable, welcomeScreen)

    return welcomeScreen
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

  private fun navigateToInstallSummaryStep(fakeUi: FakeUi) {
    navigateToSdkComponentsStep(fakeUi)

    checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Next") }).doClick()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  private fun navigateToLicenseAgreementStep(fakeUi: FakeUi) {
    navigateToInstallSummaryStep(fakeUi)

    checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Next") }).doClick()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  private fun navigateToLinuxKvmInfoStep(fakeUi: FakeUi) {
    navigateToLicenseAgreementStep(fakeUi)
    acceptAllLicenses(fakeUi)
    checkNotNull(fakeUi.findComponent<JButton> { it.text.contains(getLicenseStepNextText()) })
      .doClick()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  private fun navigateToProgressStep(fakeUi: FakeUi) {
    navigateToLicenseAgreementStep(fakeUi)
    acceptAllLicenses(fakeUi)
    checkNotNull(fakeUi.findComponent<JButton> { it.text.contains(getLicenseStepNextText()) })
      .doClick()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    if (willShowKvmStep()) {
      checkNotNull(fakeUi.findComponent<JButton> { it.text.contains(getKvmStepNextText()) })
        .doClick()
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }
  }

  private fun acceptAllLicenses(fakeUi: FakeUi) {
    val tree = checkNotNull(fakeUi.findComponent<Tree>())
    val acceptButton =
      checkNotNull(fakeUi.findComponent<JBRadioButton> { it.text.contains("Accept") })
    for (i in 0..<tree.rowCount) {
      tree.setSelectionRow(i)
      acceptButton.doClick()
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }
  }

  private fun getLicenseStepNextText(): String {
    if (willShowKvmStep()) {
      return "Next"
    }
    // This is a quirk of the old wizard - it shows 'Finish' on the penultimate step
    return if (isTestingLegacyWizard == true) "Finish" else "Next"
  }

  private fun willShowKvmStep() =
    SystemInfo.isLinux && !HardwareAccelerationCheck.isChromeOSAndIsNotHWAccelerated()

  private fun getKvmStepNextText(): String {
    // This is a quirk of the old wizard - it shows 'Finish' on the penultimate step
    return if (isTestingLegacyWizard == true) "Finish" else "Next"
  }

  private fun createFakeRemotePackageWithLicense(path: String): RemotePackage {
    val remotePackage = FakeRemotePackage(path)
    remotePackage.setCompleteUrl("http://www.example.com/package.zip")

    val factory = RepoManager.commonModule.createLatestFactory()
    val license: License = factory.createLicenseType("some license text", "license1")
    remotePackage.license = license

    return remotePackage
  }

  private fun configureFrameMock(listeners: Array<WindowListener?>): JFrame {
    val mockFrame = mock(JFrame::class.java)
    whenever(mockFrame.getListeners<WindowListener>(any())).thenReturn(listeners)
    whenever(mockFrame.addWindowListener(any())).thenAnswer { invocation: InvocationOnMock ->
      listeners[0] = invocation.getArgument(0)
      null
    }
    return mockFrame
  }
}
