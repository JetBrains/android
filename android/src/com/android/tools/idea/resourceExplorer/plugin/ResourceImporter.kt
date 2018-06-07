// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.resourceExplorer.plugin

import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.intellij.openapi.extensions.ExtensionPointName
import java.awt.Image
import javax.swing.JPanel

/**
 * Plugin interface to add resources importation plugins.
 */
interface ResourceImporter {

  companion object {
    val EP_NAME = ExtensionPointName.create<ResourceImporter>("com.android.resourceImporter")
  }

  /**
   * The name of the plugin as it should be shown to the user
   */
  fun getPresentableName() : String

  /**
   * Returns a list of the file extensions supported by this plugin.
   */
  fun getSupportedFileTypes(): Set<String>

  /**
   * Returns true if the plugin can import multiple files at a time or
   * false if each file needs to be imported separately.
   */
  fun supportsBatchImport(): Boolean = true

  /**
   * Return a [JPanel] displaying a interface to configure the importation settings.
   */
  fun getConfigurationPanel(callback: ConfigurationDoneCallback): JPanel?

  /**
   * Returns true if we should let the user configure the qualifiers or if the plugin handles it itself.
   */
  fun userCanEditQualifiers(): Boolean

  /**
   * Return a preview of the result of the importation.
   */
  fun getSourcePreview(asset: DesignAsset): Image?

  /**
   * Return a preview of the result of the importation.
   */
  fun getImportPreview(asset: DesignAsset): Image?
}

/**
 * Callback interface that the plugin should call once the configuration is done.
 */
interface ConfigurationDoneCallback {
  fun configurationDone()
}
