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
package com.google.idea.blaze.android.run

import com.google.idea.blaze.android.run.runner.AitDeployInfoExtractor
import com.google.idea.blaze.android.run.runner.ApkBuildStep
import com.google.idea.blaze.android.run.runner.BinaryDeployInfoExtractor
import com.google.idea.blaze.android.run.runner.BlazeApkBuildStep
import com.google.idea.blaze.android.run.runner.InstrumentationInfo
import com.google.idea.blaze.android.run.runner.InstrumentationInfo.InstrumentationParserException
import com.google.idea.blaze.base.settings.Blaze
import com.google.idea.blaze.base.settings.BuildSystemName
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager
import com.google.idea.blaze.common.Label
import com.intellij.execution.ExecutionException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

/**
 * Provides APK build steps for Bazel projects.
 */
class BazelApkBuildStepProvider : ApkBuildStepProvider {
  override fun getBinaryBuildStep(
    project: Project,
    useMobileInstall: Boolean,
    nativeDebuggingEnabled: Boolean,
    label: Label,
    blazeFlags: List<String>,
    exeFlags: List<String>,
    launchId: String,
  ): ApkBuildStep {
    val buildInvoker =
      Blaze.getBuildSystemProvider(project)
        .getBuildSystem()
        .getBuildInvoker(project)

    return BlazeApkBuildStep(
      project = project,
      targets = listOf(label),
      blazeFlags = blazeFlags,
      exeFlags = exeFlags,
      useMobileInstall = useMobileInstall,
      nativeDebuggingEnabled = nativeDebuggingEnabled,
      launchId = launchId,
      buildInvoker = buildInvoker,
      deployInfoExtractor =
        BinaryDeployInfoExtractor(
          project,
          com.google.idea.blaze.common.Label.of(label.toString()),
          useMobileInstall,
          nativeDebuggingEnabled
        )
    )
  }

  @Throws(ExecutionException::class)
  override fun getAitBuildStep(
    project: Project,
    useMobileInstall: Boolean,
    nativeDebuggingEnabled: Boolean,
    label: Label,
    blazeFlags: List<String>,
    exeFlags: List<String>,
    launchId: String
  ): ApkBuildStep {
    val data =
      BlazeProjectDataManager.getInstance(project)
        .getBlazeProjectData() ?: error("BlazeProjectData not found")
    val info: InstrumentationInfo =
      try {
        InstrumentationInfo.getInstrumentationInfo(label, data)
      }
      catch (e: InstrumentationParserException) {
        logger.warn("Could not get instrumentation info: " + e.message)
        throw ExecutionException(e.message, e)
      }

    val targets = listOfNotNull(info.targetApp, info.testApp)
    val buildInvoker =
      Blaze.getBuildSystemProvider(project)
        .getBuildSystem()
        .getBuildInvoker(project)
    return BlazeApkBuildStep(
      project = project,
      targets = targets,
      blazeFlags = blazeFlags,
      exeFlags = exeFlags,
      useMobileInstall = useMobileInstall,
      nativeDebuggingEnabled = nativeDebuggingEnabled,
      launchId = launchId,
      buildInvoker = buildInvoker,
      deployInfoExtractor = AitDeployInfoExtractor(project, info)
    )
  }

  override fun getSupportedBuildSystems(): Set<BuildSystemName> = setOf(BuildSystemName.Blaze, BuildSystemName.Bazel)

  companion object {
    private val logger = Logger.getInstance(BazelApkBuildStepProvider::class.java)
  }
}