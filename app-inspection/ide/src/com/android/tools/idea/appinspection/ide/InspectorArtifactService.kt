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
package com.android.tools.idea.appinspection.ide

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.appinspection.ide.resolver.ArtifactResolverFactory
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.ide.resolver.ArtifactResolverFactory as ArtifactResolverFactoryBase
import com.android.tools.idea.io.FileService
import com.android.tools.idea.io.IdeFileService
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.NonInjectable
import java.nio.file.Path

/**
 * An application service that exposes functionality to find, retrieve and manipulate inspector
 * artifacts.
 */
interface InspectorArtifactService {
  /**
   * Gets the cached inspector artifact if it exists, otherwise try to resolve it.
   *
   * Returns null if artifact can't be resolved.
   */
  suspend fun getOrResolveInspectorArtifact(
    artifactCoordinate: ArtifactCoordinate,
    project: Project
  ): Path

  companion object {
    val instance
      get() = service<InspectorArtifactService>()
  }
}

/** A helper function that returns an [AppInspectorJar] directly instead of a path. */
suspend fun InspectorArtifactService.getOrResolveInspectorJar(
  project: Project,
  coordinate: ArtifactCoordinate
): AppInspectorJar {
  val inspectorPath = getOrResolveInspectorArtifact(coordinate, project)
  return AppInspectorJar(
    inspectorPath.fileName.toString(),
    inspectorPath.parent.toString(),
    inspectorPath.parent.toString()
  )
}

class InspectorArtifactServiceImpl
@NonInjectable
@VisibleForTesting
constructor(
  private val fileService: FileService,
  private val artifactResolverFactory: ArtifactResolverFactoryBase =
    ArtifactResolverFactory(fileService)
) : InspectorArtifactService {
  // Called using reflection by intellij service framework
  constructor() : this(IdeFileService("app-inspection"))

  @WorkerThread
  override suspend fun getOrResolveInspectorArtifact(
    artifactCoordinate: ArtifactCoordinate,
    project: Project
  ) = artifactResolverFactory.getArtifactResolver(project).resolveArtifact(artifactCoordinate)
}
