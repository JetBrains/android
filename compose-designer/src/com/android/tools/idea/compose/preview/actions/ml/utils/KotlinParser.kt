/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.actions.ml.utils

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtBlockCodeFragment
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtCodeFragment.Companion.IMPORT_SEPARATOR
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespace
import org.jetbrains.kotlin.resolve.ImportPath

internal class KotlinParser(val project: Project, val context: PsiElement) {

  private fun PsiElement.getPrecedingCommentsString(): String {
    val builder = StringBuilder()
    var prev = (if (prevSibling == null) parent else this).getPrevSiblingIgnoringWhitespace()
    while (prev != null && prev is PsiComment) {
      builder.insert(0, "\n")
      builder.insert(0, prev.text)
      prev = prev.getPrevSiblingIgnoringWhitespace()
    }
    return builder.toString()
  }

  private fun getErrors(psi: PsiElement): List<String> {
    val allErrors =
      ReadAction.nonBlocking<List<PsiErrorElement>> {
          psi.collectDescendantsOfType<PsiErrorElement>()
        }
        .inSmartMode(project)
        .executeSynchronously()

    // Companion objects aren't parsed correctly, but we can identify them by the signature of the
    // syntax error they cause.
    return allErrors
      .filterNot {
        it.errorDescription ==
          "Unexpected tokens (use ';' to separate expressions on the same line)" &&
          runReadAction { it.prevSibling.text } == "companion"
      }
      .map { it.errorDescription }
  }

  private fun makePsiFragment(code: String): KtBlockCodeFragment {
    return WriteCommandAction.runWriteCommandAction<KtBlockCodeFragment>(project) {
      val (imports, content) = code.splitImportsFromContent()

      val importPaths =
        imports.lines().map { it.trim().removePrefix("import ") }.filter { it.isNotEmpty() }

      val fragment =
        KtBlockCodeFragment(
          project,
          "fragment.kt",
          content,
          importPaths.ifEmpty { null }?.joinToString(IMPORT_SEPARATOR),
          context,
        )

      if (importPaths.isNotEmpty()) {
        val importStatements =
          importPaths.map {
            KtPsiFactory(project)
              .createImportDirective(
                ImportPath(fqName = FqName(it.removeSuffix(".*")), isAllUnder = it.endsWith("*"))
              )
          }

        val fragmentFile = fragment.containingFile

        fragmentFile.addBefore(
          KtPsiFactory(project).createWhiteSpace("\n"),
          fragmentFile.firstChild,
        )
        importStatements.reversed().forEach { import ->
          fragmentFile.addAfter(
            KtPsiFactory(project).createWhiteSpace("\n"),
            fragmentFile.addBefore(import, fragmentFile.firstChild),
          )
        }
      }

      fragment
    }
  }

  private fun makeCodeFragments(psiFragment: KtBlockCodeFragment): List<KotlinCodeFragment> {

    return ReadAction.nonBlocking<List<KotlinCodeFragment>> {
        val imports = psiFragment.importsToString().split(IMPORT_SEPARATOR).joinToString("\n")

        val importsFragment =
          if (imports.isEmpty()) {
            null
          } else {
            KotlinCodeFragment(KotlinCodeFragmentType.IMPORTS, imports)
          }

        val body = psiFragment.getContentElement()
        val statements = body.statements
        val children = body.getChildrenOfType<PsiElement>().toList()

        val bodyFragments =
          if (children.isEmpty()) {
            val comments = psiFragment.getChildrenOfType<PsiComment>().toList()
            if (comments.isNotEmpty()) {
              comments.map { KotlinCodeFragment(KotlinCodeFragmentType.COMMENT, it.text) }
            } else emptyList()
          } else if (statements.isEmpty() || statements.any { it !is KtDeclaration }) {
            // Must be code block meant to be inserted into the body of a method, lambda, etc.
            listOf(
              KotlinCodeFragment(
                KotlinCodeFragmentType.BLOCK,
                body.getPrecedingCommentsString() + body.text,
              )
            )
          } else {
            // Is a list of classes or declarations (functions, properties, etc.)
            statements.mapNotNull {
              if (it is KtClass) {
                if (it is KtLightClassForFacade) {
                  null
                } else {
                  KotlinCodeFragment(
                    KotlinCodeFragmentType.CLASS,
                    it.getPrecedingCommentsString() + it.text,
                  )
                }
              } else {
                KotlinCodeFragment(
                  KotlinCodeFragmentType.DECLARATION,
                  it.getPrecedingCommentsString() + it.text,
                )
              }
            }
          }
        (listOf(importsFragment) + bodyFragments).filterNotNull()
      }
      .inSmartMode(project)
      .executeSynchronously()
  }

  fun parse(response: String): KotlinCodeBlock {
    val fixedPsiFragment = makePsiFragment(response)

    // Look for syntax errors
    val errors = getErrors(fixedPsiFragment)

    val codeFragments =
      if (errors.isNotEmpty()) emptyList() else makeCodeFragments(fixedPsiFragment)

    return KotlinCodeBlock(text = response, fragments = codeFragments)
  }
}

/**
 * Returns the original string, assumed to be a code snippet, split into two chunks. The first chunk
 * consists of all lines that start with "import ", and the second all lines that don't, both in
 * order.
 */
private fun String.splitImportsFromContent(): Pair<String, String> =
  lines()
    .partition { it.startsWith("import ") }
    .run { Pair(first.joinToString("\n").trim(), second.joinToString("\n").trim()) }
