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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade

import com.android.ide.common.gradle.model.UnusedModelMethodException
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.androidproject.AaptOptions
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.OldAaptOptions
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.OldAaptOptionsNamespacing

open class LegacyAaptOptions(private val aaptOptions: AaptOptions) : OldAaptOptions {
  override fun getNamespacing(): OldAaptOptionsNamespacing = OldAaptOptionsNamespacing.valueOf(aaptOptions.namespacing.name)

  override fun getIgnoreAssets(): String? = throw UnusedModelMethodException("getIgnoreAssets")
  override fun getNoCompress(): Collection<String>? = throw UnusedModelMethodException("getNoCompress")
  override fun getFailOnMissingConfigEntry(): Boolean = throw UnusedModelMethodException("getFailOnMissingConfigEntry")
  override fun getAdditionalParameters(): List<String> = throw UnusedModelMethodException("getAdditionalParameters")

  override fun toString(): String = "LegacyAaptOptions{namespacing=$namespacing}"
}
