/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.importer

import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.util.toIoFile
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.InvalidPathException
import kotlin.streams.toList

private const val PREFERENCE_LAST_SELECTED_DIRECTORY = "resourceExplorer.lastChosenDirectory"
/**
 * Returns a flat list of all the actual files (and not directory) within the hierarchy of this file.
 */
fun File.getAllLeafFiles(): List<File> {
  return Files.walk(toPath(), 10, FileVisitOption.FOLLOW_LINKS)
    .filter { file -> Files.isRegularFile(file) }
    .map { it.toFile() }
    .toList()
}

/**
 * Creates [DesignAssetSet] from the list of [files] using the available
 * [DesignAssetImporter] provided by [importersProvider].
 */
fun toDesignAssetSets(files: List<File>, importersProvider: ImportersProvider) =
  files
    .groupBy({ importersProvider.getImportersForExtension(it.extension) }, { it })
    .filter { (importers, _) -> importers.isNotEmpty() }
    .flatMap { (importers, files) -> importers[0].processFiles(files) }
    .groupBy { it.name }
    .map { (name, assets) -> DesignAssetSet(name, assets) }

/**
 * Displays a file picker which filters files depending on the files supported by the [DesignAssetImporter]
 * provided by the [importersProvider]. When files have been chosen, the [fileChosenCallback] is invoked with
 * the files converted into DesignAssetSet.
 */
fun chooseDesignAssets(importersProvider: ImportersProvider, fileChosenCallback: (List<DesignAssetSet>) -> Unit) {
  val lastChosenDirFile: VirtualFile? = PropertiesComponent.getInstance().getValue(PREFERENCE_LAST_SELECTED_DIRECTORY)?.let {
    try {
      VfsUtil.findFile(File(it).toPath(), true)
    }
    catch (ex: InvalidPathException) {
      null
    }
  }
  val fileChooserDescriptor = createFileDescriptor(importersProvider)
  FileChooserFactory.getInstance().createPathChooser(fileChooserDescriptor, null, null).choose(lastChosenDirFile) { selectedFiles ->
    fileChosenCallback(recursivelyFindAllDesignAssets(importersProvider, selectedFiles))
    PropertiesComponent.getInstance().setValue(PREFERENCE_LAST_SELECTED_DIRECTORY, selectedFiles.firstOrNull()?.path)
  }
}

/**
 * Recursively parse the hierarchy of the given [virtualFiles] and convert leaf files of the hierarchy into [DesignAssetSet].
 */
private fun recursivelyFindAllDesignAssets(importersProvider: ImportersProvider, virtualFiles: List<VirtualFile>): List<DesignAssetSet> {
  val files = virtualFiles
    .map(VirtualFile::toIoFile)
    .flatMap(File::getAllLeafFiles)
  return toDesignAssetSets(files, importersProvider)
}

/**
 * Create a [FileChooserDescriptor] from the available [DesignAssetImporter] provided by the ImportersProvider.
 */
private fun createFileDescriptor(importersProvider: ImportersProvider): FileChooserDescriptor {
  val supportedFileTypes = importersProvider.supportedFileTypes
  return FileChooserDescriptor(true, true, false, false, false, true)
    .withFileFilter { it.extension in supportedFileTypes }
}