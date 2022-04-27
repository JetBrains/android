/*
 * Copyright (C) 2021 The Android Open Source Project
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
package org.jetbrains.android.refactoring

import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo

private const val METHOD_REPLACING_BASE_PRIORITY = 10_000_000
private const val CLASS_MIGRATION_BASE_PRIORITY = 1_000_000
private const val PACKAGE_MIGRATION_BASE_PRIORITY = 1_000
private const val DEFAULT_MIGRATION_BASE_PRIORITY = 0

private fun isImportElement(element: PsiElement?): Boolean =
  element != null && (element.node?.elementType.toString() == "IMPORT_LIST" || isImportElement(element.parent))

/**
 * Sorts [UsageInfo]s to allow refactors to be applied correctly.
 *
 * The refactoring operations need to be done in a specific order to work correctly.
 * First we refactor method replacements. If they are affected later by any class migration, this way, they are shortened correctly
 * Then, we need to refactor the imports in order to allow shortenReferences to work (since it needs to check that the imports are there).
 * We need to process first the class migrations since they have higher priority. If we don't,
 * the package refactoring would be applied first and then the class would incorrectly be refactored.
 * Then, we need to first process the longest package names so, if there are conflicting refactorings,
 * the most specific one applies.
 */
internal fun List<MigrateToAppCompatUsageInfo>.sortToApply(): List<MigrateToAppCompatUsageInfo> =
    sortedByDescending {
      var value = when (it) {
        is MigrateToAppCompatUsageInfo.ReplaceMethodUsageInfo -> METHOD_REPLACING_BASE_PRIORITY
        is MigrateToAppCompatUsageInfo.ClassMigrationUsageInfo -> CLASS_MIGRATION_BASE_PRIORITY
        is MigrateToAppCompatUsageInfo.PackageMigrationUsageInfo -> PACKAGE_MIGRATION_BASE_PRIORITY + it.mapEntry.myOldName.length
        else -> DEFAULT_MIGRATION_BASE_PRIORITY
      }

      if (isImportElement(it.element)) {
        // This is an import, promote
        value += 1000
      }

      value
    }