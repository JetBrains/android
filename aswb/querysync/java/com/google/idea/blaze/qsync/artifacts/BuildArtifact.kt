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
package com.google.idea.blaze.qsync.artifacts

import com.google.common.annotations.VisibleForTesting
import com.google.idea.blaze.common.Interners
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.qsync.java.artifacts.AspectProto
import com.intellij.util.containers.sequenceOfNotNull
import java.nio.file.Path

/**
 * An artifact produced by a build.
 *
 * This includes the digest of the artifact as indicated by bazel, its output path and the target that produced it.
 */
data class BuildArtifact
@JvmOverloads
constructor(
  val digest: String,
  val artifactPath: Path,
  val target: Label,
  val metadata: Map<Class<out ArtifactMetadata>, ArtifactMetadata> = mapOf(),
) {

  fun <T : ArtifactMetadata> getMetadata(ofType: Class<T>): T? {
    @Suppress("UNCHECKED_CAST")
    return metadata[ofType] as T?
  }

  fun withMetadata(metadata: List<ArtifactMetadata>): BuildArtifact {
    if (metadata.isEmpty()) return this
    return copy(metadata = metadata.associateBy { it.javaClass })
  }

  @VisibleForTesting
  fun withMetadata(vararg metadata: ArtifactMetadata): BuildArtifact {
    return withMetadata(metadata.toList())
  }

  val extension: String
    get() {
      val fileName = artifactPath.fileName.toString()
      val lastDot = fileName.lastIndexOf('.')
      if (lastDot == -1) {
        return ""
      }
      return fileName.substring(lastDot + 1)
    }

  companion object {
    fun fromProtos(paths: List<AspectProto.OutputArtifact>, digestMap: DigestMap, target: Label): List<BuildArtifact> {
      return paths.map { createFromProto(it, digestMap, target) }.flatMap { it }
    }

    private fun createFromProto(artifact: AspectProto.OutputArtifact, digestMap: DigestMap, target: Label): Sequence<BuildArtifact> {
      return when (artifact.getPathCase()) {
        AspectProto.OutputArtifact.PathCase.DIRECTORY ->
          digestMap.directoryContents(Interners.pathOf(artifact.getDirectory())).mapNotNull { digestMap.createBuildArtifact(it, target) }

        AspectProto.OutputArtifact.PathCase.FILE ->
          sequenceOfNotNull(digestMap.createBuildArtifact(Interners.pathOf(artifact.getFile()), target))

        AspectProto.OutputArtifact.PathCase.PATH_NOT_SET -> emptySequence()
      }
    }
  }
}

fun Iterable<BuildArtifact>.withMetadata(toAdd: Map<BuildArtifact, List<ArtifactMetadata>>): List<BuildArtifact> {
  return map { it.withMetadata(toAdd.get(it).orEmpty()) }
}
