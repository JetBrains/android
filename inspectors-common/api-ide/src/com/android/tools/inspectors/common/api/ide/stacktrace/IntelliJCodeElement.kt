/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.inspectors.common.api.ide.stacktrace

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.codenavigation.CodeLocation
import com.android.tools.inspectors.common.api.stacktrace.CodeElement
import com.android.tools.inspectors.common.api.stacktrace.CodeElement.NO_PACKAGE
import com.android.tools.inspectors.common.api.stacktrace.CodeElement.UNKONWN_CLASS
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope

class IntelliJCodeElement(private val project: Project, private val codeLocation: CodeLocation) :
  CodeElement {
  private val packageName: String
  private val simpleClassName: String
  private val isInUserCode: Boolean

  init {
    val className = codeLocation.className
    if (className == null) {
      packageName = CodeElement.UNKNOWN_PACKAGE
      simpleClassName = UNKONWN_CLASS
    } else {
      val dot = className.lastIndexOf('.')
      packageName = if (dot <= 0) NO_PACKAGE else className.substring(0, dot)
      simpleClassName =
        if (dot + 1 < className.length) className.substring(dot + 1) else UNKONWN_CLASS
    }

    isInUserCode =
      when {
        IdeInfo.isGameTool() -> false
        codeLocation.isNativeCode -> isInNativeSources()
        else -> isInSources()
      }
  }

  override fun getCodeLocation() = codeLocation

  override fun getPackageName() = packageName

  override fun getSimpleClassName() = simpleClassName

  override fun getMethodName(): String {
    return codeLocation.methodName ?: CodeElement.UNKNOWN_METHOD
  }

  override fun isInUserCode() = isInUserCode

  private fun isInNativeSources(): Boolean {
    val sourceFileName = codeLocation.fileName ?: return false
    if (sourceFileName.isEmpty()) {
      return false
    }
    val file = LocalFileSystem.getInstance().findFileByPath(sourceFileName) ?: return false
    val application = ApplicationManager.getApplication()
    return application.runReadAction(
      Computable { ProjectFileIndex.getInstance(project).isInSource(file) }
    )
  }

  private fun isInSources(): Boolean {
    if (codeLocation.className == null) {
      return false
    }

    // JavaPsiFacade can't deal with inner classes, so we'll need to strip the class name down to
    // just the outer class name.
    val className = codeLocation.outerClass

    val psiFacade = JavaPsiFacade.getInstance(project)
    val application = ApplicationManager.getApplication()
    return application.runReadAction(
      Computable {
        val psiClass = psiFacade.findClass(className, GlobalSearchScope.allScope(project))
        val file = psiClass?.containingFile?.virtualFile ?: return@Computable false
        ProjectFileIndex.getInstance(project).isInSource(file)
      }
    )
  }
}
