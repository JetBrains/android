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
package com.android.tools.idea.dagger.index.psiwrappers

import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeElement
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType

/** A [DaggerIndexPsiWrapper] representing a type. */
interface DaggerIndexTypeWrapper : DaggerIndexPsiWrapper {
  /**
   * Simple name of a type, without any package name. Eg: "Foo"
   *
   * These simple names end up being used as keys in the Dagger index, so it's necessary to call out
   * a few special cases to be considered when looking up entries in the index:
   * 1. **Primitive Types** are returned differently depending on the language. In Java, the short
   *    name of the unboxed type is Used (eg, `Integer` or `Boolean`). In Kotlin, the short name of
   *    the Kotlin type is used (eg, `Int` or `Boolean`). Some of these names overlap, and some do
   *    not. Dagger treats Kotlin and Java primitive types as equivalent, as well as their boxed
   *    versions. It would be ideal to store them all using the same simple name, but it's not
   *    possible to resolve with certainty at indexing time. (For instance, is `Int` in Kotlin
   *    referring to the primitive type or a class called `Int` in the current package? That can't
   *    be determined.) So it is up to the index reading logic to query both variants when looking
   *    for primitive types.
   * 2. **Strings** are represented by different types in Java and Kotlin, but Dagger treats them as
   *    equivalent (similar to primitive types). Both return "String" as their simple name.
   * 3. **Generic Types** have their simple name constructed by getting a "simple" version of each
   *    type reference in their representation. So a type of `java.util.List<java.lang.String,
   *    java.lang.Integer>` has simple name "List&lt;String, Integer&gt;".
   * 4. **Arrays** have differing representations in Java and Kotlin. Java arrays will return the
   *    base type name followed by square brackets (eg, "TypeName[]" or "int[]"). Note that
   *    primitive types here are specified with the actual primitive token (eg, "int" instead of
   *    "Integer"). This is because Dagger treats arrays of boxed types as different from arrays of
   *    unboxed types. Kotlin arrays will return either a primitive array type (eg "IntArray") or a
   *    generic object array type (eg "Array<TypeName>").
   */
  fun getSimpleName(): String
}

internal class KtTypeReferenceWrapper(
  private val ktTypeReference: KtTypeReference,
  private val importHelper: KotlinImportHelper
) : DaggerIndexTypeWrapper {
  override fun getSimpleName(): String = ktTypeReference.getSimpleName()

  private fun KtTypeReference.getSimpleName(): String {
    // We always expect to have a KtUserType with a reference expression in the scenarios these
    // wrappers are used for.
    val ktUserType = typeElement as KtUserType
    val simpleNameInCode = ktUserType.referenceExpression?.getReferencedName()!!

    // Replace any import alias in the simple name.
    val aliasedSimpleName =
      if (ktUserType.qualifier != null) {
        // If the type has any qualifiers, then the only part that can be aliased is at the
        // beginning. Therefore, the alias isn't part of the simple name, and we can just return
        // here.
        simpleNameInCode
      } else {
        // Otherwise, we know the type reference is just a simple name.
        // Look for any imports that might have an alias for this type.
        importHelper.aliasMap[simpleNameInCode] ?: simpleNameInCode
      }

    return aliasedSimpleName
  }
}

internal class PsiTypeElementWrapper(private val psiTypeElement: PsiTypeElement) :
  DaggerIndexTypeWrapper {
  override fun getSimpleName(): String = psiTypeElement.type.getSimpleName()

  companion object {
    private fun PsiType.getSimpleName(): String {
      return when (this) {
        is PsiPrimitiveType ->
          // Only PsiType.NULL will return a null for `boxedTypeName`, and we don't expect to have
          // that type here.
          boxedTypeName!!.substringAfterLast(".")
        is PsiArrayType -> {
          val componentType = componentType
          if (componentType is PsiPrimitiveType) "${componentType.name}[]"
          else "${componentType.getSimpleName()}[]"
        }
        else -> presentableText.substringBefore("<")
      }
    }
  }
}
