/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.imports

import com.android.ide.common.repository.GradleCoordinate
import com.android.support.AndroidxNameUtils
import com.android.tools.idea.projectsystem.DependencyType
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.lint.detector.api.isKotlin
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.android.refactoring.isAndroidx

/**
 * An action which recognizes classes from key Maven artifacts and offers to add a dependency on them.
 *
 * TODO: Support in XML files
 */
class AndroidMavenImportIntentionAction : PsiElementBaseIntentionAction() {
  private var artifact: String? = null

  override fun invoke(project: Project, editor: Editor, element: PsiElement) {
    perform(project, element, editor.caretModel.offset, true)
  }

  fun perform(project: Project, element: PsiElement, offset: Int, sync: Boolean): ListenableFuture<ProjectSystemSyncManager.SyncResult>? {
    val module = ModuleUtil.findModuleForPsiElement(element) ?: return null
    // this.artifact should be the same, but make absolutely certain
    val artifact = findArtifact(project, element, offset) ?: return null
    addDependency(module, artifact)

    // Also add dependent annotation processor?
    MavenClassRegistry.findAnnotationProcessor(artifact)?.let { it ->
      val annotationProcessor = if (project.isAndroidx()) {
        AndroidxNameUtils.getCoordinateMapping(it)
      } else {
        it
      }

      addDependency(module, annotationProcessor, DependencyType.ANNOTATION_PROCESSOR)
    }

    return if (sync) {
      val projectSystem = project.getProjectSystem()
      val syncManager = projectSystem.getSyncManager()
      syncManager.syncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED, requireSourceGeneration = false)
    }
    else {
      null
    }
  }

  override fun getFamilyName(): String = "Add library dependency"

  override fun getText(): String {
    return when {
      artifact != null -> "Add dependency on $artifact"
      else -> familyName
    }
  }

  override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
    val module = ModuleUtil.findModuleForPsiElement(element) ?: return false
    artifact = findArtifact(project, element, editor?.caretModel?.offset ?: -1)
    artifact?.let { artifact ->
      // Make sure we aren't already depending on it
      return !dependsOn(module, artifact)
    }

    return false
  }

  private fun addDependency(module: Module, artifact: String, type: DependencyType = DependencyType.IMPLEMENTATION) {
    val coordinate = getCoordinate(artifact) ?: return
    val moduleSystem = module.getModuleSystem()
    moduleSystem.registerDependency(coordinate, type)
  }

  private fun dependsOn(module: Module, artifact: String): Boolean {
    val coordinate = getCoordinate(artifact) ?: return false
    val moduleSystem = module.getModuleSystem()
    return moduleSystem.getRegisteredDependency(coordinate) != null
  }

  private fun getCoordinate(artifact: String) = GradleCoordinate.parseCoordinateString("$artifact:+")

  private fun findElement(element: PsiElement, caret: Int): PsiElement {
    if (element is PsiIdentifier || caret == 0) {
      return element
    }

    // When the caret is at the end of the word (which it frequently is in the unresolved symbol
    // scenario, where you've just typed in the symbol you're interested in) PSI picks the element
    // on the right of the caret, which is the next element, not the symbol element.
    if (caret == element.textOffset || element is PsiWhiteSpace) {
      if (element.prevSibling != null) {
        return element.prevSibling
      }
      val targetOffset = caret - 1
      var curr = element.parent
      while (curr != null && curr.textOffset > targetOffset) {
        curr = curr.parent
      }
      if (curr != null) {
        return curr.findElementAt(targetOffset - curr.textOffset) ?: element
      }
    }

    return element
  }

  private fun findArtifact(project: Project, element: PsiElement, caret: Int): String? {
    val text = findElement(element, caret).text
    val artifact = MavenClassRegistry.findArtifact(text) ?: return null

    return if (project.isAndroidx()) {
      var androidx = AndroidxNameUtils.getCoordinateMapping(artifact)

      // Use Kotlin extension library if possible? We're basing this on
      // whether you're importing from a Kotlin file, not whether the project
      // contains Kotlin.
      if (isKotlin(element)) {
        androidx = MavenClassRegistry.findKtxLibrary(androidx) ?: androidx

      }

      androidx
    }
    else {
      artifact
    }
  }
}
