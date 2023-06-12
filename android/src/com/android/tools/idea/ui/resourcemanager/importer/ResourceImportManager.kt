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
package com.android.tools.idea.ui.resourcemanager.importer

import com.android.ide.common.resources.FileResourceNameValidator
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
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
import javax.swing.JComponent
import kotlin.streams.asSequence

/**
 * Constant used in the file chooser to save the last used location and re-open
 * it the next time the chooser is used.
 */
private const val PREFERENCE_LAST_SELECTED_DIRECTORY = "resourceExplorer.lastChosenDirectory"

/**
 * Maximum depth to walk to when recursively walking a file hierarchy
 */
private const val MAX_FILE_DEPTH = 10

/**
 * Returns a flat list of all the actual files (and not directory) within the hierarchy of this file.
 */
fun File.getAllLeafFiles(): Sequence<File> = Files.walk(toPath(), MAX_FILE_DEPTH, FileVisitOption.FOLLOW_LINKS)
  .asSequence()
  .filter { file -> Files.isRegularFile(file) }
  .map { it.toFile() }

/**
 * Transforms this [File] [Sequence] into a [DesignAsset] [Sequence] using the available.
 * [File]s that couldn't be converted into a [DesignAsset] are silently ignored.
 *
 * @see ImportersProvider
 * @see ImportersProvider.getImportersForExtension
 * @see com.android.tools.idea.ui.resourcemanager.plugin.ResourceImporter.processFile
 */
fun Sequence<File>.toDesignAsset(importersProvider: ImportersProvider): Sequence<DesignAsset> =
  mapNotNull { importersProvider.getImportersForExtension(it.extension).firstOrNull()?.processFile(it) }

/**
 * Recursively find all files that can be converted to [DesignAsset] in hierarchies
 * of this [Sequence]'s files.
 *
 * The conversion is done by the first [com.android.tools.idea.ui.resourcemanager.plugin.ResourceImporter]
 * provided by [importersProvider] and compatible with a given file.
 */
fun Sequence<File>.findAllDesignAssets(importersProvider: ImportersProvider): Sequence<DesignAsset> =
  flatMap(File::getAllLeafFiles)
    .toDesignAsset(importersProvider)

/**
 * Group [DesignAsset]s by their name into [ResourceAssetSet].
 */
fun Sequence<DesignAsset>.groupIntoDesignAssetSet(): List<ResourceAssetSet> =
  groupBy { it.name }
    .map { (name, assets) ->
      ResourceAssetSet(FileResourceNameValidator.getValidResourceFileName(name), assets)
    }

/**
 * Displays a file picker which filters files depending on the files supported by the [DesignAssetImporter]
 * provided by the [importersProvider]. When files have been chosen, the [fileChosenCallback] is invoked with
 * the files converted into DesignAssetSet.
 */
fun chooseDesignAssets(
  importersProvider: ImportersProvider,
  parent: JComponent? = null,
  fileChosenCallback: (Sequence<DesignAsset>) -> Unit
) {
  val lastChosenDirFile: VirtualFile? = PropertiesComponent.getInstance()
    .getValue(PREFERENCE_LAST_SELECTED_DIRECTORY)
    ?.let {
      try {
        VfsUtil.findFile(File(it).toPath(), true)
      }
      catch (ex: InvalidPathException) {
        null
      }
    }
  val fileChooserDescriptor = createFileDescriptor(importersProvider)
  FileChooserFactory
    .getInstance()
    .createPathChooser(fileChooserDescriptor, null, parent)
    .choose(lastChosenDirFile) { selectedFiles ->
      val allDesignAssets = selectedFiles.asSequence().map { it.toIoFile() }.findAllDesignAssets(importersProvider)
      fileChosenCallback(allDesignAssets)
      PropertiesComponent.getInstance().setValue(PREFERENCE_LAST_SELECTED_DIRECTORY, selectedFiles.firstOrNull()?.path)
    }
}

/**
 * Create a [FileChooserDescriptor] from the available [DesignAssetImporter] provided by the ImportersProvider.
 */
private fun createFileDescriptor(importersProvider: ImportersProvider): FileChooserDescriptor {
  val supportedFileTypes = importersProvider.supportedFileTypes
  return FileChooserDescriptor(true, true, false, false, false, true)
    .withFileFilter { it.extension in supportedFileTypes }
}