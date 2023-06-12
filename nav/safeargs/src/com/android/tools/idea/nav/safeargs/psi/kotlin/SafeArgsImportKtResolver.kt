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
package com.android.tools.idea.nav.safeargs.psi.kotlin

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.nav.safeargs.psi.xml.SafeArgsXmlTag
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.popup.list.PopupListElementRenderer
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.idea.quickfix.QuickFixContributor
import org.jetbrains.kotlin.idea.quickfix.QuickFixes
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.resolve.asImportedFromObject
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.ListCellRenderer

/**
 * Registers an unresolved reference resolver in Kotlin files which recognizes classes from Safe Args kotlin classes
 */
class SafeArgsImportKtResolver : QuickFixContributor {
  override fun registerQuickFixes(quickFixes: QuickFixes) {
    if (!StudioFlags.NAV_SAFE_ARGS_SUPPORT.get()) return
    quickFixes.register(Errors.UNRESOLVED_REFERENCE, SafeArgsImportIntentionAction())
  }
}

private class SafeArgsImportIntentionAction : KotlinSingleIntentionActionFactory() {
  override fun createAction(diagnostic: Diagnostic): IntentionAction? {
    val element = diagnostic.psiElement as? KtNameReferenceExpression ?: return null
    val referenceName = element.getReferencedName()
    return AddImportAction(referenceName)
  }
}

private class AddImportAction(private val referenceName: String) : IntentionAction {
  private lateinit var suggestions: List<AutoImportVariant>

  private class AutoImportVariant(private val descriptorToImport: DeclarationDescriptor) {
    val importFqName = descriptorToImport.importableFqName

    fun declarationToImport(project: Project): PsiElement? {
      return DescriptorToSourceUtilsIde.getAnyDeclaration(project, descriptorToImport)
    }
  }

  override fun startInWriteAction() = false

  override fun getFamilyName() = text

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    if (file == null || editor == null) return false
    suggestions = collectSuggestions(file)
    if (suggestions.isEmpty()) return false
    return true
  }

  override fun getText() = AndroidBundle.message("android.safeargs.fix.import")

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    if (editor == null || file !is KtFile) return
    if (!this::suggestions.isInitialized || suggestions.isEmpty()) return

    PsiDocumentManager.getInstance(project).commitAllDocuments()

    if (suggestions.size == 1 || ApplicationManager.getApplication().isUnitTestMode) {
      suggestions.first().importFqName?.let { addImport(project, file, it) }
      return
    }

    // Copied from KotlinAddImportAction
    object : ListPopupImpl(project, getVariantSelectionPopup(project, file, suggestions)) {
      override fun getListElementRenderer(): ListCellRenderer<AutoImportVariant> {
        val baseRenderer = super.getListElementRenderer() as PopupListElementRenderer<AutoImportVariant>
        val psiRenderer = SafeArgsPsiElementCellRenderer()
        return ListCellRenderer { list, value, index, isSelected, cellHasFocus ->
          JPanel(BorderLayout()).apply {
            baseRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            add(baseRenderer.nextStepLabel, BorderLayout.EAST)
            add(
              psiRenderer.getListCellRendererComponent(
                list,
                value.declarationToImport(project),
                index,
                isSelected,
                cellHasFocus
              )
            )
          }
        }
      }
    }.showInBestPositionFor(editor)
  }

  private fun collectSuggestions(file: PsiFile): List<AutoImportVariant> {
    val module = ModuleUtil.findModuleForFile(file) ?: return emptyList()
    val nameIdentifier = Name.identifier(referenceName)
    return module.getDescriptorsByModulesWithDependencies()
      .values
      .flatten()
      .asSequence()
      .flatMap { descriptor ->
        descriptor.findVisibleClassesBySimpleName(nameIdentifier) +
        descriptor.findVisibleFunctionsBySimpleName(nameIdentifier)
      }
      .map { AutoImportVariant(it) }
      .filter { it.importFqName != null }
      .distinctBy { it.importFqName }
      .toList()
  }

  private fun PackageFragmentDescriptor.findVisibleClassesBySimpleName(name: Name): Sequence<DeclarationDescriptor> {
    return getMemberScope().getContributedDescriptors(DescriptorKindFilter.CLASSIFIERS) { it == name }.asSequence() +
           getMemberScope().getContributedDescriptors(DescriptorKindFilter.CLASSIFIERS).asSequence()
             .filterIsInstance<ClassDescriptor>()
             .mapNotNull { it.companionObjectDescriptor }
             .filter { it.name == name }
  }

  private fun PackageFragmentDescriptor.findVisibleFunctionsBySimpleName(name: Name): Sequence<DeclarationDescriptor> {
    return getMemberScope().getContributedDescriptors(DescriptorKindFilter.CLASSIFIERS).asSequence()
      .filterIsInstance<ClassDescriptor>()
      .mapNotNull { it.companionObjectDescriptor }
      .flatMap {
        ProgressManager.checkCanceled();
        it.unsubstitutedMemberScope.getContributedDescriptors(DescriptorKindFilter.FUNCTIONS).asSequence()
      }
      .filterIsInstance<FunctionDescriptor>()
      .map { it.asImportedFromObject() }
      .filter { it.fqNameSafe.shortName() == name }
  }

  private fun addImport(project: Project, file: KtFile, import: FqName) {
    project.executeWriteCommand(QuickFixBundle.message("add.import")) {
      val descriptor = file.resolveImportReference(import).firstOrNull() ?: return@executeWriteCommand
      ImportInsertHelper.getInstance(project).importDescriptor(file, descriptor)
    }
  }

  private fun getVariantSelectionPopup(
    project: Project,
    file: KtFile,
    suggestions: List<AutoImportVariant>
  ): BaseListPopupStep<AutoImportVariant> {
    return object : BaseListPopupStep<AutoImportVariant>(KotlinBundle.message("action.add.import.chooser.title"), suggestions) {
      override fun isAutoSelectionEnabled() = false

      override fun isSpeedSearchEnabled() = true
      override fun onChosen(selectedValue: AutoImportVariant?, finalChoice: Boolean): PopupStep<String>? {
        if (selectedValue == null || project.isDisposed || selectedValue.importFqName == null) return null

        if (finalChoice) {
          addImport(project, file, selectedValue.importFqName)
        }

        return null
      }
    }
  }
}

private class SafeArgsPsiElementCellRenderer : DefaultPsiElementCellRenderer() {
  override fun getContainerText(element: PsiElement?, name: String?): String? {
    val tag = element as? SafeArgsXmlTag ?: error("Invalid type of $element.")

    val baseText = "(generated from ${super.getContainerText(tag, name)})"
    val addOnText = tag.getContainerIdentifier()

    return if (addOnText.isEmpty()) baseText else "(in $addOnText) $baseText"
  }
}