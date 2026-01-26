/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync

import com.android.tools.idea.concurrency.transform
import com.google.common.base.Stopwatch
import com.google.common.io.ByteSource
import com.google.common.io.MoreFiles
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.google.devtools.build.lib.view.proto.Deps
import com.google.idea.blaze.base.bazel.BazelExitCodeException
import com.google.idea.blaze.base.bazel.BazelExitCodeException.ThrowOption
import com.google.idea.blaze.base.bazel.BuildSystem
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker
import com.google.idea.blaze.base.command.BlazeCommand
import com.google.idea.blaze.base.command.BlazeCommandName
import com.google.idea.blaze.base.command.BlazeFlags
import com.google.idea.blaze.base.command.BlazeInvocationContext
import com.google.idea.blaze.base.command.buildresult.BuildResultParser
import com.google.idea.blaze.base.logging.utils.querysync.BuildDepsStatsScope
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.prefetch.FetchExecutor
import com.google.idea.blaze.base.projectview.ProjectViewManager
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager
import com.google.idea.blaze.base.util.VersionChecker
import com.google.idea.blaze.base.vcs.BlazeVcsHandlerProvider.BlazeVcsHandler
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.Interners
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.common.artifact.BuildArtifactCache
import com.google.idea.blaze.common.artifact.OutputArtifact
import com.google.idea.blaze.common.proto.ProtoStringInterner
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.BlazeQueryParser
import com.google.idea.blaze.qsync.deps.DependencyBuildContext
import com.google.idea.blaze.qsync.deps.NewArtifactTracker
import com.google.idea.blaze.qsync.deps.OutputGroup
import com.google.idea.blaze.qsync.deps.OutputInfo
import com.google.idea.blaze.qsync.java.JavaTargetInfo
import com.google.idea.blaze.qsync.java.cc.CcCompilationInfoOuterClass
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.common.experiments.BoolExperiment
import com.google.protobuf.Message
import com.google.protobuf.TextFormat
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.util.application
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.AutoCloseable
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.map
import kotlin.jvm.optionals.getOrNull
import org.jetbrains.annotations.VisibleForTesting

/** An object that knows how to build dependencies for given targets  */
open class BazelDependencyBuilder(
  protected val project: Project,
  protected val buildSystem: BuildSystem,
  protected val projectDefinition: ProjectDefinition,
  protected val snapshotHolder: SnapshotHolder,
  protected val workspaceRoot: WorkspaceRoot,
  protected val vcsHandler: BlazeVcsHandler?,
  protected val buildArtifactCache: BuildArtifactCache,
  protected val handledRuleKinds: Set<String>,
) : DependencyBuilder, BazelDependencyBuilderPublicForTests {
  @JvmRecord
  data class BuildDependencyParameters(
    val include: List<String>,
    val exclude: List<String>,
    val alwaysBuildRules: List<String>,
    val supportedBuildRules: List<String>,
    val generateIdlClasses: Boolean,
    val useGeneratedSrcJars: Boolean,
  )

  private val aspectFiles: AspectFiles = AspectFiles(workspaceRoot)

  @Throws(IOException::class, BuildException::class)
  override fun build(
    context: BlazeContext,
    buildTargets: Set<Label>,
    outputGroups: Collection<OutputGroup>,
  ): OutputInfo {
    application
      .service<BuildDependenciesLockService>()
      .lockWorkspace(workspaceRoot.path().toString())
      .use {
        if (VersionChecker.versionMismatch()) {
          throw BuildException(
            "The IDE has been upgraded in the background. Bazel build aspect files maybe incompatible. Please restart the IDE."
          )
        }
        val invoker = buildSystem.getBuildInvoker(project)
        val buildDependenciesBazelInvocationInfo = getInvocationInfo(context, buildTargets, invoker.capabilities, outputGroups)
        prepareInvocationFiles(context, buildDependenciesBazelInvocationInfo.invocationWorkspaceFiles)

        BuildDepsStatsScope.fromContext(context).ifPresent { it.setBlazeBinaryType(invoker.type) }

        val commandBuilder = BlazeCommand.builder(BlazeCommandName.BUILD)
        commandBuilder.addBlazeFlags(buildDependenciesBazelInvocationInfo.argsAndFlags)

        BuildDepsStatsScope.fromContext(context).ifPresent { it.setBuildFlags(commandBuilder.build().toArgumentList()) }

        val buildTime = Instant.now()
        return invoker.invoke(commandBuilder, context) { streamProvider ->
          val outputs = BlazeBuildOutputs.fromParsedBepOutput(BuildResultParser.getBuildOutput(streamProvider, Interners.STRING))
          BazelExitCodeException.throwIfFailed(
            commandBuilder,
            outputs.buildResult(),
            ThrowOption.ALLOW_PARTIAL_SUCCESS,
            ThrowOption.ALLOW_BUILD_FAILURE
          )
          buildDependenciesBazelInvocationInfo.createOutputInfo(
            blazeBuildOutputs = outputs,
            buildTime = buildTime,
            context = context
          )
        }
      }
  }

  @VisibleForTesting
  override fun getInvocationInfo(
    context: BlazeContext,
    buildTargets: Set<Label>,
    buildInvokerCapabilities: Set<BuildInvoker.Capability>,
    outputGroups: Collection<OutputGroup>,
  ): BuildDependenciesBazelInvocationInfo {
    val includes = projectDefinition.projectIncludes.map { "//$it" }
    val excludes = projectDefinition.projectExcludes.map { "//$it" }
    val alwaysBuildRules = BlazeQueryParser.ALWAYS_BUILD_RULE_KINDS - handledRuleKinds

    val parameters =
      BuildDependencyParameters(
        include = includes,
        exclude = excludes,
        alwaysBuildRules = alwaysBuildRules.toList(),
        supportedBuildRules =
          BlazeQueryParser.getAllKnownRuleClasses(HandledRulesProvider.getNotHandledRuleKinds(handledRuleKinds)).asList(),
        generateIdlClasses = true,
        useGeneratedSrcJars = buildGeneratedSrcJars.value
      )

    val invocationFiles = getInvocationFiles(buildTargets, buildInvokerCapabilities, parameters)

    val projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet()
    // TODO This is not SYNC_CONTEXT, but also not OTHER_CONTEXT, we need to decide what kind
    // of flags need to be passed here.
    val additionalBlazeFlags =
      BlazeFlags.blazeFlags(
        project,
        projectViewSet,
        BlazeCommandName.BUILD,
        BlazeInvocationContext.OTHER_CONTEXT
      )

    val querySyncFlags = buildList {
      if (invocationFiles.targetPatternFileWorkspaceRelativeFile != null) {
        add("--target_pattern_file=${invocationFiles.targetPatternFileWorkspaceRelativeFile}")
      }
      else {
        addAll(buildTargets.map { it.toString() })
      }
      addAll(additionalBlazeFlags)
      add("--aspects=${invocationFiles.aspectFileLabel}%collect_dependencies,${invocationFiles.aspectFileLabel}%package_dependencies")
      add("--noexperimental_run_validations")
      add("--keep_going")
      addAll(outputGroups.map { "--output_groups=${it.outputGroupName}" })
    }
    return BuildDependenciesBazelInvocationInfo(
      buildArtifactCache,
      querySyncFlags,
      outputGroups.toSet(),
      invocationFiles.files
    )
  }

  @JvmRecord
  data class InvocationFiles(
    /**
     * A workspace relative path to content map of files to be cerated in the workspace.
     */
    val files: Map<Path, ByteSource>,
    val aspectFileLabel: String,
    val targetPatternFileWorkspaceRelativeFile: String?,
  )

  protected open fun getBuildDependenciesAspectDepsFiles(): Map<String, ByteSource> {
    return buildMap {
      val querySummary = snapshotHolder()?.queryData?.querySummary()
      fun addFile(name: String, mapToName: String) = put(name, MoreFiles.asByteSource(getBundledAspectPath(mapToName)))
      fun buildFilePresent(vararg buildFile: Label) = buildFile.any { it in querySummary?.allBuildIncludedFiles.orEmpty() }
      fun blazeVersionData() = BlazeProjectDataManager.getInstance(project).getBlazeProjectData()!!.getBlazeVersionData()

      addFile("build_dependencies_deps.bzl", "build_dependencies_deps.bzl")

      addFile(
        "build_dependencies_android_deps.bzl",
        when {
          // Aspects for Android support
          buildFilePresent(RULES_ANDROID_RULES_BZL1, RULES_ANDROID_RULES_BZL2) -> "build_dependencies_android_rules_android_deps.bzl"
          blazeVersionData().bazelIsAtLeastVersion(7, 1, 0) -> "build_dependencies_android_deps.bzl"
          else -> "build_dependencies_android_legacy_deps.bzl"
        }
      )

      // Aspects for Java and ImlModule support
      addFile(
        "build_dependencies_java_deps.bzl",
        when {
          buildFilePresent(STUDIO_IML_MODULE_RULE) -> "build_dependencies_iml_module_java_deps.bzl"
            .also { addFile("build_dependencies_java_deps_wrapped.bzl", "build_dependencies_java_deps.bzl") }
          else -> "build_dependencies_java_deps.bzl"
        }
      )

      addFile("build_dependencies_cc_deps.bzl", "build_dependencies_cc_deps.bzl")
      addFile("build_dependencies_java_proto_deps.bzl", "build_dependencies_java_proto_deps.bzl")

      // Aspects for Kotlin support
      addFile(
        "build_dependencies_kotlin_deps.bzl",
        when {
          buildFilePresent(RULES_KOTLIN_BZL1, RULES_KOTLIN_BZL2) -> "build_dependencies_kotlin_deps.bzl"
          else -> "build_dependencies_kotlin_stub_deps.bzl"
        }
      )
    }
  }

  /**
   * Provides information about files that must be created in the workspace root for the aspect to
   * operate.
   */
  @VisibleForTesting
  fun getInvocationFiles(
    buildTargets: Set<Label>,
    buildInvokerCapabilities: Set<BuildInvoker.Capability>,
    parameters: BuildDependencyParameters,
  ): InvocationFiles {
    val aspectFileName = "qs-$projectHash.bzl"
    val targetPatternFileWorkspaceRelativeFile: String?
    val files = buildMap {
      fun addFile(name: String, content: ByteSource): String {
        val relativePath = "$INVOCATION_FILES_DIR/$name"
        put(Path.of(relativePath), content)
        return relativePath
      }

      fun bundled(name: String) = MoreFiles.asByteSource(getBundledAspectPath(name))

      addFile("BUILD", ByteSource.empty())
      addFile("build_dependencies.bzl", bundled("build_dependencies.bzl"))

      for ((path, content) in getBuildDependenciesAspectDepsFiles()) {
        addFile(path, content)
      }

      addFile(aspectFileName, getByteSourceFromString(getBuildDependenciesParametersFileContent(parameters)))

      targetPatternFileWorkspaceRelativeFile =
        when {
          buildTargets.size > 3 && buildUseTargetPatternFile.value
          && buildInvokerCapabilities.contains(BuildInvoker.Capability.SUPPORT_TARGET_PATTERN_FILE) -> {
            addFile("targets-$projectHash.txt", getByteSourceFromString(buildTargets.joinToString(separator = "\n") { it.toString() }))
          }

          else -> null
        }
    }
    return InvocationFiles(
      files,
      Label.of(String.format("//$INVOCATION_FILES_DIR:$aspectFileName")).toString(),
      targetPatternFileWorkspaceRelativeFile
    )
  }


  private fun getBuildDependenciesParametersFileContent(parameters: BuildDependencyParameters): String {
    return buildString {
      fun appendBoolean(name: String, value: Boolean) {
        append("  ")
        append(name)
        append(" = ")
        append(if (value) "True" else "False")
        append(",\n")
      }

      fun appendStrings(name: String, items: List<String>) {
        append("  ")
        append(name)
        append(" = [\n")
        for (item in items) {
          append("    \"")
          append(item)
          append("\",\n")
        }
        append("  ],\n")
      }

      append("""
        |load(':build_dependencies.bzl', _collect_dependencies = 'collect_dependencies', _package_dependencies = 'package_dependencies')
        |_config = struct(
       """.trimMargin("|"))
      appendStrings("include", parameters.include)
      appendStrings("exclude", parameters.exclude)
      appendStrings("always_build_rules", parameters.alwaysBuildRules)
      appendStrings("supported_build_rules", parameters.supportedBuildRules)
      appendBoolean("generate_aidl_classes", parameters.generateIdlClasses)
      appendBoolean("use_generated_srcjars", parameters.useGeneratedSrcJars)
      append("""
        |)
        |
        |collect_dependencies = _collect_dependencies(_config)
        |package_dependencies = _package_dependencies(_config)
      """.trimMargin("|"))
    }
  }

  @get:VisibleForTesting
  val projectHash: String
    get() = StringUtil.sanitizeJavaIdentifier(project.name + project.locationHash)

  @VisibleForTesting
  override fun getBundledAspectPath(filename: String): Path {
    return AspectFiles.getBundledAspectPath(filename, workspaceRoot.absolutePathFor(""))
  }

  /**
   * Prepares for use, and returns the location of the `build_dependencies.bzl` aspect.
   *
   *
   * The return value is a string in the format expected by bazel for an aspect file, omitting
   * the name of the aspect within that file. For example, `//package:aspect.bzl`.
   */
  @VisibleForTesting
  @Throws(IOException::class, BuildException::class)
  override fun prepareInvocationFiles(
    context: BlazeContext, invocationFiles: Map<Path, ByteSource>,
  ) {
    for (e in invocationFiles.entries) {
      aspectFiles.copyInvocationFile(e.key, e.value)
    }
  }

  companion object {
    @VisibleForTesting
    @JvmField
    val buildGeneratedSrcJars: BoolExperiment =
      BoolExperiment("qsync.build.generated.src.jars", false)

    // Note, this is currently incompatible with the build API.
    val buildUseTargetPatternFile: BoolExperiment =
      BoolExperiment("qsync.build.use.target.pattern.file", true)

    const val INVOCATION_FILES_DIR: String = ".aswb"

    val RULES_ANDROID_RULES_BZL1: Label = Label.of("@@rules_android~//android:rules.bzl")
    val RULES_ANDROID_RULES_BZL2: Label = Label.of("@@rules_android+//android:rules.bzl")

    val RULES_KOTLIN_BZL1: Label = Label.of("@@rules_kotlin~//kotlin/internal:defs.bzl")
    val RULES_KOTLIN_BZL2: Label = Label.of("@@rules_kotlin+//kotlin/internal:defs.bzl")

    // The following .bzl file defines the iml_module rule used by Android Studio
    val STUDIO_IML_MODULE_RULE: Label = Label.of("//tools/base/bazel:bazel.bzl")
  }
}

/**
 * @param argsAndFlags arguments and flags to be passed to `bazel build` command to build
 * dependencies and metadata required by query sync.
 * @param requestedOutputGroups lists output groups that are requested by `argsAndFlags`.
 */
class BuildDependenciesBazelInvocationInfo(
  private val buildArtifactCache: BuildArtifactCache,
  val argsAndFlags: List<String>,
  val requestedOutputGroups: Set<OutputGroup>,
  val invocationWorkspaceFiles: Map<Path, ByteSource>,
) {

  @Throws(BuildException::class)
  fun createOutputInfo(
    blazeBuildOutputs: BlazeBuildOutputs,
    buildTime: Instant,
    context: BlazeContext,
  ): OutputInfo {
    val allArtifacts = GroupedOutputArtifacts.create(blazeBuildOutputs, requestedOutputGroups)

    val artifactInfoFiles = allArtifacts[OutputGroup.ARTIFACT_INFO_FILE]
    val compileJdepsFiles = allArtifacts[OutputGroup.JDEPS]
    val ccArtifactInfoFiles = allArtifacts[OutputGroup.CC_INFO_FILE]

    val startTime = System.currentTimeMillis()
    val totalFilesToFetch = artifactInfoFiles.size + compileJdepsFiles.size + ccArtifactInfoFiles.size
    val totalBytesToFetch =
      artifactInfoFiles.sumOf { it.getLength() } + compileJdepsFiles.sumOf { it.getLength() } + ccArtifactInfoFiles.sumOf { it.getLength() }

    val shouldLog = totalFilesToFetch > FILE_NUMBER_LOG_THRESHOLD || totalBytesToFetch > FETCH_SIZE_LOG_THRESHOLD
    if (shouldLog) {
      context.output(
        PrintOutput.log("Fetching and parsing $totalFilesToFetch artifact info files (${StringUtilRt.formatFileSize(totalBytesToFetch)})")
      )
    }

    val artifactInfos = readAndTransformInfoFiles(context, artifactInfoFiles) { readArtifactInfoFile(it) }
    val ccInfos = readAndTransformInfoFiles(context, ccArtifactInfoFiles) { readCcInfoFile(it) }
    val jdeps =
      if (NewArtifactTracker.enableJdepsDependencyGraph.isEnabled())
        readAndTransformInfoFiles(context, compileJdepsFiles) { readJdepsFile(it) }
      else emptyMap()

    val elapsed = System.currentTimeMillis() - startTime
    if (shouldLog) {
      context.output(PrintOutput.log("Fetched and parsed artifact info files in $elapsed ms"))
    }
    val buildContext = DependencyBuildContext.create(blazeBuildOutputs.buildId(), buildTime)

    return OutputInfo.create(
      allArtifacts,
      artifactInfos,
      jdeps,
      ccInfos.values.toList(),
      blazeBuildOutputs.targetsWithErrors().map { Label.of(it) }.toSet(),
      blazeBuildOutputs.buildResult().exitCode,
      buildContext)
  }

  private fun interface CheckedTransform<T, R> {
    @Throws(BuildException::class)
    fun apply(t: T): R
  }

  @Throws(BuildException::class)
  private fun <T> readAndTransformInfoFiles(
    context: Context<*>,
    artifactInfoFiles: List<OutputArtifact>,
    transform: CheckedTransform<ByteSource, T>,
  ): Map<Path, T> {
    val listenableFuture = buildArtifactCache.addAll(artifactInfoFiles, context)
    val sw = Stopwatch.createStarted()
    // TODO: solodkyy - For now separate fetching and parsing. We have had some thread safety issues
    // and it allows us reporting fetching
    //  time correctly which happens to be much shorter than the time it takes to parse the info
    // files.
    try {
      listenableFuture.get()
    }
    catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      throw BuildException(e)
    }
    catch (e: ExecutionException) {
      throw BuildException(e)
    }
    context.output(PrintOutput.output("Fetched ${artifactInfoFiles.size} info files in ${sw.elapsed().toMillis()}ms"))
    val artifactFutures =
      artifactInfoFiles
        .mapNotNull { outputArtifact ->
          val result = buildArtifactCache.get(outputArtifact.getDigest()).getOrNull()
          if (result == null) {
            context.output(PrintOutput.error("Failed to get artifact future for: ${outputArtifact.getDigest()}"))
            context.setHasError()
          }
          result?.transform(directExecutor()) { cachedArtifact -> outputArtifact.getArtifactPath() to cachedArtifact }
        }

    val futures =
      artifactFutures.map { future ->
        future.transform(FetchExecutor.EXECUTOR) {
          val transformedArtifact = transform.apply(it.second.byteSource())
          it.first to transformedArtifact
        }
      }

    try {
      return Futures.allAsList(futures).get().toMap()
    }
    catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      throw BuildException(e)
    }
    catch (e: ExecutionException) {
      throw BuildException(e)
    }
  }

  @Throws(BuildException::class)
  private fun readArtifactInfoFile(file: ByteSource): JavaTargetInfo.JavaArtifacts {
    return ProtoStringInterner.intern(readArtifactInfoProtoFile(JavaTargetInfo.JavaArtifacts.newBuilder(), file).build())
  }

  @Throws(BuildException::class)
  private fun readJdepsFile(file: ByteSource): Deps.Dependencies {
    return ProtoStringInterner.intern(
      try {
        file.openStream().use { Deps.Dependencies.parseFrom(it) }
      }
      catch (e: IOException) {
        throw BuildException(e)
      }
    )
  }

  @Throws(BuildException::class)
  private fun readCcInfoFile(file: ByteSource): CcCompilationInfoOuterClass.CcCompilationInfo {
    return ProtoStringInterner.intern(readArtifactInfoProtoFile(CcCompilationInfoOuterClass.CcCompilationInfo.newBuilder(), file).build())
  }

  companion object {
    /**
     * Logs message if the number of artifact info files fetched is greater than
     * FILE_NUMBER_LOG_THRESHOLD
     */
    private const val FILE_NUMBER_LOG_THRESHOLD = 1

    @VisibleForTesting
    @Throws(BuildException::class)
    @JvmStatic
    fun <B : Message.Builder> readArtifactInfoProtoFile(builder: B, file: ByteSource): B {
      try {
        file.openStream().use { inputStream ->
          val parser = TextFormat.Parser.newBuilder().build()
          parser.merge(InputStreamReader(inputStream, StandardCharsets.UTF_8), builder)
          return builder
        }
      }
      catch (e: IOException) {
        throw BuildException(e)
      }
    }

    /**
     * Logs message if the size of all artifact info files fetched is greater than
     * FETCH_SIZE_LOG_THRESHOLD
     */
    private val FETCH_SIZE_LOG_THRESHOLD = (1 shl 20).toLong() // 1 mB
  }
}

@Service(Service.Level.APP)
class BuildDependenciesLockService {
  private val workspaceLocks: ConcurrentMap<String, ReentrantLock> = ConcurrentHashMap()

  fun interface WorkspaceLock : AutoCloseable {
    override fun close()
  }

  fun lockWorkspace(workspace: String): WorkspaceLock {
    val lock = workspaceLocks.computeIfAbsent(workspace) { ReentrantLock() }
    lock.lock()
    return WorkspaceLock { lock.unlock() }
  }
}

private fun getByteSourceFromString(content: String): ByteSource {
  return object : ByteSource() {
    override fun openStream(): InputStream {
      return ByteArrayInputStream(content.toByteArray(StandardCharsets.UTF_8))
    }
  }
}
