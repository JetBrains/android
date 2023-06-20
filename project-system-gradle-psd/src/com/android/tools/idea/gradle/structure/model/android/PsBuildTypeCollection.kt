/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.android

class PsBuildTypeCollection internal constructor(parent: PsAndroidModule)
  : PsMutableCollectionBase<PsBuildType, String, PsAndroidModule>(parent) {
  init {
    refresh()
  }

  override fun getKeys(from: PsAndroidModule): Set<String> {
    val result = mutableSetOf<String>()
    result.addAll(from.parsedModel?.android()?.buildTypes()?.map { it.name() }.orEmpty())
    result.addAll(from.resolvedModel?.androidProject?.multiVariantData?.buildTypes?.map { it.buildType.name }.orEmpty())
    return result
  }

  override fun create(key: String): PsBuildType =
    PsBuildType(parent, renamed = { oldKey, newKey -> renamed(entries[oldKey] ?: error("Old key not found: $oldKey"), newKey) })

  override fun update(key: String, model: PsBuildType) {
    model.init(
      parent.resolvedModel?.androidProject?.multiVariantData?.buildTypes?.map { it.buildType }?.firstOrNull { it.name == key },
      parent.parsedModel?.android()?.buildTypes()?.firstOrNull { it.name() == key }
    )
  }

  override fun instantiateNew(key: String) {
    parent.parsedModel!!.android().addBuildType(key)
  }

  override fun removeExisting(key: String) {
    parent.parsedModel!!.android().removeBuildType(key)
  }
}
