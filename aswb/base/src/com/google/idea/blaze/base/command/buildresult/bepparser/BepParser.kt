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
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.IdCase.ACTION_COMPLETED
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.IdCase.BUILD_FINISHED
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.IdCase.NAMED_SET
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.IdCase.STARTED
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.IdCase.TARGET_COMPLETED
import com.google.idea.blaze.common.artifact.OutputArtifact

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
 * A record of the top level file sets output by the given {@link #outputGroup}, {@link #target} and {@link #config}.
 */
private data class OutputGroupTargetConfigFileSets(
  val outputGroup: String, val target: String, val config: String, val fileSetNames: List<String>,
)

/**
 * A data structure allowing to associate file set names with output group, targets and configs and allowing to retrieve them efficiently
 * at each level of the hierarchy.
 *
 * For each config, the file set names are stored as <aspect, List<file set name>> map to avoid duplicate file set names for the same
 * config but different aspects. While retrieving, a flatmap for the given config is returned.
 */
private class OutputGroupTargetConfigFileSetMap {
  private val data: MutableMap<String, MutableMap<String, MutableMap<String, MutableMap<String, List<String>>>>> = mutableMapOf()

  private fun getOutputGroup(outputGroup: String): MutableMap<String, MutableMap<String, MutableMap<String, List<String>>>> {
    return data.computeIfAbsent(outputGroup) { mutableMapOf() }
  }

  private fun getOutputGroupTarget(outputGroup: String, target: String): MutableMap<String, MutableMap<String, List<String>>> {
    return getOutputGroup(outputGroup).computeIfAbsent(target) { mutableMapOf() }
  }

  private fun getOutputGroupTargetConfig(outputGroup: String, target: String, config: String): MutableMap<String, List<String>> {
    return getOutputGroupTarget(outputGroup, target).computeIfAbsent(config){mutableMapOf()}
  }

  fun setOutputGroupTargetConfigAspect(outputGroup: String, target: String, config: String, aspect: String, fileSetNames: List<String>) {
    val previous = getOutputGroupTargetConfig(outputGroup, target, config).put(aspect, fileSetNames.toList())
    if (previous != null) {
      error("$outputGroup:$target:$config already present")
    }
  }

  fun fileSetStream(): Sequence<OutputGroupTargetConfigFileSets> {
    return data.entries.asSequence().flatMap { outputGroup ->
      outputGroup.value.entries.asSequence().flatMap { target ->
        target.value.entries.asSequence().map { config ->
          OutputGroupTargetConfigFileSets(outputGroup.key, target.key,
                                          config.key, config.value.entries.flatMap { it.value })
        }
      }
    }
  }

  fun outputGroupFileSetStream(outputGroup: String): Sequence<OutputGroupTargetConfigFileSets> {
    val outputGroupData = data[outputGroup] ?: return emptySequence()
    return outputGroupData.entries.asSequence().flatMap { target ->
      target.value.entries.asSequence().map { config ->
        OutputGroupTargetConfigFileSets(outputGroup, target.key,
                                        config.key, config.value.entries.flatMap { it.value })
      }
    }
  }

  fun outputGroupTargetFileSetStream(outputGroup: String, target: String): Sequence<OutputGroupTargetConfigFileSets> {
    val outputGroupData = data[outputGroup] ?: return emptySequence()
    val outputGroupTargetData = outputGroupData[target] ?: return emptySequence()
    return outputGroupTargetData.entries.asSequence().map { config ->
      OutputGroupTargetConfigFileSets(outputGroup, target,
                                      config.key, config.value.entries.flatMap { it.value })
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
        val aspect = event.id.targetCompleted.aspect

        for (o in event.completed.outputGroupList) {
          val fileSetNames = getFileSets(o, interner)
          state.outputs.setOutputGroupTargetConfigAspect(
            interner.intern(o.name),
            interner.intern(label),
            interner.intern(configId),
            interner.intern(aspect),
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

