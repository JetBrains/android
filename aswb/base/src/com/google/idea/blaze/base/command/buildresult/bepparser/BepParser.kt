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
@file:JvmName("BepParser")

package com.google.idea.blaze.base.command.buildresult.bepparser

import com.google.common.base.Preconditions
import com.google.common.base.Strings
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Interner
import com.google.common.collect.Interners
import com.google.common.collect.Queues
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.IdCase.ACTION_COMPLETED
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.IdCase.BUILD_FINISHED
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.IdCase.NAMED_SET
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.IdCase.STARTED
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.IdCase.TARGET_COMPLETED
import com.google.errorprone.annotations.CanIgnoreReturnValue
import com.google.idea.blaze.common.artifact.OutputArtifact
import com.google.idea.common.experiments.BoolExperiment
import com.google.idea.common.experiments.IntExperiment
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.application
import java.util.concurrent.Semaphore


private val parallelBepPoolingEnabled: BoolExperiment = BoolExperiment("bep.parsing.pooling.enabled", true)

// Maximum number of concurrent BEP parsing operations to allow.
// For large projects, BEP parsing of a single shard can consume several hundred Mb of memory
private val maxThreads: IntExperiment = IntExperiment("bep.parsing.concurrency.limit", 5)

/**
 * Parses BEP events into {@link ParsedBepOutput}.
 */
@Throws(BuildEventStreamProvider.BuildEventStreamException::class)
fun parseBepArtifacts(stream: BuildEventStreamProvider, nullableInterner: Interner<String>?): ParsedBepOutput {
  val state = parseBep(stream, nullableInterner)
  val bepBytesConsumed = stream.getBytesConsumed()
  return object : ParsedBepOutput {
    override fun buildResult(): Int {
      return state.buildResult
    }

    override fun bepBytesConsumed(): Long {
      return bepBytesConsumed
    }

    override fun idForLogging(): String {
      return state.buildId ?: "unknown"
    }

    override fun getOutputGroupTargetArtifacts(outputGroup: String, label: String): List<OutputArtifact> {
      return state.traverseFileSets(state.outputs.outputGroupTargetFileSetStream(outputGroup, label)).toDistinctOutputArtifacts().toList()
    }

    override fun getOutputGroupArtifacts(outputGroup: String): List<OutputArtifact> {
      return state.traverseFileSets(state.outputs.outputGroupFileSetStream(outputGroup)).toDistinctOutputArtifacts().toList()
    }

    override fun targetsWithErrors(): Set<String> {
      return ImmutableSet.copyOf(state.targetsWithErrors)
    }

    override fun getAllOutputArtifactsForTesting(): List<OutputArtifact> {
      return state.traverseFileSets(state.outputs.fileSetStream()).toDistinctOutputArtifacts().toList()
    }
  }
}

/**
 * Parses BEP events into {@link ParsedBepOutput}. String references in {@link BuildEventStreamProtos.NamedSetOfFiles}
 * are interned to conserve memory.
 *
 * <p>BEP protos often contain many duplicate strings both within a single stream and across
 * shards running in parallel, so a {@link Interner} is used to share references.
 */
@Throws(BuildEventStreamProvider.BuildEventStreamException::class)
fun parseBepArtifactsForLegacySync(stream: BuildEventStreamProvider, nullableInterner: Interner<String>?): ParsedBepOutput.Legacy {
  val semaphore = application.service<BepParserSemaphore>()
  semaphore.start()
  try {
    val state = parseBep(stream, nullableInterner)
    val fileSetMap: ImmutableMap<String, ParsedBepOutput.Legacy.FileSet> =
      fillInTransitiveFileSetData(state.fileSets, state.outputs, state.startTimeMillis)
    return ParsedBepOutput.Legacy(
      state.buildId,
      fileSetMap,
      state.startTimeMillis,
      state.buildResult,
      stream.getBytesConsumed(),
      ImmutableSet.copyOf(state.targetsWithErrors))
  }
  finally {
    semaphore.end()
  }
}


/**
 * A record of the top level file sets output by the given {@link #outputGroup}, {@link #target} and {@link #config}.
 */
private data class OutputGroupTargetConfigFileSets(
  val outputGroup: String, val target: String, val config: String, val fileSetNames: List<String>,
)

/**
 * A data structure allowing to associate file set names with output group, targets and configs and allowing to retrieve them efficiently
 * at each level of the hierarchy.
 */
private class OutputGroupTargetConfigFileSetMap {
  private val data: MutableMap<String, MutableMap<String, MutableMap<String, List<String>>>> = mutableMapOf()

  private fun getOutputGroup(outputGroup: String): MutableMap<String, MutableMap<String, List<String>>> {
    return data.computeIfAbsent(outputGroup) { mutableMapOf() }
  }

  private fun getOutputGroupTarget(outputGroup: String, target: String): MutableMap<String, List<String>> {
    return getOutputGroup(outputGroup).computeIfAbsent(target) { mutableMapOf() }
  }

  private fun getOutputGroupTargetConfig(outputGroup: String, target: String, config: String): List<String> {
    return getOutputGroupTarget(outputGroup, target)[config] ?: emptyList()
  }

  fun setOutputGroupTargetConfig(outputGroup: String, target: String, config: String, fileSetNames: List<String>) {
    val previous = getOutputGroupTarget(outputGroup, target).put(config, fileSetNames.toList())
    if (previous != null) {
      error("$outputGroup:$target:$config already present")
    }
  }

  fun fileSetStream(): Sequence<OutputGroupTargetConfigFileSets> {
    return data.entries.asSequence().flatMap { outputGroup ->
      outputGroup.value.entries.asSequence().flatMap { target ->
        target.value.entries.asSequence().map { config ->
          OutputGroupTargetConfigFileSets(outputGroup.key, target.key,
                                          config.key, config.value)
        }
      }
    }
  }

  fun outputGroupFileSetStream(outputGroup: String): Sequence<OutputGroupTargetConfigFileSets> {
    val outputGroupData = data[outputGroup] ?: return emptySequence()
    return outputGroupData.entries.asSequence().flatMap { target ->
      target.value.entries.asSequence().map { config ->
        OutputGroupTargetConfigFileSets(outputGroup, target.key,
                                        config.key, config.value)
      }
    }
  }

  fun outputGroupTargetFileSetStream(outputGroup: String, target: String): Sequence<OutputGroupTargetConfigFileSets> {
    val outputGroupData = data[outputGroup] ?: return emptySequence()
    val outputGroupTargetData = outputGroupData[target] ?: return emptySequence()
    return outputGroupTargetData.entries.asSequence().map { config ->
      OutputGroupTargetConfigFileSets(outputGroup, target,
                                      config.key, config.value)
    }
  }
}

/**
 * A collection of all known named file sets.
 */
private class FileSets {
  val data: MutableMap<String, BuildEventStreamProtos.NamedSetOfFiles> = mutableMapOf()

  fun add(name: String, fileSet: BuildEventStreamProtos.NamedSetOfFiles) {
    val existing = data.put(name, fileSet)
    if (existing != null) {
      error("File set named $name already exists")
    }
  }

  fun toImmutableMap(): ImmutableMap<String, BuildEventStreamProtos.NamedSetOfFiles> {
    return ImmutableMap.copyOf(data)
  }
}

private class BepParserState {
  val outputs = OutputGroupTargetConfigFileSetMap()
  val fileSets = FileSets()
  val targetsWithErrors = mutableSetOf<String>()
  var buildId: String? = null
  var startTimeMillis: Long = 0L
  var buildResult: Int = 0

  fun traverseFileSets(fileSetNames: Sequence<OutputGroupTargetConfigFileSets>): Sequence<NamedFileSet> {
    val queue = ArrayDeque<String>()
    val visited = HashSet<String>()

    fileSetNames.flatMap { it.fileSetNames.asSequence() }.distinct().forEach { filesetName ->
      if (visited.add(filesetName)) {
        queue.addLast(filesetName)
      }
    }
    return sequence {
      while (!queue.isEmpty()) {
        val fileSetId = queue.removeFirst()
        val fileSet = fileSets.data[fileSetId] ?: error("Unknown fileSetId: $fileSetId")
        for (fileSetId in fileSet.fileSetsList) {
          if (visited.add(fileSetId.id)) {
            queue.addLast(fileSetId.id)
          }
        }
        yield(NamedFileSet(fileSetId, startTimeMillis, fileSet))
      }
    }
  }
}

private data class NamedFileSet(val id: String, val startTimeMillis: Long, val fileSet: BuildEventStreamProtos.NamedSetOfFiles)

private fun Sequence<NamedFileSet>.toDistinctOutputArtifacts(): Sequence<OutputArtifact> {
  val emitted = HashSet<String>()
  return flatMap { fileSet ->
    val artifacts = parseFiles(fileSet.fileSet, fileSet.startTimeMillis)
    artifacts.mapNotNull { artifact ->
      if (emitted.add(artifact.getArtifactPath().toString())) artifact else null
    }
  }
}

@Service(Service.Level.APP)
class BepParserSemaphore {

  val parallelParsingSemaphore: Semaphore? = if (parallelBepPoolingEnabled.getValue()) Semaphore(maxThreads.getValue()) else null

  @Throws(BuildEventStreamProvider.BuildEventStreamException::class)
  fun start() {
    try {
      parallelParsingSemaphore?.acquire()
    }
    catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      throw BuildEventStreamProvider.BuildEventStreamException("Failed to acquire a parser semphore permit", e)
    }
  }

  fun end() {
    parallelParsingSemaphore?.release()
  }
}

private class FileSetBuilder {
  var namedSet: BuildEventStreamProtos.NamedSetOfFiles? = null
  var configId: String? = null
  val outputGroups: MutableSet<String> = HashSet()
  val targets: MutableSet<String> = HashSet()

  @CanIgnoreReturnValue
  fun updateFromParent(parent: FileSetBuilder): FileSetBuilder {
    configId = parent.configId
    outputGroups.addAll(parent.outputGroups)
    targets.addAll(parent.targets)
    return this
  }

  fun isValid(): Boolean {
    return namedSet != null && configId != null
  }

  fun build(startTimeMillis: Long): ParsedBepOutput.Legacy.FileSet {
    return ParsedBepOutput.Legacy.FileSet(parseFiles(namedSet!!, startTimeMillis).toList(), outputGroups, targets)
  }
}


@Throws(BuildEventStreamProvider.BuildEventStreamException::class)
private fun parseBep(stream: BuildEventStreamProvider, nullableInterner: Interner<String>?): BepParserState {
  val interner = nullableInterner ?: Interners.newStrongInterner()
  val state = BepParserState()
  var emptyBuildEventStream = true
  for (event in generateSequence { stream.next }) {
    emptyBuildEventStream = false
    when (event.id.idCase) {
      NAMED_SET -> {
        val namedSet = internNamedSet(event.getNamedSetOfFiles(), interner)
        state.fileSets.add(interner.intern(event.id.namedSet.id), namedSet)
      }

      ACTION_COMPLETED -> {
        Preconditions.checkState(event.hasAction())
        if (!event.action.success) {
          state.targetsWithErrors.add(event.id.actionCompleted.label)
        }
      }

      TARGET_COMPLETED -> {
        val label = event.id.targetCompleted.label
        val configId = event.id.targetCompleted.configuration.id

        for (o in event.completed.outputGroupList) {
          val fileSetNames = getFileSets(o, interner)
          state.outputs.setOutputGroupTargetConfig(interner.intern(o.name), interner.intern(label), interner.intern(configId),
                                                   fileSetNames)
        }
      }

      STARTED -> {
        state.buildId = Strings.emptyToNull(event.started.uuid)
        state.startTimeMillis = event.started.startTimeMillis
      }

      BUILD_FINISHED -> {
        state.buildResult = event.finished.exitCode.code
      }

      else -> Unit
    }
  }
  // If stream is empty, it means that service failed to retrieve any blaze build event from build
  // event stream. This should not happen if a build start correctly.
  if (emptyBuildEventStream) {
    throw BuildEventStreamProvider.BuildEventStreamException("No build events found")
  }
  return state
}

private fun getFileSets(group: BuildEventStreamProtos.OutputGroup, interner: Interner<String>): List<String> {
  return group.fileSetsList.map { interner.intern(it.id) }
}

/**
 * Only top-level targets have configuration mnemonic, producing target, and output group data
 * explicitly provided in BEP. This method fills in that data for the transitive closure.
 */
private fun fillInTransitiveFileSetData(
  namedFileSets: FileSets,
  data: OutputGroupTargetConfigFileSetMap,
  startTimeMillis: Long,
): ImmutableMap<String, ParsedBepOutput.Legacy.FileSet> {
  val fileSets = namedFileSets.toImmutableMap().mapValues { FileSetBuilder().apply { namedSet = it.value } }
  val topLevelFileSets = HashSet<String>()
  data.fileSetStream().forEach { entry ->
    entry.fileSetNames.forEach { fileSetName ->
      val fileSet = fileSets[fileSetName] ?: error("fileSet $fileSetName not found")
      fileSet.apply {
        configId = entry.config
        outputGroups.add(entry.outputGroup)
        targets.add(entry.target)
      }
      topLevelFileSets.add(fileSetName)
    }
  }

  val toVisit = Queues.newArrayDeque(topLevelFileSets)
  val visited: MutableSet<String> = HashSet(topLevelFileSets)
  while (!toVisit.isEmpty()) {
    val setId = toVisit.remove()
    val fileSet = fileSets[setId] ?: continue
    fileSet.namedSet
      ?.fileSetsList
      ?.map { it.id }
      ?.filter { !visited.contains(it) }
      ?.forEach { child ->
        fileSets[child]?.updateFromParent(fileSet)
        toVisit.add(child)
        visited.add(child)
      }
  }
  return ImmutableMap.copyOf(fileSets.filter { it.value.isValid() }.mapValues { it.value.build(startTimeMillis) })
}

/** Returns a copy of a {@link BuildEventStreamProtos.NamedSetOfFiles} with interned string references. */
private fun internNamedSet(
  namedSet: BuildEventStreamProtos.NamedSetOfFiles,
  interner: Interner<String>,
): BuildEventStreamProtos.NamedSetOfFiles {
  return namedSet.toBuilder()
    .clearFiles()
    .addAllFiles(
      namedSet.filesList.asSequence()
        .map { file ->

          file.toBuilder()
            .setUri(interner.intern(file.getUri()))
            .setName(interner.intern(file.getName()))
            .clearPathPrefix()
            .addAllPathPrefix(file.getPathPrefixList().map(interner::intern))
            .build()
        }.toList()
    )

    .build()
}


private fun parseFiles(namedSet: BuildEventStreamProtos.NamedSetOfFiles, startTimeMillis: Long): Sequence<OutputArtifact> {
  return namedSet.filesList.asSequence()
    .mapNotNull { OutputArtifactParser.parseArtifact(it, startTimeMillis) }
}

