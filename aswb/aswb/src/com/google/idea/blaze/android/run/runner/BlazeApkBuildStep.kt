/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.runner

import com.google.common.base.Stopwatch
import com.google.idea.blaze.android.run.LaunchMetrics
import com.google.idea.blaze.android.run.NativeSymbolFinder
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector.DeviceSession
import com.google.idea.blaze.base.bazel.BazelExitCodeException
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker
import com.google.idea.blaze.base.command.BlazeCommand
import com.google.idea.blaze.base.command.BlazeCommandName
import com.google.idea.blaze.base.command.buildresult.BuildResultParser
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.scope.output.StatusOutput
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs
import com.google.idea.blaze.base.util.SaveUtil
import com.google.idea.blaze.common.Interners
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.exception.BuildException
import com.intellij.openapi.diagnostic.Logger

/** Blaze specific build flow for android_binary builds.  */
class BlazeApkBuildStep(
  val targets: List<Label>,
  private val blazeFlags: List<String>,
  private val exeFlags: List<String>,
  val useMobileInstall: Boolean,
  val nativeDebuggingEnabled: Boolean,
  private val launchId: String,
  private val buildInvoker: BuildInvoker,
  val deployInfoExtractor: DeployInfoExtractor
) : ApkBuildStep {

  /**
   * Builds the android_binary.
   */
  override fun build(context: BlazeContext): BlazeBuildOutputs {
    SaveUtil.saveAllFiles()

    context.output(StatusOutput("Building Application."))
    val stopwatch = Stopwatch.createStarted()

    val commandName =
      if (useMobileInstall) BlazeCommandName.MOBILE_INSTALL else BlazeCommandName.BUILD
    val command =
      BlazeCommand.builder(commandName)
        .addTargetStrings(targets.map { it.toString() })
        .addBlazeFlags(blazeFlags)
        .addExeFlags(exeFlags)
    if (useMobileInstall) {
      // mobile_install targets need these flags for build-only mode.
      command.addExeFlags("--nolaunch_app", "--nodeploy")
    } else {
      // standard build needs this to ensure deploy info is generated.
      command.addBlazeFlags("--output_groups=+android_deploy_info")
    }
    if (nativeDebuggingEnabled) {
      command.addBlazeFlags(
        NativeSymbolFinder.getInstances().map { it.additionalBuildFlags }
      )
    }

    val buildOutputs =
      try {
        val buildOutputs =
          buildInvoker.invoke(command, context) { streamProvider ->
            BlazeBuildOutputs.fromParsedBepOutput(BuildResultParser.getBuildOutput(streamProvider, Interners.STRING))
          }
        LaunchMetrics.logBuildTime(
          launchId,
          stopwatch.elapsed(),
          buildOutputs!!.buildResult().exitCode,
          mapOf()
        )
        BazelExitCodeException.throwIfFailed(command, buildOutputs.buildResult())
        logger.info("Finished build, id: " + buildOutputs.idForLogging())
        context.output(StatusOutput("Build complete."))
        buildOutputs
      } catch (e: BuildException) {
        context.handleException("Failed to build APK", e)
        throw e
      }
    return buildOutputs
  }

  companion object {
    private val logger = Logger.getInstance(BlazeApkBuildStep::class.java)
  }
}