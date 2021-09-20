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

import com.android.SdkConstants
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.lang.proguardR8.ProguardR8FileType
import com.android.tools.idea.projectsystem.getHolderModule
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Queryable.PrintInfo
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import icons.GradleIcons
import org.jetbrains.android.facet.AndroidRootUtil

class AndroidBuildScriptsGroupNode(project: Project, settings: ViewSettings)
  : ProjectViewNode<List<PsiDirectory?>?>(project, emptyList(), settings) {

  override fun contains(file: VirtualFile): Boolean = getBuildScriptsWithQualifiers().containsKey(file)

  override fun getChildren(): Collection<AbstractTreeNode<*>?> {
    val scripts = getBuildScriptsWithQualifiers()
    val children = ArrayList<PsiFileNode>(scripts.size)
    for ((key, value) in scripts) {
      addPsiFile(children, key, value)
    }
    return children
  }

  private fun getBuildScriptsWithQualifiers(): Map<VirtualFile, String> {
    val buildScripts = mutableMapOf<VirtualFile, String>()
    for (module in ModuleManager.getInstance(myProject).modules) {
      val moduleName = getPrefixForModule(module) + module.getHolderModule().name
      val gradleBuildFile = GradleUtil.getGradleBuildFile(module)
      if (gradleBuildFile != null) {
        buildScripts[gradleBuildFile] = moduleName
      }

      // include all .gradle and ProGuard files from each module
      for (file in findAllGradleScriptsInModule(module)) {
        buildScripts[file] =
          if (file.fileType === getProguardFileType()) {
            String.format("ProGuard Rules for %1\$s", module.getHolderModule().name)
          }
          else {
            moduleName
          }
      }
    }
    val projectRootFolder = myProject.baseDir
    if (projectRootFolder != null) {
      // Should not happen, but we have reports that there is a NPE in this area.
      findChildAndAddToMapIfFound(
        SdkConstants.FN_SETTINGS_GRADLE, projectRootFolder, "Project Settings", buildScripts)
      findChildAndAddToMapIfFound(
        SdkConstants.FN_SETTINGS_GRADLE_KTS, projectRootFolder, "Project Settings", buildScripts)
      findChildAndAddToMapIfFound(
        SdkConstants.FN_GRADLE_PROPERTIES, projectRootFolder, "Project Properties", buildScripts)
      projectRootFolder.findFileByRelativePath(FileUtilRt.toSystemIndependentName(GradleUtil.GRADLEW_PROPERTIES_PATH))?.let {
        buildScripts[it] = "Gradle Version"
      }
      projectRootFolder.findChild("gradle")?.takeIf { it.isDirectory }?.let { gradle ->
        gradle.children.filter { !it.isDirectory && it.name.endsWith(".versions.toml") }.forEach {
          buildScripts[it] = "Version Catalog"
        }
      }
      findChildAndAddToMapIfFound(SdkConstants.FN_LOCAL_PROPERTIES, projectRootFolder, "SDK Location", buildScripts)
    }
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      val userSettingsFile = GradleUtil.getGradleUserSettingsFile()
      if (userSettingsFile != null) {
        val file = VfsUtil.findFileByIoFile(userSettingsFile, false)
        if (file != null) {
          buildScripts[file] = "Global Properties"
        }
      }
    }
    return buildScripts
  }

  private fun addPsiFile(psiFileNodes: MutableList<PsiFileNode>, file: VirtualFile, qualifier: String) {
    val psiFile = PsiManager.getInstance(myProject).findFile(file)
    if (psiFile != null) {
      psiFileNodes.add(AndroidBuildScriptNode(myProject, psiFile, settings, qualifier))
    }
  }

  override fun getWeight(): Int = 100 // Gradle scripts node should be at the end after all the modules

  override fun update(presentation: PresentationData) {
    presentation.presentableText = "Gradle Scripts"
    presentation.setIcon(GradleIcons.Gradle)
  }

  override fun toTestString(printInfo: PrintInfo?): String? = "Gradle Scripts"
}

private fun getProguardFileType(): FileType = ProguardR8FileType.INSTANCE

private fun findChildAndAddToMapIfFound(
  childName: String,
  parent: VirtualFile,
  value: String,
  map: MutableMap<VirtualFile, String>
) {
  val child = parent.findChild(childName)
  if (child != null) {
    map[child] = value
  }
}

private fun getPrefixForModule(module: Module): String {
  return if (GradleUtil.isRootModuleWithNoSources(module)) AndroidBuildScriptNode.PROJECT_PREFIX
  else AndroidBuildScriptNode.MODULE_PREFIX
}

private fun findAllGradleScriptsInModule(module: Module): List<VirtualFile> {
  val moduleRootFolderPath = AndroidRootUtil.findModuleRootFolderPath(module) ?: return emptyList()
  val moduleRootFolder = VfsUtil.findFileByIoFile(moduleRootFolderPath, false)?.takeUnless { it.children == null } ?: return emptyList()

  val files = mutableListOf<VirtualFile>()
  for (child in moduleRootFolder.children) {
    if (!child.isValid ||
        child.isDirectory ||
        (
          !child.name.endsWith(SdkConstants.EXT_GRADLE) &&
          !child.name.endsWith(SdkConstants.EXT_GRADLE_KTS) &&
          child.fileType !== getProguardFileType()
        )
    ) {
      continue
    }

    // TODO: When a project is imported via unit tests, there is a ijinitXXXX.gradle file created somehow, exclude that.
    if (ApplicationManager.getApplication().isUnitTestMode &&
        (child.name.startsWith("ijinit") || child.name.startsWith("asLocalRepo"))) {
      continue
    }
    files.add(child)
  }
  return files
}
