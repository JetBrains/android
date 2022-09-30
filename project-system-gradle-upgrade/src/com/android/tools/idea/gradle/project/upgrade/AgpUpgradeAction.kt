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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.GradleVersion
import com.android.ide.common.repository.GradleVersion.AgpVersion
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.repositories.IdeGoogleMavenRepository
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.actions.BaseRefactoringAction

class AgpUpgradeAction: BaseRefactoringAction() {
  override fun isAvailableInEditorOnly() = false
  override fun isEnabledOnDataContext(dataContext: DataContext) = true
  override fun isEnabledOnElements(elements: Array<out PsiElement>) = true
  override fun getHandler(dataContext: DataContext): RefactoringActionHandler? = AgpUpgradeActionHandler()
}

class AgpUpgradeActionHandler: RefactoringActionHandler {
  override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) = invoke(project)
  override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?)  = invoke(project)

  fun invoke(project: Project) {
    val current = project.findPluginInfo()?.pluginVersion ?: return
    val latestKnown = AgpVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())
    ApplicationManager.getApplication().executeOnPooledThread {
      val published = IdeGoogleMavenRepository.getAgpVersions()
      val state = computeGradlePluginUpgradeState(current, latestKnown, published)
      invokeLater(ModalityState.NON_MODAL) {
        showAndInvokeAgpUpgradeRefactoringProcessor(project, current, state.target)
      }
    }
  }
}