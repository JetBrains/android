/*
 * Copyright (C) 2019 The Android Open Source Project
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
package org.jetbrains.android.refactoring.renaming

import com.android.ide.common.resources.ValueResourceNameValidator
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.res.psi.ResourceReferencePsiElement
import com.intellij.ide.TitledHandler
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenameDialog
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.rename.RenamePsiElementProcessor

/**
 * Custom Rename processor that accepts ResourceReferencePsiElement and renames all corresponding references to that resource.
 */
class ResourceReferenceRenameProcessor : RenamePsiElementProcessor() {

  override fun canProcessElement(element: PsiElement): Boolean {
    return StudioFlags.RESOLVE_USING_REPOS.get() &&
           ResourceReferencePsiElement.create(element)?.toWritableResourceReferencePsiElement() != null
  }

  override fun substituteElementToRename(element: PsiElement, editor: Editor?): PsiElement? {
    return when (element) {
      is ResourceReferencePsiElement -> element.toWritableResourceReferencePsiElement()
      else -> ResourceReferencePsiElement.create(element)?.toWritableResourceReferencePsiElement()
    }
  }

  override fun getPostRenameCallback(element: PsiElement, newName: String, elementListener: RefactoringElementListener): Runnable? {
    return Runnable { (element as? ResourceReferencePsiElement)?.psiManager?.dropResolveCaches() }
  }
}

/**
 * [RenameHandler] for Android Resources.
 */
class ResourceRenameHandler : RenameHandler, TitledHandler {
  override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
    if (!StudioFlags.RESOLVE_USING_REPOS.get()) {
      return false
    }
    val element = CommonDataKeys.PSI_ELEMENT.getData(dataContext) ?: return false
    return ResourceReferencePsiElement.create(element)?.toWritableResourceReferencePsiElement() != null
  }

  override fun isRenaming(dataContext: DataContext): Boolean {
    return isAvailableOnDataContext(dataContext)
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext) {
    val element = CommonDataKeys.PSI_ELEMENT.getData(dataContext) ?: return
    val referencePsiElement = ResourceReferencePsiElement.create(element)?.toWritableResourceReferencePsiElement() ?: return
    val renameDialog = ResourceRenameDialog(project, referencePsiElement, null, editor)
    RenameDialog.showRenameDialog(dataContext, renameDialog)
  }

  override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext) {
    val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return
    val file = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return
    invoke(project, editor, file, dataContext)
  }

  override fun getActionTitle(): String {
    return "Rename Android Resource"
  }

  /**
   * Custom [RenameDialog] for renaming Android resources.
   */
  private class ResourceRenameDialog internal constructor(
    project: Project,
    resourceReferenceElement: ResourceReferencePsiElement,
    nameSuggestionContext: PsiElement?,
    editor: Editor?
  ) : RenameDialog(project, resourceReferenceElement, nameSuggestionContext, editor) {

    override fun canRun() {
      val name = newName
      val errorText = ValueResourceNameValidator.getErrorText(name, null)
      if (errorText != null) {
        throw ConfigurationException(errorText)
      }
    }
  }
}
