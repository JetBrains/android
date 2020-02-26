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
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.core.util.getLineStartOffset
import org.jetbrains.kotlin.idea.debugger.SourceLineKind
import org.jetbrains.kotlin.idea.debugger.mapStacktraceLineToSource
import org.jetbrains.kotlin.idea.debugger.readBytecodeInfo
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.jvm.JvmClassName
import java.util.regex.Pattern

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

  // TODO: Add test for this method...
  private fun findNavigatableFromComposable(model: InspectorModel, node: ComposeViewNode): Navigatable? {
    val project = model.project
    val ktFile = findKotlinFile(project, node.composeFilename, node.composeMethod) ?: return null
    return findNavigatableFromDebugInfo(project, ktFile, node.composeMethod, node.composeLineNumber) ?:
           findNavigatableFromPsi(project, ktFile, node.composeMethod, node.unqualifiedName)
  }

  /**
   * Use the debug information created for the class to map the found line number.
   *
   * The line number found in the Compose tooling API is not the same as the physical line number. We can use
   * the debug information to map it back to the real line number.
   */
  private fun findNavigatableFromDebugInfo(project: Project, ktFile: KtFile, composeName: String, lineNumber: Int): Navigatable? {
    val vFile = ktFile.getVirtualFile() ?: return null
    val internalClassName = JvmClassName.byInternalName(composeName.substringBeforeLast(".invoke").replace(".", "/"))
    val info = readBytecodeInfo(project, internalClassName, vFile)
    val scope = GlobalSearchScope.allScope(project)
    val pair = info?.smapData?.let { mapStacktraceLineToSource(it, lineNumber, project, SourceLineKind.CALL_LINE, scope) }
    val line = pair?.second ?: return null
    // A negative line number is a clear indication that either the given line number or the debug information is faulty.
    // Fallback to psi.
    if (line < 1) {
      return null
    }
    val offset = (ktFile as PsiFile).getLineStartOffset(line) ?: return null
    return PsiNavigationSupport.getInstance().createNavigatable(project, vFile, offset)
  }

  /**
   * Attempt to find the lambda call in the kotlin psi.
   *
   * This method is a guess, it is not possible to make a perfect identification in all cases.
   *
   * <p> The approach is find the top level functions in the file that matches the method name given from the compose tooling API.
   * Then locate the function being called matching the callee seen in the tooling API. The latter is usually found as a lambda
   * invocation but may just be a simple function call.
   */
  private fun findNavigatableFromPsi(project: Project, ktFile: KtFile, composeName: String, callee: String): Navigatable? {
    val vFile = ktFile.getVirtualFile() ?: return null
    val method = ComposeMethod.parse(composeName) ?: return null
    val namedFunctions = PsiTreeUtil.findChildrenOfType(ktFile, KtNamedFunction::class.java).filter { it.name == method.methodName }
    val callsInMethod = namedFunctions.flatMap { PsiTreeUtil.findChildrenOfType(it, KtCallExpression::class.java) }
    val callOfCallee = callsInMethod.filter { it.calleeExpression?.text == callee }
    val anyCallOfCallee = callOfCallee.firstOrNull() as? PsiElement ?: return null
    return PsiNavigationSupport.getInstance().createNavigatable(project, vFile, anyCallOfCallee.startOffset)
  }

  /**
   * Find the kotlin file from the filename found in the tooling API.
   *
   * If there are multiple files with the same name
   */
  private fun findKotlinFile(project: Project, fileName: String, methodName: String): KtFile? {
    val files = FilenameIndex.getFilesByName(project, fileName, GlobalSearchScope.allScope(project))
    if (files.size == 1) {
      return files[0] as? KtFile
    }
    val qualifiedMethodName = ComposeMethod.parse(methodName)?.qualifiedName ?: return null
    return files.filterIsInstance(KtFile::class.java)
             .firstOrNull { file -> file.getClasses().any { it.qualifiedName == qualifiedMethodName } } ?: return null
  }

  private fun model(event: AnActionEvent): InspectorModel? = event.getData(LAYOUT_INSPECTOR_DATA_KEY)?.layoutInspectorModel
}

/**
 * Parse result of the method names received from Compose.
 */
@VisibleForTesting
data class ComposeMethod(
  val fileName: String,
  val packageName: String,
  val methodName: String,
  val levels: Int
) {
  val qualifiedName: String
    get() = "$packageName.$methodName"

  companion object {
    private val methodPattern = Pattern.compile("""((?:[a-z_0-9]+\.)+)(\w+\.)?(\w+)(?:\$(\w+))?((?:\$\d+)*\.invoke)?""")

    fun parse(method: String): ComposeMethod? {
      val match = methodPattern.matcher(method)
      if (!match.matches()) {
        return null
      }
      val packageName = match.group(1)?.substringBeforeLast(".") ?: ""
      val fileName = (match.group(2)?.substringBeforeLast(".") ?: match.group(3)).substringBeforeLast("Kt")
      val methodName = if (match.group(2) == null) match.group(4) else match.group(3)
      val levels = match.group(5)?.count { it == '$' } ?: 0
      return ComposeMethod(fileName, packageName, methodName, levels)
    }
  }
}
