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
package com.android.tools.idea

import com.intellij.lang.ASTNode
import com.intellij.lang.FileASTNode
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes
import com.intellij.psi.impl.source.JavaFileElementType
import com.intellij.psi.impl.source.tree.JavaElementType
import org.jetbrains.kotlin.psi.stubs.elements.KtFileElementType
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes


private fun ASTNode.isClass() = when(elementType) {
  KtStubElementTypes.CLASS, JavaStubElementTypes.CLASS -> true
  else -> false
}

private fun ASTNode.mayContainClass() = when(elementType) {
  KtFileElementType.INSTANCE, KtStubElementTypes.CLASS, JavaStubElementTypes.CLASS -> true
  is JavaFileElementType -> true
  else -> false
}

private fun ASTNode.getChildren() = generateSequence(this.firstChildNode) { it.treeNext }

private fun ASTNode.isImport() = when(elementType) {
  KtStubElementTypes.IMPORT_DIRECTIVE, JavaElementType.IMPORT_STATEMENT -> true
  else -> false
}

private fun ASTNode.isImportList() = when(elementType) {
  KtStubElementTypes.IMPORT_LIST, JavaStubElementTypes.IMPORT_LIST -> true
  else -> false
}

private fun ASTNode.getImportedFQCN(): String {
  require(isImport()) { "Requesting FCQN import name from $elementType entry (not an import entry)" }
  return firstChildNode.treeNext.treeNext.text
}

private fun ASTNode.isSuperTypeList() = when(elementType) {
  KtStubElementTypes.SUPER_TYPE_LIST, JavaStubElementTypes.EXTENDS_LIST, KtStubElementTypes.SUPER_TYPE_ENTRY -> true
  else -> false
}

private fun ASTNode.getSuperTypeName() = when(elementType) {
  KtStubElementTypes.SUPER_TYPE_CALL_ENTRY -> firstChildNode.firstChildNode.text
  JavaElementType.JAVA_CODE_REFERENCE, KtStubElementTypes.SUPER_TYPE_ENTRY -> text
  else -> throw IllegalArgumentException("Requesting type name from $elementType entry (not JVM type field)")
}

private fun ASTNode.isSuperTypeEntry() = when(elementType) {
  KtStubElementTypes.SUPER_TYPE_CALL_ENTRY, JavaElementType.JAVA_CODE_REFERENCE, KtStubElementTypes.SUPER_TYPE_ENTRY -> true
  else -> false
}

/**
 * This is a very simple heuristic to detect if a class referenced by [name] is actually one of the [fqcns] classes
 *
 * The logic is the following:
 * 1) If name simply matches one the [fqcns] we have a perfect match and we return true
 * 2) Alternatively, name can be a short name, thus we are checking if any of the [fqcns] has short name matching [name] and
 * that this fqcn is found in the [imports] (was imported in this file)
 *
 * [name2fqcn] is passed for performance reasons, so that we don't have to calculate the mapping at every iteration of recursion
 */
private fun isOneOf(name: String, fqcns: Set<String>, name2fqcn: Map<String, String>, imports: Set<String>): Boolean {
  return fqcns.contains(name) || (name2fqcn.contains(name) && imports.contains(name2fqcn[name]))
}

private fun ASTNode.extends(fqcns: Set<String>, name2fqcn: Map<String, String>, imports: Set<String>): Boolean {
  if (!isClass()) {
    return false
  }
  val node = this.getChildren().firstOrNull { it.isSuperTypeList() }
  node?.let {
    return it.getChildren().filter { it.isSuperTypeEntry() }.any { isOneOf(it.getSuperTypeName(), fqcns, name2fqcn, imports) }
  }
  return false
}

private fun ASTNode.hasClassesExtending(fqcns: Set<String>, name2fqcn: Map<String, String>, imports: Set<String>): Boolean {
  if (extends(fqcns, name2fqcn, imports)) {
    return true
  } else if (mayContainClass()) {
    return this.getChildren().any { it.hasClassesExtending(fqcns, name2fqcn, imports) }
  }
  return false
}

/**
 * Returns a sequence of all the FQCN explicitly imported in the file
 * Only explicitly imported classes are supported, no aliases etc.
 */
fun FileASTNode.getImportedClassNames() = this.getChildren()
  .filter { it.isImportList() }
  .flatMap { it.getChildren() }
  .filter { it.isImport() }
  .map { it.getImportedFQCN() }

/**
 * Checks that a given [FileASTNode] has CLASS nodes in its tree that extend classes from [fqcns].
 */
fun FileASTNode.hasClassesExtending(fqcns: Set<String>): Boolean {
  val imports = getImportedClassNames().toSet()
  // Creating [short name] to [fqcs] mapping
  val name2fqcn = fqcns.associateBy { it.substringAfterLast('.') }
  return hasClassesExtending(fqcns, name2fqcn, imports)
}
