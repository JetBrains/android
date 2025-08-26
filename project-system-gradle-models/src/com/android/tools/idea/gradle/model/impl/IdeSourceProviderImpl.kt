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

import com.android.tools.idea.gradle.model.IdeSourceProvider
import java.io.File
import java.io.Serializable

data class IdeSourceProviderImpl private constructor(
  private val nameField: String,
  private val folderField: FileImpl?,
  private val manifestFileField: FileImpl?,
  private val javaDirectoriesField: List<FileImpl>,
  private val kotlinDirectoriesField: List<FileImpl>,
  private val resourcesDirectoriesField: List<FileImpl>,
  private val aidlDirectoriesField: List<FileImpl>,
  private val renderscriptDirectoriesField: List<FileImpl>,
  private val resDirectoriesField: List<FileImpl>,
  private val assetsDirectoriesField: List<FileImpl>,
  private val jniLibsDirectoriesField: List<FileImpl>,
  private val shadersDirectoriesField: List<FileImpl>,
  private val mlModelsDirectoriesField: List<FileImpl>,
  private val customSourceDirectoriesField: List<IdeCustomSourceDirectoryImpl>,
  private val baselineProfileDirectoriesField: List<FileImpl>,
) : Serializable, IdeSourceProvider {

  constructor(
    name: String,
    folder: File?,
    manifestFile: File?,
    javaDirectories: List<File>,
    kotlinDirectories: List<File>,
    resourcesDirectories: List<File>,
    aidlDirectories: List<File>,
    renderscriptDirectories: List<File>,
    resDirectories: List<File>,
    assetsDirectories: List<File>,
    jniLibsDirectories: List<File>,
    shadersDirectories: List<File>,
    mlModelsDirectories: List<File>,
    customSourceDirectories: List<IdeCustomSourceDirectoryImpl>,
    baselineProfileDirectories: List<File>
  ) : this(
    nameField = name,
    folderField = folder?.toImpl(),
    manifestFileField = manifestFile?.toImpl(),
    javaDirectoriesField = javaDirectories.toImpl(),
    kotlinDirectoriesField = kotlinDirectories.toImpl(),
    resourcesDirectoriesField = resourcesDirectories.toImpl(),
    aidlDirectoriesField = aidlDirectories.toImpl(),
    renderscriptDirectoriesField = renderscriptDirectories.toImpl(),
    resDirectoriesField = resDirectories.toImpl(),
    assetsDirectoriesField = assetsDirectories.toImpl(),
    jniLibsDirectoriesField = jniLibsDirectories.toImpl(),
    shadersDirectoriesField = shadersDirectories.toImpl(),
    mlModelsDirectoriesField = mlModelsDirectories.toImpl(),
    customSourceDirectoriesField = customSourceDirectories,
    baselineProfileDirectoriesField = baselineProfileDirectories.toImpl(),
  )

  constructor(
    name: String,
    folder: File?,
    manifestFile: String?,
    javaDirectories: List<String>,
    kotlinDirectories: List<String>,
    resourcesDirectories: List<String>,
    aidlDirectories: List<String>,
    renderscriptDirectories: List<String>,
    resDirectories: List<String>,
    assetsDirectories: List<String>,
    jniLibsDirectories: List<String>,
    shadersDirectories: List<String>,
    mlModelsDirectories: List<String>,
    customSourceDirectories: List<IdeCustomSourceDirectoryImpl>,
    baselineProfileDirectories: List<String>
  ) : this(
    name,
    folder,
    manifestFile?.translate(folder),
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
    folderField = FileImpl(""),
    manifestFileField = null,
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
    javaDirectories: List<File> = emptyList(),
    kotlinDirectories: List<File> = emptyList(),
    resourcesDirectories: List<File> = emptyList(),
    aidlDirectories: List<File> = emptyList(),
    renderscriptDirectories: List<File> = emptyList(),
    resDirectories: List<File> = emptyList(),
    assetsDirectories: List<File> = emptyList(),
    jniLibsDirectories: List<File> = emptyList(),
    shadersDirectories: List<File> = emptyList(),
    mlModelsDirectories: List<File> = emptyList(),
    baselineProfileDirectories: List<File> = emptyList(),
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
  override val manifestFile: FileImpl? get() = manifestFileField
  override val javaDirectories: List<FileImpl> get() = javaDirectoriesField
  override val kotlinDirectories: List<FileImpl> get() = kotlinDirectoriesField
  override val resourcesDirectories: List<FileImpl> get() = resourcesDirectoriesField
  override val aidlDirectories: List<FileImpl> get() = aidlDirectoriesField
  override val renderscriptDirectories: List<FileImpl> get() = renderscriptDirectoriesField
  override val resDirectories: List<FileImpl> get() = resDirectoriesField
  override val assetsDirectories: List<FileImpl> get() = assetsDirectoriesField
  override val jniLibsDirectories: List<FileImpl> get() = jniLibsDirectoriesField
  override val shadersDirectories: List<FileImpl> get() = shadersDirectoriesField
  override val mlModelsDirectories: List<FileImpl> get() = mlModelsDirectoriesField
  override val customSourceDirectories: List<IdeCustomSourceDirectoryImpl>
    get() = customSourceDirectoriesField
  override val baselineProfileDirectories: List<FileImpl>
    get() = baselineProfileDirectoriesField
}

private fun normalize(folder: File?, file: File): FileImpl = (if (folder != null) file.relativeToOrSelf(folder).path else file.path).translate(folder)

private fun String.translate(folder: File?): FileImpl = (folder?.resolve(this) ?: FileImpl(this)).normalize().toImpl()

private fun List<String>.translate(folder: File?): List<FileImpl> = map { it.translate(folder) }
