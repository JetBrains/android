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
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.io.Serializable

data class IdeModuleLibraryImpl(
  override val buildId: String,
  override val projectPath: String,
  override val variant: String?,
  override val lintJar: File?,
  override val sourceSet: IdeModuleSourceSet
) : IdeModuleLibrary, Serializable {

  // Used for serialization by the IDE.
  constructor() : this(
    buildId = "",
    projectPath = "",
    variant = null,
    lintJar = null,
    sourceSet = IdeModuleSourceSet.MAIN
  )

  constructor(
    projectPath: String,
    buildId: String
  ) : this(
    buildId = buildId,
    projectPath = projectPath,
    variant = null,
    lintJar = null,
    sourceSet = IdeModuleSourceSet.MAIN
  )

  @get:TestOnly
  val displayName: String get() = "$buildId:$projectPath@$variant"
}
