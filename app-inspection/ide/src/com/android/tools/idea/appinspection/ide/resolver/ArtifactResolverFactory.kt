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
package com.android.tools.idea.appinspection.ide.resolver

import com.android.tools.idea.appinspection.ide.resolver.http.HttpArtifactResolver
import com.android.tools.idea.appinspection.inspector.ide.resolver.ArtifactResolver
import com.android.tools.idea.appinspection.inspector.ide.resolver.ArtifactResolverFactory
import com.android.tools.idea.io.FileService
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.Token
import com.android.tools.idea.projectsystem.getProjectSystem
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

class ArtifactResolverFactory(private val fileService: FileService) : ArtifactResolverFactory {
  private val httpArtifactResolver =
    HttpArtifactResolver(fileService, AppInspectorArtifactPaths(fileService))

  override fun getArtifactResolver(project: Project): ArtifactResolver {
    val projectSystem = project.getProjectSystem()
    val token =
      ArtifactResolverFactoryToken.EP_NAME.getExtensions(project).firstOrNull {
        it.isApplicable(projectSystem)
      } ?: return httpArtifactResolver
    return token.getArtifactResolver(projectSystem, fileService, httpArtifactResolver)
  }
}

interface ArtifactResolverFactoryToken<P : AndroidProjectSystem> : Token {
  fun getArtifactResolver(
    projectSystem: P,
    fileService: FileService,
    httpArtifactResolver: HttpArtifactResolver,
  ): ArtifactResolver

  companion object {
    val EP_NAME =
      ExtensionPointName<ArtifactResolverFactoryToken<AndroidProjectSystem>>(
        "com.android.tools.idea.appinspection.ide.resolver.artifactResolverFactoryToken"
      )
  }
}
