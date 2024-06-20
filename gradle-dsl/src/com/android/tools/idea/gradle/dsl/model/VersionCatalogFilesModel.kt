/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project


/**
 * This interface should be implemented by extension that knows about project version catalog files
 * from some external sources like IDE or Gradle. That means it's likely get information from sync
 */
interface VersionCatalogFilesModel {
  fun getCatalogNameToFileMapping(project: Project): Map<String, String>
}

/**
 * We iterate over all extensions that knows about catalogs from external sources and
 * put all information in one map.
 *
 * This is the only source we can get imported catalogs file path from.
 *
 * In case no extension or additional data - result will be empty.
 * That is - no default catalog information.
 */
fun getGradleVersionCatalogFiles(project: Project): Map<String, String> {
  val result = mutableMapOf<String, String>()
  for (extension in EP_NAME.extensionList) {
    result.putAll(extension.getCatalogNameToFileMapping(project))
  }
  return result
}

private val EP_NAME: ExtensionPointName<VersionCatalogFilesModel> =
  ExtensionPointName.create("com.android.tools.idea.versionCatalogFilesGradleModel")