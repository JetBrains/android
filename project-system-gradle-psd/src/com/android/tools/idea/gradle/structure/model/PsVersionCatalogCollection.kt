/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.structure.model.android.PsCollectionBase

data class VersionCatalogKey(val name: String)

class PsVersionCatalogCollection(parent: PsProjectImpl) : PsCollectionBase<PsVersionCatalog, VersionCatalogKey, PsProjectImpl>(parent) {
  init {
    refresh()
  }

  override fun getKeys(from: PsProjectImpl): Set<VersionCatalogKey> {
    val result = mutableSetOf<VersionCatalogKey>()
    val projectParsedModel = from.parsedModel
    val versionCatalogModel = projectParsedModel.versionCatalogsModel
    for (catalogName in versionCatalogModel.catalogNames())
      result.add(VersionCatalogKey(catalogName))

    return result.sortedBy { it.name }.toSet()
  }

  override fun create(key: VersionCatalogKey): PsVersionCatalog = PsVersionCatalog(key.name, parent)

  override fun update(key: VersionCatalogKey, model: PsVersionCatalog) {
    val projectParsedModel = parent.parsedModel
    val catalogModel = projectParsedModel.versionCatalogsModel!!
    model.init(catalogModel.getVersionCatalogModel(key.name))
  }
}