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

import com.intellij.openapi.vfs.VirtualFile

private fun String.cleanInnerClassNames() = replace('$', '.')

/**
 * A class that represents raw data extracted from a layout's xml file, plus some additional
 * relevant metadata (e.g. folder and filename).
 *
 * This does not contain all XML data; just parts that are particularly relevant to
 * data binding / view binding.
 *
 * @param customBindingName A custom binding name, which a user may optionally set using the
 * `<data class=...>` attribute
 */
data class BindingLayoutXml(val file: VirtualFile,
                            val customBindingName: String? = null,
                            val variables: List<Variable> = emptyList(),
                            val imports: List<Import> = emptyList()) {
  class Variable(val name: String, type: String?) {
    val type: String? = type?.cleanInnerClassNames()
  }

  class Import(type: String, val alias: String?) {
    val type: String = type.cleanInnerClassNames()

    /**
     * The alias of this import or its unqualified type.
     *
     * An import is either a type plus an alias, e.g. `<import alias="MyMap" type="java.util.Map"/>`,
     * or just a pure type, e.g. `<import type="java.util.Map"/>`. In the former case, this
     * property will return the alias, i.e. "MyMap", while in the latter case, the type, i.e. "Map".
     */
    val aliasOrType: String = alias ?: type.substringAfterLast('.')
  }

  /**
   * The name of the folder that the layout for this binding is located in.
   *
   * The folder name defines the layout's configuration (e.g. "layout-land").
   */
  val folderName
    get() = file.parent.name
}
