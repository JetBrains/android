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

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.PsiElementNavigationItem
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.psi.PsiFile
import com.intellij.usages.ConfigurableUsageTarget
import com.intellij.usages.PsiElementUsageTarget
import com.intellij.usages.UsageTarget
import javax.swing.Action

/**
 * The UsageView (preview) window includes a toolbar ribbon, which itself includes a settings button if:
 * - the refactoring usageViewDescriptor contains at least one target, and;
 * - the target is a ConfigurableUsageTarget.
 *
 * In order to work around the bug in the UsageView leading to inconsistent tree views when findUsages is re-executed, we always have
 * a target, generating [PsiElement2UsageTargetAdapter]s from our [WrappedPsiElement]s.  However: the PsiElement2UsageTargetAdapter
 * implements [ConfigurableUsageTarget], which leads to a settings icon even if our refactoring has no meaningful settings; and the
 * implementation of showSettings() on [PsiElement2UsageTargetAdapter] in any case does not do what we want, in a non-overrideable way.
 *
 * Therefore, instead of using raw [PsiElement2UsageTargetAdapter]s, we wrap them in one of these delegating classes: one which implements
 * [UsageTarget], for use when there is no suitable showSettings() action, and one which implements [ConfigurableUsageTarget], when there
 * is.
 */
internal class WrappedUsageTarget(
  private val usageTarget: PsiElement2UsageTargetAdapter
): PsiElementUsageTarget by usageTarget,
   DataProvider by usageTarget,
   PsiElementNavigationItem by usageTarget,
   ItemPresentation by usageTarget,
   UsageTarget by usageTarget {
  // We need these overrides (here and in WrappedConfigurableUsageTarget) because of multiple implementations
  override fun canNavigate() = usageTarget.canNavigate()
  override fun navigate(requestFocus: Boolean) = usageTarget.navigate(requestFocus)
  override fun getName() = usageTarget.getName()
  override fun findUsages() = usageTarget.findUsages()
  override fun canNavigateToSource() = usageTarget.canNavigateToSource()
  override fun isValid() = usageTarget.isValid()
  override fun getPresentation() = usageTarget.getPresentation()
  // We need these because otherwise the default methods get called
  override fun findUsagesInEditor(editor: FileEditor) = usageTarget.findUsagesInEditor(editor)
  override fun highlightUsages(file: PsiFile, editor: Editor, clearHighlights: Boolean) =
    usageTarget.highlightUsages(file, editor, clearHighlights)
  override fun isReadOnly() = usageTarget.isReadOnly()
  override fun getFiles() = usageTarget.getFiles()
  override fun update() = usageTarget.update()
}

internal class WrappedConfigurableUsageTarget(
  private val usageTarget: PsiElement2UsageTargetAdapter,
  private val showSettingsAction: Action
): PsiElementUsageTarget by usageTarget,
   DataProvider by usageTarget,
   PsiElementNavigationItem by usageTarget,
   ItemPresentation by usageTarget,
   ConfigurableUsageTarget by usageTarget {
  override fun canNavigate() = usageTarget.canNavigate()
  override fun navigate(requestFocus: Boolean) = usageTarget.navigate(requestFocus)
  override fun getName() = usageTarget.getName()
  override fun findUsages() = usageTarget.findUsages()
  override fun canNavigateToSource() = usageTarget.canNavigateToSource()
  override fun isValid() = usageTarget.isValid()
  override fun getPresentation() = usageTarget.getPresentation()

  override fun findUsagesInEditor(editor: FileEditor) = usageTarget.findUsagesInEditor(editor)
  override fun highlightUsages(file: PsiFile, editor: Editor, clearHighlights: Boolean) =
    usageTarget.highlightUsages(file, editor, clearHighlights)
  override fun isReadOnly() = usageTarget.isReadOnly()
  override fun getFiles() = usageTarget.getFiles()
  override fun update() = usageTarget.update()

  override fun showSettings() = showSettingsAction.actionPerformed(null)
}