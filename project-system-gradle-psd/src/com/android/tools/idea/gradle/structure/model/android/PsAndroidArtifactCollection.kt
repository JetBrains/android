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

import com.android.tools.idea.gradle.model.IdeArtifactName

class PsAndroidArtifactCollection internal constructor(parent: PsVariant) : PsCollectionBase<PsAndroidArtifact, IdeArtifactName, PsVariant>(
  parent) {
  init {
    refresh()
  }

  override fun getKeys(from: PsVariant): Set<IdeArtifactName> {
    val variant = from.resolvedModel
    return listOfNotNull(
      variant?.mainArtifact?.name,
      variant?.deviceTestArtifacts?.find { it.name == IdeArtifactName.ANDROID_TEST }?.name,
      variant?.hostTestArtifacts?.find { it.name == IdeArtifactName.UNIT_TEST }?.name,
      // TODO(karimai): Add support for ScreenshotTest once available for PSD
      variant?.testFixturesArtifact?.name
    ).toSet()
  }

  override fun create(key: IdeArtifactName): PsAndroidArtifact = PsAndroidArtifact(parent, key)

  override fun update(key: IdeArtifactName, model: PsAndroidArtifact) {
    val resolved = parent.resolvedModel?.let { variant ->
      variant.mainArtifact.takeIf { it.name == key }
      ?: variant.deviceTestArtifacts.find { it.name == IdeArtifactName.ANDROID_TEST }?.takeIf { it.name == key }
      ?: variant.testFixturesArtifact?.takeIf { it.name == key }
      ?: variant.hostTestArtifacts.find { it.name == IdeArtifactName.UNIT_TEST }?.takeIf { it.name == key }
      // TODO(karimai): add support for ScreenshotTest.
    }
    model.init(resolved ?: throw IllegalStateException("Cannot find a resolved artifact named '$key' in variant '${parent.name}'"))
  }
}
