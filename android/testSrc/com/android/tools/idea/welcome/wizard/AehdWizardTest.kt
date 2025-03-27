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

import com.android.repository.api.License
import com.android.repository.api.RepoManager
import com.android.repository.impl.meta.RepositoryPackages
import com.android.repository.testframework.FakePackage.FakeRemotePackage
import com.android.repository.testframework.FakeRepoManager
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.wizard.AehdModelWizard
import com.android.tools.idea.sdk.wizard.AehdWizard
import com.android.tools.idea.sdk.wizard.AehdWizardController
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.welcome.install.AehdSdkComponentTreeNode
import com.google.wireless.android.sdk.stats.SetupWizardEvent
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.treeStructure.Tree
import java.awt.Component
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JLabel
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters
import org.mockito.MockedStatic
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.timeout
import org.mockito.kotlin.whenever

@RunsInEdt
@RunWith(Parameterized::class)
class AehdWizardTest {
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

  private lateinit var mockAndroidSdksStatic: MockedStatic<AndroidSdks>
  private lateinit var mockAehdWizardController: AehdWizardController

  @Before
  fun setUp() {
    val remotePackage = FakeRemotePackage("extras;google;Android_Emulator_Hypervisor_Driver")
    remotePackage.setCompleteUrl("http://www.example.com/package.zip")

    val factory = RepoManager.commonModule.createLatestFactory()
    val license: License = factory.createLicenseType("some license text", "license1")
    remotePackage.license = license

    val fakeRepoManager = FakeRepoManager(RepositoryPackages(emptyList(), listOf(remotePackage)))
    val sdkPath = FileUtil.createTempDirectory("sdk", null)
    val sdkHandler = AndroidSdkHandler(sdkPath.toPath(), null, fakeRepoManager)

    val mockAndroidSdks = mock(AndroidSdks::class.java)
    whenever(mockAndroidSdks.tryToChooseSdkHandler()).thenReturn(sdkHandler)

    mockAndroidSdksStatic = mockStatic(AndroidSdks::class.java)
    whenever(AndroidSdks.getInstance()).thenReturn(mockAndroidSdks)

    mockAehdWizardController = mock(AehdWizardController::class.java)
    whenever(mockAehdWizardController.getPackagesToInstall(any(), any()))
      .thenReturn(listOf(remotePackage))
    whenever(mockAehdWizardController.setupAehd(any(), any(), any())).thenReturn(true)
  }

  @After
  fun tearDown() {
    mockAndroidSdksStatic.close()
  }

  @Test
  fun navigatingThroughWizardInstallsAehd() {
    showWizard(mockAehdWizardController, mock()) { fakeUi ->
      val infoStepTitle =
        checkNotNull(
          fakeUi.findComponent<JLabel> {
            it.text.contains("Installing Android Emulator hypervisor driver")
          }
        )
      assertTrue { fakeUi.isShowing(infoStepTitle) }

      val infoStepDescription =
        checkNotNull(
          fakeUi.findComponent<JLabel> {
            it.text.contains(
              "This wizard will execute Android Emulator hypervisor driver stand-alone installer."
            )
          }
        )
      assertTrue { fakeUi.isShowing(infoStepDescription) }

      val nextButton = checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Next") })
      assertTrue { fakeUi.isShowing(nextButton) }
      assertTrue { nextButton.isEnabled }

      nextButton.doClick()
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

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

      val installingStepTitle =
        checkNotNull(fakeUi.findComponent<JLabel> { it.text.contains("Invoking installer") })
      assertTrue { fakeUi.isShowing(installingStepTitle) }

      val finishButton = checkNotNull(fakeUi.findComponent<JButton> { it.text.equals("Finish") })
      assertTrue { fakeUi.isShowing(finishButton) }
      waitForCondition(2, TimeUnit.SECONDS) { finishButton.isEnabled }
      finishButton.doClick()

      verify(mockAehdWizardController, times(1)).setupAehd(any(), any(), any())
      verify(mockAehdWizardController, times(0)).handleCancel(any(), any(), any(), any())
    }
  }

  @Test
  fun cancellingWizardTriggersCleanup() {
    val tracker =
      FirstRunWizardTracker(
        SetupWizardEvent.SetupWizardMode.AEHD_WIZARD,
        isTestingLegacyWizard == true,
      )
    showWizard(mockAehdWizardController, tracker) { fakeUi ->
      val cancelButton = checkNotNull(fakeUi.findComponent<JButton> { it.text.equals("Cancel") })
      assertTrue { fakeUi.isShowing(cancelButton) }
      cancelButton.doClick()

      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

      verify(mockAehdWizardController, timeout(2000).times(1))
        .handleCancel(any(), any(), any(), any())
    }
  }

  @Test
  fun usageMetricsTracked_wizardComplete() {
    val mockTracker = mock(FirstRunWizardTracker::class.java)
    showWizard(mockAehdWizardController, mockTracker) { fakeUi ->
      val nextButton = checkNotNull(fakeUi.findComponent<JButton> { it.text.contains("Next") })
      nextButton.doClick()
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

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

      val finishButton = checkNotNull(fakeUi.findComponent<JButton> { it.text.equals("Finish") })
      waitForCondition(2, TimeUnit.SECONDS) { finishButton.isEnabled }
      finishButton.doClick()

      inOrder(mockTracker).apply {
        verify(mockTracker).trackWizardStarted()
        verify(mockTracker)
          .trackStepShowing(SetupWizardEvent.WizardStep.WizardStepKind.AEHD_INSTALL_INFO)
        verify(mockTracker)
          .trackStepShowing(SetupWizardEvent.WizardStep.WizardStepKind.LICENSE_AGREEMENT)
        verify(mockTracker).trackStepShowing(SetupWizardEvent.WizardStep.WizardStepKind.INSTALL_SDK)
        verify(mockTracker).trackWizardFinished(SetupWizardEvent.CompletionStatus.FINISHED)
      }

      verify(mockTracker, never()).trackInstallationMode(any())
      verify(mockTracker, never()).trackSdkInstallLocationChanged()
      verify(mockTracker)
        .trackSdkComponentsToInstall(
          listOf(SetupWizardEvent.SdkInstallationMetrics.SdkComponentKind.AEHD)
        )
      verify(mockTracker).trackInstallingComponentsStarted()
      verify(mockTracker)
        .trackInstallingComponentsFinished(
          SetupWizardEvent.SdkInstallationMetrics.SdkInstallationResult.SUCCESS
        )
    }
  }

  @Test
  fun usageMetricsTracked_wizardCancelled() {
    val trackerMock = mock(FirstRunWizardTracker::class.java)
    showWizard(mockAehdWizardController, trackerMock) {
      val cancelButton = checkNotNull(it.findComponent<JButton> { it.text.equals("Cancel") })
      cancelButton.doClick()

      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

      inOrder(trackerMock).apply {
        verify(trackerMock).trackWizardStarted()
        verify(trackerMock).trackWizardFinished(SetupWizardEvent.CompletionStatus.CANCELED)
      }
    }
  }

  private fun showWizard(
    aehdWizardController: AehdWizardController,
    tracker: FirstRunWizardTracker,
    showCallback: (FakeUi) -> Unit,
  ) {
    if (isTestingLegacyWizard == true) {
      showOldWizard(aehdWizardController, showCallback, tracker)
    } else {
      showNewWizard(aehdWizardController, showCallback, tracker)
    }
  }

  private fun showOldWizard(
    aehdWizardController: AehdWizardController,
    showCallback: (FakeUi) -> Unit,
    tracker: FirstRunWizardTracker,
  ) {
    val wizard =
      AehdWizard(
          AehdSdkComponentTreeNode.InstallationIntention.INSTALL_WITH_UPDATES,
          aehdWizardController,
          tracker,
        )
        .apply { init() }

    createModalDialogAndInteractWithIt(dialogTrigger = { wizard.show() }) {
      showCallback(FakeUi(getRoot(wizard.contentPane), createFakeWindow = true))
    }
  }

  private fun showNewWizard(
    aehdWizardController: AehdWizardController,
    showCallback: (FakeUi) -> Unit,
    tracker: FirstRunWizardTracker,
  ) {
    val wizard =
      AehdModelWizard(
        AehdSdkComponentTreeNode.InstallationIntention.INSTALL_WITH_UPDATES,
        aehdWizardController,
        tracker,
      )

    createModalDialogAndInteractWithIt(dialogTrigger = { wizard.showAndGet() }) { dialogWrapper ->
      showCallback(FakeUi(getRoot(dialogWrapper.contentPane), createFakeWindow = true))
    }
  }

  private fun getRoot(component: Component): Component {
    var root = component
    while (root.parent != null) {
      root = root.parent
    }
    return root
  }
}
