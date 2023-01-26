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

@file:JvmName("ProguardR8PsiImplUtil")

package com.android.tools.idea.lang.proguardR8.psi

import com.android.tools.idea.lang.proguardR8.parser.ProguardR8Lexer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameterList
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceSet
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil

private class ProguardR8JavaClassReferenceProvider(
  val scope: GlobalSearchScope,
  val treatDollarAsSeparator: Boolean
) : JavaClassReferenceProvider() {

  override fun getScope(project: Project): GlobalSearchScope = scope

  override fun getReferencesByString(str: String, position: PsiElement, offsetInPosition: Int): Array<PsiReference> {
    return if (StringUtil.isEmpty(str)) {
      PsiReference.EMPTY_ARRAY
    }
    else {
      object : JavaClassReferenceSet(str, position, offsetInPosition, false, this) {
        // If true allows inner classes to be separated by a dollar sign "$", e.g.java.lang.Thread$State
        // We can't just use ALLOW_DOLLAR_NAMES flag because to make JavaClassReferenceSet work in the way we want
        // language of PsiElement that we parse should be instance of XMLLanguage.
        override fun isAllowDollarInNames() = treatDollarAsSeparator

      }.allReferences as Array<PsiReference>
    }
  }
}

fun containsWildcards(className: ProguardR8QualifiedName): Boolean {
  return className.node.findChildByType(ProguardR8Lexer.wildcardsTokenSet) != null
}

fun getReferences(className: ProguardR8QualifiedName): Array<PsiReference> {
  val provider = ProguardR8JavaClassReferenceProvider(className.resolveScope, true)
  var referenceSet = provider.getReferencesByElement(className)
  if (className.lastChild.textContains('$')) {
    // If there is a '$' in the last part we want to add reference for a name_with_dollar.
    // We already have references for class + inner class. See [isAllowDollarInNames] and [JavaClassReferenceSet.reparse]
    referenceSet += ProguardR8JavaClassReferenceProvider(className.resolveScope, false).getReferencesByElement(className).last()
  }
  return referenceSet
}

fun resolveToPsiClass(className: ProguardR8QualifiedName): PsiClass? {
  // We take last reference because it corresponds to PsiClass (or not), previous are for packages
  var lastElement = className.references.lastOrNull()?.resolve()
  if (lastElement == null && className.lastChild.textContains('$')) {
    // If there is a '$' in the last part, in `references` last element is corresponding for class_name_with_$, it means
    // there could be references corresponding to inner class name at `references.size - 2` position. See getReference() implementation.
    lastElement = className.references.getOrNull(className.references.size - 2)?.resolve()
  }
  return lastElement as? PsiClass
}

/**
 * Returns all resolvable psiClasses found in header, excluding classes that specified after "extends"/"implements" key words.
 *
 * Example: for "-keep myClass1, myClass2 extends myClass3" returns "myClass1", "myClass2"
 */
fun resolvePsiClasses(classSpecificationHeader: ProguardR8ClassSpecificationHeader): List<PsiClass> {
  return classSpecificationHeader.classNameList.mapNotNull { it.qualifiedName.resolveToPsiClass() }
}

/**
 * Returns classes in header that specified after "extends"/"implements" key words.
 */
fun resolveSuperPsiClasses(classSpecificationHeader: ProguardR8ClassSpecificationHeader): List<PsiClass> {
  return classSpecificationHeader.superClassNameList.mapNotNull { it.qualifiedName.resolveToPsiClass() }
}

fun getPsiPrimitive(proguardR8JavaPrimitive: ProguardR8JavaPrimitive): PsiPrimitiveType? {
  val primitive = proguardR8JavaPrimitive.node.firstChildNode
  return when (primitive.elementType) {
    ProguardR8PsiTypes.BOOLEAN -> PsiTypes.booleanType()
    ProguardR8PsiTypes.BYTE -> PsiTypes.byteType()
    ProguardR8PsiTypes.CHAR -> PsiTypes.charType()
    ProguardR8PsiTypes.SHORT -> PsiTypes.shortType()
    ProguardR8PsiTypes.INT -> PsiTypes.intType()
    ProguardR8PsiTypes.LONG -> PsiTypes.longType()
    ProguardR8PsiTypes.FLOAT -> PsiTypes.floatType()
    ProguardR8PsiTypes.DOUBLE -> PsiTypes.doubleType()
    ProguardR8PsiTypes.VOID -> PsiTypes.voidType()
    else -> {
      assert(false) { "Couldn't match ProguardR8JavaPrimitive \"${primitive.text}\" to PsiPrimitive" }
      null
    }
  }
}

/**
 * Returns number of dimensions or 0 if there is error
 *
 * Examnple: For int[][][] it returns 3
 */
fun getNumberOfDimensions(array: ProguardR8ArrayType): Int {
  if (PsiTreeUtil.hasErrorElements(array)) return 0
  return (array.node as CompositeElement).countChildren(TokenSet.create(ProguardR8PsiTypes.OPEN_BRACKET))
}

/**
 * Returns true if ProguardR8Type would match given "other" PsiType otherwise returns false
 */
fun matchesPsiType(type: ProguardR8Type, other: PsiType): Boolean {
  if (PsiTreeUtil.hasErrorElements(type)) return false

  var typeToMatch = other
  if (type.arrayType != null) {
    for (x in 0 until type.arrayType!!.numberOfDimensions)
      if (typeToMatch is PsiArrayType) {
        typeToMatch = typeToMatch.componentType
      }
      else {
        return false
      }
  }
  return when {
    type.javaPrimitive != null -> type.javaPrimitive!!.psiPrimitive == typeToMatch
    type.qualifiedName != null && typeToMatch is PsiClassReferenceType -> type.qualifiedName!!.resolveToPsiClass() == typeToMatch.resolve()
    // "%" matches any primitive type ("boolean", "int", etc, but not "void").
    type.anyPrimitiveType != null -> typeToMatch is PsiPrimitiveType && typeToMatch != PsiTypes.voidType()
    type.anyNotPrimitiveType != null -> typeToMatch is PsiClassReferenceType
    type.anyType != null -> true
    else -> false
  }
}

/**
 * Returns true if ProguardR8Parameters doesn't have errors and matches given "other" PsiParameterList otherwise returns false
 *
 * In general it checks if every type within ProguardR8Parameters [matchesPsiType] type in PsiParameterList at the same position.
 * Tricky case is when ProguardR8Parameters ends with '...' (matches any number of arguments of any type). In this case we need to check
 * that all types at positions before '...' match and after if there are still some types remain at PsiParameterList, we just ignore them.
 */
fun matchesPsiParameterList(parameters: ProguardR8Parameters, psiParameterList: PsiParameterList): Boolean {
  if (PsiTreeUtil.hasErrorElements(parameters)) return false
  if (parameters.isAcceptAnyParameters) return true

  val proguardTypes = PsiTreeUtil.findChildrenOfType(parameters, ProguardR8Type::class.java).toList()
  if (proguardTypes.isEmpty() != psiParameterList.isEmpty) return false
  // endsWithAnyTypAndNumOfArg is true when parameter list looks like (param1, param2, ...).
  val endsWithAnyTypAndNumOfArg = parameters.typeList!!.lastChild?.node?.elementType == ProguardR8PsiTypes.ANY_TYPE_AND_NUM_OF_ARGS
  val psiTypes = psiParameterList.parameters.map { it.type }

  // proguardTypes.size can be less psiTypes because we can match tail of psiTypes to ANY_TYPE_AND_NUM_OF_ARGS.
  if (proguardTypes.size > psiTypes.size) return false

  for (i in psiTypes.indices) {
    // Returns whether we can match tail of psiTypes to ANY_TYPE_AND_NUM_OF_ARGS or not.
    if (i > proguardTypes.lastIndex) return endsWithAnyTypAndNumOfArg
    // Type at the same position in list doesn't much.
    if (!proguardTypes[i].matchesPsiType(psiTypes[i])) return false
  }

  return true
}

fun isAcceptAnyParameters(parameters: ProguardR8Parameters): Boolean {
  return !PsiTreeUtil.hasErrorElements(parameters) &&
         PsiTreeUtil.findChildOfType(parameters, ProguardR8TypeList::class.java) == null &&
         parameters.node.findChildByType(ProguardR8PsiTypes.ANY_TYPE_AND_NUM_OF_ARGS) != null
}

fun getParameters(field: ProguardR8Field): ProguardR8Parameters? = null

fun containsWildcards(member: ProguardR8ClassMemberName): Boolean {
  return member.node.findChildByType(ProguardR8Lexer.wildcardsTokenSet) != null
}

fun getReference(member: ProguardR8ClassMemberName) = if (member.containsWildcards()) null else ProguardR8ClassMemberNameReference(member)

private val accessModifiers = setOf(PsiModifier.PRIVATE, PsiModifier.PROTECTED, PsiModifier.PUBLIC)

fun isNegated(modifier: ProguardR8Modifier) = modifier.firstChild.node.elementType == ProguardR8PsiTypes.EM

fun isAccessModifier(modifier: ProguardR8Modifier) = accessModifiers.contains(modifier.toPsiModifier())

fun toPsiModifier(modifier: ProguardR8Modifier) = when {
  modifier.node.findChildByType(ProguardR8PsiTypes.PRIVATE) != null -> PsiModifier.PRIVATE
  modifier.node.findChildByType(ProguardR8PsiTypes.PROTECTED) != null -> PsiModifier.PROTECTED
  modifier.node.findChildByType(ProguardR8PsiTypes.PUBLIC) != null -> PsiModifier.PUBLIC
  modifier.node.findChildByType(ProguardR8PsiTypes.STATIC) != null -> PsiModifier.STATIC
  modifier.node.findChildByType(ProguardR8PsiTypes.FINAL) != null -> PsiModifier.FINAL
  modifier.node.findChildByType(ProguardR8PsiTypes.ABSTRACT) != null -> PsiModifier.ABSTRACT
  modifier.node.findChildByType(ProguardR8PsiTypes.VOLATILE) != null -> PsiModifier.VOLATILE
  modifier.node.findChildByType(ProguardR8PsiTypes.TRANSIENT) != null -> PsiModifier.TRANSIENT
  modifier.node.findChildByType(ProguardR8PsiTypes.SYNCHRONIZED) != null -> PsiModifier.SYNCHRONIZED
  modifier.node.findChildByType(ProguardR8PsiTypes.NATIVE) != null -> PsiModifier.NATIVE
  modifier.node.findChildByType(ProguardR8PsiTypes.STRICTFP) != null -> PsiModifier.STRICTFP
  else -> error("Couldn't match ProguardR8AccessModifier \"${modifier.text}\" to PsiModifier")
}

fun getType(fullyQualifiedNameConstructor: ProguardR8FullyQualifiedNameConstructor): ProguardR8Type? = null

fun isQuoted(file: ProguardR8File): Boolean {
  return file.singleQuotedString != null ||
         file.unterminatedSingleQuotedString != null ||
         file.doubleQuotedString != null ||
         file.unterminatedDoubleQuotedString != null
}

fun getReferences(file: ProguardR8File): Array<FileReference> {
  return if (file.isQuoted) {
    val lastIndex = if (file.singleQuotedString != null || file.doubleQuotedString != null) file.text.length - 1 else file.text.length
    FileReferenceSet(file.text.substring(1, lastIndex), file, 1, null, true).allReferences
  }
  else {
    FileReferenceSet(file).allReferences
  }
}