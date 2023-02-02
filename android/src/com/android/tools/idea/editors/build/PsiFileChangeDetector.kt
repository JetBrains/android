/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors.build

import com.google.common.base.Objects
import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiBinaryFile
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.templateLanguages.OuterLanguageElement
import com.intellij.psi.util.elementType
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtPsiUtil

fun PsiElement.toKeyableString(): String =
  toString() + (if (children.isEmpty()) "($text)" else "")

/**
 * A [PsiRecursiveElementWalkingVisitor] that filters basic changes and allows the caller additional filters.
 * @param onElementVisited called for every element with a unique key for the element.
 */
private class FilteredPsiRecursiveElementWalkingVisitor(
  private val onElementVisited: (PsiElement, Int) -> Boolean
) : PsiRecursiveElementWalkingVisitor() {
  private val filteredElementTypes = setOf(
    KtTokens.LBRACE,
    KtTokens.RBRACE,
    KtTokens.DOT,
    KtTokens.OPEN_QUOTE,
    KtTokens.CLOSING_QUOTE,
  )

  private fun key(element: PsiElement): Int = Objects.hashCode(element.toKeyableString())

  override fun visitElement(element: PsiElement) {
    if (element is KtImportDirective
      || KtPsiUtil.isInComment(element)
      || filteredElementTypes.contains(element.elementType)
      || element is KtAnnotationEntry
    ) return

    super.visitElement(element)
    if (!onElementVisited(element, key(element))) {
      stopWalking()
    }
  }

  override fun visitComment(comment: PsiComment) {}
  override fun visitBinaryFile(file: PsiBinaryFile) {}
  override fun visitWhiteSpace(space: PsiWhiteSpace) {}
  override fun visitOuterLanguageElement(element: OuterLanguageElement) {}
}

private const val LARGE_FILE_THRESHOLD_BYTES = 20_000 // bytes

/**
 * Interface to detect when changes have happend to a given [PsiFile].
 */
interface PsiFileChangeDetector {
  /**
   * Returns true if the given [file] has changed since the last [markFileAsUpToDate] or the last time this method was called with
   * [updateOnCheck] set to true.
   * @param file the file to check for changes.
   * @param updateOnCheck if true, this call will also mark the file as up to date after the calls.
   */
  fun hasFileChanged(file: PsiFile?, updateOnCheck: Boolean = false): Boolean

  /**
   * Marks the [file] as up to date. If no changes are done to the file, [hasFileChanged] will return false until the file changes.
   */
  fun markFileAsUpToDate(file: PsiFile?)

  /**
   * Clears the up to date mark from this file.
   */
  fun clearMarks(file: PsiFile?)

  /**
   * Same as [markFileAsUpToDate] but it will force recalculating the changes even when the file has not changed at all.
   */
  fun forceMarkFileAsUpToDate(file: PsiFile?) {
    clearMarks(file)
    markFileAsUpToDate(file)
  }

  companion object {
    @JvmStatic
    fun getInstance() = HashPsiFileChangeDetector()
  }
}

/**
 * A naive [PsiFileChangeDetector] that just uses the [PsiFile.getModificationStamp] to detect changes. This is very fast to execute but
 * it will also report the file as changed even for simple changes like space additions.
 * @param id an id to be used to allow multiple checkers to operate in the same file.
 */
private class NaivePsiFileChangeDetector(id: String) : PsiFileChangeDetector {
  private val marker = Key.create<Long>("${NaivePsiFileChangeDetector::class.java.name}#$id")

  override fun hasFileChanged(file: PsiFile?, updateOnCheck: Boolean): Boolean = file?.let {
    val hasChanged = file.getCopyableUserData(marker) != file.modificationStamp
    if (updateOnCheck) {
      markFileAsUpToDate(file)
    }
    return hasChanged
  } ?: false

  override fun markFileAsUpToDate(file: PsiFile?) {
    file?.putCopyableUserData(marker, file.modificationStamp)
  }

  override fun clearMarks(file: PsiFile?) {
    file?.putCopyableUserData(marker, null)
  }
}

/**
 * A more expensive [PsiFileChangeDetector] that ignores a number of trivial changes in the file like comment changes or addition/removal
 * of spaces.
 * [onElementVisited] will be called for every [PsiElement] visited by this detector.
 */
class HashPsiFileChangeDetector private constructor(private val onElementVisited: () -> Unit) : PsiFileChangeDetector {
  constructor() : this({})

  private val log: Logger = Logger.getInstance(HashPsiFileChangeDetector::class.java)

  private data class CalculatedHashValue(val lastPsiModificationStamp: Long, val lastHashValue: Int)

  private val naiveDetector = NaivePsiFileChangeDetector("${HashPsiFileChangeDetector::class.java.name}#$onElementVisited}")

  // The markers are unique for the given elementFilter.
  private val upToDateMarker = Key.create<Int>("${HashPsiFileChangeDetector::class.java.name}#$onElementVisited}")

  // Tis marker is used to avoid calling calculateFileHash for a file with 0 changes.
  private val lastCalculatedHashValueMarker = Key.create<CalculatedHashValue>(
    "${HashPsiFileChangeDetector::class.java.name}#hashMarker#$onElementVisited}"
  )

  /**
   * Calculate a hash for the given element. The hash only takes into account the elements that pass the [elementFilter] check.
   * This method will return a cached value if available.
   */
  private fun calculateFileHash(root: PsiFile): Int {
    root.getCopyableUserData(lastCalculatedHashValueMarker)?.let { (lastPsiModificationCheck, cachedHash) ->
      if (lastPsiModificationCheck == root.modificationStamp) {
        return cachedHash
      }
    }

    return ReadAction.compute<Int, Throwable> {
      var fileHash = 0
      root.accept(FilteredPsiRecursiveElementWalkingVisitor { _, elementKey ->
        onElementVisited()
        fileHash = 31 * fileHash + elementKey
        true
      })

      root.putCopyableUserData(lastCalculatedHashValueMarker, CalculatedHashValue(root.modificationStamp, fileHash))
      return@compute fileHash
    }
  }

  /**
   * Returns true if the given file is to large to be processed by this [PsiFileChangeDetector]. If this method returns true, only the
   * [NaivePsiFileChangeDetector] will be used.
   */
  private fun isFileTooLarge(file: PsiFile): Boolean = file.virtualFile.length > LARGE_FILE_THRESHOLD_BYTES

  private fun calculateAndCheckFileHash(file: PsiFile, updateOnCheck: Boolean): Boolean {
    val currentHash = file.getCopyableUserData(upToDateMarker)
    val newHash = calculateFileHash(file)
    if (updateOnCheck) {
      file.putCopyableUserData(upToDateMarker, newHash)
    }

    return newHash != currentHash
  }

  override fun hasFileChanged(file: PsiFile?, updateOnCheck: Boolean): Boolean = file?.let {
    // If the file is too large, or we are in power save mode, do a simple check.
    val isFileTooLarge = isFileTooLarge(file)
    if (isFileTooLarge || PowerSaveMode.isEnabled()) {
      if (isFileTooLarge) log.debug("$file is too large to be evaluated via HashPsiFileChangeDetector")
      return naiveDetector.hasFileChanged(file, updateOnCheck)
    }

    return calculateAndCheckFileHash(file, updateOnCheck)
  } ?: false

  override fun markFileAsUpToDate(file: PsiFile?) {
    file?.let {
      // First use the naive change detector to avoid running an expensive change calculation where there have been no changes at all
      if (!naiveDetector.hasFileChanged(file, false)) return

      val isFileTooLarge = isFileTooLarge(file)
      if (!isFileTooLarge && !PowerSaveMode.isEnabled()) {
        calculateAndCheckFileHash(file, true)
      } else {
        if (isFileTooLarge) log.debug("$file is too large to be evaluated via HashPsiFileChangeDetector")
      }
      naiveDetector.markFileAsUpToDate(file)
    }
  }

  override fun clearMarks(file: PsiFile?) {
    file?.let {
      file.putCopyableUserData(upToDateMarker, null)
      file.putCopyableUserData(lastCalculatedHashValueMarker, null)
      naiveDetector.clearMarks(file)
    }
  }

  companion object {
    @TestOnly
    fun forTest(onElementVisited: () -> Unit) = HashPsiFileChangeDetector(onElementVisited)
  }
}