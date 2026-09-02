/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.bazel

import com.android.tools.idea.sdk.IdeSdks
import com.google.common.io.Closer
import com.google.idea.blaze.base.async.process.ExternalTask
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream
import com.google.idea.blaze.base.async.process.PrintOutputLineProcessor
import com.google.idea.blaze.base.command.BlazeCommand
import com.google.idea.blaze.base.command.BlazeCommandName
import com.google.idea.blaze.base.command.buildresult.BuildEventProtocolUtils
import com.google.idea.blaze.base.command.buildresult.BuildResult
import com.google.idea.blaze.base.command.buildresult.GetArtifactsException
import com.google.idea.blaze.base.command.buildresult.bepparser.BuildEventStreamProvider
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider
import com.google.idea.blaze.base.logging.utils.querysync.BuildDepsStatsScope
import com.google.idea.blaze.base.logging.utils.querysync.SyncQueryStatsScope
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.scope.output.IssueOutput
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.exception.BuildException
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.EnvironmentUtil
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.function.Supplier

/** A local Blaze/Bazel invoker that issues commands via CLI. */
abstract class AbstractLocalInvoker
protected constructor(
  project: Project,
  buildSystem: BuildSystem,
  invokeCommand: Supplier<List<String>>,
) : AbstractBuildInvoker(project, buildSystem, invokeCommand) {

  private val logger = Logger.getInstance(AbstractLocalInvoker::class.java)

  @Throws(BuildException::class)
  override fun <T : Any> invoke(
    blazeCommandBuilder: BlazeCommand.Builder,
    blazeContext: BlazeContext,
    consumer: BuildSystem.BuildEventStreamConsumer<T>,
  ): T {
    createBuildEventStreamProvider(blazeCommandBuilder, blazeContext).use { streamProvider ->
      return consumer.consume(streamProvider)
    }
  }

  @Throws(BuildException::class)
  private fun createBuildEventStreamProvider(
    blazeCommandBuilder: BlazeCommand.Builder,
    blazeContext: BlazeContext,
  ): BuildEventStreamProvider {
    val outputFile = BuildEventProtocolUtils.createTempOutputFile()
    blazeCommandBuilder.addBlazeFlags(BuildEventProtocolUtils.getBuildFlags(outputFile))
    val buildResult = issueBuild(blazeCommandBuilder, WorkspaceRoot.fromProject(project), blazeContext)
    if (buildResult != BuildResult.SUCCESS) {
      blazeContext.setHasError()
      IssueOutput.error("Blaze build failed. See Blaze Console for details.").submit(blazeContext)
    }
    if (blazeCommandBuilder.build().name == BlazeCommandName.BUILD) {
      BuildDepsStatsScope.fromContext(blazeContext).ifPresent { stats ->
        stats.setBazelExitCode(buildResult.exitCode)
      }
    }
    return getBepStream(outputFile)
  }

  @Throws(BuildException::class)
  override fun invokeAsProcessHandler(
    blazeCommandBuilder: BlazeCommand.Builder,
    blazeContext: BlazeContext,
    consumer: BuildSystem.BuildEventStreamConsumer<Unit>,
  ): ProcessHandler {
    val outputFile = BuildEventProtocolUtils.createTempOutputFile()
    blazeCommandBuilder.addBlazeFlags(BuildEventProtocolUtils.getBuildFlags(outputFile))
    try {
      val environment = buildMap { maybeAddAndroidHome(::put) }
      val command = invokeCommand + blazeCommandBuilder.build().toArgumentList()
      return LocalInvokerHelper.getScopedProcessHandler(
        project,
        command,
        WorkspaceRoot.fromProject(project),
        environment,
      ) {
        try {
          getBepStream(outputFile).use { streamProvider ->
            consumer.consume(streamProvider)
          }
        } catch (e: BuildException) {
          throw RuntimeException(e)
        }
      }
    } catch (e: ExecutionException) {
      throw BuildException(e)
    }
  }

  @Throws(BuildException::class)
  override fun invokeQuery(
    blazeCommandBuilder: BlazeCommand.Builder,
    blazeContext: BlazeContext,
  ): InputStream {
    val blazeCommand = blazeCommandBuilder.build()
    var tempFile: Path? = null
    try {
      tempFile = Files.createTempFile("intellij-bazel-${blazeCommand.name}-", ".stdout")
      val retVal =
        Closer.create().use { closer ->
          val out = closer.register(Files.newOutputStream(tempFile))
          val workspaceRoot = WorkspaceRoot.fromProject(project)
          val isUnitTestMode = ApplicationManager.getApplication().isUnitTestMode
          val stderr =
            closer.register(
              LineProcessingOutputStream.of(
                LineProcessingOutputStream.LineProcessor { line ->
                  val trimmed = line.trimEnd()
                  if (isUnitTestMode) {
                    println(trimmed)
                  }
                  logger.info(trimmed)
                  blazeContext.output(PrintOutput.output(trimmed))
                  true
                }
              )
            )
          val builder =
            ExternalTask.builder(workspaceRoot)
              .args(invokeCommand)
              .args(blazeCommand.toArgumentList())
              .context(blazeContext)
              .stdout(out)
              .stderr(stderr)
              .ignoreExitCode(true)
          maybeAddAndroidHome { k, v -> builder.environmentVar(k, v) }
          builder.build().run()
        }
      SyncQueryStatsScope.fromContext(blazeContext).ifPresent { stats ->
        stats.setBazelExitCode(retVal)
      }
      BazelExitCodeException.throwIfFailed(
        blazeCommand,
        retVal,
        BazelExitCodeException.ThrowOption.ALLOW_PARTIAL_SUCCESS,
      )
      val stream = Files.newInputStream(tempFile, StandardOpenOption.DELETE_ON_CLOSE)
      tempFile = null
      return BufferedInputStream(stream)
    } catch (e: IOException) {
      throw BuildException("Failed to run blaze query", e)
    } finally {
      tempFile?.let { runCatching { Files.deleteIfExists(it) } }
    }
  }

  @Throws(BuildException::class)
  override fun invokeInfo(
    blazeCommandBuilder: BlazeCommand.Builder,
    blazeContext: BlazeContext,
  ): InputStream {
    val blazeCommand = blazeCommandBuilder.build()
    var tempFile: Path? = null
    try {
      tempFile = Files.createTempFile("intellij-bazel-${blazeCommand.name}-", ".stdout")
      val exitCode =
        Closer.create().use { closer ->
          val out = closer.register(Files.newOutputStream(tempFile))
          val stderr = closer.register(LineProcessingOutputStream.of(PrintOutputLineProcessor(blazeContext)))
          val builder =
            ExternalTask.builder(WorkspaceRoot.fromProject(project))
              .args(invokeCommand)
              .args(blazeCommand.toArgumentList())
              .context(blazeContext)
              .stdout(out)
              .stderr(stderr)
              .ignoreExitCode(true)
          maybeAddAndroidHome { k, v -> builder.environmentVar(k, v) }
          builder.build().run()
        }
      BazelExitCodeException.throwIfFailed(blazeCommand, exitCode)
      val stream = Files.newInputStream(tempFile, StandardOpenOption.DELETE_ON_CLOSE)
      tempFile = null
      return BufferedInputStream(stream)
    } catch (e: IOException) {
      throw BuildException("Failed to run blaze info", e)
    } finally {
      tempFile?.let { runCatching { Files.deleteIfExists(it) } }
    }
  }

  private fun issueBuild(
    blazeCommandBuilder: BlazeCommand.Builder,
    workspaceRoot: WorkspaceRoot,
    context: BlazeContext,
  ): BuildResult {
    val blazeCommand = blazeCommandBuilder.build()
    val builder =
      ExternalTask.builder(workspaceRoot)
        .args(invokeCommand)
        .args(blazeCommand.toArgumentList())
        .context(context)
        .stdout(LineProcessingOutputStream.of(PrintOutputLineProcessor(context)))
        .stderr(LineProcessingOutputStream.of(BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(context)))
        .ignoreExitCode(true)
    maybeAddAndroidHome { k, v -> builder.environmentVar(k, v) }
    val retVal = builder.build().run()
    return BuildResult.fromExitCode(retVal)
  }

  private fun maybeAddAndroidHome(addVariable: (String, String) -> Unit) {
    if (type.needsAndroidHome) {
      val env = EnvironmentUtil.getEnvironmentMap()
      val androidHome = env["ANDROID_HOME"]?.takeIf { it.isNotEmpty() } ?: IdeSdks.getInstance().androidSdkPath?.toString()
      val androidNdkHome = env["ANDROID_NDK_HOME"]?.takeIf { it.isNotEmpty() } ?: IdeSdks.getInstance().androidNdkPath?.toString()
      if (!androidHome.isNullOrEmpty()) {
        addVariable("ANDROID_HOME", androidHome)
      }
      if (!androidNdkHome.isNullOrEmpty()) {
        addVariable("ANDROID_NDK_HOME", androidNdkHome)
      }
    }
  }

  @Throws(GetArtifactsException::class)
  private fun getBepStream(outputFile: File): BuildEventStreamProvider {
    return try {
      BuildEventStreamProvider.fromInputStream(BufferedInputStream(FileInputStream(outputFile)))
    } catch (e: FileNotFoundException) {
      logger.warn(e)
      throw GetArtifactsException(e.message)
    }
  }
}
