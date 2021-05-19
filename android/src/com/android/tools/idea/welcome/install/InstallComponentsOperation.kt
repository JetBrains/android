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
package com.android.tools.idea.welcome.install

import com.android.repository.api.RemotePackage
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils.PackageResolutionException
import com.android.tools.idea.util.formatElementListString
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.progress.ProgressIndicator
import java.io.File

/**
 * Install or updates SDK components if needed.
 */
class InstallComponentsOperation(
  context: InstallContext,
  private val components: Collection<InstallableComponent>,
  private val componentInstaller: ComponentInstaller,
  progressRatio: Double
) : InstallOperation<File, File>(context, progressRatio) {
  @Throws(WizardException::class)
  override fun perform(indicator: ProgressIndicator, argument: File): File {
    indicator.text = "Checking for updated SDK components"
    var packages: List<RemotePackage> = try {
      componentInstaller.getPackagesToInstall(components)
    }
    catch (e: PackageResolutionException) {
      throw WizardException("Failed to determine required packages", e)
    }
    while (packages.isNotEmpty()) {
      indicator.fraction = 0.0
      val logger = SdkManagerProgressIndicatorIntegration(indicator, context)
      componentInstaller.installPackages(packages, StudioDownloader(), logger)
      // If we didn't set remote information on the installer we assume we weren't expecting updates. So set false for
      // defaultUpdateAvailable so we don't think everything failed to install.
      packages = try {
        componentInstaller.getPackagesToInstall(components)
      }
      catch (e: PackageResolutionException) {
        throw WizardException("Failed to determine required packages", e)
      }
      getRetryMessage(packages)?.let { message ->
        promptToRetry(message, logger.errors, null)
      }
    }
    context.print("Android SDK is up to date.\n", ConsoleViewContentType.SYSTEM_OUTPUT)
    indicator.fraction = 1.0 // 100%
    return argument
  }

  override fun cleanup(result: File) {
    // Nothing here to do
  }

  companion object {
    private fun getRetryMessage(packages: Collection<RemotePackage>): String? {
      if (packages.isEmpty()) {
        return null
      }
      return formatElementListString(
        packages.map { it.displayName },
        "The following SDK component was not installed: %s",
        "The following SDK components were not installed: %1\$s and %2\$s",
        "%1\$s and %2\$s more SDK components were not installed"
      )
    }
  }
}