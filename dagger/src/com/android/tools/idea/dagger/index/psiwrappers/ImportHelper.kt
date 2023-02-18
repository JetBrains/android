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

import com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.psi.KtFile

internal abstract class ImportHelperBase {
  private val possibleAnnotations: MutableMap<String, Set<String>> = mutableMapOf()

  /**
   * Returns the set of names that might be used to refer to an annotation in code.
   *
   * When an annotation is written, its name can take a few different forms based upon what import
   * statements exist. For example, the annotation "com.example.Annotation" could show up as both
   * `@Annotation` or `@com.example.Annotation`. In addition to those common cases, annotations may
   * exist in the same package (and therefore referenced by simple name even though they're not
   * imported), or they could be imported with an alias.
   *
   * Note that this doesn't mean that any reference using one of these names actually resolves to
   * the given fully-qualified name. We don't have enough information at indexing time to do that
   * resolution, so these names just represent a list of what might end up matching.
   *
   * This method doesn't handle a corner case around annotations that are defined as inner classes;
   * there are a few potential names that might be skipped in that case. That's ok, since this is
   * only used for Dagger annotations, and none are defined in that manner.
   */
  fun getPossibleAnnotationText(fqName: String): Set<String> {
    return possibleAnnotations.getOrPut(fqName) { buildPossibleAnnotationText(fqName) }
  }

  protected abstract fun buildPossibleAnnotationText(fqName: String): Set<String>
}

/** Utility class used to compute and cache information related to imports in a Kotlin file. */
internal class KotlinImportHelper(private val ktFile: KtFile) : ImportHelperBase() {
  /** Map of import alias to simple name of the type being imported. */
  val aliasMap: Map<String, String> by
    lazy(LazyThreadSafetyMode.NONE) {
      ktFile.importDirectives
        .filter { it.aliasName?.isNotEmpty() == true && it.importedFqName != null }
        .associate { it.aliasName!! to it.importedFqName!!.asString().substringAfterLast(".") }
    }

  override fun buildPossibleAnnotationText(fqName: String): Set<String> {
    val desiredSimpleName = fqName.substringAfterLast(".")
    val desiredPackageName = fqName.substringBeforeLast(".", missingDelimiterValue = "")

    // fqName is always allowed.
    val result: MutableSet<String> = mutableSetOf(fqName)

    // Simple name is allowed if we're in the same package.
    if (fqName == "${ktFile.packageFqName}.$desiredSimpleName") result.add(desiredSimpleName)

    // Process the import list to figure out what values might be allowed.
    for (import in ktFile.importDirectives) {
      when (import.importedFqName?.asString()) {
        // If this is a ".*" import, then the simple name can be used.
        desiredPackageName -> if (import.isAllUnder) result.add(desiredSimpleName)

        // If the fqName matches, then either the simple name or the alias can be used.
        fqName -> {
          val aliasName = import.aliasName
          if (aliasName.isNullOrEmpty()) result.add(desiredSimpleName) else result.add(aliasName)
        }
      }
    }

    return result
  }
}

/** Utility class used to compute and cache information related to imports in a Java file. */
internal class JavaImportHelper(private val psiJavaFile: PsiJavaFile) : ImportHelperBase() {
  override fun buildPossibleAnnotationText(fqName: String): Set<String> {
    val desiredSimpleName = fqName.substringAfterLast(".")
    return if (isSimpleNameAllowed(fqName, desiredSimpleName)) setOf(fqName, desiredSimpleName)
    else setOf(fqName)
  }

  private fun isSimpleNameAllowed(fqName: String, desiredSimpleName: String): Boolean {
    // Simple name is allowed if:
    // 1. We're in the same package.
    // 2. There's a full import for the fully-qualified name.
    // 3. There's a .* import for the package the name is in.

    if (fqName == "${psiJavaFile.packageName}.$desiredSimpleName") return true

    val importList = psiJavaFile.importList ?: return false
    if (importList.findSingleClassImportStatement(fqName) != null) return true

    val desiredPackageName = fqName.substringBeforeLast(".", missingDelimiterValue = "")
    return importList.findOnDemandImportStatement(desiredPackageName) != null
  }
}
