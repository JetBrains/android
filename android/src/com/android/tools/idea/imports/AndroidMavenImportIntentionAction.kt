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
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.ImportHelper
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.ui.popup.list.ListPopupImpl
import org.jetbrains.android.refactoring.isAndroidx
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.util.ImportInsertHelperImpl
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * An action which recognizes classes from key Maven artifacts and offers to add a dependency on them.
 */
class AndroidMavenImportIntentionAction : PsiElementBaseIntentionAction() {
  private var intentionActionText: String = familyName
  private val mavenClassRegistryManager = MavenClassRegistryManager.getInstance()

  private data class AutoImportVariant(
    val artifactToAdd: String,
    val classToImport: String,
    val version: String?
  ) : Comparable<AutoImportVariant> {
    override fun compareTo(other: AutoImportVariant): Int {
      artifactToAdd.compareTo(other.artifactToAdd).let {
        if (it != 0) return it
      }

      return classToImport.compareTo(other.classToImport)
    }
  }

  override fun invoke(project: Project, editor: Editor, element: PsiElement) {
    perform(project, editor, element, true)
  }

  /**
   * Performs a fix. Or let users to choose from the popup if there's multiple options.
   */
  fun perform(project: Project, editor: Editor, element: PsiElement, sync: Boolean) {
    val leaf = findElement(element, editor.caretModel.offset)
    val text = leaf.text

    val suggestions = findLibraryData(project, text)
      .asSequence()
      .map {
        val artifact = resolveArtifact(project, leaf, it.artifact)
        val resolvedText = text.substringAfterLast('.')
        val importSymbol = resolveImport(project, "${it.packageName}.$resolvedText")
        AutoImportVariant(artifact, importSymbol, it.version)
      }
      .toSortedSet()

    if (suggestions.isEmpty()) return

    if (suggestions.size == 1 || ApplicationManager.getApplication().isUnitTestMode) {
      val suggestion = suggestions.first()
      perform(project, element, suggestion.artifactToAdd, suggestion.classToImport, true)
      return
    }

    chooseFromPopup(project, editor, element, suggestions.toList(), sync)
  }

  private fun chooseFromPopup(
    project: Project,
    editor: Editor,
    element: PsiElement,
    suggestions: List<AutoImportVariant>,
    sync: Boolean
  ) {
    val step = object : BaseListPopupStep<AutoImportVariant>(
      AndroidBundle.message("android.suggested.imports.title"),
      suggestions
    ) {
      override fun getTextFor(value: AutoImportVariant): String {
        return flagPreview(value.artifactToAdd, value.version)
      }

      override fun onChosen(selectedValue: AutoImportVariant, finalChoice: Boolean): PopupStep<*>? {
        perform(project, element, selectedValue.artifactToAdd, selectedValue.classToImport, sync)
        return FINAL_CHOICE
      }
    }

    ListPopupImpl(project, step).showInBestPositionFor(editor)
  }

  fun perform(
    project: Project,
    element: PsiElement,
    artifact: String,
    importSymbol: String?,
    sync: Boolean
  ): ListenableFuture<ProjectSystemSyncManager.SyncResult>? {
    var future: ListenableFuture<ProjectSystemSyncManager.SyncResult>? = null
    WriteCommandAction.runWriteCommandAction(project) {
      future = performWithLock(project, element, artifact, importSymbol, sync)
    }

    trackSuggestedImport(artifact)
    return future
  }

  /**
   * Imports a given artifact in the project, and optionally also imports the given symbol, which is
   * currently limited to classes but will be updated to support functions (for KTX in particular)
   * in a future CL.
   */
  private fun performWithLock(
    project: Project,
    element: PsiElement,
    artifact: String,
    importSymbol: String?,
    sync: Boolean
  ): ListenableFuture<ProjectSystemSyncManager.SyncResult>? {
    val module = ModuleUtil.findModuleForPsiElement(element) ?: return null

    // Import the class as well (if possible); otherwise it might be confusing that you have to invoke two
    // separate intention actions in order to get your symbol resolved
    if (importSymbol != null) {
      addImportStatement(project, element, importSymbol)
    }

    addDependency(module, artifact)

    // Also add dependent annotation processor?
    if (module.getModuleSystem().canRegisterDependency(DependencyType.ANNOTATION_PROCESSOR).isSupported()) {
      getMavenClassRegistry().findAnnotationProcessor(artifact)?.let { it ->
        val annotationProcessor = if (project.isAndroidx()) {
          AndroidxNameUtils.getCoordinateMapping(it)
        }
        else {
          it
        }

        addDependency(module, annotationProcessor, DependencyType.ANNOTATION_PROCESSOR)
      }
    }

    return if (sync) {
      val projectSystem = project.getProjectSystem()
      val syncManager = projectSystem.getSyncManager()
      syncManager.syncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED)
    }
    else {
      null
    }
  }

  override fun getFamilyName(): String = AndroidBundle.message("android.suggested.import.action.family.name")

  override fun getText(): String = intentionActionText

  override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
    val module = ModuleUtil.findModuleForPsiElement(element) ?: return false
    if (!module.getModuleSystem().canRegisterDependency().isSupported()) return false

    val leaf = findElement(element, editor?.caretModel?.offset ?: -1)
    val foundLibraries = findLibraryData(project, leaf.text)

    // If we are already depending on any of them, we just abort providing any suggestions as well.
    if (foundLibraries.isEmpty() || foundLibraries.any { dependsOn(module, it.artifact) }) return false

    // Update the text.
    intentionActionText = if (foundLibraries.size == 1) {
      val library = foundLibraries.single()
      val artifact = resolveArtifact(project, element, library.artifact)
      AndroidBundle.message("android.suggested.import.action.name.prefix", flagPreview(artifact, library.version))
    }
    else {
      familyName
    }

    return true
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
      // If you're pointing somewhere in the middle of a fully qualified name (such as an import statement
      // to a library that isn't available), the unresolved symbol won't be the final class, it will be the
      // first unavailable package segment. In these cases, search down the chain for the actual imported
      // class symbol and scan on that one instead.
      if (element.text[0].isLowerCase() && element.parent is PsiJavaCodeReferenceElement) {
        var curr: PsiJavaCodeReferenceElement = element.parent as PsiJavaCodeReferenceElement
        while (curr.parent is PsiJavaCodeReferenceElement) {
          curr = curr.parent as PsiJavaCodeReferenceElement
        }
        val referenceNameElement = curr.referenceNameElement
        if (referenceNameElement != null) {
          return referenceNameElement
        }
      }
      return element
    }
    else if (element is LeafPsiElement && element.elementType == KtTokens.IDENTIFIER) {
      if (element.text[0].isLowerCase() &&
          element.parent is KtNameReferenceExpression &&
          element.parent.parent is KtDotQualifiedExpression) {
        var curr: KtDotQualifiedExpression = element.parent.parent as KtDotQualifiedExpression
        while (curr.parent is KtDotQualifiedExpression) {
          curr = curr.parent as KtDotQualifiedExpression
        }
        val referenceNameElement = curr.selectorExpression
        if (referenceNameElement != null) {
          var left: PsiElement = referenceNameElement
          while (left.firstChild != null) {
            left = left.firstChild
          }
          return left
        }
      }
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

  private fun findLibraryData(project: Project, text: String): Collection<MavenClassRegistryBase.Library> {
    return getMavenClassRegistry().findLibraryData(text, project.isAndroidx())
  }

  private fun resolveArtifact(project: Project, element: PsiElement, artifact: String): String {
    return if (project.isAndroidx()) {
      var androidx = AndroidxNameUtils.getCoordinateMapping(artifact)

      // Use Kotlin extension library if possible? We're basing this on
      // whether you're importing from a Kotlin file, not whether the project
      // contains Kotlin.
      if (isKotlin(element)) {
        androidx = getMavenClassRegistry().findKtxLibrary(androidx) ?: androidx
      }

      androidx
    }
    else {
      artifact
    }
  }

  private fun resolveImport(project: Project, fqn: String): String {
    return if (project.isAndroidx()) {
      AndroidxNameUtils.getNewName(fqn)
    }
    else {
      fqn
    }
  }

  private fun addImportStatement(project: Project, element: PsiElement, import: String) {
    val file = element.containingFile
    if (file.text.contains(import)) { // either as import statement or fully qualified reference
      return
    }

    when (element.language) {
      JavaLanguage.INSTANCE -> {
        val factory = JavaPsiFacade.getInstance(project).elementFactory
        val dot = import.lastIndexOf('.')
        val pkg = import.substring(0, dot)
        val name = import.substring(dot + 1)
        val cls = factory.createClass(name)
        (cls.containingFile as PsiJavaFile).packageName = pkg
        val importHelper = ImportHelper(JavaCodeStyleSettings.getInstance(element.containingFile))
        importHelper.addImport(file as PsiJavaFile, cls)
      }
      KotlinLanguage.INSTANCE -> {
        // Can't access org.jetbrains.kotlin.idea.util.ImportInsertHelper
        ImportInsertHelperImpl.addImport(project, file as KtFile, FqName(import))
      }
      // Nothing to do in XML etc
    }
  }

  private fun getMavenClassRegistry(): MavenClassRegistryBase {
    return mavenClassRegistryManager.getMavenClassRegistry()
  }
}
