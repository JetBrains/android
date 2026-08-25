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
package com.google.idea.blaze.base.run

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.google.idea.blaze.base.qsync.QuerySyncManager
import com.google.idea.blaze.common.AtomicFileWriter
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.qsync.deps.ArtifactDirectories
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import org.jetbrains.annotations.TestOnly

class DeployedApplicationTargetStoreImpl(
  private val applicationIdsDirectory: Path,
  private val knownTargetsSupplier: () -> Set<Label>?,
  private val modificationTracker: ModificationTracker,
) : DeployedApplicationTargetStore {

  @Suppress("unused")
  constructor(
    project: Project
  ) : this(
    Path.of(requireNotNull(project.basePath)).resolve(ArtifactDirectories.APPLICATION_IDS.relativePath),
    knownTargetsSupplier = { getKnownTargets(project) },
    modificationTracker = QuerySyncManager.getInstance(project).projectModificationTracker,
  )

  private val mappingsFile: Path
    get() = applicationIdsDirectory.resolve(MAPPINGS_FILE_NAME)

  private val lock = Any()
  private val targetToAppId: BiMap<Label, String> by lazy {
    synchronized(lock) {
      loadMappings()
    }
  }

  private fun loadMappings(): BiMap<Label, String> {
    val mappings = HashBiMap.create<Label, String>()
    if (!mappingsFile.isRegularFile()) {
      return mappings
    }
    return try {
      for (line in mappingsFile.readLines(StandardCharsets.UTF_8)) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
          continue
        }
        val parts = trimmed.split(WHITESPACE_REGEX, limit = 2)
        if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
          val target = Label.of(parts[0])
          val appId = parts[1]
          mappings.forcePut(target, appId)
        }
      }
      mappings
    } catch (e: IOException) {
      logger.error("Failed to read application ID mappings from $mappingsFile", e)
      mappings
    }
  }

  private var lastModificationCount: Long = -1L

  private fun cleanUpStaleTargetsIfNeeded() {
    val currentModCount = modificationTracker.modificationCount
    if (currentModCount != lastModificationCount) {
      val knownTargets = knownTargetsSupplier()
      if (knownTargets != null) {
        val changed = targetToAppId.keys.retainAll(knownTargets)
        if (changed) {
          saveMappings()
        }
        lastModificationCount = currentModCount
      }
    }
  }

  override fun trackTargetForApplication(applicationId: String, target: Label) {
    synchronized(lock) {
      cleanUpStaleTargetsIfNeeded()
      targetToAppId.forcePut(target, applicationId)
      saveMappings()
    }
  }

  private fun saveMappings() {
    try {
      if (!applicationIdsDirectory.exists()) {
        Files.createDirectories(applicationIdsDirectory)
      }
      AtomicFileWriter.create(mappingsFile).use { writer ->
        val sortedEntries = targetToAppId.entries.sortedBy { it.key.toString() }
        val output = buildString {
          for ((target, appId) in sortedEntries) {
            append(target.toString())
            append(' ')
            append(appId)
            append('\n')
          }
        }
        writer.outputStream.write(output.toByteArray(StandardCharsets.UTF_8))
        writer.onWriteComplete()
      }
    } catch (e: IOException) {
      logger.error("Failed to write application ID mappings to $mappingsFile", e)
    }
  }

  override fun getTargetForApplication(applicationId: String): Label? {
    synchronized(lock) {
      cleanUpStaleTargetsIfNeeded()
      return targetToAppId.inverse()[applicationId]
    }
  }

  override fun getAllApplicationIds(): Set<String> {
    synchronized(lock) {
      cleanUpStaleTargetsIfNeeded()
      return targetToAppId.values.toSet()
    }
  }

  companion object {
    private const val MAPPINGS_FILE_NAME = "application_ids.txt"
    private val WHITESPACE_REGEX = "\\s+".toRegex()
    private val logger = Logger.getInstance(DeployedApplicationTargetStoreImpl::class.java)

    private fun getKnownTargets(project: Project): Set<Label>? {
      val graph = QuerySyncManager.getInstance(project).currentBuildGraphdata ?: return null
      return graph.allLoadedTargets().map { it.label() }.toSet()
    }

    @TestOnly
    fun createForTest(
      applicationIdsDirectory: Path,
      knownTargetsSupplier: () -> Set<Label>? = { null },
      modificationTracker: ModificationTracker = SimpleModificationTracker(),
    ): DeployedApplicationTargetStoreImpl =
      DeployedApplicationTargetStoreImpl(applicationIdsDirectory, knownTargetsSupplier, modificationTracker)
  }
}
