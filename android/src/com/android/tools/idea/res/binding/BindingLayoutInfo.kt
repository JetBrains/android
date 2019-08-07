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

import com.android.tools.idea.databinding.DataBindingUtil
import com.android.tools.idea.res.binding.BindingLayoutInfo.LayoutType.DATA_BINDING_LAYOUT
import com.android.tools.idea.res.binding.BindingLayoutInfo.LayoutType.VIEW_BINDING_LAYOUT
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet

/**
 * Info for a single, target layout XML file useful for generating a Binding or BindingImpl class
 * (assuming it is a data binding or view binding layout).
 *
 * See also: [BindingLayoutGroup], which owns one (or more) related [BindingLayoutInfo] instances.
 *
 * @param modulePackage The package for the current module, useful when generating this binding's
 *    path. While in practice this shouldn't be null, a user or test configuration may have left
 *    this unset. See also: `MergedManifestSnapshot.getPackage`.
 *    TODO(b/138720985): See if it's possible to make modulePackage non-null
 * @param layoutFile The virtual file for the layout.xml this binding will be associated with.
 *    This file is expected to exist directly underneath a layout directory.
 * @param customBindingName A name which, if present, modifies the logic for choosing a name for
 *    a generated binding class.
 */
class BindingLayoutInfo(private val facet: AndroidFacet,
                        private val modulePackage: String?,
                        layoutFile: VirtualFile,
                        customBindingName: String?) : ModificationTracker {
  init {
    // XML layout files should always exist in a parent layout directory
    assert(layoutFile.parent != null)
  }

  /**
   * The package + name for the binding class we want to generate for this layout.
   *
   * The package for a binding class is usually a subpackage of [modulePackage], but it can be
   * fully customized based on the value passed in for `customBindingName`
   *
   * See also: [getImplSuffix], if you want to generate the path to a binding impl class instead.
   */
  private class BindingClassPath(val packageName: String, val className: String)

  /**
   * The different android layouts that we create [BindingLayoutInfo] for, depending on whether data binding or view binding is
   * switched on.
   *
   * [VIEW_BINDING_LAYOUT] bindings are generated for legacy views - those that are not data binding views.
   *
   * [DATA_BINDING_LAYOUT] bindings are generated for views using data binding. They start with `<layout>` and `<data>`
   * tags.
   *
   * When both are enabled, data binding layouts will be of type [DATA_BINDING_LAYOUT], the rest will be [VIEW_BINDING_LAYOUT].
   *
   * Note: This enum is used by DataBindingXmlIndex and is serialized and de-serialized. Please only append.
   */
  enum class LayoutType {
    VIEW_BINDING_LAYOUT,
    DATA_BINDING_LAYOUT
  }

  /**
   * Relevant, non-PSI information related to or extracted from the XML layout associated with this
   * binding.
   *
   * Note: This information can also be requested through the [psi] field, but doing so may cause a
   * more expensive parsing pass to happen to lazily generate a PSI tree. Prefer using the raw xml
   * data directly whenever possible.
   */
  var xml = BindingLayoutXml(layoutFile, customBindingName)
    private set

  /**
   * PSI-related information for this binding.
   *
   * It is instantiated lazily. Callers should only access it when they need to. Otherwise, they
   * should try to get the information they need using the [xml] property.
   */
  val psi: BindingLayoutPsi by lazy { BindingLayoutPsi(facet, this) }

  /**
   * Note: This backing field is lazily loaded but potentially reset by [updateClassData].
   */
  private var _bindingClassPath: BindingClassPath? = null
  private val bindingClassPath: BindingClassPath
    get() {
      if (_bindingClassPath == null) {
        if (xml.customBindingName.isNullOrEmpty()) {
          _bindingClassPath = BindingClassPath("$modulePackage.databinding",
                                               DataBindingUtil.convertToJavaClassName(xml.file.name) + "Binding")
        }
        else {
          val customBindingName = xml.customBindingName!!
          val firstDotIndex = customBindingName.indexOf('.')

          if (firstDotIndex < 0) {
            _bindingClassPath = BindingClassPath("$modulePackage.databinding", customBindingName)
          }
          else {
            val lastDotIndex = customBindingName.lastIndexOf('.')
            val packageName = if (firstDotIndex == 0) {
              // A custom name like ".ExampleBinding" generates a binding class in the module package
              modulePackage + customBindingName.substring(0, lastDotIndex)
            }
            else {
              customBindingName.substring(0, lastDotIndex)
            }
            val className = customBindingName.substring(lastDotIndex + 1)
            _bindingClassPath = BindingClassPath(packageName, className)
          }
        }
      }

      return _bindingClassPath!!
    }

  val packageName
    get() = bindingClassPath.packageName
  val className
    get() = bindingClassPath.className
  val qualifiedName
    get() = "${packageName}.${className}"

  internal var modificationCount: Long = 0

  /**
   * Returns the unique "Impl" suffix for this specific layout configuration.
   *
   * In multi-layout configurations, a general "Binding" class will be generated as well as a
   * unique "Impl" version for each configuration. This method returns what that exact "Impl"
   * suffix should be, which can safely be appended to [qualifiedName] or [className].
   */
  fun getImplSuffix(): String {
    val folderName = xml.folderName
    return when {
      folderName.isEmpty() -> "Impl"
      folderName.startsWith("layout-") ->
        DataBindingUtil.convertToJavaClassName(folderName.substringAfter("layout-")) + "Impl"
      folderName.startsWith("layout") -> "Impl"
      else -> DataBindingUtil.convertToJavaClassName(folderName) + "Impl"
    }
  }

  /**
   * Given an alias or (unqualified) type name, returns the (qualified) type it resolves to, if such
   * a rule is registered with this layout (e.g.
   * `"Calc"` for `<import alias='Calc' type='org.example.math.calc.Calculator'>` or
   * `"Map"` for `<import type='java.util.Map'>`)
   */
  fun resolveImport(aliasOrType: String): String? = xml.imports.find { import -> import.aliasOrType == aliasOrType }?.type

  /**
   * Updates settings for generating the final Binding name for this layout.
   */
  fun updateClassData(customBindingName: String?, modificationCount: Long) {
    if (xml.customBindingName == customBindingName) {
      return
    }

    xml = xml.copy(customBindingName = customBindingName)
    _bindingClassPath = null // Causes this to regenerate lazily next time

    this.modificationCount = modificationCount
  }

  /**
   * Updates this layout info with a whole new set of `<data>` values.
   *
   * After calling this, the data model ([xml]) will be updated.
   */
  fun replaceDataItems(variables: List<BindingLayoutXml.Variable>, imports: List<BindingLayoutXml.Import>, modificationCount: Long) {
    if (xml.variables != variables || xml.imports != imports) {
      xml = xml.copy(variables = variables, imports = imports)
      this.modificationCount = modificationCount
    }
  }

  override fun getModificationCount(): Long = modificationCount
}
