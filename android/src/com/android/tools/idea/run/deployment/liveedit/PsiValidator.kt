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
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import kotlin.system.measureTimeMillis

private val logger = LogWrapper(Logger.getInstance(LiveEditOutputBuilder::class.java))

@RequiresReadLock
fun getPsiValidationState(psiFile: PsiFile): PsiState {
  val state = PsiState(psiFile)
  val traverseMs = measureTimeMillis {
    psiFile.accept(state)
  }
  logger.info("Live Edit: PSI validator traversed PSI in $traverseMs ms")
  return state
}

@RequiresReadLock
fun validatePsiChanges(old: PsiState?, new: PsiState): List<LiveEditUpdateException> {
  if (old == null) {
    logger.info("No PSI snapshot for ${new.psiFile.name}; it is likely a new file. Skipping PSI validation.")
    return emptyList()
  }
  if (old.psiFile != new.psiFile) {
    throw IllegalArgumentException("No reason to check differences between distinct PSI files")
  }
  val errors = mutableListOf<LiveEditUpdateException>()
  val validateMs = measureTimeMillis {
    errors.addIfNotNull(validateProperties(old.psiFile, old.properties, new.properties))
    errors.addIfNotNull(validateInitBlocks(old.psiFile, old.initBlocks, new.initBlocks))
    errors.addIfNotNull(validateConstructors(old.psiFile, old.primaryConstructors, new.primaryConstructors))
    errors.addIfNotNull(validateConstructors(old.psiFile, old.secondaryConstructors, new.secondaryConstructors))
  }
  logger.info("Live Edit: PSI validator checked PSI in $validateMs ms")
  return errors
}

private fun validateConstructors(psiFile: PsiFile,
                                 oldConstructors: Map<List<String?>, String>,
                                 newConstructors: Map<List<String?>, String>): LiveEditUpdateException? {
  for (entry in oldConstructors) {
    val other = newConstructors[entry.key] ?: continue
    if (other != entry.value) {
      return LiveEditUpdateException.unsupportedSourceModificationConstructor("in $psiFile, modified constructor $entry.key")
    }
  }
  return null
}

private fun validateInitBlocks(psiFile: PsiFile, oldInit: Map<Int, String>, newInit: Map<Int, String>): LiveEditUpdateException? {
  // We don't care about the exact offsets of each init block, only that the relative order of blocks has remained the same.
  val old = oldInit.toSortedMap().map { it.value }
  val new = newInit.toSortedMap().map { it.value }
  if (old != new) {
    return LiveEditUpdateException.unsupportedSourceModificationInit("in $psiFile, modified init block", psiFile)
  }
  return null
}

private fun validateProperties(psiFile: PsiFile, oldProps: Map<FqName, String>, newProps: Map<FqName, String>): LiveEditUpdateException? {
  for (entry in oldProps) {
    val other = newProps[entry.key] ?: continue
    if (other != entry.value) {
      return LiveEditUpdateException.unsupportedSourceModificationModifiedField(
        "$psiFile", "modified property ${entry.key.shortName()} of ${entry.key.parent()}")
    }
  }
  return null
}

class PsiState(val psiFile: PsiFile) : KtTreeVisitorVoid() {
  // Map of fully qualified property name to the code of the initializer or delegate.
  val properties = mutableMapOf<FqName, String>()

  // Map of the file position of the init block to the kotlin code of the init block.
  val initBlocks = mutableMapOf<Int, String>()

  // Map of constructor parameter types to kotlin code of the constructor. The constructor accepting
  // an integer and a string will have the key ["Int", "String"].
  val primaryConstructors = mutableMapOf<List<String?>, String>()

  // Map of secondary constructor parameter types to kotlin code of the constructor.
  val secondaryConstructors = mutableMapOf<List<String?>, String>()

  override fun visitPrimaryConstructor(constructor: KtPrimaryConstructor) {
    val params = constructor.valueParameters.map { it.typeReference?.getTypeText() }
    primaryConstructors[params] = flatten(constructor)
    super.visitPrimaryConstructor(constructor)
  }

  override fun visitSecondaryConstructor(constructor: KtSecondaryConstructor) {
    val params = constructor.valueParameters.map { it.typeReference?.getTypeText() }
    secondaryConstructors[params] = flatten(constructor)
    super.visitSecondaryConstructor(constructor)
  }

  override fun visitClassInitializer(initializer: KtClassInitializer) {
    initBlocks[initializer.startOffset] =flatten(initializer)
    super.visitClassInitializer(initializer)
  }

  override fun visitProperty(property: KtProperty) {
    // Ignore val/var declarations inside of methods
    if (property.isLocal) {
      return
    }
    val fqName = property.kotlinFqName ?: return

    // Only check property initializers and delegates; changes to the property getter, setter, name,
    // and type are handled by the class differ.
    if (property.hasDelegateExpressionOrInitializer()) {
      properties[fqName] = flatten(property.delegateExpressionOrInitializer!!)
    }
    super.visitProperty(property)
  }
}

// Performs a preorder traversal of a PSI tree and returns a string concatenation of the contents of all leaf nodes,
// ignoring whitespace and comments.
private fun flatten(elem: PsiElement): String {
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
  return leafs.joinToString { it.text }
}
