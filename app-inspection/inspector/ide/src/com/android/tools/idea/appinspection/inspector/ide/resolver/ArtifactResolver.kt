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
package com.android.tools.idea.appinspection.inspector.ide.resolver

import com.android.tools.idea.appinspection.inspector.api.launch.RunningArtifactCoordinate
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

/** A class that handles the downloading of gradle/maven artifacts. */
interface ArtifactResolver {
  /**
   * Attempts to resolve the requested artifact and returns the path of the resolved jar. Throws an
   * AppInspectionArtifactNotFoundException when the artifact can't be resolved.
   */
  suspend fun resolveArtifact(artifactCoordinate: RunningArtifactCoordinate): Path
}

/**
 * An adapter for implementors of the interface that are unable to provide a `suspend fun`
 * implementation. It might seem that this interface is unused in production (it is used in tests)
 * but downstream projects use it: do not remove unless in collaboration with at least Android
 * Studio with Blaze.
 */
abstract class BlockingArtifactResolver : ArtifactResolver {
  abstract fun resolveArtifactBlocking(artifactCoordinate: RunningArtifactCoordinate): Path

  final override suspend fun resolveArtifact(artifactCoordinate: RunningArtifactCoordinate): Path =
    runBlocking {
      resolveArtifactBlocking(artifactCoordinate)
    }
}
