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
package com.android.tools.idea.ui.resourcemanager.plugin

import com.android.resources.ResourceType
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.android.facet.AndroidFacet
import java.io.File

/**
 * Plugin interface to add resources importation plugins.
 */
interface ResourceImporter {

  companion object {
    val EP_NAME: ExtensionPointName<ResourceImporter> = ExtensionPointName.create<ResourceImporter>("com.android.resourceImporter")
  }

  /**
   * The name of the plugin as it should be shown to the user
   */
  val presentableName: String

  /**
   * Returns a list of the file extensions supported by this plugin.
   */
  fun getSupportedFileTypes(): Set<String>

  /**
   * Returns true if the plugin can import multiple files at a time or
   * false if each file needs to be imported separately.
   */
  val supportsBatchImport: Boolean
    get() = true

  /**
   * Invoke a custom panel that handles the importation.
   * @param filePaths the paths of the files to import.
   * If [supportsBatchImport] is false, the list will only contain a single element.
   */
  fun invokeCustomImporter(facet: AndroidFacet, filePaths: Collection<String>) {}

  /**
   * If true, the implementing class should handle the import flow itself.
   * The [com.android.tools.idea.ui.resourcemanager.importer.ImportersProvider] will just invoke
   * the implementing class with the selected files.
   */
  val hasCustomImport: Boolean
    get() = false

  /**
   * Returns true if we should let the user configure the qualifiers or if the plugin handles it itself.
   */
  val userCanEditQualifiers: Boolean
    get() = true

  /**
   * Return the [DesignAssetRenderer] needed to preview the source file of the provided [asset]
   */
  fun getSourcePreview(asset: DesignAsset): DesignAssetRenderer?

  /**
   * Wrap the provided files into [DesignAsset].
   *
   * The default implementation wrap each file if found on disk into a [DesignAsset] of with [ResourceType.RAW] and no qualifiers
   * Implementing methods can do some processing on the files like converting, or resizing...
   */
  fun processFile(file: File): DesignAsset? {
    val virtualFile = VfsUtil.findFileByIoFile(file, true) ?: return null
    return if (DesignAssetRendererManager.getInstance().hasViewer(virtualFile)) {
      DesignAsset(virtualFile, listOf(), ResourceType.RAW)
    }
    else {
      null
    }
  }
}

