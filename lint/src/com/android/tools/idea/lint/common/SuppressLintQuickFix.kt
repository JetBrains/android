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
package com.android.tools.idea.lint.common

import com.android.SdkConstants
import com.android.SdkConstants.FQCN_SUPPRESS_LINT
import com.android.tools.idea.lint.common.AndroidLintInspectionBase.LINT_INSPECTION_PREFIX
import com.google.common.base.Joiner
import com.google.common.base.Splitter
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.intention.AddAnnotationFix
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils.prepareElementForWrite
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.codeInspection.SuppressionUtilCore
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiImportStatementBase
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiPackageStatement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.toml.lang.TomlLanguage

class SuppressLintQuickFix(private val id: String, element: PsiElement? = null) : SuppressQuickFix {
  private val label = displayName(element, id)

  override fun isAvailable(project: Project, context: PsiElement): Boolean = true

  override fun isSuppressAll(): Boolean {
    return false
  }

  override fun getName(): String = label

  override fun getFamilyName(): String {
    return "Suppress"
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement ?: return
    applyFix(element)
  }

  fun applyFix(element: PsiElement) {
    when (element.language) {
      JavaLanguage.INSTANCE -> handleJava(element)
      XMLLanguage.INSTANCE -> handleXml(element)
      GroovyLanguage -> handleGroovy(element)
      KotlinLanguage.INSTANCE -> handleKotlin(element)
      TomlLanguage -> handleToml(element)
      else -> {
        // Suppressing lint checks tagged on things like icons
        val file = if (element is PsiFile) element else element.containingFile ?: return
        handleFile(file)
      }
    }
  }

  @Throws(IncorrectOperationException::class)
  private fun handleXml(element: PsiElement) {
    val tag = PsiTreeUtil.getParentOfType(element, XmlTag::class.java, false) ?: return
    if (!prepareElementForWrite(tag)) {
      return
    }
    val file = if (tag is XmlFile) tag else tag.containingFile as? XmlFile ?: return
    val lintId = getLintId(id)
    addSuppressAttribute(file, tag, lintId)
  }

  @Throws(IncorrectOperationException::class)
  private fun handleJava(element: PsiElement) {
    val container = findJavaSuppressElement(element) ?: return
    if (!prepareElementForWrite(container)) {
      return
    }
    val project = element.project
    if (container is PsiImportStatementBase) {
      // Cannot annotate import statements; use //noinspection comment instead
      val offset = element.textOffset
      addNoInspectionComment(element.project, container.containingFile, offset)
    } else if (container is PsiModifierListOwner) {
      val lintId = id.removePrefix(LINT_INSPECTION_PREFIX)
      addSuppressAnnotation(project, container, container, lintId)
    }
  }

  @Throws(IncorrectOperationException::class)
  private fun handleGroovy(element: PsiElement) {
    val file = if (element is PsiFile) element else element.containingFile ?: return
    if (!prepareElementForWrite(file)) {
      return
    }
    val project = file.project
    val offset = element.textOffset
    addNoInspectionComment(project, file, offset)
  }

  @Throws(IncorrectOperationException::class)
  private fun handleToml(element: PsiElement) {
    val file = if (element is PsiFile) element else element.containingFile ?: return
    if (!prepareElementForWrite(file)) {
      return
    }
    val project = file.project
    val offset = element.textOffset
    addNoInspectionComment(project, file, offset, "#")
  }

  /**
   * Given a file and offset of a statement, inserts a //noinspection <id> comment on the
   * **previous** line.
   */
  private fun addNoInspectionComment(
    project: Project,
    file: PsiFile,
    offset: Int,
    commentPrefix: String = "//"
  ) {
    val documentManager = PsiDocumentManager.getInstance(project)
    val document = documentManager.getDocument(file) ?: return
    val line = document.getLineNumber(offset)
    val lineStart = document.getLineStartOffset(line)
    if (lineStart > 0) {
      val prevLineStart = document.getLineStartOffset(line - 1)
      val prevLineEnd = document.getLineEndOffset(line - 1)
      val prevLine = document.getText(TextRange(prevLineStart, prevLineEnd))
      val index = prevLine.indexOf(NO_INSPECTION_PREFIX)
      if (index != -1) {
        document.insertString(
          prevLineStart + index + NO_INSPECTION_PREFIX.length,
          getLintId(id) + ","
        )
        return
      }
    }
    val linePrefix = document.getText(TextRange(lineStart, offset))
    var nonSpace = 0
    while (nonSpace < linePrefix.length) {
      if (!Character.isWhitespace(linePrefix[nonSpace])) {
        break
      }
      nonSpace++
    }
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    document.insertString(
      lineStart + nonSpace,
      commentPrefix +
        NO_INSPECTION_PREFIX +
        getLintId(id) +
        "\n" +
        linePrefix.substring(0, nonSpace)
    )
    documentManager.commitDocument(document)
  }

  @Throws(IncorrectOperationException::class)
  private fun handleFile(file: PsiFile) {
    val virtualFile = file.virtualFile
    if (virtualFile != null) {
      val binaryFile = VfsUtilCore.virtualToIoFile(virtualFile)
      // Can't suppress lint checks inside a binary file (typically an icon): use
      // the lint XML facility instead
      val module = ModuleUtilCore.findModuleForPsiElement(file)
      if (module != null) {
        val dir = LintIdeProject.getLintProjectDirectory(module)
        if (dir != null) {
          val project = file.project
          val client = LintIdeSupport.get().createClient(project)
          val lintProject = client.getProject(dir, dir)
          val configuration = client.getConfiguration(lintProject, null)
          configuration.ignore(id, binaryFile)
        }
      }
    }
  }

  @Throws(IncorrectOperationException::class)
  private fun handleKotlin(element: PsiElement) {
    val target = findKotlinSuppressElement(element) ?: return

    if (!prepareElementForWrite(target)) {
      return
    }

    when (target) {
      is KtModifierListOwner -> {
        val argument = "\"${getLintId(id)}\""
        target.addAnnotation(
          FqName(getAnnotationClass(element)),
          argument,
          whiteSpaceText = if (target.isNewLineNeededForAnnotation()) "\n" else " ",
          addToExistingAnnotation = { entry -> addArgumentToAnnotation(entry, argument) }
        )
      }
      else -> {
        // Cannot annotate non-annotation owner elements; use //noinspection comment instead
        val offset = element.textOffset
        val file = target.containingFile
        addNoInspectionComment(file.project, file, offset)
      }
    }
  }

  override fun startInWriteAction(): Boolean {
    return true
  }

  companion object {
    private const val NO_INSPECTION_PREFIX = SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME + " "

    private fun getAnnotationClass(context: PsiElement): String {
      val project = context.project

      val module = ModuleUtilCore.findModuleForPsiElement(context)
      val scope = module?.getModuleWithDependenciesAndLibrariesScope(false)
      return when {
        scope != null &&
          JavaPsiFacade.getInstance(project).findClass(FQCN_SUPPRESS_LINT, scope) != null ->
          FQCN_SUPPRESS_LINT
        context.language == KotlinLanguage.INSTANCE -> "kotlin.Suppress"
        else -> "java.lang.SuppressWarnings"
      }
    }

    fun getLintId(intentionId: String): String {
      return intentionId.removePrefix(LINT_INSPECTION_PREFIX)
    }

    @Throws(IncorrectOperationException::class)
    private fun addSuppressAttribute(file: XmlFile, element: XmlTag, id: String) {
      val attribute = element.getAttribute(SdkConstants.ATTR_IGNORE, SdkConstants.TOOLS_URI)
      val value: String =
        if (attribute == null || attribute.value == null) {
          id
        } else {
          val ids = ArrayList<String>()
          for (existing in Splitter.on(',').trimResults().split(attribute.value!!)) {
            if (existing != id) {
              ids.add(existing)
            }
          }
          ids.add(id)
          ids.sort()
          Joiner.on(',').join(ids)
        }
      LintIdeSupport.get().ensureNamespaceImported(file, SdkConstants.TOOLS_URI, null)
      element.setAttribute(SdkConstants.ATTR_IGNORE, SdkConstants.TOOLS_URI, value)
    }

    // Based on the equivalent code in com.intellij.codeInsight.daemon.impl.actions.SuppressFix
    // to add @SuppressWarnings annotations

    @Throws(IncorrectOperationException::class)
    private fun addSuppressAnnotation(
      project: Project,
      container: PsiElement,
      modifierOwner: PsiModifierListOwner,
      id: String
    ) {
      val annotationName = getAnnotationClass(container)
      val annotation = AnnotationUtil.findAnnotation(modifierOwner, annotationName)
      val newAnnotation = createNewAnnotation(project, container, annotation, id)
      if (newAnnotation != null) {
        if (annotation != null && annotation.isPhysical) {
          annotation.replace(newAnnotation)
        } else {
          val attributes = newAnnotation.parameterList.attributes
          AddAnnotationFix(annotationName, modifierOwner, attributes)
            .invoke(project, null, container.containingFile) /*editor*/
        }
      }
    }

    private fun createNewAnnotation(
      project: Project,
      container: PsiElement,
      annotation: PsiAnnotation?,
      id: String
    ): PsiAnnotation? {
      if (annotation != null) {
        val currentSuppressedId = "\"" + id + "\""
        val annotationText = annotation.text
        if (!annotationText.contains("{")) {
          val attributes = annotation.parameterList.attributes
          if (attributes.size == 1) {
            val suppressedWarnings = attributes[0].text
            return if (suppressedWarnings.contains(currentSuppressedId)) null
            else
              JavaPsiFacade.getInstance(project)
                .elementFactory
                .createAnnotationFromText(
                  "@${getAnnotationClass(container)}({$suppressedWarnings, $currentSuppressedId})",
                  container
                )
          }
        } else {
          val curlyBraceIndex = annotationText.lastIndexOf('}')
          if (curlyBraceIndex > 0) {
            val oldSuppressWarning = annotationText.substring(0, curlyBraceIndex)
            return if (oldSuppressWarning.contains(currentSuppressedId)) null
            else
              JavaPsiFacade.getInstance(project)
                .elementFactory
                .createAnnotationFromText("$oldSuppressWarning, $currentSuppressedId})", container)
          }
        }
      } else {
        return JavaPsiFacade.getInstance(project)
          .elementFactory
          .createAnnotationFromText("@${getAnnotationClass(container)}(\"$id\")", container)
      }
      return null
    }

    private fun addArgumentToAnnotation(entry: KtAnnotationEntry, argument: String): Boolean {
      // add new arguments to an existing entry
      val args = entry.valueArgumentList
      val psiFactory = KtPsiFactory(entry)
      val newArgList = psiFactory.createCallArguments("($argument)")
      when {
        args == null -> // new argument list
        entry.addAfter(newArgList, entry.lastChild)
        args.arguments.isEmpty() -> // replace '()' with a new argument list
        args.replace(newArgList)
        args.arguments.none { it.textMatches(argument) } ->
          args.addArgument(newArgList.arguments[0])
      }

      return true
    }

    /**
     * Like [findJavaAnnotationTarget], but also includes other PsiElements where we can place
     * suppression comments
     */
    private fun findJavaSuppressElement(element: PsiElement): PsiElement? {
      // In addition to valid annotation targets we can also place suppress directives
      // using comments on import or package statements
      return findJavaAnnotationTarget(element)
        ?: element.getParentOfType<PsiImportStatementBase>(false)
          ?: element.getParentOfType<PsiPackageStatement>(false)
    }

    private fun findKotlinSuppressElement(element: PsiElement): PsiElement? {
      return PsiTreeUtil.findFirstParent(element, true) { it.isSuppressLintTarget() }
    }

    private fun PsiElement.isSuppressLintTarget(): Boolean {
      return this is KtDeclaration &&
        (this as? KtProperty)?.hasBackingField() ?: true &&
        this !is KtFunctionLiteral &&
        this !is KtDestructuringDeclaration &&
        this !is KtClassInitializer
      // We also allow placing suppression via comments on imports and package statements
      || this is KtImportDirective || this is KtPackageDirective
    }

    fun displayName(element: PsiElement?, inspectionId: String): String {
      val id = getLintId(inspectionId)
      return when (element?.language) {
        XMLLanguage.INSTANCE -> LintBundle.message("android.lint.fix.suppress.lint.api.attr", id)
        JavaLanguage.INSTANCE -> {
          val target = findJavaSuppressElement(element)
          if (target is PsiModifierListOwner)
            LintBundle.message("android.lint.fix.suppress.lint.api.annotation", id)
          else LintBundle.message("android.lint.fix.suppress.lint.api.comment", id)
        }
        KotlinLanguage.INSTANCE -> {
          val target = findKotlinSuppressElement(element)
          if (target is KtDeclaration)
            LintBundle.message("android.lint.fix.suppress.lint.api.annotation", id)
          else LintBundle.message("android.lint.fix.suppress.lint.api.comment", id)
        }
        GroovyLanguage -> LintBundle.message("android.lint.fix.suppress.lint.api.comment", id)
        else -> "Suppress $id"
      }
    }
  }
}
