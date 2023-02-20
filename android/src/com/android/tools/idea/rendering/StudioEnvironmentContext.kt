/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.ide.common.rendering.api.RenderResources
import com.android.ide.common.util.PathString
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.util.toVirtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.xml.XmlFile

/** Studio-specific implementation of [EnvironmentContext]. */
class StudioEnvironmentContext(private val project: Project) : EnvironmentContext {
  override val parentDisposable: Disposable
    get() = (project as ProjectEx).earlyDisposable
  override fun hasLayoutlibCrash(): Boolean = hasStudioLayoutlibCrash()
  override val runnableFixFactory: RenderProblem.RunnableFixFactory = ShowFixFactory

  override fun createIncludeReference(xmlFile: XmlFile, resolver: RenderResources): IncludeReference =
    PsiIncludeReference.get(xmlFile, resolver)

  override fun getFileText(fileName: String): String? {
    val virtualFile = LocalFileSystem.getInstance().findFileByPath(fileName)
    if (virtualFile != null) {
      val psiFile = AndroidPsiUtils.getPsiFileSafely(project, virtualFile)
      if (psiFile != null) {
        return if (ApplicationManager.getApplication().isReadAccessAllowed) psiFile.text
        else ApplicationManager.getApplication().runReadAction(
          Computable { psiFile.text } as Computable<String>)
      }
    }
    return null
  }

  override fun getXmlFile(filePath: PathString): XmlFile? {
    val file = filePath.toVirtualFile()
    return file?.let { AndroidPsiUtils.getPsiFileSafely(project, it) as? XmlFile }
  }
}