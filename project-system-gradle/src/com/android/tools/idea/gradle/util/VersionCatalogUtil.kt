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
package com.android.tools.idea.gradle.util

import com.android.ide.common.repository.keysMatch
import com.android.tools.idea.gradle.dsl.api.GradleModelProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlHeaderOwner
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlKeyValueOwner

/**
 * Given a [TomlFile] and a path, returns the corresponding key element.
 * For example, given "versions.foo", it will locate the `foo =` key/value
 * pair under the `\[versions]` table and return it. As a special case,
 * `libraries` don't have to be explicitly named in the path.
 */
fun findCatalogKey(tomlFile: TomlFile, declarationPath: String): PsiElement? {
  val prefix = listOf("versions.", "bundles.", "plugins.")
  val section: String
  val target: String
  if (prefix.none { declarationPath.startsWith(it) }) {
    section = "libraries"
    target = declarationPath
  }
  else {
    section = declarationPath.substringBefore('.')
    target = declarationPath.substringAfter('.')
  }

  // At the root level, look for the right section (versions, libraries, etc)
  tomlFile.children.forEach { element ->
    // [table]
    // alias =
    if (element is TomlHeaderOwner) {
      val keyText = element.header.key?.text
      if (keysMatch(keyText, section)) {
        if (element is TomlKeyValueOwner) {
          return findAlias(element,target)
        }
      }
    }
    // for corner cases
    if (element is TomlKeyValue) {
      val keyText = element.key.text
      // libraries.alias = ""
      if (keysMatch(keyText, "$section.$target")) {
        return element
      } else
      // libraries = { alias = ""
        if(element.value is TomlInlineTable && keysMatch(keyText, section)) {
          return findAlias(element.value as TomlInlineTable,target)
        }
    }
  }
  return null
}

private fun findAlias(valueOwner: TomlKeyValueOwner, target:String):PsiElement?{
  for (entry in valueOwner.entries) {
    val entryKeyText = entry.key.text
    if (keysMatch(entryKeyText, target)) {
      return entry
    }
  }
  return null
}

fun findVersionCatalog(gradleDeclarationReference: String, project: Project): TomlFile? {
  val reference = gradleDeclarationReference.substringBefore(".")
  val view = GradleModelProvider.getInstance().getVersionCatalogView(project);
  val file = view.catalogToFileMap[reference] ?: return null

  val psiFile = PsiManager.getInstance(project).findFile(file)
  if (psiFile is TomlFile) {
    return psiFile
  }

  return null
}