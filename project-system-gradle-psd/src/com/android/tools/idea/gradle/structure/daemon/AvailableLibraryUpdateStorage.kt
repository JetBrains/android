/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.daemon

import com.android.ide.common.gradle.Version
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec
import com.android.tools.idea.gradle.structure.model.PsLibraryKey
import com.android.tools.idea.gradle.repositories.search.FoundArtifact
import com.android.tools.idea.gradle.structure.model.toLibraryKey
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.util.xmlb.annotations.XCollection.Style.v2
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Stores available library updates to disk. These stored updates are displayed to the user in the "Project Structure" dialog, until the
 * next scheduled check for updates is executed or until the user manually triggers a check for updates.
 */
@State(name = "AvailableLibraryUpdateStorage", storages = [Storage(StoragePathMacros.CACHE_FILE)])
class AvailableLibraryUpdateStorage : PersistentStateComponent<AvailableLibraryUpdateStorage.AvailableLibraryUpdates> {
  private val lock: Lock = ReentrantLock()
  private val updatesByKey = mutableMapOf<PsLibraryKey, AvailableLibraryUpdate>()
  private var myState = AvailableLibraryUpdates()

  override fun getState(): AvailableLibraryUpdates = lock.withLock { myState }

  override fun loadState(state: AvailableLibraryUpdates) {
    lock.withLock {
      myState = state
      index()
    }
  }

  fun addOrUpdate(artifact: FoundArtifact, timestamp: Long) {
    lock.withLock {
      val updateKey = PsLibraryKey(artifact.groupId, artifact.name)
      updatesByKey[updateKey]?.let { myState.updates.remove(it) }

      val update = AvailableLibraryUpdate().apply {
        groupId = artifact.groupId
        name = artifact.name
        stableVersion = artifact.versions.firstOrNull { !it.isPreview }?.toString()
        stableOrPreviewVersion = artifact.versions.firstOrNull()?.toString()
        versions = artifact.versions.map { it.toString() }.toMutableList()
        repository = artifact.repositoryNames.joinToString(",")
        lastSearchTimeMillis = timestamp
      }
      myState.updates.add(update)
      updatesByKey[updateKey] = update
    }
  }

  fun retainAll(predicate: (AvailableLibraryUpdate) -> Boolean): List<AvailableLibraryUpdate> {
    return lock.withLock {
      myState.updates.retainAll(predicate)
      index()
      myState.updates.toList()
    }
  }

  fun findUpdatedVersionFor(spec: PsArtifactDependencySpec): Version? {
    fun findUpdatedVersionForGuava(update: AvailableLibraryUpdate, version: String): Version? {
      val versions = update.versions
      if (versions.isEmpty()) return null
      val parsedVersion = Version.parse(version)
      return when {
        version.endsWith("-jre") -> versions.firstOrNull { it.endsWith("-jre") }
        version.endsWith("-android") -> versions.firstOrNull { it.endsWith("-android") }
        else -> throw(IllegalStateException("version ends with neither -jre nor -android: $version"))
      }?.let { Version.parse(it) }?.takeIf { it > parsedVersion }
    }
    fun findUpdatedNativeMtVersionForKotlinxCoroutines(update: AvailableLibraryUpdate, version: String): Version? {
      val versions = update.versions
      if (versions.isEmpty()) return null
      val parsedVersion = Version.parse(version)
      return when {
        version.contains("-native-mt-2") -> versions.firstOrNull { it.contains("-native-mt-2") }
        version.contains("-native-mt") -> versions.firstOrNull { it.contains("-native-mt") && !it.contains("-native-mt-2") }
        else -> throw(IllegalStateException("version is not a -native-mt version: $version"))
      }?.let { Version.parse(it) }?.takeIf { it > parsedVersion }
    }
    lock.withLock {
      val version = spec.version.takeUnless { it.isNullOrEmpty() } ?: return null
      val key = spec.toLibraryKey()
      val update = updatesByKey[key] ?: return null
      if (key.group == "com.google.guava" && (version.endsWith("-jre") || version.endsWith("-android"))) {
        return findUpdatedVersionForGuava(update, version)
      }
      var stableOrPreviewVersion = update.stableOrPreviewVersion
      if (key.group == "org.jetbrains.kotlinx" && key.name.contains("kotlinx-coroutines")) {
        if (version.contains("-native-mt")) {
          return findUpdatedNativeMtVersionForKotlinxCoroutines(update, version)
        }
        else {
          stableOrPreviewVersion = update.versions.firstOrNull { !it.contains("-native-mt") }
        }
      }
      val parsedVersion = Version.parse(version)
      val infimum = parsedVersion.previewInfimum
      val supremum = parsedVersion.previewSupremum
      val suggestPreview = when {
        parsedVersion.major == null -> false
        infimum == null || supremum == null -> false
        stableOrPreviewVersion == null -> false
        Version.parse(stableOrPreviewVersion).let { infimum < it && it < supremum } -> true
        else -> false
      }
      val updateString = (if (suggestPreview) update.stableOrPreviewVersion else update.stableVersion) ?: return null
      val foundVersion = Version.parse(updateString)
      return if (foundVersion > parsedVersion) foundVersion else null
    }
  }

  private fun index() {
    updatesByKey.clear()
    myState.updates.forEach { updatesByKey[it.toLibraryKey()] = it }
  }

  class AvailableLibraryUpdates {
    @XCollection(propertyElementName = "library-updates")
    var updates: MutableList<AvailableLibraryUpdate> = mutableListOf()
  }

  @Tag("library-update")
  data class AvailableLibraryUpdate(
    @Tag("group-id") var groupId: String? = null,
    @Tag("name") var name: String? = null,
    @Tag("stableOrPreviewVersion") var stableOrPreviewVersion: String? = null,
    @Tag("stableVersion") var stableVersion: String? = null,
    @XCollection(propertyElementName = "versions", style = v2, elementName = "version")
    var versions: MutableList<String> = mutableListOf(),
    @Tag("repository") var repository: String? = null,
    @Tag("last-search-timestamp") var lastSearchTimeMillis: Long = -1L
  ) {
    fun toLibraryKey() = PsLibraryKey(groupId.orEmpty(), name.orEmpty())
  }

  companion object {
    fun getInstance(project: Project): AvailableLibraryUpdateStorage {
      return project.getService(AvailableLibraryUpdateStorage::class.java)
    }
  }
}
