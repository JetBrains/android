/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.tree

import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import kotlin.math.absoluteValue

/**
 * Action for navigating to the currently selected node in the layout inspector.
 */
object GotoDeclarationAction : AnAction("Go to Declaration") {
  override fun actionPerformed(event: AnActionEvent) {
    findNavigatable(event)?.navigate(true)
  }

  private fun findNavigatable(event: AnActionEvent): Navigatable? {
    return model(event)?.let { findNavigatable(it) }
  }

  fun findNavigatable(model: InspectorModel): Navigatable? {
    val resourceLookup = model.resourceLookup
    val node = model.selection ?: return null
    if (node is ComposeViewNode) {
      return findNavigatableFromComposable(model, node)
    }
    else {
      return resourceLookup.findFileLocation(node)?.navigatable
    }
  }

  private fun findNavigatableFromComposable(model: InspectorModel, node: ComposeViewNode): Navigatable? {
    val project = model.project
    val ktFile = findKotlinFile(project, node.composeFilename, node.composePackageHash) ?: return null
    val vFile = ktFile.getVirtualFile() ?: return null
    return PsiNavigationSupport.getInstance().createNavigatable(project, vFile, node.composeOffset)
  }

  /**
   * Find the kotlin file from the filename found in the tooling API.
   *
   * If there are multiple files with the same name chose the one where the package name hash matches.
   */
  private fun findKotlinFile(project: Project, fileName: String, packageHash: Int): PsiFile? {
    val files = FilenameIndex.getFilesByName(project, fileName, GlobalSearchScope.allScope(project))
    if (files.size == 1) {
      return files[0]
    }
    return files.asSequence().firstOrNull { packageNameHashMatch(it, packageHash) }
  }

  private fun packageNameHashMatch(file: PsiFile, packageHash: Int): Boolean {
    val classOwner = file as? PsiClassOwner ?: return false
    val packageName = classOwner.packageName
    val hash = packageName.fold(0) { hash, char -> hash * 31 + char.toInt() }.absoluteValue
    return hash == packageHash
  }

  private fun model(event: AnActionEvent): InspectorModel? = event.getData(LAYOUT_INSPECTOR_DATA_KEY)?.layoutInspectorModel
}
