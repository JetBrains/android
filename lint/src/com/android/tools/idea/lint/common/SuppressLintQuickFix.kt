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
import com.android.tools.idea.gradle.declarative.DeclarativeLanguage
import com.android.tools.idea.lint.common.AndroidLintInspectionBase.LINT_INSPECTION_PREFIX
import com.android.tools.lint.detector.api.ClassContext
import com.google.common.base.Joiner
import com.google.common.base.Splitter
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.intention.AddAnnotationFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.codeInspection.SuppressionUtilCore
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.xml.XMLLanguage
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandQuickFix
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
import com.intellij.psi.SyntheticElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.name.ClassId
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

@Suppress("UnstableApiUsage")
class SuppressLintQuickFix(private val id: String, element: PsiElement? = null) :
  ModCommandQuickFix(), SuppressQuickFix {
  private val label = displayName(element, id)

  override fun isAvailable(project: Project, context: PsiElement): Boolean = true

  override fun isSuppressAll(): Boolean {
    return false
  }

  override fun getName(): String = label

  override fun getFamilyName(): String {
    return "Suppress"
  }

  override fun perform(project: Project, descriptor: ProblemDescriptor): ModCommand {
    return applyFix(descriptor.startElement, ActionContext.from(descriptor))
  }

  fun applyFix(element: PsiElement, context: ActionContext): ModCommand {
    // In the simple case, quick fixes don't venture outside the containing file of the PsiElement.
    // ModCommand.psiUpdate takes care of snapshotting the file by calling getWritable().
    fun simplePsiUpdater(applyFixFun: (PsiElement) -> Unit) =
      ModCommand.psiUpdate(element) { e, _ -> applyFixFun(e) }

    return when (element.language) {
      JavaLanguage.INSTANCE -> simplePsiUpdater(::handleJava)
      XMLLanguage.INSTANCE -> simplePsiUpdater(::handleXml)
      //GroovyLanguage -> simplePsiUpdater(::handleGroovy)
      KotlinLanguage.INSTANCE -> simplePsiUpdater(::handleKotlin)
      TomlLanguage -> simplePsiUpdater(::handleToml)
      DeclarativeLanguage.INSTANCE -> simplePsiUpdater(::handleDeclarative)
      else -> {
        // Suppressing lint checks tagged on things like icons, where we edit a different file.
        if (element is PsiFile) {
          // TODO: replace handleFile with a PSI-based implementation
          handleFile(element)
        }
        ModCommand.nop() // write is done outside PSI
      }
    }
  }

  @Throws(IncorrectOperationException::class)
  private fun handleXml(element: PsiElement) {
    val tag = PsiTreeUtil.getParentOfType(element, XmlTag::class.java, false) ?: return
    val file = if (tag is XmlFile) tag else tag.containingFile as? XmlFile ?: return
    val lintId = getLintId(id)
    addSuppressAttribute(file, tag, lintId)
  }

  @Throws(IncorrectOperationException::class)
  private fun handleJava(element: PsiElement) {
    val container = findJavaSuppressElement(element) ?: return
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
/*
  @Throws(IncorrectOperationException::class)
  private fun handleGroovy(element: PsiElement) {
    val file = if (element is PsiFile) element else element.containingFile ?: return
    val project = file.project
    val offset = element.textOffset
    addNoInspectionComment(project, file, offset)
  }
*/
  @Throws(IncorrectOperationException::class)
  private fun handleToml(element: PsiElement) {
    val file = if (element is PsiFile) element else element.containingFile ?: return
    val project = file.project
    val offset = element.textOffset
    addNoInspectionComment(project, file, offset, "#")
  }

  @Throws(IncorrectOperationException::class)
  private fun handleDeclarative(element: PsiElement) {
    val file = if (element is PsiFile) element else element.containingFile ?: return
    val project = file.project
    val offset = element.textOffset
    addNoInspectionComment(project, file, offset)
  }

  /**
   * Given a file and offset of a statement, inserts a //noinspection <id> comment on the
   * **previous** line.
   */
  private fun addNoInspectionComment(
    project: Project,
    file: PsiFile,
    offset: Int,
    commentPrefix: String = "//",
  ) {
    val documentManager = PsiDocumentManager.getInstance(project)
    val document = file.viewProvider.document ?: return
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
          getLintId(id) + ",",
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
    document.insertString(
      lineStart + nonSpace,
      commentPrefix +
        NO_INSPECTION_PREFIX +
        getLintId(id) +
        "\n" +
        linePrefix.substring(0, nonSpace),
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

    if (element.isKotlinScript()) return handleKotlinScript(element)
    val target = findKotlinSuppressElement(element) ?: return

    when (target) {
      is KtModifierListOwner -> {
        val argument = "\"${getLintId(id)}\""
        target.addAnnotation(
          ClassId.fromString(ClassContext.getInternalName(getAnnotationClass(element))),
          argument,
          whiteSpaceText = if (target.isNewLineNeededForAnnotation()) "\n" else " ",
          addToExistingAnnotation = { entry -> addArgumentToAnnotation(entry, argument) },
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

  @Throws(IncorrectOperationException::class)
  private fun handleKotlinScript(element: PsiElement) {
    val file = if (element is PsiFile) element else element.containingFile ?: return
    val project = file.project
    val offset = element.textOffset
    addNoInspectionComment(project, file, offset)
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
      id: String,
    ) {
      val annotationName = getAnnotationClass(container)
      val annotation = AnnotationUtil.findAnnotation(modifierOwner, annotationName)
      val newAnnotation = createNewAnnotation(project, container, annotation, id)
      if (newAnnotation != null) {
        if (annotation != null && annotation !is SyntheticElement) {
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
      id: String,
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
                  container,
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
      val psiFactory = KtPsiFactory(entry.project)
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

    private fun PsiElement.isKotlinScript(): Boolean {
      return this.containingFile?.name?.endsWith(".kts") ?: false
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
          if (element.isKotlinScript())
            LintBundle.message("android.lint.fix.suppress.lint.api.comment", id)
          else if (target is KtDeclaration)
            LintBundle.message("android.lint.fix.suppress.lint.api.annotation", id)
          else LintBundle.message("android.lint.fix.suppress.lint.api.comment", id)
        }
        //GroovyLanguage -> LintBundle.message("android.lint.fix.suppress.lint.api.comment", id)
        DeclarativeLanguage.INSTANCE ->
          LintBundle.message("android.lint.fix.suppress.lint.api.comment", id)
        else -> "Suppress $id"
      }
    }
  }
}
