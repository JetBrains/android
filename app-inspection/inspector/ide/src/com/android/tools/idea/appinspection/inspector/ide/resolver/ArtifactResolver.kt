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

import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.intellij.openapi.project.Project

/**
 * A class that handles the downloading of gradle/maven artifacts.
 */
interface ArtifactResolver {
  /**
   * For each request, attempts to resolve the requested artifact and returns a [ArtifactResolverRequest].
   */
  suspend fun <T : ArtifactResolverRequest> resolveArtifacts(requests: List<T>, project: Project): List<ArtifactResolverResult<T>>
}

/**
 * Contains all of the information needed to make a attempt to resolve an artifact.
 */
abstract class ArtifactResolverRequest(
  val artifactCoordinate: ArtifactCoordinate
)

/**
 * Represents the result of resolving the artifact of a given coordinate.
 */
sealed class ArtifactResolverResult<T : ArtifactResolverRequest>(
  val request: T
)

/**
 * Represents the success scenario of resolving an artifact, in which the jar is returned.
 */
class SuccessfulResult<T : ArtifactResolverRequest>(
  request: T,
  val jar: AppInspectorJar
) : ArtifactResolverResult<T>(request)

/**
 * Represents the failure scenario in which the error message is returned.
 */
class FailureResult<T : ArtifactResolverRequest>(
  request: T,
  val errorMessage: String? = null
) : ArtifactResolverResult<T>(request)