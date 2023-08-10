/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.model.impl

import com.android.tools.idea.gradle.model.IdeBuildType
import java.io.File
import java.io.Serializable

data class IdeBuildTypeImpl(
  override val name: String,
  override val applicationIdSuffix: String?,
  override val versionNameSuffix: String?,
  override val resValues: Map<String, IdeClassFieldImpl>,
  override val proguardFiles: Collection<File>,
  override val consumerProguardFiles: Collection<File>,
  override val manifestPlaceholders: Map<String, String>,
  override val multiDexEnabled: Boolean?,
  override val isDebuggable: Boolean,
  override val isJniDebuggable: Boolean,
  override val isRenderscriptDebuggable: Boolean,
  override val renderscriptOptimLevel: Int,
  override val isMinifyEnabled: Boolean,
  override val isZipAlignEnabled: Boolean,
  override val isDefault: Boolean?
) : IdeBuildType, Serializable
