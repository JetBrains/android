/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.rendering

import com.android.ide.common.rendering.api.SessionParams
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.facet.AndroidFacet
import java.util.concurrent.CompletableFuture

/**
 * Returns a [CompletableFuture] that creates a [RenderTask] for a single
 * [VirtualFile]. It is the responsibility of a client of this function to
 * dispose the resulting [RenderTask] when no loner needed.
 */
fun createRenderTaskFuture(
  facet: AndroidFacet,
  file: VirtualFile,
  privateClassLoader: Boolean = false,
  classesToPreload: Collection<String> = emptyList(),
  configure: (Configuration) -> Unit = {}
) : CompletableFuture<RenderTask> {
  val project = facet.module.project

  val xmlFile =
    AndroidPsiUtils.getPsiFileSafely(project, file) as? XmlFile
    ?: return CompletableFuture.completedFuture(null)
  val configuration =
    Configuration.create(
      ConfigurationManager.getOrCreateInstance(facet.module),
      null,
      FolderConfiguration.createDefault()
    )
  configure(configuration)

  return StudioRenderService.getInstance(project)
    .taskBuilder(facet, configuration)
    .withPsiFile(xmlFile)
    .disableDecorations()
    .apply {
      if (privateClassLoader) {
        usePrivateClassLoader()
      }
      if (classesToPreload.isNotEmpty()) {
        preloadClasses(classesToPreload)
      }
    }
    .withRenderingMode(SessionParams.RenderingMode.SHRINK)
    // Compose Preview has its own out-of-date reporting mechanism
    .doNotReportOutOfDateUserClasses()
    .build()
}