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

import com.android.ide.common.resources.DataBindingResourceType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.android.facet.AndroidFacet

/**
 * Binding info merged from multiple BindingLayoutInfo instances from different configurations.
 */
class MergedBindingLayoutInfo(val infos: List<DefaultBindingLayoutInfo>) : BindingLayoutInfo() {
  private val baseInfo: DefaultBindingLayoutInfo
  private val resourceItemCache: CachedValue<Map<DataBindingResourceType, Map<String, PsiDataBindingResourceItem>>>

  init {
    baseInfo = selectBaseInfo()
    val cacheManager = CachedValuesManager.getManager(baseInfo.project)
    resourceItemCache = cacheManager.createCachedValue(
      {
        val allResourceItems =
          DataBindingResourceType.values().associate { type -> type to mutableMapOf<String, PsiDataBindingResourceItem>() }
        infos
          .map { info -> info.resourceItems }
          .flatMap { items -> items.values }
          .flatMap { nameToItems -> nameToItems.values }
          .forEach { item ->
            val allNamesToItems = allResourceItems.getValue(item.type)
            allNamesToItems.putIfAbsent(item.name, item)
          }
        CachedValueProvider.Result.create(allResourceItems as Map<DataBindingResourceType, Map<String, PsiDataBindingResourceItem>>, infos)
      }, false)
  }

  override val facet: AndroidFacet
    get() = baseInfo.facet

  override val project: Project
    get() = baseInfo.project

  override val navigationElement: PsiElement
    get() = baseInfo.navigationElement

  override val psiFile: PsiFile
    get() = baseInfo.psiFile

  override val module: Module?
    get() = baseInfo.module

  override fun getItems(type: DataBindingResourceType): Map<String, PsiDataBindingResourceItem> {
    return resourceItemCache.value[type] ?: emptyMap()
  }

  override fun getModificationCount(): Long {
    return infos.map { info -> info.modificationCount }.sum()
  }

  override val layoutType: LayoutType
    get() = baseInfo.layoutType

  override val isMerged: Boolean
    get() = true

  override val mergedInfo: BindingLayoutInfo?
    get() = null

  override val className: String
    get() = baseInfo.nonConfigurationClassName

  override val packageName: String
    get() = baseInfo.packageName

  private fun selectBaseInfo(): DefaultBindingLayoutInfo {
    // The base info is the one that has the shortest configuration name, e.g. "layout" vs "layout-w600dp"
    return infos.minBy { info -> info.configurationName.length }!!
  }
}
