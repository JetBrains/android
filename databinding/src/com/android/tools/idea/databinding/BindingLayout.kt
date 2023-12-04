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
package com.android.tools.idea.databinding

import com.android.ide.common.resources.ResourceItem
import com.android.tools.idea.databinding.BindingLayout.Companion.tryCreate
import com.android.tools.idea.databinding.index.BindingLayoutType
import com.android.tools.idea.databinding.index.BindingXmlData
import com.android.tools.idea.databinding.index.BindingXmlIndex
import com.android.tools.idea.databinding.util.DataBindingUtil
import com.android.tools.idea.databinding.util.isViewBindingEnabled
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.getSourceAsVirtualFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.facet.AndroidFacet

/**
 * Information for a single, target layout XML file that is useful for generating a Binding or
 * BindingImpl class (assuming it is a data binding or a view binding layout).
 *
 * See also: [BindingLayoutGroup], which owns one (or more) related [BindingLayout] instances.
 *
 * Note: In order to ensure that all necessary constructor fields are non-null, and to avoid a user
 * from specifying values that are inconsistent with each other, you cannot instantiate this class
 * directly. Instead, you must use [tryCreate].
 *
 * @param facet the facet of the Android module this layout belongs to.
 * @param modulePackage the base package for all classes in the current module.
 * @param file the [VirtualFile] backing the XML layout associated with this binding.
 * @param data the raw [BindingXmlData] extracted from this binding's layout. If you need a PSI
 *   representation of this data, see [toXmlFile]
 * @param resource the [ResourceItem] representation of the XML layout file.
 */
class BindingLayout
private constructor(
  private val facet: AndroidFacet,
  private val modulePackage: String,
  val file: VirtualFile,
  val data: BindingXmlData,
  internal val resource: ResourceItem
) {

  companion object {
    /**
     * Tries to create a [BindingLayout] instance corresponding to a target resource file, returning
     * null if unable to do so or if the target layout should not have a binding created for it.
     *
     * Most initialization logic in here is not expected to return null, but it has been reported in
     * production, so the following plays it safe to avoid crashing. See: b/140308533
     */
    fun tryCreate(facet: AndroidFacet, resource: ResourceItem): BindingLayout? {
      val modulePackage = facet.getModuleSystem().getPackageName() ?: return null
      val file = resource.getSourceAsVirtualFile() ?: return null
      val data = BindingXmlIndex.getDataForFile(facet.module.project, file) ?: return null
      if (
        data.viewBindingIgnore ||
          (data.layoutType == BindingLayoutType.PLAIN_LAYOUT && !facet.isViewBindingEnabled())
      )
        return null
      return BindingLayout(facet, modulePackage, file, data, resource)
    }
  }

  /**
   * Creates a PSI representation of the XML layout associated with this binding.
   *
   * Note that PSI parsing incurs a performance hit, so this should only be called if needed.
   * Otherwise, it is preferable to work with [file] and [data] directly.
   *
   * In most cases, this method will return a valid PSI file, but it could technically return null,
   * so that should be handled.
   */
  fun toXmlFile(): XmlFile? {
    return DataBindingUtil.findXmlFile(facet.module.project, file)
  }

  private val bindingClassName = computeBindingClassName()

  private fun computeBindingClassName(): BindingClassName {
    if (data.customBindingName.isNullOrEmpty()) {
      return BindingClassName(
        "$modulePackage.databinding",
        DataBindingUtil.convertFileNameToJavaClassName(file.name) + "Binding"
      )
    } else {
      val customBindingName = data.customBindingName!!
      val firstDotIndex = customBindingName.indexOf('.')

      if (firstDotIndex < 0) {
        return BindingClassName("$modulePackage.databinding", customBindingName)
      } else {
        val lastDotIndex = customBindingName.lastIndexOf('.')
        val packageName =
          if (firstDotIndex == 0) {
            // A custom name like ".ExampleBinding" generates a binding class in the module package.
            modulePackage + customBindingName.substring(0, lastDotIndex)
          } else {
            customBindingName.substring(0, lastDotIndex)
          }
        val simpleClassName = customBindingName.substring(lastDotIndex + 1)
        return BindingClassName(packageName, simpleClassName)
      }
    }
  }

  val packageName
    get() = bindingClassName.packageName

  val className
    get() = bindingClassName.className

  val qualifiedClassName
    get() = bindingClassName.qualifiedClassName

  /**
   * Returns the unique "Impl" suffix for this specific layout configuration.
   *
   * In multi-layout configurations, a general "Binding" class will be generated as well as a unique
   * "Impl" version for each configuration. This method returns what that exact "Impl" suffix should
   * be, which can safely be appended to [qualifiedClassName] or [className].
   */
  fun getImplSuffix(): String {
    val folderName = file.parent.name
    return when {
      folderName.isEmpty() -> "Impl"
      folderName.startsWith("layout-") ->
        DataBindingUtil.convertFileNameToJavaClassName(folderName.substringAfter("layout-")) +
          "Impl"
      folderName.startsWith("layout") -> "Impl"
      else -> DataBindingUtil.convertFileNameToJavaClassName(folderName) + "Impl"
    }
  }

  /**
   * The package + name for the binding class generated for this layout.
   *
   * The package for a binding class is usually a subpackage of module's package, but it can be
   * fully customized based on the value of [BindingXmlData.customBindingName].
   *
   * See also: [getImplSuffix], if you want to generate the path to a binding impl class instead.
   */
  private class BindingClassName(val packageName: String, val className: String) {
    val qualifiedClassName
      get() = "${packageName}.${className}"
  }

  override fun equals(other: Any?): Boolean {
    return other is BindingLayout && file.path == other.file.path
  }

  override fun hashCode(): Int {
    return file.path.hashCode()
  }
}
