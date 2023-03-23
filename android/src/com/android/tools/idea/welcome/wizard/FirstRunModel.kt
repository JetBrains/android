/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.prefs.AndroidLocationsSingleton
import com.android.repository.api.RepoManager
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths
import com.android.tools.idea.observable.core.ObjectValueProperty
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.sdk.StudioSettingsController
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.progress.StudioProgressRunner
import com.android.tools.idea.welcome.config.FirstRunWizardMode
import com.android.tools.idea.welcome.install.AndroidSdk
import com.android.tools.idea.welcome.install.AndroidVirtualDevice
import com.android.tools.idea.welcome.install.ComponentCategory
import com.android.tools.idea.welcome.install.ComponentTreeNode
import com.android.tools.idea.welcome.install.Aehd
import com.android.tools.idea.welcome.install.Haxm
import com.android.tools.idea.welcome.install.InstallationIntention
import com.android.tools.idea.welcome.install.Platform
import com.android.tools.idea.welcome.install.getInitialSdkLocation
import com.android.tools.idea.welcome.wizard.deprecated.FirstRunWizard
import com.android.tools.idea.welcome.wizard.deprecated.ProgressStep
import com.android.tools.idea.wizard.model.WizardModel
import com.intellij.openapi.Disposable
import java.io.File

// Contains all the data which Studio should collect in the First Run Wizard
class FirstRunModel(private val mode: FirstRunWizardMode): WizardModel() {
  enum class InstallationType {
    STANDARD,
    CUSTOM
  }

  var sdkLocation: File = getInitialSdkLocation(mode)
  val installationType = ObjectValueProperty(
    if (sdkLocation.path.isEmpty()) InstallationType.CUSTOM else InstallationType.STANDARD
  )
  val customInstall: Boolean get() = installationType.get() == InstallationType.CUSTOM
  val jdkLocation = EmbeddedDistributionPaths.getInstance().embeddedJdkPath
  val sdkExists = if (sdkLocation.isDirectory) {
    val sdkHandler = AndroidSdkHandler.getInstance(AndroidLocationsSingleton, sdkLocation.toPath())
    val progress = StudioLoggerProgressIndicator(javaClass)
    sdkHandler.getSdkManager(progress).packages.localPackages.isNotEmpty()
  } else {
    false
  }

  var localHandler: AndroidSdkHandler = AndroidSdkHandler.getInstance(AndroidLocationsSingleton, sdkLocation.toPath())

  // FIXME (why always true?)
  /**
   * Should store the root node of the component tree.
   */
  val componentTree = createComponentTree(true)

  init {
    val mockDisposable = Disposable { }
    val mockProgressStep = object : ProgressStep(mockDisposable, "loading component tree") {
      override fun execute() {
        // TODO (doing nothing)
      }
    }
    componentTree.init(mockProgressStep)
    componentTree.updateState(localHandler)
  }


  private fun createComponentTree(createAvd: Boolean): ComponentTreeNode {
    val installUpdates = true // FIXME
    val components: MutableList<ComponentTreeNode> = mutableListOf(AndroidSdk(installUpdates))

    val sdkManager = localHandler.getSdkManager(StudioLoggerProgressIndicator(javaClass)).apply {
      loadSynchronously(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null,
           StudioProgressRunner(true, false, "Finding Available SDK Components", null),
           StudioDownloader(), StudioSettingsController.getInstance())
    }

    val remotePackages = sdkManager.packages.remotePackages

    val platforms = Platform.createSubtree(remotePackages, installUpdates)
    if (platforms != null) {
      components.add(platforms)
    }
    val installationIntention = if (installUpdates) InstallationIntention.INSTALL_WITH_UPDATES else InstallationIntention.INSTALL_WITHOUT_UPDATES
    if (mode === FirstRunWizardMode.NEW_INSTALL && Haxm.canRun()) {
      components.add(Haxm(installationIntention, FirstRunWizard.KEY_CUSTOM_INSTALL))
    }
    if (mode === FirstRunWizardMode.NEW_INSTALL && Aehd.canRun()) {
      components.add(Aehd(installationIntention, FirstRunWizard.KEY_CUSTOM_INSTALL))
    }
    if (createAvd) {
      components.add(AndroidVirtualDevice(remotePackages, installUpdates))
    }
    return ComponentCategory("Root", "Root node that is not supposed to appear in the UI", components)
  }

  override fun handleFinished() {
  }
}
