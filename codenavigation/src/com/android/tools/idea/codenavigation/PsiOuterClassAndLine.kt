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
package com.android.tools.idea.codenavigation

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiManager
import com.intellij.psi.util.ClassUtil

/**
 * There has been at least one case where the PsiManager could not find an inner class in Kotlin code, which caused us to abort navigating.
 * However, if we have the outer class (which is easier for PsiManager to find) and a line number, that's enough information to help us
 * navigate. So, to be more robust against PsiManager error, we try one more time.
 */
class PsiOuterClassAndLine(private val project: Project) : NavSource {
  private val manager = PsiManager.getInstance(project)

  override fun lookUp(location: CodeLocation, arch: String?): Navigatable? {
    if (location.className.isNullOrEmpty() || location.lineNumber == CodeLocation.INVALID_LINE_NUMBER) {
      return null
    }

    val outerClass = ClassUtil.findPsiClassByJVMName(manager, location.outerClass) ?: return null
    return OpenFileDescriptor(project, outerClass.navigationElement.containingFile.virtualFile, location.lineNumber, 0)
  }
}