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
package com.android.tools.idea.gradle.dsl.model

import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription
import com.google.common.collect.ImmutableMap
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.DynamicPluginListener.Companion.TOPIC
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Experimental
class GradleBlockModelMap internal constructor() {
  private val blockMapCache: MutableMap<Pair<Class<out GradleDslModel>,GradleDslNameConverter.Kind>, Map<Class<out GradleDslModel>, BlockModelBuilder<*, *>>> =
    ConcurrentHashMap()
  private val elementMapCache: MutableMap<Pair<Class<out GradlePropertiesDslElement>,GradleDslNameConverter.Kind>, ImmutableMap<String, PropertiesElementDescription<*>>> =
    ConcurrentHashMap()

  init {
    val connection = ApplicationManager.getApplication().messageBus.connect()
    connection.subscribe(TOPIC, object : DynamicPluginListener {
      override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        resetCache()
      }

      override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        resetCache()
      }
    })
  }

  @Suppress("UNCHECKED_CAST")
  fun <T : GradleDslModel, P : GradleDslModel, PD : GradlePropertiesDslElement> getBlockModel(
    dslElement: PD,
    parentType: Class<out P>,
    modelInterface: Class<T>
  ): T {
    val kind = dslElement.dslFile.parser.kind
    val blockMap = getOrCreateBlockMap(parentType, kind)
    val builder = blockMap[modelInterface]?.let { it as BlockModelBuilder<T, PD> }
                       ?: throw IllegalArgumentException("Block model for $modelInterface is not registered in $parentType")
    return builder.create(dslElement)
  }

  fun getOrCreateElementMap(parentType: Class<out GradlePropertiesDslElement>, kind: GradleDslNameConverter.Kind): ImmutableMap<String, PropertiesElementDescription<*>> {
    return elementMapCache.computeIfAbsent(parentType to kind) { calculateElements(it.first, it.second) }
  }

  private fun getOrCreateBlockMap(parentType: Class<out GradleDslModel>, kind: GradleDslNameConverter.Kind): Map<Class<out GradleDslModel>, BlockModelBuilder<*, *>> {
    return blockMapCache.computeIfAbsent(parentType to kind) { calculateBlocks(it.first, it.second) }
  }

  fun <T : GradleDslModel> childrenOf(parentType: Class<T>, kind: GradleDslNameConverter.Kind): Set<Class<out GradleDslModel>> {
    val modelsMap = getOrCreateBlockMap(parentType, kind)
    return Collections.unmodifiableSet(modelsMap.keys)
  }

  @TestOnly
  fun resetCache() {
    blockMapCache.clear()
    elementMapCache.clear()
  }

  companion object {
    @JvmStatic
    fun getElementMap(parentType: Class<out GradlePropertiesDslElement>, kind: GradleDslNameConverter.Kind): ImmutableMap<String, PropertiesElementDescription<*>> {
      return ApplicationManager.getApplication().getService(GradleBlockModelMap::class.java).getOrCreateElementMap(parentType, kind)
    }

    @JvmStatic
    operator fun <T : GradleDslModel, P : GradleDslModel, PD : GradlePropertiesDslElement> get(
      dslElement: PD,
      parentType: Class<out P>,
      modelInterface: Class<T>
    ): T {
      return instance.getBlockModel(dslElement, parentType, modelInterface)
    }

    val instance: GradleBlockModelMap
      get() = ApplicationManager.getApplication().getService(GradleBlockModelMap::class.java)

    private fun calculateElements(parentType: Class<out GradlePropertiesDslElement>, kind: GradleDslNameConverter.Kind): ImmutableMap<String, PropertiesElementDescription<*>> {
      val builder = ImmutableMap.builder<String, PropertiesElementDescription<*>>()
      BlockModelProvider.EP.forEachExtensionSafe { p: BlockModelProvider<*, *> ->
        if (!p.parentDslClass.isAssignableFrom(parentType)) {
          return@forEachExtensionSafe
        }
        val elementsMap = p.elementsMap(kind)
        builder.putAll(elementsMap)
      }
      return builder.build()
    }

    private fun calculateBlocks(parentType: Class<out GradleDslModel>, kind: GradleDslNameConverter.Kind): Map<Class<out GradleDslModel>, BlockModelBuilder<*, *>> {
      val result: MutableMap<Class<out GradleDslModel>, BlockModelBuilder<*, *>> = HashMap()
      BlockModelProvider.EP.forEachExtensionSafe { p: BlockModelProvider<*, *> ->
        if (!p.parentClass.isAssignableFrom(parentType)) {
          return@forEachExtensionSafe
        }
        val builders = p.availableModels(kind)
        builders.forEach { builder: BlockModelBuilder<*, *> -> result[builder.modelClass()] = builder }
      }
      return result
    }
  }
}
