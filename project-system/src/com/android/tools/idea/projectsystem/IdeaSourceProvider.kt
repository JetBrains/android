/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.projectsystem

import com.intellij.openapi.vfs.VirtualFile

/**
 * A provider representing all or a subset of the source code of a module grouped by source type (Java, resources, Android resources etc.)
 *
 * Each category is represented by a pair of collections: a collection of IDEA file urls (see [VirtualFile.getUrl]) and a collection of
 * [VirtualFile]'s. The first one represents all the configured files/directories including those that do not exist in the file system and
 * the second one is a subset that exists in the virtual file system.
 *
 * Items in the collections are ordered in the overlay order. (The exact overlaying/merging rules are source type specific).
 */
interface IdeaSourceProvider {
  val scopeType: ScopeType
  val manifestFileUrls: Iterable<String>
  val manifestFiles: Iterable<VirtualFile>

  /**
   * Parent directory urls of the manifest files listed in [manifestFileUrls].
   */
  val manifestDirectoryUrls: Iterable<String>

  /**
   * Existing in the file system parent directories of the manifest files listed in [manifestFileUrls] including files which themselves
   * do not exist in the file system.
   */
  val manifestDirectories: Iterable<VirtualFile>

  val javaDirectoryUrls: Iterable<String>
  val javaDirectories: Iterable<VirtualFile>

  val kotlinDirectoryUrls: Iterable<String>
  val kotlinDirectories: Iterable<VirtualFile>

  val resourcesDirectoryUrls: Iterable<String>
  val resourcesDirectories: Iterable<VirtualFile>

  val aidlDirectoryUrls: Iterable<String>
  val aidlDirectories: Iterable<VirtualFile>

  val renderscriptDirectoryUrls: Iterable<String>
  val renderscriptDirectories: Iterable<VirtualFile>

  val jniLibsDirectoryUrls: Iterable<String>
  val jniLibsDirectories: Iterable<VirtualFile>

  val resDirectoryUrls: Iterable<String>
  val resDirectories: Iterable<VirtualFile>

  val assetsDirectoryUrls: Iterable<String>
  val assetsDirectories: Iterable<VirtualFile>

  val shadersDirectoryUrls: Iterable<String>
  val shadersDirectories: Iterable<VirtualFile>

  val mlModelsDirectoryUrls: Iterable<String>
  val mlModelsDirectories: Iterable<VirtualFile>

  val baselineProfileDirectoryUrls: Iterable<String>
  val baselineProfileDirectories: Iterable<VirtualFile>

  interface Custom {
    val directoryUrls: Iterable<String>
    val directories: Iterable<VirtualFile>
  }

  /** Map of sourceTypeName to the source roots */
  val custom: Map<String, Custom>

}

/**
 * A named source provider that represents a subset of the source code of a module with a single manifest file (which may exist or not in
 * the file system).
 *
 * Note: [NamedIdeaSourceProvider] is currently used to represent Gradle source sets and will likely be made private to Gradle related
 * Android Studio modules.
 */
interface NamedIdeaSourceProvider : IdeaSourceProvider {
  val name: String
}

interface BuildConfigurationSourceProvider {
  interface ConfigurationFile {
    val displayName: String
    val file: VirtualFile
    val groupOrder: Int
  }

  fun getBuildConfigurationFiles(): List<ConfigurationFile>
  fun contains(file: VirtualFile): Boolean

  object EMPTY : BuildConfigurationSourceProvider {
    override fun getBuildConfigurationFiles(): List<ConfigurationFile> = emptyList()
    override fun contains(file: VirtualFile): Boolean = false
  }
}