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
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

/**
 * A cache wrapper around all PSI resource items aggregated from all [BindingLayoutInfo] instances
 * within in a [BindingLayoutGroup].
 *
 * When a layout has multiple configurations, each separate layout may declare its own collection
 * of variables, but the classes we generate for them need to present a unified API that exposes
 * ways to access all of them.
 */
class AggregateBindingItemCache(private val layouts: List<BindingLayoutInfo>) {
  private val aggregateCache: CachedValue<Map<DataBindingResourceType, Map<String, PsiDataBindingResourceItem>>>

  init {
    val cacheManager = CachedValuesManager.getManager(layouts[0].psi.project)
    aggregateCache = cacheManager.createCachedValue(
      {
        // Each layout owns a map of maps (e.g. {"variable" -> {"a" to PSI, "b" to PSI} }).
        // To merge everything, we create our own local map of maps, iterate over the original
        // contents, and add all information to our merged version. Any duplicates are ignored.
        val allResourceItems =
          DataBindingResourceType.values().associate { type -> type to mutableMapOf<String, PsiDataBindingResourceItem>() }
        layouts
          .map { info -> info.psi.resourceItems }
          .flatMap { items -> items.values }
          .flatMap { nameToItems -> nameToItems.values }
          .forEach { item ->
            val allNamesToItems = allResourceItems.getValue(item.type)
            allNamesToItems.putIfAbsent(item.name, item)
          }
        CachedValueProvider.Result.create(allResourceItems as Map<DataBindingResourceType, Map<String, PsiDataBindingResourceItem>>, layouts)
      }, false)
  }

  operator fun get(type: DataBindingResourceType): Map<String, PsiDataBindingResourceItem> {
    return aggregateCache.value[type] ?: emptyMap()
  }
}
