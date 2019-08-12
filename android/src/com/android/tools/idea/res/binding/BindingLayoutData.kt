/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.res.binding

import com.android.tools.idea.res.binding.BindingLayoutType.DATA_BINDING_LAYOUT
import com.android.tools.idea.res.binding.BindingLayoutType.VIEW_BINDING_LAYOUT
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet

/**
 * Represents raw data extracted from contents of the <data> tag of a layout XML file.
 *
 * @param facet the Android facet of the module containing the layout
 * @param file the layout XML file
 * @param layoutType either [VIEW_BINDING_LAYOUT] or [DATA_BINDING_LAYOUT]
 * @param customBindingName A custom binding name, if defined by the `class` attribute of the `<data class="...">` XML tag
 * @param variables databinding variables keyed by their names from the `<variable name="..." type="..."/>` XML tags
 * @param imports imports keyed by their imported short names from the `<import type="..." [alias="..."]/>` XML tags
 */
data class BindingLayoutData(val facet: AndroidFacet,
                             val file: VirtualFile,
                             val layoutType: BindingLayoutType,
                             val customBindingName: String?,
                             val variables: Map<String, Variable>,
                             val imports: Map<String, Import>) {
  class Variable(val name: String, val type: String?)

  class Import(val qualifiedName: String, val importedShortName: String) {
    /**
     * Checks if the [importedShortName] matches the last segment of the qualified name.
     */
    fun isShortNameDerivedFromQualifiedName(): Boolean {
      return qualifiedName.endsWith(importedShortName) &&
             qualifiedName.length > importedShortName.length &&
             qualifiedName[qualifiedName.length - importedShortName.length - 1] == '.'
    }

    companion object Util {
      /**
       * Returns the imported short type name given a qualified type name and an optional alias.
       */
      @JvmStatic
      fun getImportedShortName(qualifiedName: String, alias: String?): String {
        return alias ?: qualifiedName.substringAfterLast('.')
      }
    }
  }

  /**
   * Returns a variable definition given a variable name, or null if not found.
   */
  fun findVariable(variableName: String): Variable? = variables[variableName]

  /**
   * Returns an import statement given an imported unqualified type name, or null if not found.
   */
  fun findImport(importedShortName: String): Import? = imports[importedShortName]

  /**
   * Given an alias or an unqualified type name, returns the qualified type name it resolves to, if such
   * a rule is registered with this layout (e.g.
   * `"Calc"` for `<import alias='Calc' type='org.example.math.calc.Calculator'>` or
   * `"Map"` for `<import type='java.util.Map'>`)
   */
  fun resolveImport(importedShortName: String): String? = findImport(importedShortName)?.qualifiedName
}

/**
 * Layout's relationship to data binding.
 *
 * Note: This enum is used by DataBindingXmlIndex and is serialized and de-serialized. Please only append.
 */
enum class BindingLayoutType {
  /**
   * The layout XML doesn't contain any data binding information, but view binding option is enabled.
   */
  VIEW_BINDING_LAYOUT,
  /**
   * The layout XML starts with `<layout>` tag, and possibly contains a `<data>` tag.
   */
  DATA_BINDING_LAYOUT
}
