/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.deps

import com.android.tools.idea.protobuf.ExtensionRegistry
import com.google.common.base.Stopwatch
import com.google.common.collect.ImmutableMap
import com.google.common.io.ByteSource
import com.google.common.io.MoreFiles
import com.google.common.util.concurrent.AsyncFunction
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.Uninterruptibles
import com.google.errorprone.annotations.concurrent.GuardedBy
import com.google.idea.blaze.common.AtomicFileWriter
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.common.artifact.BuildArtifactCache
import com.google.idea.blaze.common.artifact.CachedArtifact
import com.google.idea.blaze.common.proto.ProtoStringInterner
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata
import com.google.idea.blaze.qsync.artifacts.BuildArtifact
import com.google.idea.blaze.qsync.artifacts.DigestMap
import com.google.idea.blaze.qsync.artifacts.DigestMapImpl
import com.google.idea.blaze.qsync.deps.CcCompilationInfo.Companion.create
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo.Companion.create
import com.google.idea.blaze.qsync.deps.TargetBuildInfo.Cc
import com.google.idea.blaze.qsync.deps.TargetBuildInfo.Companion.forCcTarget
import com.google.idea.blaze.qsync.deps.TargetBuildInfo.Companion.forJavaTarget
import com.google.idea.blaze.qsync.java.ArtifactTrackerProto.ArtifactTrackerState
import com.google.idea.blaze.qsync.project.ProjectPath.ExternalRepositoryFinder
import com.google.idea.blaze.qsync.project.ProjectPath.ExternalRepositoryFinder.Companion.createAndPrepare
import com.google.idea.common.experiments.FeatureRolloutExperiment
import com.intellij.util.containers.addIfNotNull
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayDeque
import java.util.Locale
import java.util.Queue
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.function.Supplier
import java.util.logging.Level
import java.util.logging.Logger
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.jetbrains.annotations.TestOnly

/**
 * The artifact tracker performs the following tasks:
 * * Keep track of which dependencies have been built, when they were build, and the set of artifacts produced by each.
 * * Requests that all artifacts are cached by [BuildArtifactCache].
 * * Provides details of build artifacts, allowing the project proto to be updated accordingly.
 */
class NewArtifactTracker<C : Context<C>>(
  private val workspaceRoot: Path,
  projectDirectory: Path,
  private val artifactCache: BuildArtifactCache,
  private val targetToMetadataFn: (TargetBuildInfo) -> Map<BuildArtifact, Collection<ArtifactMetadata.Extractor<*>>>,
  private val metadataFactory: ArtifactMetadata.Factory,
  private val executor: Executor,
) : ArtifactTracker<C> {
  private val stateFile: Path = projectDirectory.resolve("artifact_state")

  // Lock for making updates to the mutable state
  private val stateLock = Any()

  // TODO(mathewi) this state should really be owned by BlazeProjectSnapshot like all other state,
  //   and updated in lock step with it.
  @GuardedBy("stateLock") private val builtDeps: MutableMap<Label, TargetBuildInfo> = hashMapOf()

  @GuardedBy("stateLock") private val ccToolchainMap: MutableMap<String, CcToolchain> = hashMapOf()

  init {
    loadState()
  }

  @get:TestOnly
  val builtDepsForTesting: Collection<TargetBuildInfo>
    get() {
      synchronized(stateLock) {
        return builtDeps.values.toList()
      }
    }

  override fun getStateSnapshot(): ArtifactTracker.State {
    synchronized(stateLock) {
      return ArtifactTracker.State.create(builtDeps, ccToolchainMap)
    }
  }

  @Throws(IOException::class)
  override fun clear() {
    synchronized(stateLock) { builtDeps.clear() }
    saveState()
  }

  internal data class MetadataKey(val artifact: BuildArtifact, val mdClass: Class<out ArtifactMetadata>)

  private fun getTargetBuildInfo(
    targets: Set<Label>,
    outputInfo: OutputInfo,
    digestMap: DigestMap,
    externalRepositoryFinder: ExternalRepositoryFinder,
  ): Collection<TargetBuildInfo> {
    return ((if (enableJdepsDependencyGraph.isEnabled())
        getJavaTargetBuildInfoViaJdeps(targets, outputInfo, digestMap, externalRepositoryFinder)
      else getJavaTargetBuildInfo(outputInfo, digestMap, externalRepositoryFinder)) +
        outputInfo.ccTargets.values.map { ccTarget ->
          forCcTarget(create(ccTarget, digestMap, externalRepositoryFinder), outputInfo.buildContext)
        })
      .distinct()
  }

  private fun getJavaTargetBuildInfo(
    outputInfo: OutputInfo,
    digestMap: DigestMap,
    externalRepositoryFinder: ExternalRepositoryFinder,
  ): Collection<TargetBuildInfo> {
    return outputInfo.javaArtifactInfo.values.map { javaTarget ->
      val target = Label.of(javaTarget.target)
      forJavaTarget(
        create(javaTarget, digestMap, externalRepositoryFinder, outputInfo.getDependencies(target).toSet()),
        outputInfo.buildContext,
      )
    }
  }

  private fun getJavaTargetBuildInfoViaJdeps(
    targets: Set<Label>,
    outputInfo: OutputInfo,
    digestMap: DigestMap,
    externalRepositoryFinder: ExternalRepositoryFinder,
  ): Collection<TargetBuildInfo> {
    return buildSet {
      val toVisitTargets: Queue<Label> = ArrayDeque(targets)
      val visitedTargets: MutableSet<Label> = hashSetOf()
      while (!toVisitTargets.isEmpty()) {
        val target = toVisitTargets.poll()
        visitedTargets.add(target)
        val javaTargetArtifacts = outputInfo.javaArtifactInfo[target]
        if (javaTargetArtifacts != null) {
          add(
            forJavaTarget(
              create(javaTargetArtifacts, digestMap, externalRepositoryFinder, outputInfo.getDependencies(target).toSet()),
              outputInfo.buildContext,
            )
          )
        }
        val deps = outputInfo.getCompileDeps(target)
        if (deps is DepsAvailable) {
          for (label in deps.deps) {
            if (visitedTargets.add(label)) {
              toVisitTargets.add(label)
            }
          }
        } else {
          for (label in outputInfo.getDependencies(target)) {
            if (visitedTargets.add(label)) {
              toVisitTargets.add(label)
            }
          }
        }
      }
      for (javaTargetArtifacts in outputInfo.javaArtifactInfo.values) {
        if (javaTargetArtifacts.getIsExternalDependency()) {
          continue
        }
        // for a in-project target javaTargetArtifacts.getJarsList() returns all generated class jars collected
        // via java_outputs and AIDL base jar needed for resolving base classes for aidl generated
        // stubs.
        // They are necessary for symbol resolving. More details can be found in b/448400351.
        if (javaTargetArtifacts.getGenSrcsCount() > 0 || javaTargetArtifacts.getJarsCount() > 0) {
          val javaTargetLabel = Label.of(javaTargetArtifacts.target)
          add(
            forJavaTarget(
              create(javaTargetArtifacts, digestMap, externalRepositoryFinder, outputInfo.getDependencies(javaTargetLabel).toSet()),
              outputInfo.buildContext,
            )
          )
        }
      }
    }
  }

  @Throws(BuildException::class)
  private fun extractArtifactMetadata(
    targetBuildInfo: Iterable<TargetBuildInfo>,
    digestMap: DigestMap,
    buildIdForLogging: String?,
  ): Map<Label, Map<BuildArtifact, List<ArtifactMetadata>>> {
    val metadataFutures: MutableMap<MetadataKey, ListenableFuture<ArtifactMetadata>> = hashMapOf()
    for (targetInfo in targetBuildInfo) {
      for (entry in
        targetToMetadataFn(targetInfo).entries.flatMap { artifactEntry ->
          artifactEntry.value.map { extractor -> artifactEntry.key to extractor }
        }) {
        val key = MetadataKey(entry.first, entry.second.metadataClass())
        if (metadataFutures.containsKey(key)) {
          // this metadata has already been requested.
          continue
        }
        val digest =
          digestMap.digestForArtifactPath(entry.first.artifactPath, targetInfo.label)
            ?: throw BuildException(
              String.format(
                "Could not find digest for artifact path %s, target %s, build %s." + " It was requested for metadata %s.",
                entry.first.artifactPath,
                targetInfo.label,
                buildIdForLogging,
                entry.second.javaClass.getName(),
              )
            )
        val artifact =
          artifactCache
            .get(digest)
            .orElseThrow(
              Supplier {
                BuildException(
                  String.format(
                    "Digest %s not present in the cache, for %s built by %s in build" + " %s.  It was requested for metadata %s.",
                    digest,
                    entry.first.artifactPath,
                    targetInfo.label,
                    buildIdForLogging,
                    entry.second.metadataClass().getName(),
                  )
                )
              }
            )
        val transformed =
          Futures.transformAsync<CachedArtifact, ArtifactMetadata>(
            artifact,
            AsyncFunction { Futures.immediateFuture<ArtifactMetadata>(entry.second.extractFrom(it, entry.first)) },
            executor,
          )
        metadataFutures[key] = transformed
      }
    }

    val metadata: MutableMap<Label, MutableMap<BuildArtifact, MutableList<ArtifactMetadata>>> = hashMapOf()

    val failures: MutableList<BuildException> = mutableListOf()
    for (entry in metadataFutures.entries) {
      val buildArtifact = entry.key.artifact
      try {
        metadata
          .getOrPut(buildArtifact.target) { mutableMapOf() }
          .getOrPut(buildArtifact) { mutableListOf() }
          .addIfNotNull(Uninterruptibles.getUninterruptibly(entry.value))
      } catch (e: ExecutionException) {
        failures.add(
          BuildException(
            String.format(
              "Failed to extract metadata '%s' from artifact '%s' (from %s, produced by build" + " %s)",
              entry.key.mdClass.getName(),
              buildArtifact.artifactPath,
              buildArtifact.target,
              buildIdForLogging,
            ),
            e,
          )
        )
      }
    }
    if (!failures.isEmpty()) {
      val e = BuildException(String.format(Locale.ROOT, "Failed to extract metadata from %d artifacts", failures.size))
      failures.forEach(Consumer { exception: BuildException? -> e.addSuppressed(exception) })
      throw e
    }
    return metadata
  }

  @Throws(BuildException::class)
  override fun update(targets: Set<Label>, outputInfo: OutputInfo, context: C) {
    val externalRepositoryFinder = createAndPrepare(workspaceRoot)
    val artifactsCached = artifactCache.addAll(outputInfo.allJavaArtifacts, context)
    try {
      val unused = Uninterruptibles.getUninterruptibly(Futures.allAsList(artifactsCached))
    } catch (e: ExecutionException) {
      throw BuildException("Failed to cache build artifacts", e)
    }

    val digestMap: DigestMap =
      DigestMapImpl(
        outputInfo.allJavaArtifacts.map { it.artifactPath to it.digest }.distinct().toMap(),
        !outputInfo.targetsWithErrors.isEmpty() || outputInfo.exitCode != 0,
      )

    val sw = Stopwatch.createStarted()
    val newTargetInfo =
      getUniqueTargetBuildInfos(getTargetBuildInfo(targets, outputInfo, digestMap, externalRepositoryFinder)).toMutableMap()
    context.output(PrintOutput.output("Target build info map built in %dms", sw.elapsed(TimeUnit.MILLISECONDS)))

    val newToolchains = getCcToolchains(outputInfo, externalRepositoryFinder)

    // extract required metadata from the build artifacts
    val metadata = extractArtifactMetadata(newTargetInfo.values, digestMap, outputInfo.buildContext.buildIdForLogging())

    // insert this metadata into newTargetInfo
    for (entry in metadata.entries) {
      val tbi = newTargetInfo[entry.key] ?: continue
      newTargetInfo[entry.key] =
        when (tbi) {
          is TargetBuildInfo.Java -> forJavaTarget(tbi.javaInfo.withMetadata(entry.value), tbi.buildContext)

          is Cc -> forCcTarget(tbi.ccInfo.withMetadata(entry.value), tbi.buildContext)
        }
    }

    synchronized(stateLock) {
      for (tbi in newTargetInfo.values) {
        builtDeps[tbi.label] = tbi
      }
      for (toolchain in newToolchains) {
        ccToolchainMap[toolchain.id()] = toolchain
      }
      for (label in targets) {
        if (!builtDeps.containsKey(label)) {
          logger.warning("Target $label was not built. If the target is an alias, this is expected")
          builtDeps[label] =
            forJavaTarget(
              JavaArtifactInfo.empty(label).copy(dependencies = outputInfo.getDependencies(label).toSet()),
              outputInfo.buildContext,
            )
        }
      }
    }

    try {
      saveState()
    } catch (e: IOException) {
      throw BuildException("Failed to write artifact state", e)
    }
  }

  override fun getBugreportFiles(): ImmutableMap<String, ByteSource> {
    return ImmutableMap.of(stateFile.fileName.toString(), MoreFiles.asByteSource(stateFile))
  }

  @Throws(IOException::class)
  private fun saveState() {
    val serializer = synchronized(stateLock) { ArtifactTrackerStateSerializer().visitDepsMap(builtDeps).visitToolchainMap(ccToolchainMap) }
    AtomicFileWriter.create(stateFile).use { atomicFileWriter ->
      GZIPOutputStream(atomicFileWriter.outputStream).use { stream -> serializer.toProto().writeTo(stream) }
      atomicFileWriter.onWriteComplete()
    }
  }

  private fun loadState() {
    if (!Files.exists(stateFile)) {
      return
    }
    val state: ArtifactTrackerState
    try {
      GZIPInputStream(Files.newInputStream(stateFile)).use { stream ->
        state = ProtoStringInterner.intern(ArtifactTrackerState.parseFrom(stream, ExtensionRegistry.getEmptyRegistry()))
      }
    } catch (e: IOException) {
      logger.log(Level.WARNING, "Failed to read artifact tracker state from $stateFile", e)
      return
    }

    val deserializer = ArtifactTrackerStateDeserializer(metadataFactory)
    deserializer.visit(state)

    synchronized(stateLock) {
      builtDeps.putAll(deserializer.builtDepsMap)
      ccToolchainMap.putAll(deserializer.ccToolchainMap)
    }
  }

  companion object {
    private val logger: Logger = Logger.getLogger(NewArtifactTracker::class.java.getName())

    @JvmField val enableJdepsDependencyGraph: FeatureRolloutExperiment = FeatureRolloutExperiment("qsync.enable.jdeps.dependency.graph.3")

    private fun getCcToolchains(outputInfo: OutputInfo, externalRepositoryFinder: ExternalRepositoryFinder): List<CcToolchain> {
      return outputInfo.ccToolchains.values.map { CcToolchain.create(it, externalRepositoryFinder) }
    }

    /**
     * Gets unique [TargetBuildInfo]s per target.
     *
     * In some cases, the aspect can return slightly different target infos for the same target, due to the way that file-to-target mapping
     * happens inside the aspect:
     * * The aspect uses the bazel `File.owner` API to determine which target built a target.
     * * Depending on the dependency chain, the set of files returned for a specific target can differ.
     *
     * A specific example:
     *
     * The proto rules can yield classes jars for default (immutable) java classes, or mutable versions of the same classes. Consider the
     * rules:
     * * `:my_proto` - the proto rule itself
     * * `:my_java_proto` - generates java classes for the above
     * * `:my_mutable_java_proto` - generated mutable java classes for the above
     *
     * In this case, all the generated jars are attributed to the `:my_proto` target by the Bazel `File.owner` API.
     *
     * Now, if we have two project targets that transitively depend on the `:my_java_proto` and `my_mutable_java_proto` respectively, then
     * the [JavaArtifacts] generated for each target will contain entries for `:my_proto` that differ in their jars: one will contain
     * `libmy_proto.jar` or similar, and the other `libmy_proto_mutable.jar` or similar. The output for the target should be identical in
     * all other respects.
     *
     * We resolve this here by building sets of distinct [TargetBuildInfo] objects per target. In most cases there will only be one. In
     * cases such as the above, they will be identical in all respects other than the set of jar files. So we assert that, and then combine
     * the jar files to produce a single [TargetBuildInfo] per target.
     */
    @Throws(BuildException::class)
    private fun getUniqueTargetBuildInfos(allTargets: Collection<TargetBuildInfo>): Map<Label, TargetBuildInfo> {
      val targetInfoByTarget = allTargets.groupBy { it.label }
      val uniqueTargetInfo: MutableMap<Label, TargetBuildInfo> = hashMapOf()
      for (t in targetInfoByTarget.keys) {
        val targetInfos = targetInfoByTarget[t].orEmpty()
        val info: TargetBuildInfo?
        // TODO: 376833687 - The idea of conflicting targets is just wrong. We need to track targets
        // per configuration. Work in progress...
        // For now, ignore any conflicts as different configurations can conflict in any attribute.
        val first = targetInfos.firstOrNull()
        uniqueTargetInfo[t] =
          when {
            first == null -> continue
            first is TargetBuildInfo.Java && targetInfos.size > 1 -> {
              val firstJava = first.javaInfo
              val jars: MutableSet<BuildArtifact> = HashSet(firstJava.jars)
              val outputJars: MutableSet<BuildArtifact> = HashSet(firstJava.outputJars)

              targetInfos
                .drop(1)
                .filterIsInstance<TargetBuildInfo.Java>()
                .map { it.javaInfo }
                .forEach { javaInfo ->
                  jars.addAll(javaInfo.jars)
                  outputJars.addAll(javaInfo.outputJars)
                }

              val combinedJava =
                JavaArtifactInfo(
                  label = firstJava.label,
                  isExternalDependency = firstJava.isExternalDependency,
                  isKotlinToolchain = firstJava.isKotlinToolchain,
                  jars = jars,
                  outputJars = outputJars,
                  ideAar = firstJava.ideAar,
                  genSrcs = firstJava.genSrcs,
                  genAndroidRes = firstJava.genAndroidRes,
                  protoSrcjars = firstJava.protoSrcjars,
                  sources = firstJava.sources,
                  srcJars = firstJava.srcJars,
                  androidResourcesPackage = firstJava.androidResourcesPackage,
                  kotlinCompilerFlags = firstJava.kotlinCompilerFlags,
                  dependencies = firstJava.dependencies,
                )
              forJavaTarget(combinedJava, first.buildContext)
            }
            else -> first
          }
      }
      return uniqueTargetInfo
    }
  }
}
