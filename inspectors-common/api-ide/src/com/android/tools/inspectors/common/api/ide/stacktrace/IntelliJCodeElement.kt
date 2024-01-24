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
import com.google.common.base.Strings
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.StubVirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope

private val UNRESOLVED_CLASS_FILE: VirtualFile = StubVirtualFile()

class IntelliJCodeElement(private val project: Project, private val codeLocation: CodeLocation) : CodeElement {
  private val packageName: String
  private val simpleClassName: String

  private var cachedClassFile: VirtualFile? = UNRESOLVED_CLASS_FILE

  init {
    val className = codeLocation.className
    if (className == null) {
      packageName = CodeElement.UNKNOWN_PACKAGE
      simpleClassName = UNKONWN_CLASS
    } else {
      val dot = className.lastIndexOf('.')
      packageName = if (dot <= 0) NO_PACKAGE else className.substring(0, dot)
      simpleClassName = if (dot + 1 < className.length) className.substring(dot + 1) else UNKONWN_CLASS
    }
  }

  override fun getCodeLocation() = codeLocation

  override fun getPackageName() = packageName

  override fun getSimpleClassName() = simpleClassName

  override fun getMethodName(): String {
    return codeLocation.methodName ?: CodeElement.UNKNOWN_METHOD
  }

  override fun isInUserCode(): Boolean {
    if (IdeInfo.isGameTool()) {
      // For standalone game tools, source code navigation is not supported at this moment.
      return false
    }

    val sourceFile = if (codeLocation.isNativeCode) findSourceFile() else findClassFile()
    return sourceFile != null && ProjectFileIndex.getInstance(project).isInSource(sourceFile)
  }

  private fun findSourceFile(): VirtualFile? {
    val sourceFileName = codeLocation.fileName
    if (Strings.isNullOrEmpty(sourceFileName)) {
      return null
    }
    return LocalFileSystem.getInstance().findFileByPath(sourceFileName!!)
  }

  private fun findClassFile(): VirtualFile? {
    @Suppress("UseVirtualFileEquals")
    if (cachedClassFile !== UNRESOLVED_CLASS_FILE) {
      return cachedClassFile
    }

    var className = codeLocation.className
    if (className == null) {
      cachedClassFile = null
      return null
    }

    // JavaPsiFacade can't deal with inner classes, so we'll need to strip the class name down to just the outer class name.
    className = codeLocation.outerClass

    val psiClass = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project))
    if (psiClass == null) {
      cachedClassFile = null
      return null
    }
    cachedClassFile = psiClass.containingFile.virtualFile
    return cachedClassFile
  }
}
