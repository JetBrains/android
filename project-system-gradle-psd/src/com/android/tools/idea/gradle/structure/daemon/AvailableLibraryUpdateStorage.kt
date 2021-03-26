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

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec
import com.android.tools.idea.gradle.structure.model.PsLibraryKey
import com.android.tools.idea.gradle.structure.model.repositories.search.FoundArtifact
import com.android.tools.idea.gradle.structure.model.toLibraryKey
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
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

  fun findUpdatedVersionFor(spec: PsArtifactDependencySpec): GradleVersion? {
    lock.withLock {
      val version = spec.version.takeUnless { it.isNullOrEmpty() } ?: return null
      val parsedVersion = GradleVersion.tryParse(version) ?: return null
      val key = spec.toLibraryKey()
      val update = updatesByKey[key] ?: return null
      val foundVersion =
        GradleVersion.tryParse(
          (if (parsedVersion.isPreview) update.stableOrPreviewVersion else update.stableVersion) ?: return null) ?: return null
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
