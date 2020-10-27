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

import com.android.ide.common.gradle.model.IdeBaseArtifact


class PsAndroidArtifactCollection internal constructor(parent: PsVariant) : PsCollectionBase<PsAndroidArtifact, String, PsVariant>(parent) {
  init {
    refresh()
  }

  override fun getKeys(from: PsVariant): Set<String> {
    val result = mutableSetOf<String>()
    val variant = from.resolvedModel
    if (variant != null) {
      result.add(variant.mainArtifact.name)
      result.addAll(variant.extraAndroidArtifacts.map { it.name })
      result.addAll(variant.extraJavaArtifacts.map { it.name })
    }
    return result
  }

  override fun create(key: String): PsAndroidArtifact = PsAndroidArtifact(parent, key)

  override fun update(key: String, model: PsAndroidArtifact) {
    val resolved = parent.resolvedModel?.let {
      it.mainArtifact.takeIf { it.name == key } ?: it.extraAndroidArtifacts.firstOrNull { it.name == key }
      ?: it.extraJavaArtifacts.firstOrNull { it.name == key }
    } as? IdeBaseArtifact
    model.init(resolved ?: throw IllegalStateException("Cannot find a resolved artifact named '$key' in variant '${parent.name}'"))
  }
}
