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
package com.android.tools.idea.dagger.index

import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.stubindex.KotlinTypeAliasByExpansionShortNameIndex

/**
 * Returns the list of index keys to search for a given type in priority order.
 *
 * The index stores values using the type name as the key, but that type might be fully-qualified,
 * just a simple name, or in some cases the "unknown" type represented by an empty string.
 * Additionally, Kotlin allows type aliases that need to be looked at as well.
 */
internal fun getIndexKeys(
  psiType: PsiType,
  project: Project,
  scope: GlobalSearchScope
): List<String> {
  // Treat unboxed types as equivalent to boxed types
  PsiPrimitiveType.getUnboxedType(psiType)?.let {
    return getIndexKeys(it, project, scope)
  }

  // Fully-qualified name is only added to the index when we're indexing a class definition, which
  // means it isn't needed for arrays or primitive types.
  val fqName = fqNameWithoutGenerics(psiType)
  val includeFqName: Boolean

  // Simple names will be added in many circumstances, but only some of them can have type aliases
  // in Kotlin.
  val simpleNamesCanHaveAlias: MutableList<String> = mutableListOf()
  val simpleNamesCannotHaveAlias: MutableList<String> = mutableListOf()
  when (psiType) {
    is PsiPrimitiveType -> {
      includeFqName = false
      simpleNamesCanHaveAlias.addAll(psiType.getPrimitiveShortNames())
    }
    is PsiArrayType -> {
      includeFqName = false
      val componentType = psiType.componentType
      if (componentType is PsiPrimitiveType) {
        // Primitive arrays are store as "IntegerArray" or similar in Kotlin, and "int[]" or
        // similar in Java. The former can be aliased, but there's no way to specify the
        // Java format in Kotlin, so it can't have type aliases.
        componentType.getKotlinPrimitiveArrayName()?.let { simpleNamesCanHaveAlias.add(it) }
        simpleNamesCannotHaveAlias.add("${componentType.canonicalText}[]")
      } else {
        // For non-primitive arrays, add in Kotlin's "Array" entry and Java's "typeName[]" entry.
        // As with primitives, only the Kotlin name can be part of a type alias.
        // Note that arrays of boxed primitive types are handled here like all other object arrays,
        // since Dagger treats arrays of boxed primitives as distinct from arrays of unboxed
        // primitives.
        simpleNamesCanHaveAlias.add("Array")
        simpleNamesCannotHaveAlias.add(
          "${fqNameWithoutGenerics(psiType.componentType).substringAfterLast(".")}[]"
        )
      }
    }
    else -> {
      includeFqName = true
      simpleNamesCanHaveAlias.add(fqName.substringAfterLast(".", fqName))
    }
  }

  // Alias fully-qualified names are not needed, since we only index fully-qualified names for class
  // definitions.
  // Some type aliases may not actually match the type we're looking for, since this index only
  // stores by simple name and that can cause collisions. For now there is no filtering applied
  // here, and we're relying on filtering done when DaggerElements are resolved. Filtering could
  // potentially be added here if the number of items returned becomes a performance issue.
  val aliasSimpleNames = simpleNamesCanHaveAlias.flatMap { getAliasSimpleNames(it, project, scope) }

  return buildList {
      // Fully-qualified name should go first, since it's most specific.
      if (includeFqName) add(fqName)

      // All simple names next.
      addAll(simpleNamesCanHaveAlias)
      addAll(simpleNamesCannotHaveAlias)
      addAll(aliasSimpleNames)

      // The unknown type last, since it's most generic.
      add("")
    }
    .distinct()
}

/**
 * Given a simple name, returns the simple names of any Kotlin type aliases that might correspond to
 * it.
 */
internal fun getAliasSimpleNames(
  baseTypeSimpleName: String,
  project: Project,
  scope: GlobalSearchScope
) =
  KotlinTypeAliasByExpansionShortNameIndex.get(baseTypeSimpleName, project, scope).mapNotNull {
    it.fqName?.asString()?.substringAfterLast(".")
  }

private fun fqNameWithoutGenerics(psiType: PsiType) =
  // Using `rawType` ensures generics aren't included.
  ((psiType as? PsiClassType)?.rawType() ?: psiType).canonicalText

private fun PsiPrimitiveType.getPrimitiveShortNames(): List<String> =
  when (kind) {
    JvmPrimitiveTypeKind.BOOLEAN -> listOf("Boolean")
    JvmPrimitiveTypeKind.BYTE -> listOf("Byte")
    JvmPrimitiveTypeKind.CHAR -> listOf("Char", "Character")
    JvmPrimitiveTypeKind.DOUBLE -> listOf("Double")
    JvmPrimitiveTypeKind.FLOAT -> listOf("Float")
    JvmPrimitiveTypeKind.INT -> listOf("Int", "Integer")
    JvmPrimitiveTypeKind.LONG -> listOf("Long")
    JvmPrimitiveTypeKind.SHORT -> listOf("Short")
    else -> emptyList()
  }

private fun PsiPrimitiveType.getKotlinPrimitiveArrayName(): String? =
  when (kind) {
    JvmPrimitiveTypeKind.BOOLEAN -> "BooleanArray"
    JvmPrimitiveTypeKind.BYTE -> "ByteArray"
    JvmPrimitiveTypeKind.CHAR -> "CharArray"
    JvmPrimitiveTypeKind.DOUBLE -> "DoubleArray"
    JvmPrimitiveTypeKind.FLOAT -> "FloatArray"
    JvmPrimitiveTypeKind.INT -> "IntArray"
    JvmPrimitiveTypeKind.LONG -> "LongArray"
    JvmPrimitiveTypeKind.SHORT -> "ShortArray"
    else -> null
  }
