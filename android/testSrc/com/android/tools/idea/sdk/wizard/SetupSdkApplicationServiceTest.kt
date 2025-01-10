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
package com.android.tools.idea.sdk.wizard

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
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.welcome.install.SdkComponentInstaller
import com.android.tools.idea.welcome.wizard.FirstRunWizardTracker
import com.google.wireless.android.sdk.stats.SetupWizardEvent
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.treeStructure.Tree
import org.junit.After
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JLabel
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters
import org.mockito.MockedStatic
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

@RunsInEdt
@RunWith(Parameterized::class)
class SetupSdkApplicationServiceTest {
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
      projectRule,
      HeadlessDialogRule(),
      EdtRule(),
    ) // AndroidProjectRule must get initialized off the EDT thread

  private lateinit var mockAndroidSdkHandler: MockedStatic<AndroidSdkHandler>
  private val newSdkPath = FileUtil.createTempDirectory("sdk", null)

  @Before
  fun setUp() {
    StudioFlags.SDK_SETUP_MIGRATED_WIZARD_ENABLED.override(isTestingLegacyWizard == false)

    mockAndroidSdkHandler = mockStatic(AndroidSdkHandler::class.java, CALLS_REAL_METHODS)
    val fakeRepoManager =
      spy(
        FakeRepoManager(
          RepositoryPackages(
            emptyList(),
            listOf(
              createFakeRemotePackageWithLicense("build-tools"),
              createFakeRemotePackageWithLicense("platform-tools"),
              createFakeRemotePackageWithLicense("platforms"),
              createFakeRemotePackageWithLicense("emulator"),
              createFakeRemotePackageWithLicense("platforms;android-35")
            ),
          )
        )
      )
    val sdkHandler = AndroidSdkHandler(newSdkPath.toPath(), null, fakeRepoManager)
    whenever(AndroidSdkHandler.getInstance(any(), eq(newSdkPath.toPath()))).thenReturn(sdkHandler)

    IdeSdks.removeJdksOn(projectRule.testRootDisposable)
  }

  @After
  fun tearDown() {
    mockAndroidSdkHandler.close()
  }

  @Test
  fun installsSdkComponentsWhenNoneAreInstalled() {
    val mockInstaller = mock(SdkComponentInstaller::class.java)
    val remotePackage = createFakeRemotePackageWithLicense("platforms;android-35")
    whenever(mockInstaller.getPackagesToInstall(any(), any())).thenReturn(listOf(remotePackage))

    createModalDialogAndInteractWithIt(
      dialogTrigger = {
        SetupSdkApplicationService.instance.showSdkSetupWizard(
          newSdkPath.absolutePath,
          {},
          mockInstaller,
          mock(),
          isTestingLegacyWizard == true
        )
      }
    ) {
      assertEquals(it.title, "SDK Setup")

      val fakeUi = FakeUi(it.rootPane)

      val sdkComponentsTitle =
        checkNotNull(fakeUi.findComponent<JLabel> { it.text.contains("SDK Components Setup") })
      assertTrue { fakeUi.isShowing(sdkComponentsTitle) }

      val nextButton = checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Next") })
      assertTrue { nextButton.isEnabled }
      nextButton.doClick()
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

      val summaryTitle =
        checkNotNull(fakeUi.findComponent<JLabel> { it.text.contains("Verify Settings") })
      assertTrue { fakeUi.isShowing(summaryTitle) }

      nextButton.doClick()
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

      val licensesTitle =
        checkNotNull(fakeUi.findComponent<JLabel> { it.text.contains("License Agreement") })
      assertTrue { fakeUi.isShowing(licensesTitle) }

      // Accept all licenses
      val tree = checkNotNull(fakeUi.findComponent<Tree>())
      val acceptButton =
        checkNotNull(fakeUi.findComponent<JBRadioButton> { it.text.contains("Accept") })
      for (i in 0..<tree.rowCount) {
        tree.setSelectionRow(i)
        acceptButton.doClick()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      }

      nextButton.doClick()
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

      val downloadingTitle =
        checkNotNull(fakeUi.findComponent<JLabel> { it.text.contains("Downloading Components") })
      assertTrue { fakeUi.isShowing(downloadingTitle) }

      val finishButton = checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Finish") })
      waitForCondition(2, TimeUnit.SECONDS) { finishButton.isEnabled }
      finishButton.doClick()
    }
  }

  @Test
  fun usageMetricsTracked() {
    val mockInstaller = mock(SdkComponentInstaller::class.java)
    val remotePackage = createFakeRemotePackageWithLicense("platforms;android-35")
    whenever(mockInstaller.getPackagesToInstall(any(), any())).thenReturn(listOf(remotePackage))

    val mockTracker = mock(FirstRunWizardTracker::class.java)

    createModalDialogAndInteractWithIt(
      dialogTrigger = {
        SetupSdkApplicationService.instance.showSdkSetupWizard(
          newSdkPath.absolutePath,
          {},
          mockInstaller,
          mockTracker,
          isTestingLegacyWizard == true
        )
      }
    ) {
      assertEquals(it.title, "SDK Setup")

      val fakeUi = FakeUi(it.rootPane)
      val nextButton = checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Next") })

      // Continue from SDK components step
      nextButton.doClick()
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

      // Continue from summary step
      nextButton.doClick()
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

      // Accept all licenses and continue
      val tree = checkNotNull(fakeUi.findComponent<Tree>())
      val acceptButton =
        checkNotNull(fakeUi.findComponent<JBRadioButton> { it.text.contains("Accept") })
      for (i in 0..<tree.rowCount) {
        tree.setSelectionRow(i)
        acceptButton.doClick()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      }
      nextButton.doClick()
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

      // Click 'finish' on the downloading components step
      val finishButton = checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Finish") })
      waitForCondition(2, TimeUnit.SECONDS) { finishButton.isEnabled }
      assertTrue { finishButton.isEnabled }
      finishButton.doClick()
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

      inOrder(mockTracker).apply {
        verify(mockTracker).trackWizardStarted()
        verify(mockTracker)
          .trackStepShowing(SetupWizardEvent.WizardStep.WizardStepKind.SDK_COMPONENTS)
        verify(mockTracker)
          .trackStepShowing(SetupWizardEvent.WizardStep.WizardStepKind.LICENSE_AGREEMENT)
        verify(mockTracker)
          .trackStepShowing(SetupWizardEvent.WizardStep.WizardStepKind.INSTALL_SDK)
        verify(mockTracker).trackWizardFinished(SetupWizardEvent.CompletionStatus.FINISHED)
      }

      verify(mockTracker, never()).trackInstallationMode(any())
      verify(mockTracker, never()).trackSdkInstallLocationChanged()
      verify(mockTracker).trackSdkComponentsToInstall(any())
      verify(mockTracker).trackInstallingComponentsStarted()
      verify(mockTracker)
        .trackInstallingComponentsFinished(
          SetupWizardEvent.SdkInstallationMetrics.SdkInstallationResult.SUCCESS
        )
    }
  }

  private fun createFakeRemotePackageWithLicense(path: String): RemotePackage {
    val remotePackage = FakeRemotePackage(path)
    remotePackage.setCompleteUrl("http://www.example.com/package.zip")

    val factory = RepoManager.getCommonModule().createLatestFactory()
    val license: License = factory.createLicenseType("some license text", "license1")
    remotePackage.license = license

    return remotePackage
  }
}
