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
package com.google.idea.blaze.android.run

import com.google.idea.blaze.android.run.runner.ApkBuildStep
import com.google.idea.blaze.base.model.primitives.Label
import com.google.idea.blaze.base.settings.BuildSystemName
import com.google.idea.blaze.base.util.BuildSystemExtensionPoint
import com.intellij.execution.ExecutionException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/** A service that provides the build step capable of building APKs suitable for deployment.  */
interface ApkBuildStepProvider : BuildSystemExtensionPoint {
  /** Returns a build step that can build artifacts required for an `android_binary`.  */
  @Throws(ExecutionException::class)
  fun getBinaryBuildStep(
    project: Project,
    useMobileInstall: Boolean,
    nativeDebuggingEnabled: Boolean,
    label: Label,
    blazeFlags: List<String>,
    exeFlags: List<String>,
    launchId: String
  ): ApkBuildStep

  /**
   * Returns a build step that can build artifacts required for an `android_instrumentation_test`.
   */
  @Throws(ExecutionException::class)
  fun getAitBuildStep(
    project: Project,
    useMobileInstall: Boolean,
    nativeDebuggingEnabled: Boolean,
    label: Label,
    blazeFlags: List<String>,
    exeFlags: List<String>,
    launchId: String
  ): ApkBuildStep

  companion object {
    @JvmStatic
    fun getInstance(buildSystemName: BuildSystemName): ApkBuildStepProvider{
      return BuildSystemExtensionPoint.getInstance(EP_NAME, buildSystemName)
    }

    val EP_NAME: ExtensionPointName<ApkBuildStepProvider> =
      ExtensionPointName.create("com.google.idea.blaze.android.ApkBuildStepProvider")
  }
}
