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
package com.android.tools.idea.preview.rendering

import com.android.ide.common.rendering.api.SessionParams
import com.android.ide.common.rendering.api.ViewInfo
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.tools.configurations.Configuration
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.rendering.StudioRenderService
import com.android.tools.idea.rendering.parsers.PsiXmlFile
import com.android.tools.idea.rendering.taskBuilder
import com.android.tools.rendering.RenderTask
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.facet.AndroidFacet
import java.util.concurrent.CompletableFuture

/**
 * Returns a [CompletableFuture] that creates a [RenderTask] for a single [VirtualFile]. It is the
 * responsibility of a client of this function to dispose the resulting [RenderTask] when no loner
 * needed.
 */
fun createRenderTaskFuture(
  facet: AndroidFacet,
  file: VirtualFile,
  privateClassLoader: Boolean = false,
  useLayoutScanner: Boolean = false,
  classesToPreload: Collection<String> = emptyList(),
  customViewInfoParser: ((Any) -> List<ViewInfo>)? = null,
  showDecorations: Boolean = false,
  configure: (Configuration) -> Unit = {},
): CompletableFuture<RenderTask> {
  val project = facet.module.project

  val xmlFile =
    AndroidPsiUtils.getPsiFileSafely(project, file) as? XmlFile
      ?: return CompletableFuture.completedFuture(null)
  val configuration =
    Configuration.create(
      ConfigurationManager.getOrCreateInstance(facet.module),
      FolderConfiguration.createDefault(),
    )
  configure(configuration)

  val builder =
    StudioRenderService.getInstance(project)
      .taskBuilder(facet, configuration)
      .withPsiFile(PsiXmlFile(xmlFile))
      .apply {
        if (privateClassLoader) {
          usePrivateClassLoader()
        }
        if (classesToPreload.isNotEmpty()) {
          preloadClasses(classesToPreload)
        }
        if (!showDecorations) {
          disableDecorations()
          withRenderingMode(SessionParams.RenderingMode.SHRINK)
        }
      }
      .withLayoutScanner(useLayoutScanner)
      // Compose Preview has its own out-of-date reporting mechanism
      .doNotReportOutOfDateUserClasses()

  customViewInfoParser?.let { builder.setCustomContentHierarchyParser(it) }
  return builder.build()
}
