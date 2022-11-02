/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tools.idea.lint.common

import com.android.tools.lint.client.api.GradleVisitor
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Location
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlRecursiveVisitor
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind

/** Gradle visitor for Toml Version files. */
class TomlIdeGradleVisitor : GradleVisitor() {
  override fun getStartOffset(context: GradleContext, cookie: Any): Int {
    val element = cookie as Element
    val textRange = element.psi.textRange
    return textRange.startOffset
  }

  override fun createLocation(context: GradleContext, cookie: Any): Location {
    val element = cookie as Element
    return Location.create(context.file, context.getContents(), element.psi.startOffset, element.psi.endOffset)
  }

  override fun visitBuildScript(context: GradleContext, detectors: List<GradleScanner>) {
    ApplicationManager.getApplication().runReadAction(Runnable {
      val psiFile: PsiFile = (context.getPsiFile() as? TomlFile) ?: return@Runnable

      val tomlFile: TomlFile = psiFile as TomlFile
      // we need to store variables and libraries as then can go in any order
      val variables: MutableMap<String, Pair<String, Element>> = HashMap()
      val libraries: MutableMap<String, MutableList<String>> = HashMap()

      tomlFile.accept(object : TomlRecursiveVisitor() {
        override fun visitTable(element: TomlTable) {
          val headerKind = element.header.key?.segments?.singleOrNull()?.name ?: return
          when (headerKind) {
            TOML_TABLE_LIBRARIES -> resolveLibraries(parse(element))
            TOML_TABLE_VERSIONS -> resolveVersions(parse(element))
          }
        }

        private fun parse(table: TomlTable): MapProperty {
          val parent = MapProperty("", table)
          table.entries.forEach { it.accept(getVisitor(parent, "")) }
          return parent
        }

        private fun resolveLibraries(libraries: MapProperty) {
          libraries.elements.forEach { element ->
            parseLibraryElement(element)?.let { (lib, location) ->
              callDetectors(lib, location)
            }
          }
        }

        private fun wrapWithApostrophe(value: String): String = "'$value'"

        private fun resolveVersions(versions: MapProperty) {
          versions.elements.forEach { element ->
            val key = element.elementName
            val value = parseVersionElement(element)
            if (value != null) variables[key] = Pair(value, element)
            // iterate through libraries id any of those waiting for
            libraries[key]?.forEach { lib ->
              val finalLibrary = "$lib:$value"
              callDetectors(finalLibrary, element)
            }
          }
        }

        private fun callDetectors(value: String,
                                  propertyCookie: Any) {
          detectors.forEach {
            it.checkDslPropertyAssignment(context,
                                          "",
                                          wrapWithApostrophe(value),
                                          "dependencies",
                                          null,
                                          propertyCookie,
                                          propertyCookie,
                                          propertyCookie)
          }
        }

        private fun parseLibraryElement(library: Element): Pair<String, Element>? {
          when (library) {
            is LiteralProperty -> return Pair(library.value, library)
            is MapProperty -> { // lib = { ... }
              val map = library.getElementsAsMap()
              val module: String? = map["module"]?.getValueOrNull() ?: run {
                val group = map["group"]?.getValueOrNull()
                val name = map["name"]?.getValueOrNull()
                if (name != null && group != null) {
                  "$group:$name"
                }
                else null
              }
              module?.let { return extractVersion(map, it, library) }
            }
          }
          return null
        }

        private fun extractVersion(map: Map<String, Element>, module: String, libraryElement: Element): Pair<String, Element>? {
          val versionRef = map["version.ref"]?.getValueOrNull()
          val version = parseVersionElement(map["version"])
          if (versionRef != null) {
            if (variables[versionRef] != null) {
              val (ver, element) = variables[versionRef]!!
              return Pair("$module:$ver", element)
            }
            else {
              // add to buffer as there version with such name has not been parsed
              libraries.getOrPut(versionRef) { mutableListOf() }.add("$module")
            }
          }
          else if (version != null) {
            //version is literal
            return Pair("$module:$version", libraryElement)
          }
          return null
        }


        private fun parseVersionElement(version: Element?): String? {
          assert(version == null || version.elementName != "version.ref")
          if (version == null) return null
          return when (version) {
            is LiteralProperty -> version.value
            is MapProperty -> {
              val map = version.getElementsAsMap()
              val strictElement = map["strictly"]?.getValueOrNull()
              val output = StringBuilder()
              strictElement?.let { output.append("$it!!") }
              if (output.isNotEmpty()) output.toString() else null
            }
          }
        }
      })
    })
  }

  private fun getVisitor(context: MapProperty,
                         name: String): TomlRecursiveVisitor = object : TomlRecursiveVisitor() {

    override fun visitInlineTable(element: TomlInlineTable) {
      val map = MapProperty(name, element)
      context.addElement(map)
      getVisitor(map, "").let { visitor ->
        element.entries.forEach {
          it.accept(visitor)
        }
      }
    }

    override fun visitLiteral(element: TomlLiteral) {
      extractValue(element)?.let {
        val literal = LiteralProperty(name, it, element)
        context.addElement(literal)
      }
    }

    private fun extractValue(literal: TomlLiteral): String? {
      return when (val kind = literal.kind) {
        is TomlLiteralKind.String -> kind.value
        else -> literal.text
      }
    }

    override fun visitKeyValue(element: TomlKeyValue) {
      val key = element.key.segments.map { it.name }.joinToString(separator = ".")
      getVisitor(context, key).let { element.value?.accept(it) }
    }

  }
}

//Middle level model classes to transform PSI level to
sealed class Element(val elementName: String, val psi: PsiElement) {
  /*
   * Utility method to get value literal if possible
   */
  fun getValueOrNull(): String? {
    return (this as? LiteralProperty)?.value
  }
}

class LiteralProperty(val name: String, val value: String, val psiElement: PsiElement) : Element(name, psiElement)
class MapProperty(val name: String, val psiElement: PsiElement) : Element(name, psiElement) {
  private val elementsMap = LinkedHashMap<String, Element>()
  val elements: Collection<Element>
    get() = elementsMap.values

  fun addElement(element: Element) {
    elementsMap[element.elementName] = element
  }

  fun getElementsAsMap(): Map<String, Element> = elementsMap
}

private const val TOML_TABLE_VERSIONS = "versions"
private const val TOML_TABLE_LIBRARIES = "libraries"
