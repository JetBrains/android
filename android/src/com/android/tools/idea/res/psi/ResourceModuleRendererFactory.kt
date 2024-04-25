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
package com.android.tools.idea.res.psi

import com.android.tools.idea.res.getSourceAsVirtualFile
import com.intellij.icons.AllIcons
import com.intellij.ide.util.ModuleRendererFactory
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.util.TextWithIcon

/** Finds the module name and icon for [ResourceNavigationItem] to be used in Goto symbol action. */
class ResourceModuleRendererFactory : ModuleRendererFactory() {
  override fun handles(element: Any?): Boolean = element is ResourceNavigationItem

  override fun getModuleTextWithIcon(element: Any?): TextWithIcon? {
    // workaround
    // https://youtrack.jetbrains.com/issue/IDEA-345002/ModuleRendererFactorygetModuleTextWithIcon-not-called-in-ReadAction
    val textWithIcon = runReadAction {
      val resourceNavigationItem = element as ResourceNavigationItem
      val virtualFile =
        resourceNavigationItem.resource.getSourceAsVirtualFile() ?: return@runReadAction null
      val fileIndex = ProjectFileIndex.getInstance(resourceNavigationItem.project)
      val inTestSource = fileIndex.isInTestSourceContent(virtualFile)
      val module = fileIndex.getModuleForFile(virtualFile) ?: return@runReadAction null
      val icon =
        if (inTestSource) {
          AllIcons.Nodes.TestSourceFolder
        } else {
          ModuleType.get(module).icon
        }
      return@runReadAction TextWithIcon(module.name, icon)
    }
    return textWithIcon
  }
}
