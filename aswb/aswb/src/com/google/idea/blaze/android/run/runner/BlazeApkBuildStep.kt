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

import com.android.tools.idea.run.ApkProvisionException
import com.google.common.base.Stopwatch
import com.google.common.collect.ImmutableMap
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
import com.google.idea.blaze.base.scope.output.IssueOutput
import com.google.idea.blaze.base.scope.output.StatusOutput
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs
import com.google.idea.blaze.base.util.SaveUtil
import com.google.idea.blaze.common.Interners
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.exception.BuildException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.IOException

/** Blaze specific build flow for android_binary builds.  */
class BlazeApkBuildStep(
  private val project: Project,
  private val targets: List<Label>,
  private val blazeFlags: List<String>,
  private val exeFlags: List<String>,
  private val useMobileInstall: Boolean,
  private val nativeDebuggingEnabled: Boolean,
  private val launchId: String,
  private val buildInvoker: BuildInvoker,
  private val deployInfoExtractor: DeployInfoExtractor
) : ApkBuildStep {
  private var done = false
  override fun isDone(): Boolean {
    return done
  }

  private var blazeAndroidDeployInfo: BlazeAndroidDeployInfo? = null

  /**
   * Builds the android_binary, and save the deployment information such that it can be retrieved by
   * [.getDeployInfo].
   */
  override fun build(context: BlazeContext, deviceSession: DeviceSession?) {
    SaveUtil.saveAllFiles()

    context.output(StatusOutput("Building Application."))
    val stopwatch = Stopwatch.createStarted()
    val deployOutputGroup: String?
    val apkOutputGroup: String?
    val commandName =
      if (useMobileInstall) BlazeCommandName.MOBILE_INSTALL else BlazeCommandName.BUILD
    val command =
      BlazeCommand.builder(commandName)
        .addTargetStrings(targets.map { it.toString() })
        .addBlazeFlags(blazeFlags)
        .addExeFlags(exeFlags)
    if (useMobileInstall) {
      // deploy_info.pb and .apk files are in mobile_install_INTERNAL_ output group.
      deployOutputGroup = "mobile_install_INTERNAL_"
      apkOutputGroup = "mobile_install_INTERNAL_"
      command.addExeFlags("--nolaunch_app", "--nodeploy")
    } else {
      // deploy_info.pb is in android_deploy_info output group and .apk files are in the default.
      deployOutputGroup = "android_deploy_info"
      apkOutputGroup = "default"
      command.addBlazeFlags("--output_groups=+android_deploy_info")
    }
    if (nativeDebuggingEnabled) {
      command.addBlazeFlags(
        NativeSymbolFinder.getInstances().joinToString(" ") { it.getAdditionalBuildFlags() }
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
          ImmutableMap.of()
        )
        BazelExitCodeException.throwIfFailed(command, buildOutputs.buildResult())
        logger.info("Finished build, id: " + buildOutputs.idForLogging())
        context.output(StatusOutput("Build complete."))
        buildOutputs
      } catch (e: BuildException) {
        context.handleException("Failed to build APK", e)
        return
      }

    val nativeSymbols =
      if (nativeDebuggingEnabled) {
        val nativeSymbolFinderList: List<NativeSymbolFinder> = NativeSymbolFinder.EP_NAME.extensionList
        targets.flatMap { target ->
            nativeSymbolFinderList.flatMap {
              it.getNativeSymbolsForBuild(project, context, target, buildOutputs)
            }
        }
      } else emptyList()
    try {
      blazeAndroidDeployInfo =
        deployInfoExtractor.extract(
          project,
          buildOutputs,
          deployOutputGroup,
          apkOutputGroup,
          context,
          nativeSymbols
        )
    } catch (e: IOException) {
      logger.warn("Unexpected error while retrieving deploy info", e)
      val message = "Error retrieving deployment info from build results: " + e.message
      IssueOutput.error(message).submit(context)
      return
    }
    done = true
    context.output(StatusOutput("Deployment information parsed from build artifacts."))
  }

  @Throws(ApkProvisionException::class)
  override fun getDeployInfo(): BlazeAndroidDeployInfo? {
    return blazeAndroidDeployInfo
  }

  companion object {
    private val logger = Logger.getInstance(BlazeApkBuildStep::class.java)
  }
}
