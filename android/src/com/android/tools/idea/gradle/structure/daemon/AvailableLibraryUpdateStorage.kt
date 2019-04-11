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
import com.android.tools.idea.gradle.structure.model.repositories.search.FoundArtifact
import com.google.common.collect.Maps
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.Transient
import com.intellij.util.xmlb.annotations.XCollection

import com.intellij.openapi.util.text.StringUtil.isNotEmpty

/**
 * Stores available library updates to disk. These stored updates are displayed to the user in the "Project Structure" dialog, until the
 * next scheduled check for updates is executed or until the user manually triggers a check for updates.
 */
@State(name = "AvailableLibraryUpdateStorage", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class AvailableLibraryUpdateStorage : PersistentStateComponent<AvailableLibraryUpdateStorage.AvailableLibraryUpdates> {
  private var myState = AvailableLibraryUpdates()

  override fun getState(): AvailableLibraryUpdates = myState

  override fun loadState(state: AvailableLibraryUpdates) {
    myState = state
    myState.index()
  }

  class AvailableLibraryUpdates {
    @XCollection(propertyElementName = "library-updates")
    var updates: MutableList<AvailableLibraryUpdate> = mutableListOf()

    @Tag("last-search-timestamp")
    var lastSearchTimeMillis = -1L

    @Transient
    private val myUpdatesById = mutableMapOf<LibraryUpdateId, AvailableLibraryUpdate>()

    fun clear() {
      updates.clear()
      myUpdatesById.clear()
    }

    internal fun index() {
      myUpdatesById.clear()
      updates.forEach { this.index(it) }
    }

    fun add(artifact: FoundArtifact) {
      val update = AvailableLibraryUpdate()
      update.groupId = artifact.groupId
      update.name = artifact.name
      update.version = artifact.versions[0].toString()
      update.repository = artifact.repositoryNames.joinToString(",")
      updates.add(update)
      index(update)
    }

    private fun index(update: AvailableLibraryUpdate) {
      myUpdatesById[LibraryUpdateId(update.groupId.orEmpty(), update.name!!)] = update
    }

    fun findUpdateFor(spec: PsArtifactDependencySpec): AvailableLibraryUpdate? {
      val version = spec.version
      if (isNotEmpty(version)) {
        val parsedVersion = GradleVersion.tryParse(spec.version!!)
        if (parsedVersion != null) {
          val id = LibraryUpdateId(spec.group.orEmpty(), spec.name)
          val update = myUpdatesById[id]
          if (update != null) {
            val foundVersion = GradleVersion.parse(update.version!!)
            if (foundVersion.compareTo(parsedVersion) > 0) {
              return update
            }
          }
        }
      }
      return null
    }
  }

  @Tag("library-update")
  class AvailableLibraryUpdate {
    @Tag("group-id")
    var groupId: String? = null

    @Tag("name")
    var name: String? = null

    @Tag("version")
    var version: String? = null

    @Tag("repository")
    var repository: String? = null
  }

  companion object {
    fun getInstance(project: Project): AvailableLibraryUpdateStorage {
      return ServiceManager.getService(project, AvailableLibraryUpdateStorage::class.java)
    }
  }
}
