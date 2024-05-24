/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.idea.projectsystem.AndroidIconProviderProjectToken
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.isAndroidTestModule
import com.android.tools.idea.projectsystem.isHolderModule
import com.android.tools.idea.projectsystem.isMainModule
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.impl.ProjectRootsUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import icons.StudioIcons
import javax.swing.Icon

class AndroidIconProviderProjectGradleToken : AndroidIconProviderProjectToken<GradleProjectSystem>, GradleToken {
  override fun getIcon(projectSystem: GradleProjectSystem, element: PsiElement): Icon? {
    if (element is PsiDirectory) {
      val psiDirectory = element
      val virtualDirectory = psiDirectory.virtualFile
      val project = psiDirectory.project
      if (ProjectRootsUtil.isModuleContentRoot(virtualDirectory, project)) {
        val projectFileIndex = ProjectRootManager.getInstance(project).fileIndex
        val module = projectFileIndex.getModuleForFile(virtualDirectory)
        // Only provide icons for modules that are setup by the Android plugin - other modules may be provided for
        // by later providers, we don't assume getModuleIcon returns the correct icon in these cases.
        if (module != null && !module.isDisposed && module.isLinkedAndroidModule()) {
          return getModuleIcon(module)
        }
      }
    }

    return null
  }

  override fun getModuleIcon(projectSystem: GradleProjectSystem, module: Module): Icon? = getModuleIcon(module)

  companion object {
    @JvmStatic
    fun getModuleIcon(module: Module): Icon = when {
      module.isHolderModule() || module.isMainModule() -> getAndroidModuleIcon(module.getModuleSystem())
      module.isAndroidTestModule() -> StudioIcons.Shell.Filetree.ANDROID_MODULE
      else -> AllIcons.Nodes.Module
    }

    private fun getAndroidModuleIcon(androidModuleSystem: AndroidModuleSystem): Icon {
      return getAndroidModuleIcon(androidModuleSystem.type)
    }

    fun getAndroidModuleIcon(androidProjectType: AndroidModuleSystem.Type): Icon {
      return when (androidProjectType) {
        AndroidModuleSystem.Type.TYPE_NON_ANDROID -> AllIcons.Nodes.Module
        AndroidModuleSystem.Type.TYPE_APP -> StudioIcons.Shell.Filetree.ANDROID_MODULE
        AndroidModuleSystem.Type.TYPE_FEATURE, AndroidModuleSystem.Type.TYPE_DYNAMIC_FEATURE -> StudioIcons.Shell.Filetree.FEATURE_MODULE
        AndroidModuleSystem.Type.TYPE_INSTANTAPP -> StudioIcons.Shell.Filetree.INSTANT_APPS
        AndroidModuleSystem.Type.TYPE_LIBRARY -> StudioIcons.Shell.Filetree.LIBRARY_MODULE
        AndroidModuleSystem.Type.TYPE_TEST -> StudioIcons.Shell.Filetree.ANDROID_TEST_ROOT
        else -> StudioIcons.Shell.Filetree.ANDROID_MODULE
      }
    }
  }
}