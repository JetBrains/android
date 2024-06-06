/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.service

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.findParentOfType
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable

class VersionCatalogDocumentationProvider : DocumentationProvider {
  private val all = mapOf(
    "version" to "Plugin or Library dependency version as <br/>\"<small>androidx.core:core-ktx:</small><b>1.12.0</b>\"",
    "ref" to "Version reference. Declaration must be presented in [versions]",
  )
  private val libraries = mapOf(
    "module" to "Dependency module as <br/>\"<b>androidx.core:core-ktx</b><small>:1.12.0</small>\"",
    "name" to "Library dependency name as <br/>\"<small>androidx.core:</small><b>core-ktx</b><small>:1.12.0</small>\"",
    "group" to "Library group description as <br/>\"<b>androidx.core</b><small>:core-ktx:1.12.0</small>\"",
  ) + all

  private val plugins = mapOf(
    "id" to "Plugin identifier \"<b>com.android.application</b><small>:8.4.0</small>\"",
  ) + all

  override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement): String? =
    generate(element, originalElement)

  override fun generateHoverDoc(element: PsiElement, originalElement: PsiElement?): String? =
    generate(element, originalElement)

  override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? =
    generate(element, originalElement)

  private fun generate(element: PsiElement, originalElement: PsiElement?): String? {
    if (!element.containingFile.name.endsWith("versions.toml")) return null
    // sometimes element is KeySegment and from tests it's leaf
    val el = if(element is LeafPsiElement) element.parent else element
    val table = element.findParentOfType<TomlTable>()
    val tableName = table?.header?.key?.text
    if (el is TomlKeySegment && el.parent.parent is TomlKeyValue && tableName != null) {
      return when (tableName) {
        "libraries" -> libraries[el.text]
        "plugins" -> plugins[el.text]
        else -> null
      }
    }
    return null
  }
}