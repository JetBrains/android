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
import com.android.repository.api.RemotePackage
import com.android.repository.api.RepoManager
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.observable.core.ObjectValueProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.progress.StudioProgressRunner
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.sdk.StudioSettingsController
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.android.tools.idea.welcome.config.FirstRunWizardMode
import com.android.tools.idea.welcome.install.Aehd
import com.android.tools.idea.welcome.install.AndroidSdk
import com.android.tools.idea.welcome.install.AndroidVirtualDevice
import com.android.tools.idea.welcome.install.ComponentCategory
import com.android.tools.idea.welcome.install.ComponentTreeNode
import com.android.tools.idea.welcome.install.InstallContext
import com.android.tools.idea.welcome.install.InstallableComponent
import com.android.tools.idea.welcome.install.Platform
import com.android.tools.idea.welcome.install.WizardException
import com.android.tools.idea.welcome.wizard.deprecated.InstallComponentsPath
import com.android.tools.idea.wizard.model.WizardModel
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.containers.orNull
import java.io.File
import java.nio.file.Path
import java.util.function.Supplier
import kotlin.io.path.isDirectory

// Contains all the data which Studio should collect in the First Run Wizard
class FirstRunModel(private val mode: FirstRunWizardMode, initialSdkLocation: Path, private val componentInstallerProvider: ComponentInstallerProvider): WizardModel() {
  enum class InstallationType {
    STANDARD,
    CUSTOM
  }

  val isStandardInstallSupported = initialSdkLocation.toString().isNotEmpty()
  var installationType: InstallationType? = null

  val sdkExists = if (initialSdkLocation.isDirectory()) {
    val sdkHandler = AndroidSdkHandler.getInstance(AndroidLocationsSingleton, initialSdkLocation)
    val progress = StudioLoggerProgressIndicator(javaClass)
    sdkHandler.getSdkManager(progress).packages.localPackages.isNotEmpty()
  } else {
    false
  }

  val localHandlerProperty: ObjectValueProperty<AndroidSdkHandler> = ObjectValueProperty(AndroidSdkHandler.getInstance(AndroidLocationsSingleton, initialSdkLocation)).apply {
    this.addListener {
      val location = this.get().location
      if (location == null) {
        sdkInstallLocationProperty.clear()
      } else {
        sdkInstallLocationProperty.value = location
      }
    }
  }
  val localHandler get() = localHandlerProperty.get()

  val sdkInstallLocationProperty: OptionalValueProperty<Path> = OptionalValueProperty(initialSdkLocation)
  val sdkInstallLocation: Path? get() = sdkInstallLocationProperty.get().orNull()

  // FIXME (why always true?)
  /**
   * Should store the root node of the component tree.
   */
  val componentTree = createComponentTree(true)

  init {
    componentTree.updateState(localHandler)
  }

  fun getPackagesToInstallSupplier(): Supplier<Collection<RemotePackage>?> = Supplier {
    val components: Iterable<InstallableComponent> = componentTree.childrenToInstall
    try {
      componentInstallerProvider.getComponentInstaller(localHandler).getPackagesToInstall(components)
    }
    catch (e: SdkQuickfixUtils.PackageResolutionException) {
      logger<StudioFirstRunWelcomeScreen>().warn(e)
      null
    }
  }

  private fun createComponentTree(createAvd: Boolean): ComponentTreeNode {
    val installUpdates = true // FIXME
    val components: MutableList<ComponentTreeNode> = mutableListOf(AndroidSdk(installUpdates))

    val sdkManager = localHandler.getSdkManager(StudioLoggerProgressIndicator(javaClass)).apply {
      loadSynchronously(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null,
           StudioProgressRunner(true, false, "Finding Available SDK Components", null),
           StudioDownloader(), StudioSettingsController.getInstance())
    }

    val remotePackages = sdkManager.packages.remotePackages.values

    val platforms = Platform.createSubtree(remotePackages, installUpdates)
    if (platforms != null) {
      components.add(platforms)
    }
    val installationIntention =
      if (installUpdates) Aehd.InstallationIntention.INSTALL_WITH_UPDATES
      else Aehd.InstallationIntention.INSTALL_WITHOUT_UPDATES
    if (mode === FirstRunWizardMode.NEW_INSTALL && Aehd.canRun()) {
      components.add(Aehd(installationIntention))
    }
    if (createAvd) {
      val avdCreator = AndroidVirtualDevice(remotePackages, installUpdates)
      if (avdCreator.isAvdCreationNeeded(localHandler)) {
        components.add(avdCreator)
      }
    }
    return ComponentCategory("Root", "Root node that is not supposed to appear in the UI", components)
  }

  /**
   * Installs all components in the `componentTree` that are configured to be installed.
   * Once the components have been installed, the SDK path and installer timestamp are
   * stored in preferences.
   *
   * @param progressStep used to provide feedback on installation progress
   */
  @Throws(WizardException::class)
  fun installComponents(progressStep: ProgressStep) {
    val sdkHandler = localHandler
    InstallComponentsPath.installComponents(
      componentTree.childrenToInstall,
      InstallContext(InstallComponentsPath.createTempDir(), progressStep),
      componentInstallerProvider.getComponentInstaller(sdkHandler),
      mode.installerTimestamp,
      ModalityState.stateForComponent(progressStep.component),
      sdkHandler,
      getDestination()
    )
  }

  @Throws(WizardException::class)
  private fun getDestination(): File {
    val destinationPath = sdkInstallLocation ?: throw WizardException("SDK install path is null")

    val destination = destinationPath.toFile()
    if (destination.isFile) {
      throw WizardException("Path $destinationPath does not point to a directory")
    }
    return destination
  }

  override fun handleFinished() {}
}
