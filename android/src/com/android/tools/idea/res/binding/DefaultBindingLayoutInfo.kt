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

import com.android.SdkConstants.TAG_LAYOUT
import com.android.ide.common.resources.DataBindingResourceType
import com.android.tools.idea.databinding.DataBindingUtil
import com.android.tools.idea.res.PsiResourceFile
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.facet.AndroidFacet

/**
 * Binding info for a single, target layout XML file.
 *
 * See also: [MergedBindingLayoutInfo]
 */
class DefaultBindingLayoutInfo(
  override val facet: AndroidFacet,
  private val psiResourceFile: PsiResourceFile,
  override var className: String,
  override var packageName: String,
  private var customBinding: Boolean) : BindingLayoutInfo() {

  override val project: Project
    get() = psiResourceFile.psiFile.project

  override val module: Module?
    get() = ModuleUtilCore.findModuleForPsiElement(psiResourceFile.psiFile)

  /**
   * resource type -> name -> resource item
   * (e.g. VARIABLE + "varName" -> resource item representing the variable)
   */
  private val _resourceItems = mutableMapOf<DataBindingResourceType, Map<String, PsiDataBindingResourceItem>>()
  val resourceItems: Map<DataBindingResourceType, Map<String, PsiDataBindingResourceItem>>
    get() = _resourceItems

  /**
   * How many times the info's layout has changed (essentially, one of the <data> tags has changed)
   */
  private var layoutModificationCount = 0L

  /**
   * How many times we have updated the [mergedInfo] value.
   */
  private var mergedModificationCount = 0L

  override fun getModificationCount(): Long {
    return layoutModificationCount + mergedModificationCount
  }

  /**
   * The raw class name, as [className] itself may get modified to match a specific layout
   * configuration.
   */
  var nonConfigurationClassName: String
    private set

  /**
   * The specific layout configuration for this layout, e.g. "layout-land", "layout-w600dp"
   *
   * This value is extracted from the layout file's owning folder, but it may be set to the empty
   * string if, for some reason, the name couldn't be found via PSI APIs (this shouldn't happen...)
   */
  val configurationName: String

  private var _mergedInfo: MergedBindingLayoutInfo? = null
  override val mergedInfo: BindingLayoutInfo?
      get() { return _mergedInfo }

  val fileName: String
    get() = psiResourceFile.name

  override val navigationElement: PsiElement
    get() = BindingLayoutInfoFile(this)

  override val psiFile: PsiFile
    get() = psiResourceFile.psiFile

  override fun getItems(type: DataBindingResourceType): Map<String, PsiDataBindingResourceItem> {
    return _resourceItems[type] ?: emptyMap()
  }

  override val layoutType: LayoutType
    get() = if (TAG_LAYOUT == (psiResourceFile.psiFile as XmlFile).rootTag!!.name) {
      LayoutType.DATA_BINDING_LAYOUT
    }
    else {
      LayoutType.VIEW_BINDING_LAYOUT
    }

  override val isMerged: Boolean
    get() = false

  init {
    nonConfigurationClassName = className
    configurationName = psiResourceFile.psiFile.parent?.name ?: ""
  }

  /**
   * Update the qualified class name for this info.
   *
   * This information comes from the `<data class="...">` attribute.
   */
  fun update(className: String, packageName: String, customBinding: Boolean, modificationCount: Long) {
    if (nonConfigurationClassName == className && this.packageName == packageName && this.customBinding == customBinding) {
      return
    }

    this.nonConfigurationClassName = className
    this.packageName = packageName
    this.customBinding = customBinding
    updateClassName()
    layoutModificationCount = modificationCount
  }

  /**
   * Completely replace all the `import`/`variable` (i.e. `<data>`) resources in this info.
   */
  fun replaceItems(newItems: List<PsiDataBindingResourceItem>, modificationCount: Long) {
    val newItemsGrouped = newItems.associate { item -> item.type to mutableMapOf<String, PsiDataBindingResourceItem>() }
    for (item in newItems) {
      val itemsByName = newItemsGrouped.getValue(item.type)
      itemsByName[item.name] = item
    }

    if (newItemsGrouped != _resourceItems) {
      _resourceItems.clear()
      _resourceItems.putAll(newItemsGrouped)
      layoutModificationCount = modificationCount
    }
  }

  /**
   * Attach merged info to a core info (useful if there are multiple configurations)
   */
  fun setMergedInfo(mergedInfo: MergedBindingLayoutInfo?) {
    if (_mergedInfo == mergedInfo) {
      return
    }
    mergedModificationCount++
    _mergedInfo = mergedInfo
    updateClassName()
  }

  private fun updateClassName() {
    if (_mergedInfo != null) {
      className = buildFinalClassName(configurationName, nonConfigurationClassName)
    }
    else {
      className = nonConfigurationClassName
    }
  }

  /**
   * Given a configuration (which may be "") and the unadorned class name, generate a final class
   * name for the combination.
   *
   * For example, "layout-land" + "CustomBinding" -> "CustomBindingLandImpl"
   */
  private fun buildFinalClassName(configurationName: String, rawClassName: String): String {
    val suffix: String = when {
      configurationName.isEmpty() -> "Impl"
      configurationName.startsWith("layout-") ->
        DataBindingUtil.convertToJavaClassName(configurationName.substringAfter("layout-")) + "Impl"
      configurationName.startsWith("layout") -> "Impl"
      else -> DataBindingUtil.convertToJavaClassName(configurationName) + "Impl"
    }

    return rawClassName + suffix
  }
}
