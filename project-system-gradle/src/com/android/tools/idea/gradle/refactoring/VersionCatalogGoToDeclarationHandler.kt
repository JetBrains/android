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

import com.android.SdkConstants
import com.android.SdkConstants.DOT_VERSIONS_DOT_TOML
import com.android.SdkConstants.FD_GRADLE
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable

/**
 * Go to declaration handler for providing navigation from references to version
 * catalog references -- both from references in KTS files (e.g. from dependency
 * and plugin references) and within TOML files (e.g. from library version variable
 * references and from bundle array references).
 *
 * Surely the right solution here is to actually have an index, and to perform
 * indexing of version catalog files. This is a temporary quick solution
 * which manually looks for the gradle catalogs and parses them on the fly.
 */
class VersionCatalogGoToDeclarationHandler : GotoDeclarationHandlerBase() {
  override fun getGotoDeclarationTarget(sourceElement: PsiElement?, editor: Editor?): PsiElement? {
    sourceElement ?: return null
    val parent = sourceElement.parent ?: return null
    val grandParent = parent.parent ?: return null

    // Reference from build.gradle.kts to dependency?
    if (grandParent is KtDotQualifiedExpression) {
      if (grandParent.firstChild?.firstChild?.text == "libs" && grandParent.containingFile.name.endsWith(SdkConstants.DOT_KTS)) {
        val catalog = findVersionCatalog(parent) ?: return null
        val key = grandParent.text
        val target = findCatalogKey(catalog, key.removePrefix("libs."))
        if (target != null) {
          return target
        }

        // If you have dashes in the library name, Gradle will convert this into dotted notation, and will actually
        // create a "group" DSL object for the libraries sharing the same prefix. But we typically don't have a
        // TOML object for this; e.g. we may have "constraint-layout=X", and maybe "constraint-solver=Y"; if we've
        // tried to navigate to the "constraint" part of "libs.constraint.layout", we shouldn't attempt to find
        // a key named "constraint" in the TOML file, we'll look for the whole reference instead.
        val argument = grandParent.getParentOfType<KtValueArgument>(true) ?: return null
        val fullKey = argument.text
        if (fullKey != key) {
          return findCatalogKey(catalog, fullKey.removePrefix("libs."))
        }
      }
      return null
    }

    // Reference within a Groovy build.gradle file?
    // Handled by GradleVersionCatalogTomlAwareGotoDeclarationHandler
    //   assuming "gradle.version.catalogs.dynamic.support" is turned on in the registry.
    // ...but it doesn't seem to work quite right
    if (parent is GrReferenceExpression) {
      val key = parent.text
      if (key.startsWith("libs.") && grandParent.containingFile.name.endsWith(SdkConstants.DOT_GRADLE)) {
        val catalog = findVersionCatalog(parent) ?: return null
        val target = findCatalogKey(catalog, key.removePrefix("libs."))
        if (target != null) {
          return target
        }
      }
    }

    // Reference within TOML file?
    if (parent is TomlLiteral) {
      if (grandParent is TomlArray) {
        // Bundle definition -- string value points to a library key
        val file = parent.containingFile as? TomlFile ?: return null
        val libraryVar = parent.getString()
        return findCatalogKey(file, libraryVar)
      } else if (grandParent is TomlKeyValue) {
        // version references in library definition -- string value points to a version variable key
        if (grandParent.key.text == "version.ref") {
          val versionVar = parent.getString()
          val file = parent.containingFile as? TomlFile ?: return null
          return findCatalogKey(file, "versions.$versionVar")
        }
      }
    }

    return null
  }

  /**
   * Given a [TomlFile] and a path, returns the corresponding key element.
   * For example, given "versions.foo", it will locate the `foo =` key/value
   * pair under the `\[versions]` table and return it. As a special case,
   * `libraries` don't have to be explicitly named in the path.
   */
  private fun findCatalogKey(tomlFile: TomlFile, path: String): PsiElement? {
    val section: String
    val target: String
    if (path.startsWith("versions.") ||
      path.startsWith("libraries.") ||
      path.startsWith("bundles.") ||
      path.startsWith("plugins.")
    ) {
      val index = path.indexOf('.')
      section = path.substring(0, index)
      target = path.substring(index + 1)
    } else {
      section = "libraries"
      target = path
    }
    var sectionElement = tomlFile.firstChild
    // At the root level, look for the right section (versions, libraries, etc)
    while (sectionElement != null) {
      if (sectionElement is TomlTable) {
        val keyText = sectionElement.header.key?.text
        if (keysMatch(keyText, section)) {
          // Found the right section; now search for the specific key
          for (entry in sectionElement.entries) {
            val entryKeyText = entry.key.text
            if (keysMatch(entryKeyText, target)) {
              return entry
            }
          }
        } else if (keysMatch(keyText, target)) {
          return sectionElement
        }
      }
      sectionElement = sectionElement.nextSibling
    }

    return null
  }

  private fun keysMatch(s1: String?, s2: String): Boolean {
    s1 ?: return false
    if (s1.length != s2.length) {
      return false
    }
    for (i in s1.indices) {
      if (s1[i].normalize() != s2[i].normalize()) {
        return false
      }
    }
    return true
  }

  // Gradle converts dashed-keys into dashed.keys
  private fun Char.normalize(): Char {
    if (this == '-') {
      return '.'
    }
    return this
  }

  // TODO: Hook up to however we're really supposed to find the version catalog
  private fun findVersionCatalog(element: PsiElement): TomlFile? {
    val module = element.module ?: return null
    val roots = ModuleRootManager.getInstance(module).contentRoots
    var dir = roots.firstOrNull() ?: return null

    while (true) {
      val gradle = dir.findChild(FD_GRADLE)
      if (gradle != null) {
        for (file in gradle.children) {
          if (file.name.endsWith(DOT_VERSIONS_DOT_TOML)) {
            val psiFile = PsiManager.getInstance(element.project).findFile(file)
            if (psiFile is TomlFile) {
              return psiFile
            }
          }
        }
        break
      }
      dir = dir.parent ?: break
    }

    return null
  }
}

private fun TomlLiteral.getString(): String {
  // Surprisingly it looks like the TomlLiteral PSI element doesn't
  // have a direct method for returning the unescaped string?
  // It probably does *somewhere*; hook that up.
  return text.tomlStringSourceToString()
}

// The below two methods are copied from DefaultLintTomlParser in lint; I'm assuming there's
// a better utility to call from within IntelliJ's TOML PSI support; if not,
// we can probably move this to a more reusable place outside of lint.

// TOML string escaping; see https://toml.io/en/v1.0.0#string
private fun unescape(s: String, skipInitialNewline: Boolean = false): String {
  val sb = StringBuilder()
  val length = s.length
  var i = 0
  if (skipInitialNewline && length > 0 && s[i] == '\n') {
    i++
  }
  while (i < length) {
    val c = s[i++]
    if (c == '\\' && i < s.length) {
      when (val next = s[i++]) {
        '\n' -> {
          // From the toml spec: "When the last non-whitespace character on a
          // line is an unescaped \, it will be trimmed along with all whitespace
          // (including newlines) up to the next non-whitespace character or
          // closing delimiter."
          while (i < length && s[i].isWhitespace()) {
            i++
          }
          continue
        }

        'n' -> sb.append('\n')
        't' -> sb.append('\t')
        'b' -> sb.append('\b')
        'f' -> sb.append('\u000C')
        'r' -> sb.append('\r')
        '"' -> sb.append('\"')
        '\\' -> sb.append('\\')
        'u' -> { // \uXXXX
          if (i <= s.length - 4) {
            try {
              val uc = Integer.parseInt(s.substring(i, i + 4), 16).toChar()
              sb.append(uc)
              i += 4
            } catch (e: NumberFormatException) {
              sb.append(next)
            }
          } else {
            sb.append(next)
          }
        }

        'U' -> { // \UXXXXXXXX
          if (i <= s.length - 8) {
            try {
              val uc = Integer.parseInt(s.substring(i, i + 8), 16).toChar()
              sb.append(uc)
              i += 8
            } catch (e: NumberFormatException) {
              sb.append(next)
            }
          } else {
            sb.append(next)
          }
        }

        else -> sb.append(next)
      }
    } else {
      sb.append(c)
    }
  }
  return sb.toString()
}

private fun String.tomlStringSourceToString(): String {
  val valueSource = this
  when {
    valueSource.isEmpty() -> {
      return valueSource
    }

    valueSource.startsWith("\"\"\"") -> {
      val body = valueSource.removeSurrounding("\"\"\"")
      return unescape(body, skipInitialNewline = true)
    }

    valueSource.startsWith("\"") -> {
      return unescape(valueSource.removeSurrounding("\""))
    }

    valueSource.startsWith("'''") -> {
      var body = valueSource.removeSurrounding("'''")
      if (body.startsWith("\n")) {
        // Leading newlines in """ and ''' strings should be removed
        body = body.substring(1)
      }
      return body
    }

    valueSource.startsWith("'") -> {
      return valueSource.removeSurrounding("'")
    }

    else -> return valueSource
  }
}
