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

data class IdeSourceProviderImpl private constructor(
  private val nameField: String,
  private val folderField: File?,
  private val manifestFileField: File,
  private val javaDirectoriesField: Collection<File>,
  private val kotlinDirectoriesField: Collection<File>,
  private val resourcesDirectoriesField: Collection<File>,
  private val aidlDirectoriesField: Collection<File>,
  private val renderscriptDirectoriesField: Collection<File>,
  private val resDirectoriesField: Collection<File>,
  private val assetsDirectoriesField: Collection<File>,
  private val jniLibsDirectoriesField: Collection<File>,
  private val shadersDirectoriesField: Collection<File>,
  private val mlModelsDirectoriesField: Collection<File>,
  private val customSourceDirectoriesField: Collection<IdeCustomSourceDirectory>,
  private val baselineProfileDirectoriesField: Collection<File>,
) : Serializable, IdeSourceProvider {

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
  ) : this(
    name,
    folder,
    manifestFile.translate(folder),
    javaDirectories.translate(folder),
    kotlinDirectories.translate(folder),
    resourcesDirectories.translate(folder),
    aidlDirectories.translate(folder),
    renderscriptDirectories.translate(folder),
    resDirectories.translate(folder),
    assetsDirectories.translate(folder),
    jniLibsDirectories.translate(folder),
    shadersDirectories.translate(folder),
    mlModelsDirectories.translate(folder),
    customSourceDirectories,
    baselineProfileDirectories.translate(folder),
  )

  // Used for serialization by the IDE.
  constructor() : this(
    nameField = "",
    folderField = File(""),
    manifestFileField = File(""),
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
    javaDirectoriesField = javaDirectoriesField + javaDirectories.map { normalize(folderField, it) },
    kotlinDirectoriesField = kotlinDirectoriesField + kotlinDirectories.map { normalize(folderField, it) },
    resourcesDirectoriesField = resourcesDirectoriesField + resourcesDirectories.map { normalize(folderField, it) },
    aidlDirectoriesField = aidlDirectoriesField + aidlDirectories.map { normalize(folderField, it) },
    renderscriptDirectoriesField = renderscriptDirectoriesField + renderscriptDirectories.map { normalize(folderField, it) },
    resDirectoriesField = resDirectoriesField + resDirectories.map { normalize(folderField, it) },
    assetsDirectoriesField = assetsDirectoriesField + assetsDirectories.map { normalize(folderField, it) },
    jniLibsDirectoriesField = jniLibsDirectoriesField + jniLibsDirectories.map { normalize(folderField, it) },
    shadersDirectoriesField = shadersDirectoriesField + shadersDirectories.map { normalize(folderField, it) },
    mlModelsDirectoriesField = mlModelsDirectoriesField + mlModelsDirectories.map { normalize(folderField, it) },
    baselineProfileDirectoriesField = baselineProfileDirectoriesField + baselineProfileDirectories.map { normalize(folderField, it) },
  )


  override val name: String get() = nameField
  override val manifestFile: File get() = manifestFileField
  override val javaDirectories: Collection<File> get() = javaDirectoriesField
  override val kotlinDirectories: Collection<File> get() = kotlinDirectoriesField
  override val resourcesDirectories: Collection<File> get() = resourcesDirectoriesField
  override val aidlDirectories: Collection<File> get() = aidlDirectoriesField
  override val renderscriptDirectories: Collection<File> get() = renderscriptDirectoriesField
  override val resDirectories: Collection<File> get() = resDirectoriesField
  override val assetsDirectories: Collection<File> get() = assetsDirectoriesField
  override val jniLibsDirectories: Collection<File> get() = jniLibsDirectoriesField
  override val shadersDirectories: Collection<File> get() = shadersDirectoriesField
  override val mlModelsDirectories: Collection<File> get() = mlModelsDirectoriesField
  override val customSourceDirectories: Collection<IdeCustomSourceDirectory>
    get() = customSourceDirectoriesField
  override val baselineProfileDirectories: Collection<File>
    get() = baselineProfileDirectoriesField
}

private fun normalize(folder: File?, file: File): File = (if (folder != null) file.relativeToOrSelf(folder).path else file.path).translate(folder)

private fun String.translate(folder: File?): File = (folder?.resolve(this) ?: File(this)).normalize()

private fun Collection<String>.translate(folder: File?): Collection<File> = map { it.translate(folder) }
