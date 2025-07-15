/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes.android

import com.android.tools.idea.navigator.nodes.showBuildFilesInModule
import com.android.tools.idea.navigator.nodes.showInProjectBuildScriptsGroup
import com.android.tools.idea.projectsystem.BuildConfigurationSourceProvider
import com.android.tools.idea.projectsystem.getProjectSystem
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Queryable.PrintInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import icons.GradleIcons
import org.jetbrains.annotations.VisibleForTesting

class AndroidBuildScriptsGroupNode(project: Project, settings: ViewSettings)
  : ProjectViewNode<List<PsiDirectory?>?>(project, emptyList(), settings) {

  private lateinit var cachedScripts: List<BuildConfigurationSourceProvider.ConfigurationFile>

  override fun contains(file: VirtualFile): Boolean {
    if (!::cachedScripts.isInitialized) {
      cachedScripts = buildConfigurationSourceProvider().getBuildConfigurationFiles()
    }
    return cachedScripts.any { it.file == file }
  }

  override fun getChildren(): Collection<AbstractTreeNode<*>?> {
    cachedScripts = buildConfigurationSourceProvider().getBuildConfigurationFiles()
    val children = ArrayList<PsiFileNode>(cachedScripts.size)
    cachedScripts.forEach { configFile ->
      val psiFile = PsiManager.getInstance(myProject).findFile(configFile.file)
      if (psiFile != null) {
        if (showInProjectBuildScriptsGroup(psiFile)) {
          children.add(AndroidBuildScriptNode(myProject, psiFile, settings, configFile.displayName, configFile.groupOrder))
        }
      }
    }
    return children
  }

  override fun getWeight(): Int = 100 // Gradle scripts node should be at the end after all the modules

  override fun update(presentation: PresentationData) {
    presentation.presentableText = getNodeText(showBuildFilesInModule())
    presentation.setIcon(GradleIcons.Gradle)
  }

  override fun toTestString(printInfo: PrintInfo?): String = getNodeText(showBuildFilesInModule())

  private fun buildConfigurationSourceProvider(): BuildConfigurationSourceProvider {
    return project.getProjectSystem().getBuildConfigurationSourceProvider() ?: BuildConfigurationSourceProvider.EMPTY
  }

  @VisibleForTesting
  fun getNodeText(perModule: Boolean): String {
    return if (perModule) "Project Gradle Files" else "Gradle Scripts"
  }
}
