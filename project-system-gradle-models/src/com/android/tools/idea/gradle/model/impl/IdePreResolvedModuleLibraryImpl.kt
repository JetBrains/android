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

import com.android.tools.idea.gradle.model.IdeModuleLibrary
import com.android.tools.idea.gradle.model.IdeModuleSourceSet
import com.android.tools.idea.gradle.model.IdeModuleWellKnownSourceSet.MAIN
import com.android.tools.idea.gradle.model.IdePreResolvedModuleLibrary
import com.android.tools.idea.gradle.model.IdeUnresolvedKmpAndroidModuleLibrary
import com.android.tools.idea.gradle.model.IdeUnresolvedModuleLibrary
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.io.Serializable

data class IdePreResolvedModuleLibraryImpl constructor(
  override val buildId: String,
  override val projectPath: String,
  override val variant: String?,
  override val lintJar: File?,
  override val sourceSet: IdeModuleSourceSet
) : IdePreResolvedModuleLibrary, Serializable {

  // Used for serialization by the IDE.
  @Suppress("unused")
  constructor() : this(
    buildId = "",
    projectPath = "",
    variant = null,
    lintJar = null,
    sourceSet = MAIN
  )

  @get:TestOnly
  val displayName: String get() = moduleLibraryDisplayName(buildId, projectPath, variant, sourceSet)
}

data class IdeUnresolvedKmpAndroidModuleLibraryImpl(
  override val buildId: String,
  override val projectPath: String,
  override val lintJar: File?,
): IdeUnresolvedKmpAndroidModuleLibrary, Serializable {

  // Used for serialization by the IDE.
  @Suppress("unused")
  constructor() : this(
    buildId = "",
    projectPath = "",
    lintJar = null,
  )
}

data class IdeUnresolvedModuleLibraryImpl constructor(
  override val buildId: String,
  override val projectPath: String,
  override val variant: String?,
  override val lintJar: File?,
  override val artifact: File
) : IdeUnresolvedModuleLibrary, Serializable {

  // Used for serialization by the IDE.
  @Suppress("unused")
  constructor() : this(
    buildId = "",
    projectPath = "",
    variant = null,
    lintJar = null,
    artifact = File("")
  )

  @get:TestOnly
  val displayName: String get() = moduleLibraryDisplayName(buildId, projectPath, variant, null)
}

data class IdeModuleLibraryImpl constructor(
  override val buildId: String,
  override val projectPath: String,
  override val variant: String?,
  override val lintJar: File?,
  override val sourceSet: IdeModuleSourceSet
) : IdeModuleLibrary, Serializable {

  @get:TestOnly
  val displayName: String get() = moduleLibraryDisplayName(buildId, projectPath, variant, sourceSet)
}

internal fun moduleLibraryDisplayName(
  buildId: String,
  projectPath: String,
  variant: String?,
  sourceSet: IdeModuleSourceSet?
): String {
  val variantPart = if (!variant.isNullOrEmpty()) "@$variant" else ""
  val sourceSetPart = sourceSet?.takeUnless { it == MAIN }?.let { "/$it" }.orEmpty()
  return "$buildId:$projectPath$variantPart$sourceSetPart"
}

