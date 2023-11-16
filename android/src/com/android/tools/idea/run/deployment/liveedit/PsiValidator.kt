/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit

import com.android.tools.idea.log.LogWrapper
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import kotlin.system.measureTimeMillis

private val logger = LogWrapper(Logger.getInstance(LiveEditOutputBuilder::class.java))

private class PsiState(leafNodes: List<LeafPsiElement>) {
  // This operation is O(1) for leaf nodes, since they directly contain their text.
  val text = leafNodes.map { it.text }

  override fun hashCode(): Int {
    return text.hashCode()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PsiState

    return text == other.text
  }
}

class PsiValidator {
  private var oldVisitors = mutableMapOf<KtFile, Visitor>()

  fun beforeChanges(ktFile: KtFile) {
    if (ktFile in oldVisitors) {
      return
    }
    val validateMs = measureTimeMillis {
      val visitor = Visitor()
      ktFile.accept(visitor)
      oldVisitors[ktFile] = visitor
    }
    logger.info("Live Edit: PSI validator traversed PSI in $validateMs ms")
  }

  fun validatePsiChanges(ktFile: KtFile): MutableList<LiveEditUpdateException> {
    val errors = mutableListOf<LiveEditUpdateException>()
    val validateMs = measureTimeMillis {
      val old = oldVisitors[ktFile]
      if (old == null) {
        logger.warning("No old PSI to diff against for ${ktFile.name}")
        return errors
      }
      val new = Visitor()
      ktFile.accept(new)

      errors.addIfNotNull(validateProperties(ktFile, old.properties, new.properties))
      errors.addIfNotNull(validateInitBlocks(ktFile, old.initBlocks, new.initBlocks))
      errors.addIfNotNull(validateConstructors(ktFile, old.primaryConstructors, new.primaryConstructors))
      errors.addIfNotNull(validateConstructors(ktFile, old.secondaryConstructors, new.secondaryConstructors))
    }
    logger.info("Live Edit: PSI validator checked PSI in $validateMs ms")
    return errors
  }

  fun validationFinished(ktFile: KtFile) {
    oldVisitors.remove(ktFile)
  }
}

private fun validateConstructors(ktFile: KtFile,
                                 oldConstructors: Map<List<FqName?>, PsiState>,
                                 newConstructors: Map<List<FqName?>, PsiState>): LiveEditUpdateException? {
  for (entry in oldConstructors) {
    val other = newConstructors[entry.key] ?: continue
    if (other != entry.value) {
      return LiveEditUpdateException(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_UNRECOVERABLE,
                                    "in $ktFile, modified constructor $entry.key", ktFile, null)
    }
  }
  return null
}

private fun validateInitBlocks(ktFile: KtFile, oldInit: Map<Int, PsiState>, newInit: Map<Int, PsiState>): LiveEditUpdateException? {
  // We don't care about the exact offsets of each init block, only that the relative order of blocks has remained the same.
  val old = oldInit.toSortedMap().map { it.value }
  val new = newInit.toSortedMap().map { it.value }
  if (old != new) {
    return LiveEditUpdateException(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_UNRECOVERABLE,
                                  "in $ktFile, modified init block", ktFile, null)
  }
  return null
}

private fun validateProperties(ktFile: KtFile, oldProps: Map<FqName, PsiState>, newProps: Map<FqName, PsiState>): LiveEditUpdateException? {
  for (entry in oldProps) {
    val other = newProps[entry.key] ?: continue
    if (other != entry.value) {
      return LiveEditUpdateException(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_UNRECOVERABLE,
                                    "in $ktFile, modified property ${entry.key.shortName()} of ${entry.key.parent()}", ktFile, null)
    }
  }
  return null
}

private class Visitor : KtTreeVisitorVoid() {
  val properties = mutableMapOf<FqName, PsiState>()
  val initBlocks = mutableMapOf<Int, PsiState>()
  val primaryConstructors = mutableMapOf<List<FqName?>, PsiState>()
  val secondaryConstructors = mutableMapOf<List<FqName?>, PsiState>()

  override fun visitPrimaryConstructor(constructor: KtPrimaryConstructor) {
    val params = constructor.valueParameters.map { it.kotlinFqName }
    primaryConstructors[params] = PsiState(flatten(constructor))
    super.visitPrimaryConstructor(constructor)
  }

  override fun visitSecondaryConstructor(constructor: KtSecondaryConstructor) {
    val params = constructor.valueParameters.map { it.kotlinFqName }
    secondaryConstructors[params] = PsiState(flatten(constructor))
    super.visitSecondaryConstructor(constructor)
  }

  override fun visitClassInitializer(initializer: KtClassInitializer) {
    initBlocks[initializer.startOffset] = PsiState(flatten(initializer))
    super.visitClassInitializer(initializer)
  }

  override fun visitProperty(property: KtProperty) {
    // Ignore val/var declarations inside of methods
    if (property.isLocal) {
      return
    }
    val fqName = property.kotlinFqName ?: return
    properties[fqName] = PsiState(flatten(property))
    super.visitProperty(property)
  }
}

// Traverses a PSI tree and returns a preorder traversal of all leaf nodes, ignoring whitespace and comments.
private fun flatten(elem: PsiElement): List<LeafPsiElement> {
  val leafs = mutableListOf<LeafPsiElement>()
  val queue = ArrayDeque<PsiElement>()
  queue.addFirst(elem)
  while (queue.isNotEmpty()) {
    val node = queue.removeFirst()
    if (node.firstChild == null && node is LeafPsiElement) {
      leafs.add(node)
    } else {
      // Add children in reverse order so that they appear in the queue in the same order as in the children array.
      // Using the getChildren() method of PsiElement doesn't work the same way; it misses child nodes for some reason.
      var child = node.lastChild
      while (child != null) {
        queue.addFirst(child)
        child = child.getPrevSiblingIgnoringWhitespaceAndComments()
      }
    }
  }
  return leafs
}
