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

class PsFlavorDimensionCollection internal constructor(parent: PsAndroidModule)
  : PsMutableCollectionBase<PsFlavorDimension, String, PsAndroidModule>(parent) {
  init {
    refresh()
  }

  override fun getKeys(from: PsAndroidModule): Set<String> =
    (
      from.resolvedModel?.androidProject?.flavorDimensions?.map { it }.orEmpty() +
      from.parsedModel?.android()?.flavorDimensions()?.toList()?.map { it.toString() }.orEmpty()
    ).toSet()

  override fun create(key: String): PsFlavorDimension = PsFlavorDimension(parent)

  override fun update(key: String, model: PsFlavorDimension) {
    model.init(key)
  }

  override fun instantiateNew(key: String) {
    parent.parsedModel!!.android().flavorDimensions().addListValue()?.setValue(key)
  }

  override fun removeExisting(key: String) {
    parent.parsedModel!!.android().flavorDimensions().let {
      it.toList()
        ?.first { it.toString() == key }
        ?.delete()
    }
  }
}
