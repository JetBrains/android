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

import com.android.tools.idea.gradle.model.IdeJavaLibrary
import com.google.common.annotations.VisibleForTesting
import java.io.File
import java.io.Serializable

/**
 * The implementation of IdeLibrary for Java libraries.
 **/
data class IdeJavaLibraryImpl(
  val core: IdeJavaLibraryCore,
  override val isProvided: Boolean
) : IdeJavaLibrary by core, Serializable {
  @VisibleForTesting
  constructor(
    artifactAddress: String,
    artifact: File,
    isProvided: Boolean
  ) : this(IdeJavaLibraryCore(artifactAddress, artifact), isProvided)
}

data class IdeJavaLibraryCore(
  override val artifactAddress: String,
  override val artifact: File
) : IdeJavaLibrary, Serializable {
  // Used for serialization by the IDE.
  internal constructor() : this(
    artifactAddress = "",
    artifact = File("")
  )

  override val lintJar: String?
    get() = null

  override val isProvided: Nothing
    get() = error("abstract")
}
