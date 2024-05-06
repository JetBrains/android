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
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile

internal abstract class ImportHelperBase {
  private val possibleAnnotations: MutableMap<DaggerAnnotation, Set<String>> = mutableMapOf()

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
  fun getPossibleAnnotationText(annotation: DaggerAnnotation): Set<String> {
    return possibleAnnotations.getOrPut(annotation) { buildPossibleAnnotationText(annotation) }
  }

  protected abstract fun buildPossibleAnnotationText(annotation: DaggerAnnotation): Set<String>
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

  override fun buildPossibleAnnotationText(annotation: DaggerAnnotation): Set<String> {
    val desiredPackageName = annotation.classId.packageFqName
    val desiredClassName = annotation.classId.relativeClassName

    // Fully-qualified annotation name is always allowed.
    val result: MutableSet<String> = mutableSetOf(annotation.fqNameString)

    // If the annotation is in the same package, it can be referred to using only the
    // relative class name.
    if (desiredPackageName == ktFile.packageFqName) {
      result.add(desiredClassName.asString())
    }

    // Process the import list to figure out what other values might be allowed.
    for (import in ktFile.importDirectives) {
      val importedFqName = import.importedFqName ?: continue

      // If this is a ".*" import matching the package, we can use the relative class name.
      if (import.isAllUnder) {
        if (desiredPackageName == importedFqName) {
          result.add(desiredClassName.asString())
        }
        continue
      }

      // Otherwise, figure out if the import can match the given fully-qualified name, or some
      // part of it that can be used for a partially-qualified reference.
      val nameSequenceAfterImport = annotation.classId.getNameSequenceAfterImport(importedFqName)
      if (nameSequenceAfterImport != null) {
        result.add(
          buildString {
            append(import.aliasName ?: importedFqName.shortName().asString())
            nameSequenceAfterImport.forEach {
              append('.')
              append(it.asString())
            }
          }
        )
      }
    }

    return result
  }

  /**
   * The sequence of names needed to refer to this ClassId after an import of importFqName, or
   * `null` if importFqName cannot refer to this ClassId.
   *
   * The sequence may be empty if the import was an exact match to the class ID.
   *
   * Examples on classId = ClassId.fromString("com/example/Foo.Bar"):
   * - FqName("com.example.Foo") -> ["Bar"]
   * - FqName("com.example.Foo.Bar") -> []
   * - FqName("com.example.Baz") -> null
   * - FqName("com.example.Foo.Baz") -> null
   * - FqName("com.example") -> null
   */
  private fun ClassId.getNameSequenceAfterImport(importFqName: FqName): List<Name>? {
    val importSegments = importFqName.pathSegments()
    val classPackageSegments = packageFqName.pathSegments()
    // Imported name must contain all labels in the package name, plus one for the imported
    // class name.
    if (importSegments.size <= classPackageSegments.size) {
      return null
    }
    val allClassSegments = classPackageSegments + relativeClassName.pathSegments()
    // All labels in the imported name must match labels in the FQ class name.
    if (importSegments != allClassSegments.take(importSegments.size)) {
      return null
    }
    // Return any labels not matched by the above check (potentially none).
    return allClassSegments.drop(importSegments.size)
  }
}

/** Utility class used to compute and cache information related to imports in a Java file. */
internal class JavaImportHelper(private val psiJavaFile: PsiJavaFile) : ImportHelperBase() {
  override fun buildPossibleAnnotationText(annotation: DaggerAnnotation): Set<String> {
    // fqName is always allowed.
    val result: MutableSet<String> = mutableSetOf(annotation.fqNameString)

    // Special case: If we're in the same package as the annotation, we can refer to
    // the annotation by its relative class name.
    if (annotation.classId.packageFqName.asString() == psiJavaFile.packageName) {
      result.add(annotation.classId.relativeClassName.asString())
    }

    // For each containing class name, see if we have an import for that class name.
    // This also accounts for the annotation being in the same package as the file (rare).
    // For example, for com.example.Foo.Bar, we check for imports of com.example.Foo, and add
    // Foo.Bar if we have one, then check for imports of com.example.Foo.Bar, adding Bar if we do.
    var importFqName = annotation.classId.packageFqName
    val relativeNameSequence = annotation.classId.relativeClassName.pathSegments()
    for ((i, name) in relativeNameSequence.withIndex()) {
      if (isSimpleNameAllowed(importFqName, name)) {
        result.add(
          relativeNameSequence.subList(i, relativeNameSequence.size).joinToString(".") {
            it.asString()
          }
        )
      }
      importFqName = importFqName.child(name)
    }

    return result
  }

  private fun isSimpleNameAllowed(containerName: FqName, desiredSimpleName: Name): Boolean {
    // Simple name is allowed if:
    // 1. We're in the same package (handled above).
    // 2. There's a full import for the fully-qualified name.
    // 3. There's a .* import for the package the name is in.
    val importList = psiJavaFile.importList ?: return false

    val containerNameString = containerName.asString()
    val simpleNameString = desiredSimpleName.asString()
    val importStatement =
      importList.findSingleClassImportStatement("${containerNameString}.${simpleNameString}")
        ?: importList.findOnDemandImportStatement(containerNameString)

    return (importStatement != null)
  }
}
