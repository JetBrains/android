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

import com.android.ide.common.gradle.Component
import com.android.tools.idea.gradle.model.IdeUnresolvedJavaLibrary
import java.io.File
import java.io.Serializable

/**
 * The implementation of IdeLibrary for Java libraries.
 **/
data class IdeJavaLibraryImpl(
  override val artifactAddress: String,
  override val component: Component?,
  override val name: String,
  override val artifact: File,
  override val srcJar: File?, // AGP 8.1.0-alpha08 or later only
  override val docJar: File?, // AGP 8.1.0-alpha08 or later only
  override val samplesJar: File? // AGP 8.1.0-alpha08 or later only
) : IdeUnresolvedJavaLibrary, Serializable {

  // Used for serialization by the IDE.
  internal constructor() : this(
    artifactAddress = "",
    component = null,
    name = "",
    artifact = File(""),
    srcJar = null,
    docJar = null,
    samplesJar = null
  )

  override val lintJar: File?
    get() = null
}
