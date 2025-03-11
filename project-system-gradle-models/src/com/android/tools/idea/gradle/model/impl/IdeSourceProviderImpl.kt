/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.model.impl

import com.android.tools.idea.gradle.model.IdeCustomSourceDirectory
import com.android.tools.idea.gradle.model.IdeSourceProvider
import java.io.File
import java.io.Serializable

data class IdeSourceProviderImpl(
  private val nameField: String,
  private val folderField: File?,
  private val manifestFileField: String,
  private val javaDirectoriesField: Collection<String>,
  private val kotlinDirectoriesField: Collection<String>,
  private val resourcesDirectoriesField: Collection<String>,
  private val aidlDirectoriesField: Collection<String>,
  private val renderscriptDirectoriesField: Collection<String>,
  private val resDirectoriesField: Collection<String>,
  private val assetsDirectoriesField: Collection<String>,
  private val jniLibsDirectoriesField: Collection<String>,
  private val shadersDirectoriesField: Collection<String>,
  private val mlModelsDirectoriesField: Collection<String>,
  private val customSourceDirectoriesField: Collection<IdeCustomSourceDirectory>,
  private val baselineProfileDirectoriesField: Collection<String>,
) : Serializable, IdeSourceProvider {
  private fun String.translate(): File = (folderField?.resolve(this) ?: File(this)).normalize()
  private fun Collection<String>.translate(): Collection<File> = map { it.translate() }

  constructor(
    name: String,
    folder: File?,
    manifestFile: String,
    javaDirectories: Collection<String>,
    kotlinDirectories: Collection<String>,
    resourcesDirectories: Collection<String>,
    aidlDirectories: Collection<String>,
    renderscriptDirectories: Collection<String>,
    resDirectories: Collection<String>,
    assetsDirectories: Collection<String>,
    jniLibsDirectories: Collection<String>,
    shadersDirectories: Collection<String>,
    mlModelsDirectories: Collection<String>,
    customSourceDirectories: Collection<IdeCustomSourceDirectory>,
    baselineProfileDirectories: Collection<String>,
    unused: String = "" // just to give it a different signature
  ) : this (
    name,
    folder,
    manifestFile,
    javaDirectories,
    kotlinDirectories,
    resourcesDirectories,
    aidlDirectories,
    renderscriptDirectories,
    resDirectories,
    assetsDirectories,
    jniLibsDirectories,
    shadersDirectories,
    mlModelsDirectories,
    customSourceDirectories,
    baselineProfileDirectories
 )

  // Used for serialization by the IDE.
  constructor() : this(
    nameField = "",
    folderField = File(""),
    manifestFileField = "",
    javaDirectoriesField = mutableListOf(),
    kotlinDirectoriesField = mutableListOf(),
    resourcesDirectoriesField = mutableListOf(),
    aidlDirectoriesField = mutableListOf(),
    renderscriptDirectoriesField = mutableListOf(),
    resDirectoriesField = mutableListOf(),
    assetsDirectoriesField = mutableListOf(),
    jniLibsDirectoriesField = mutableListOf(),
    shadersDirectoriesField = mutableListOf(),
    mlModelsDirectoriesField = mutableListOf(),
    customSourceDirectoriesField = mutableListOf(),
    baselineProfileDirectoriesField = mutableListOf(),
  )

  fun appendDirectories(
    javaDirectories: Collection<File> = emptyList(),
    kotlinDirectories: Collection<File> = emptyList(),
    resourcesDirectories: Collection<File> = emptyList(),
    aidlDirectories: Collection<File> = emptyList(),
    renderscriptDirectories: Collection<File> = emptyList(),
    resDirectories: Collection<File> = emptyList(),
    assetsDirectories: Collection<File> = emptyList(),
    jniLibsDirectories: Collection<File> = emptyList(),
    shadersDirectories: Collection<File> = emptyList(),
    mlModelsDirectories: Collection<File> = emptyList(),
    baselineProfileDirectories: Collection<File> = emptyList(),
  ): IdeSourceProviderImpl = copy(
    javaDirectoriesField = javaDirectoriesField + javaDirectories.map { normalize(it) },
    kotlinDirectoriesField = kotlinDirectoriesField + kotlinDirectories.map { normalize(it) },
    resourcesDirectoriesField = resourcesDirectoriesField + resourcesDirectories.map { normalize(it) },
    aidlDirectoriesField = aidlDirectoriesField + aidlDirectories.map { normalize(it) },
    renderscriptDirectoriesField = renderscriptDirectoriesField + renderscriptDirectories.map { normalize(it) },
    resDirectoriesField = resDirectoriesField + resDirectories.map { normalize(it) },
    assetsDirectoriesField = assetsDirectoriesField + assetsDirectories.map { normalize(it) },
    jniLibsDirectoriesField = jniLibsDirectoriesField + jniLibsDirectories.map { normalize(it) },
    shadersDirectoriesField = shadersDirectoriesField + shadersDirectories.map { normalize(it) },
    mlModelsDirectoriesField = mlModelsDirectoriesField + mlModelsDirectories.map { normalize(it) },
    baselineProfileDirectoriesField = baselineProfileDirectoriesField + baselineProfileDirectories.map { normalize(it) },
  )

  private fun normalize(it: File) = if (folderField != null) it.relativeToOrSelf(folderField).path else it.path

  override val name: String get() = nameField
  override val manifestFile: File get() = manifestFileField.translate()
  override val javaDirectories: Collection<File> get() = javaDirectoriesField.translate()
  override val kotlinDirectories: Collection<File> get() = kotlinDirectoriesField.translate()
  override val resourcesDirectories: Collection<File> get() = resourcesDirectoriesField.translate()
  override val aidlDirectories: Collection<File> get() = aidlDirectoriesField.translate()
  override val renderscriptDirectories: Collection<File> get() = renderscriptDirectoriesField.translate()
  override val resDirectories: Collection<File> get() = resDirectoriesField.translate()
  override val assetsDirectories: Collection<File> get() = assetsDirectoriesField.translate()
  override val jniLibsDirectories: Collection<File> get() = jniLibsDirectoriesField.translate()
  override val shadersDirectories: Collection<File> get() = shadersDirectoriesField.translate()
  override val mlModelsDirectories: Collection<File> get() = mlModelsDirectoriesField.translate()
  override val customSourceDirectories: Collection<IdeCustomSourceDirectory>
    get() = customSourceDirectoriesField
  override val baselineProfileDirectories: Collection<File>
    get() = baselineProfileDirectoriesField.translate()
}
