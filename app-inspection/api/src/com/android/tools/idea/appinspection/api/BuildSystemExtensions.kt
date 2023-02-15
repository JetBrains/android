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
package com.android.tools.idea.appinspection.api

import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate

/**
 * This file contains a collection of extension functions to [ArtifactCoordinate] that do not belong
 * in the class itself, because they are build system specific.
 */

/** The file name of the library when resolving against the blaze build system. */
val ArtifactCoordinate.blazeFileName: String
  get() = "library.${type}"

/** Converts an [ArtifactCoordinate] to a [GradleCoordinate]. */
fun ArtifactCoordinate.toGradleCoordinate(): GradleCoordinate =
  GradleCoordinate(groupId, artifactId, version)
