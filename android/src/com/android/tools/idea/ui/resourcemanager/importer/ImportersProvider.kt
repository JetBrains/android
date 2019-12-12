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

import com.android.tools.idea.ui.resourcemanager.plugin.ResourceImporter
import com.intellij.openapi.diagnostic.Logger

private val LOG : Logger by lazy { Logger.getInstance(ImportersProvider::class.java) }

/**
 * Provides methods to get aggregated data from the registered [ResourceImporter].
 */
class ImportersProvider(
    val importers: Set<ResourceImporter> = ResourceImporter.EP_NAME.extensionList.toSet()
) {

  private val typeToImporter = importers
      .flatMap { importer -> importer.getSupportedFileTypes().map { Pair(it, importer) }.toList() }
      .asSequence()
      .groupBy({ it.first }, {it.second})

  /**
   * Returns the all the file extension supported by the available plugins
   */
  val supportedFileTypes = importers.flatMap { importer -> importer.getSupportedFileTypes() }.toSet()

  /**
   * Returns a list of [ResourceImporter] that supports the provided extension.
   */
  fun getImportersForExtension(extension: String): List<ResourceImporter> {
    val importers = typeToImporter[extension] ?: emptyList()
    if (importers.isEmpty()) {
      LOG.warn("No Importers for: $extension.")
    }
    return importers
  }
}