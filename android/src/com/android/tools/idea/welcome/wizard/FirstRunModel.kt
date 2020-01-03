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

import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths
import com.android.tools.idea.observable.core.ObjectValueProperty
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.welcome.config.FirstRunWizardMode
import com.android.tools.idea.welcome.install.getInitialSdkLocation
import com.android.tools.idea.wizard.model.WizardModel
import java.io.File

// Contains all the data which Studio should collect in the First Run Wizard
class FirstRunModel(mode: FirstRunWizardMode): WizardModel() {
  enum class InstallationType {
    STANDARD,
    CUSTOM
  }

  var sdkLocation: File = getInitialSdkLocation(mode)
  val installationType = ObjectValueProperty(
    if (sdkLocation.path.isEmpty()) InstallationType.CUSTOM else InstallationType.STANDARD
  )
  val jdkLocation = EmbeddedDistributionPaths.getInstance().embeddedJdkPath
  val sdkExists = if (sdkLocation.isDirectory) {
    val sdkHandler = AndroidSdkHandler.getInstance(sdkLocation)
    val progress = StudioLoggerProgressIndicator(javaClass)
    sdkHandler.getSdkManager(progress).packages.localPackages.isNotEmpty()
  } else {
    false
  }

  override fun handleFinished() { }
}