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
package com.android.tools.idea.gradle.structure.model.android

class PsSigningConfigCollection internal constructor(parent: PsAndroidModule)
  : PsMutableCollectionBase<PsSigningConfig, String, PsAndroidModule>(parent) {
  init {
    refresh()
  }

  override fun getKeys(from: PsAndroidModule): Set<String> {
    val result = mutableSetOf<String>()
    result.addAll(from.parsedModel?.android()?.signingConfigs()?.map { it.name() }.orEmpty())
    result.addAll(from.resolvedModel?.androidProject?.signingConfigs?.map { it.name }.orEmpty())
    return result
  }

  override fun create(key: String): PsSigningConfig =
    PsSigningConfig(parent, renamed = { oldKey, newKey -> renamed(entries[oldKey] ?: error("Old key not found: $oldKey"), newKey) })

  override fun update(key: String, model: PsSigningConfig) {
    model.init(
      parent.resolvedModel?.androidProject?.signingConfigs?.firstOrNull { it.name == key },
      parent.parsedModel?.android()?.signingConfigs()?.firstOrNull { it.name() == key }
    )
  }

  override fun instantiateNew(key: String) {
    parent.parsedModel!!.android().addSigningConfig(key)
  }

  override fun removeExisting(key: String) {
    parent.parsedModel!!.android().removeSigningConfig(key)
  }
}
