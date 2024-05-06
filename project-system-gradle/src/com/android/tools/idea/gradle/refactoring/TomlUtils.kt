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
package com.android.tools.idea.gradle.refactoring

import com.intellij.psi.PsiElement
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable

val segments = setOf("libraries", "bundles", "plugins", "versions")

fun isVersionCatalogAlias(psiElement: TomlKeySegment): Boolean {
  val grandParent = psiElement.parent.parent
  if (grandParent is TomlKeyValue) {
    // navigate to TomlKeySegment -> TomlKey -> TomlKeyValue -> TomlTable
    val table = grandParent.parent ?: return true
    return table is TomlTable && segments.contains(table.header.key?.text)
  }
  return false
}

fun isVersionCatalogFile(psiElement: PsiElement): Boolean = psiElement.containingFile?.name?.endsWith(".versions.toml") ?: false
