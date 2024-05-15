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

import com.intellij.lang.Language
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiFileRange
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.startOffset

/**
 * The [CodeMerger] can take two [PsiElement] trees, one for a user's source file, and the other for
 * an updated version of the file **or** some subset of it such as a particular method, and can then
 * match up the corresponding elements and apply code and comments from one into the other.
 *
 * It does this by computing a "signature" for each documented element, and then matches up classes,
 * inner classes, methods and fields using these signatures.
 */
internal class CodeMerger(private val project: Project) {

  private fun KtDeclaration.skip(): Boolean =
    this is KtParameter || this is KtProperty && this.isLocal || this is KtScript

  /**
   * Updates the given [map] such that it contains a mapping from signatures to the corresponding
   * PSI element. A signature is the fully qualified name within the file, not including package,
   * and for methods including the parameter list (types only). For example,
   * "Foo.Bar.call(String,Int)" corresponds to a method named call with arguments String and int
   * within a class named Bar nested within a class named Foo.
   */
  private fun collectElements(root: PsiElement, map: MutableMap<String, PsiElement>) {
    visitMembers(root) { element ->
      val signature =
        when (element) {
          is KtDeclaration -> element.getSignature()
          else -> error("internal error")
        }
      map[signature] = element
      true
    }
  }

  /** Visits classes (including nested and inner), methods, and fields */
  private fun visitMembers(target: PsiElement, visitor: (PsiElement) -> Boolean) {
    if (target is KtElement) {
      target.accept(
        object : KtTreeVisitorVoid() {
          override fun visitDeclaration(dcl: KtDeclaration) {
            if (!dcl.skip()) {
              if (!visitor(dcl)) {
                return
              }
            }
            super.visitDeclaration(dcl)
          }
        }
      )
    }
  }

  /**
   * Gets the element text of the [source] element, possibly adjusting the offset to match the
   * indentation at the offset pointed to be [destinationRange] in [destinationFile]
   */
  private fun getElementText(
    source: PsiElement,
    destinationFile: PsiFile,
    destinationRange: TextRange,
  ): String {
    val text = source.text

    val destinationIndent = getIndent(destinationFile, destinationRange.startOffset)
    val sourceIndent = getIndent(source.containingFile, source.startOffset)
    if (sourceIndent != destinationIndent && destinationIndent.startsWith(sourceIndent)) {
      val adjust = destinationIndent.substring(sourceIndent.length)
      val lines = text.split("\n")
      val remainder = lines.subList(1, lines.size)
      return (lines[0] + "\n" + remainder.joinToString("\n") { "$adjust$it" }).trim()
    }

    return text.trim()
  }

  /**
   * Returns the document obtained by merging the given [block] of code into the existing
   * [userFile].
   *
   * @param block The codeblocks to merge in.
   * @param userFile The existing file to apply changes with
   * @param language The language of the code block to merge in.
   */
  fun mergeBlock(block: KotlinCodeBlock, userFile: PsiFile, language: Language): Document? {
    val entries = mutableMapOf<String, PsiElement>()
    block.fragments.forEach { fragment -> collectElements(fragment.getPsi(userFile), entries) }
    if (entries.isEmpty()) {
      return null
    }

    val documentManager = PsiDocumentManager.getInstance(project)
    val originalDocument = documentManager.getDocument(userFile) ?: return null

    // Line up the generated code snippet with the editor, and add any comments
    // missing
    val codeStyleManager = CodeStyleManager.getInstance(project)
    val edits = mutableMapOf<TextRange, String>()
    visitMembers(userFile) { element ->
      var visitChildren = true
      val signature =
        when (element) {
          is KtDeclaration -> element.getSignature()
          else -> error("internal error")
        }
      val entry = entries[signature] ?: return@visitMembers true
      if (element !is KtClassOrObject && element !is PsiClass) {
        val range = element.textRange
        edits[range] = getElementText(entry, userFile, range)
        visitChildren = false
      }
      visitChildren
    }
    if (edits.isEmpty()) {
      return null
    }
    val psiFile =
      PsiFileFactory.getInstance(project).createFileFromText(language, originalDocument.text)
    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)!!

    WriteCommandAction.runWriteCommandAction(project) {
      val markers = mutableListOf<RangeMarker>()

      var prev: TextRange? = null
      for (range in edits.keys.sortedByDescending { it.startOffset }) {
        // make sure there's no overlap/nesting
        assert(prev == null || prev.startOffset > range.endOffset)
        val text = edits[range]!!
        document.replaceString(range.startOffset, range.endOffset, text)
        markers.add(
          document.createRangeMarker(TextRange(range.startOffset, range.startOffset + text.length))
        )
        prev = range
      }
      documentManager.commitDocument(document)

      val pointerManager = SmartPointerManager.getInstance(project)
      val psiMarkers = mutableListOf<SmartPsiFileRange>()
      for (marker in markers) {
        val resultSmartRange: SmartPsiFileRange =
          pointerManager.createSmartPsiFileRangePointer(psiFile, marker.textRange)
        psiMarkers.add(resultSmartRange)
      }

      // Reformat each span
      for (psiMarker in psiMarkers) {
        val range = psiMarker.psiRange
        if (range != null) {
          codeStyleManager.reformatRange(psiFile, range.startOffset, range.endOffset)
        }
      }
    }
    return document
  }

  /**
   * Returns the document obtained by appending the given [block] of code to the end of the existing
   * [userFile]. The block starts in a new line, so a line break is needed before inserting the full
   * string.
   */
  fun appendBlock(block: KotlinCodeBlock, userFile: PsiFile): Document {
    val project = userFile.project

    val documentManager = PsiDocumentManager.getInstance(project)
    val originalDocument =
      documentManager.getDocument(userFile)
        ?: throw RuntimeException("No document associated with ${userFile.name}")

    val psiFile =
      PsiFileFactory.getInstance(project)
        .createFileFromText(userFile.language, originalDocument.text)
    val mergedDocument = PsiDocumentManager.getInstance(project).getDocument(psiFile)!!

    WriteCommandAction.runWriteCommandAction(project) {
      mergedDocument.insertString(mergedDocument.textLength, block.text)
      documentManager.commitDocument(mergedDocument)
    }

    return mergedDocument
  }
}

private fun getIndent(file: PsiFile, offset: Int): CharSequence {
  val project = file.project
  val documentManager = PsiDocumentManager.getInstance(project)
  val document = documentManager.getDocument(file)?.immutableCharSequence ?: file.text
  val lineBegin = document.lastIndexOf('\n', offset) + 1
  return document.substring(lineBegin, offset)
}

/**
 * Simplifies a type parameter to just the basic types; e.g. `Int` stays `Int`, but
 * `java.util.Map<java.lang.String, Int>` becomes `Map<String,Int>`
 */
private fun String.simpleType(): String {
  for (c in this) {
    if (c == '.' || c.isWhitespace()) {
      val sb = StringBuilder(this.length)
      var j = 0
      val n = length

      while (j < n) {
        val d = this[j]
        if (d.isWhitespace()) {
          j++
        } else if (!d.isJavaIdentifierPart()) {
          sb.append(d)
          j++
        } else {
          // symbol start; see if it's part of a qualification: next
          // non-identifier char is a "." (not part of a vararg ellipsis)
          var scout = j + 1
          while (scout < n && this[scout].isJavaIdentifierPart()) scout++
          if (scout < n && this[scout] == '.' && !this.startsWith("..", scout)) {
            j = scout + 1
            continue
          }
          while (j < scout) {
            val e = this[j++]
            if (!e.isWhitespace()) {
              sb.append(e)
            }
          }
        }
      }
      return sb.toString()
    }
  }
  return this
}

private fun KtParameter.getTypeString(): String {
  val typeRef = typeReference?.text ?: return "?"
  return typeRef.simpleType()
}

private fun KtDeclaration.getSignature(): String {
  if (this is KtScript) {
    return ""
  }
  val nested =
    parent.parentOfType<KtDeclaration>()?.getSignature()?.let { if (it.isEmpty()) it else "$it." }
      ?: ""
  if (this is KtFunction) {
    val args = "(" + this.valueParameters.joinToString(",") { it.getTypeString() } + ")"
    return "$nested$name$args"
  }
  return "$nested$name"
}
