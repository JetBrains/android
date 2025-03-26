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
package com.android.tools.idea.sdk.wizard

import com.android.repository.api.ProgressIndicator
import com.android.repository.api.RemotePackage
import com.android.repository.impl.meta.RepositoryPackages
import com.android.repository.testframework.FakePackage
import com.android.repository.testframework.FakeProgressIndicator
import com.android.repository.testframework.FakeRepoManager
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils.PackageResolutionException
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.welcome.install.AehdSdkComponentTreeNode
import com.android.tools.idea.welcome.install.AehdSdkComponentTreeNode.InstallationIntention.CONFIGURE_ONLY
import com.android.tools.idea.welcome.install.AehdSdkComponentTreeNode.InstallationIntention.INSTALL_WITH_UPDATES
import com.android.tools.idea.welcome.install.AehdSdkComponentTreeNode.InstallationIntention.UNINSTALL
import com.android.tools.idea.welcome.install.SdkComponentInstaller
import com.android.tools.idea.welcome.wizard.ProgressStep
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

@RunsInEdt
class AehdWizardControllerTest {
  private val projectRule = AndroidProjectRule.withSdk()

  @get:Rule
  val chain = RuleChain(projectRule, EdtRule())

  private val sdkLocation: File = FileUtil.createTempDirectory("sdk", null)
  private lateinit var sdkHandler: AndroidSdkHandler
  private lateinit var fakeProgressIndicator: ProgressIndicator
  private lateinit var progressStep: ProgressStep
  private lateinit var sdkComponentInstaller: SdkComponentInstaller
  private lateinit var controller: AehdWizardController
  private val sdkPackages = listOf(
    FakePackage.FakeRemotePackage("extras;google;Android_Emulator_Hypervisor_Driver"),
  )

  @Before
  fun setUp() {
    fakeProgressIndicator = FakeProgressIndicator()
    sdkHandler = AndroidSdkHandler(sdkLocation.toPath(), null, FakeRepoManager(RepositoryPackages(emptyList(), sdkPackages)))
    whenever(projectRule.mockService(AndroidSdks::class.java).tryToChooseSdkHandler()).thenReturn(sdkHandler)
    progressStep = FakeProgressStep()
    sdkComponentInstaller = spy(SdkComponentInstaller())
    doAnswer {
      doReturn(emptyList<RemotePackage>()).whenever(sdkComponentInstaller).getPackagesToInstall(any(), any())
    }.whenever(sdkComponentInstaller).installPackages(any(), any(), any(), any())
    controller = AehdWizardController(sdkComponentInstaller)
  }

  @Test
  fun getPackagesToInstall_returnsRequiredPackages() {
    val aehdNode = AehdSdkComponentTreeNode(INSTALL_WITH_UPDATES)
    aehdNode.updateState(sdkHandler)

    val packages = controller.getPackagesToInstall(sdkHandler, aehdNode)

    assertThat(packages).containsExactlyElementsIn(sdkPackages)
  }

  @Test
  fun getPackagesToInstall_returnsEmptyListIfResolutionFails() {
    val aehdNode = spy(AehdSdkComponentTreeNode(INSTALL_WITH_UPDATES))
    aehdNode.updateState(sdkHandler)
    doThrow(PackageResolutionException("")).whenever(sdkComponentInstaller).getPackagesToInstall(any(), any())

    val packages = controller.getPackagesToInstall(sdkHandler, aehdNode)

    assertThat(packages).isEmpty()
  }

  @Test
  fun setupAehd_installsAndConfigures() {
    val aehdNode = spy(AehdSdkComponentTreeNode(INSTALL_WITH_UPDATES))
    aehdNode.updateState(sdkHandler)
    doAnswer {
      doReturn(true).whenever(aehdNode).isInstallerSuccessfullyCompleted
    }.whenever(aehdNode).configure(any(), any())

    val result = controller.setupAehd(aehdNode, progressStep, fakeProgressIndicator)

    verify(sdkComponentInstaller).installPackages(eq(sdkHandler), eq(sdkPackages), any(), any())
    verify(aehdNode).configure(any(), eq(sdkHandler))

    assertThat(result).isTrue()
  }

  @Test
  fun setupAehd_configuresOnlyWhenInstallationIntentionSetToConfigureOnly() {
    val aehdNode = spy(AehdSdkComponentTreeNode(CONFIGURE_ONLY))
    aehdNode.updateState(sdkHandler)
    doAnswer {
      doReturn(true).whenever(aehdNode).isInstallerSuccessfullyCompleted
    }.whenever(aehdNode).configure(any(), any())

    val result = controller.setupAehd(aehdNode, progressStep, fakeProgressIndicator)

    verify(sdkComponentInstaller, times(0)).installPackages(any(), any(), any(), any())
    verify(aehdNode).configure(any(), eq(sdkHandler))

    assertThat(result).isTrue()
  }

  @Test
  fun setupAehd_configuresOnlyWhenInstallationIntentionSetToUninstall() {
    val aehdNode = spy(AehdSdkComponentTreeNode(UNINSTALL))
    aehdNode.updateState(sdkHandler)
    doAnswer {
      doReturn(true).whenever(aehdNode).isInstallerSuccessfullyCompleted
    }.whenever(aehdNode).configure(any(), any())

    val result = controller.setupAehd(aehdNode, progressStep, fakeProgressIndicator)

    verify(sdkComponentInstaller, times(0)).installPackages(any(), any(), any(), any())
    verify(aehdNode).configure(any(), eq(sdkHandler))

    assertThat(result).isTrue()
  }

  @Test
  fun setupAehd_cleansUpInstalledPackagesIfConfigurationFailed() {
    val aehdNode = spy(AehdSdkComponentTreeNode(INSTALL_WITH_UPDATES))
    aehdNode.updateState(sdkHandler)
    doAnswer {
      doReturn(false).whenever(aehdNode).isInstallerSuccessfullyCompleted
    }.whenever(aehdNode).configure(any(), any())
    doNothing().whenever(sdkComponentInstaller).ensureSdkPackagesUninstalled(any(), any(), any())

    val result = controller.setupAehd(aehdNode, progressStep, fakeProgressIndicator)

    verify(aehdNode).configure(any(), eq(sdkHandler))
    verify(sdkComponentInstaller).ensureSdkPackagesUninstalled(eq(sdkHandler), eq(sdkPackages.map { it.path }), any())

    assertThat(result).isFalse()
  }

  @Test
  fun handleCancel_cleansUpInstalledPackagesWhenInstallationIntentionIsInstall() {
    val aehdNode = AehdSdkComponentTreeNode(INSTALL_WITH_UPDATES)
    aehdNode.updateState(sdkHandler)
    doNothing().whenever(sdkComponentInstaller).ensureSdkPackagesUninstalled(any(), any(), any())

    controller.handleCancel(INSTALL_WITH_UPDATES, aehdNode, mock())

    verify(sdkComponentInstaller).ensureSdkPackagesUninstalled(eq(sdkHandler), eq(sdkPackages.map { it.path }), any())
  }

  @Test
  fun handleCancel_cleansUpInstalledPackagesWhenInstallationIntentionIsConfigureOnly() {
    val aehdNode = AehdSdkComponentTreeNode(CONFIGURE_ONLY)
    aehdNode.updateState(sdkHandler)
    doNothing().whenever(sdkComponentInstaller).ensureSdkPackagesUninstalled(any(), any(), any())

    controller.handleCancel(INSTALL_WITH_UPDATES, aehdNode, mock())

    verify(sdkComponentInstaller).ensureSdkPackagesUninstalled(eq(sdkHandler), eq(sdkPackages.map { it.path }), any())
  }

  @Test
  fun handleCancel_doesNotCleanUpWhenInstallationIntentionIsUninstall() {
    val aehdNode = AehdSdkComponentTreeNode(UNINSTALL)
    aehdNode.updateState(sdkHandler)

    controller.handleCancel(UNINSTALL, aehdNode, mock())

    verify(sdkComponentInstaller, times(0)).ensureSdkPackagesUninstalled(any(), any(), any())
  }

  @Test
  fun handleCancel_showDialogMessageWhenExceptionDuringCleanup() {
    val aehdNode = AehdSdkComponentTreeNode(INSTALL_WITH_UPDATES)
    aehdNode.updateState(sdkHandler)
    doThrow(RuntimeException()).whenever(sdkComponentInstaller).ensureSdkPackagesUninstalled(any(), any(), any())
    var dialogShown = false
    TestDialogManager.setTestDialog { _: String ->
      dialogShown = true
      Messages.OK
    }

    controller.handleCancel(INSTALL_WITH_UPDATES, aehdNode, mock())

    assertThat(dialogShown).isTrue()
  }

  class FakeProgressStep : ProgressStep {
    override fun isCanceled(): Boolean = false
    override fun print(s: String, contentType: ConsoleViewContentType) {}
    override fun run(runnable: Runnable, progressPortion: Double) {
      runnable.run()
    }

    override fun attachToProcess(processHandler: ProcessHandler) {}
    override fun getProgressIndicator(): com.intellij.openapi.progress.ProgressIndicator = ProgressIndicatorBase()
  }
}
