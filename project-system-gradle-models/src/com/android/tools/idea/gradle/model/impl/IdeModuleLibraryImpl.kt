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
import com.google.common.annotations.VisibleForTesting
import java.io.File
import java.io.Serializable

/**
 * The implementation of IdeLibrary for modules.
 **/
data class IdeModuleLibraryImpl(
  val core: IdeModuleLibraryCore,
  override val isProvided: Boolean
) : IdeModuleLibrary by core, Serializable {
  @VisibleForTesting
  constructor(
    projectPath: String,
    artifactAddress: String,
    buildId: String?,
    variant: String? = null
  ) : this(
      IdeModuleLibraryCore(
          projectPath = projectPath,
          artifactAddress = artifactAddress,
          buildId = buildId,
          variant = variant,
          lintJar = null
      ),
      isProvided = false
  )
}

data class IdeModuleLibraryCore(
  override val artifactAddress: String,
  override val buildId: String?,
  override val projectPath: String,
  override val variant: String?,
  override val lintJar: String?

) : IdeModuleLibrary, Serializable {

  // Used for serialization by the IDE.
  constructor() : this(
    artifactAddress = "",
    buildId = null,
    projectPath = "",
    variant = null,
    lintJar = null
  )

  constructor(
    projectPath: String,
    artifactAddress: String,
    buildId: String?
  ) : this(
    artifactAddress = artifactAddress,
    buildId = buildId,
    projectPath = projectPath,
    variant = null,
    lintJar = null
  )

  override val artifact: File
    get() = throw unsupportedMethodForModuleLibrary("getArtifact()")

  override val isProvided: Nothing
    get() = error("abstract")
}

private fun unsupportedMethodForModuleLibrary(methodName: String): UnsupportedOperationException {
  return UnsupportedOperationException("$methodName() cannot be called when getType() returns LIBRARY_MODULE")
}
