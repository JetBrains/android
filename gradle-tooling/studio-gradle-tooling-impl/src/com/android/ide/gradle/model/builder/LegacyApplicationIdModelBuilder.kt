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
package com.android.ide.gradle.model.builder

import com.android.ide.gradle.model.LegacyApplicationIdModel
import com.android.ide.gradle.model.impl.LegacyApplicationIdModelImpl
import org.gradle.api.DomainObjectSet
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * An injected Gradle tooling model builder to fetch the Application ID from AGP versions that don't report it directly in the model
 *
 * This model should not be requested when AGP >= 7.4 is used, as the information is held directly in the model.
 */
class LegacyApplicationIdModelBuilder(private val pluginType: PluginType) : ToolingModelBuilder {

  override fun canBuild(modelName: String): Boolean {
    return modelName == LegacyApplicationIdModel::class.java.name
  }


  override fun buildAll(modelName: String, project: Project): LegacyApplicationIdModel {
    check (modelName == LegacyApplicationIdModel::class.java.name) { "Only valid model is ${LegacyApplicationIdModel::class.java.name}" }

    val extension = project.extensions.findByName("android") ?: return LegacyApplicationIdModelImpl(mapOf(), listOf())

    val applicationIdMap = mutableMapOf<String, String>()
    val problems = mutableListOf<Exception>()

    for (method: String in pluginType.variantCollectionGetters(extension)) {
      val container = extension.invokeMethod<DomainObjectSet<*>>(method)
      for (variant in container) {
        val componentName = variant.invokeMethod<String>("getName")
        val applicationId = variant.javaClass.getMethodCached("getApplicationId").runCatching {
          invoke(variant) as String
        }.getOrElse {
          problems += RuntimeException(
            "Failed to read applicationId for ${componentName}.\n" +
            "Setting the application ID to the output of a task in the variant api is not supported",
            it
          )
          null
        } ?: continue
        applicationIdMap[componentName] = applicationId
      }
    }
    return LegacyApplicationIdModelImpl(applicationIdMap, problems)
  }

  enum class PluginType(val variantCollectionGetters: (Any) -> Set<String>) {
    APPLICATION({ setOf("getApplicationVariants", "getTestVariants") }),
    LIBRARY({ setOf("getTestVariants") }),
    TEST({ setOf("getApplicationVariants") }),
    // Don't get main application IDs from dynamic features (see comment on com.android.builder.model.v2.ide.AndroidArtifact.applicationId)
    DYNAMIC_FEATURE({ setOf("getTestVariants") }),
    INSTANT_APP_FEATURE(
      { androidExtension ->
        // Only return the main application ID for base features (equivalent to apps in the new dynamic feature world)
        if (androidExtension.invokeMethod<Boolean?>("getBaseFeature") == true) {
          setOf("getFeatureVariants", "getTestVariants")
        } else {
          setOf("getTestVariants")
        }
      }
    ),
  }

  companion object {

    private data class MethodCacheKey(val clazz: Class<*>, val methodName: String)

    private val methodCache: MutableMap<MethodCacheKey, Method> = ConcurrentHashMap()

    private fun Class<*>.getMethodCached(name: String) =
      methodCache.computeIfAbsent(MethodCacheKey(this, name)) { key -> key.clazz.getMethod(key.methodName) }

    fun <T> Any.invokeMethod(methodName: String): T {
      @Suppress("UNCHECKED_CAST")
      return javaClass.getMethodCached(methodName).invoke(this) as T
    }

    fun maybeRegister(project: Project, registry: ToolingModelBuilderRegistry) {
      project.pluginManager.withPlugin("com.android.application") { register(registry, PluginType.APPLICATION) }
      project.pluginManager.withPlugin("com.android.library") { register(registry, PluginType.LIBRARY) }
      project.pluginManager.withPlugin("com.android.dynamic-feature") { register(registry, PluginType.DYNAMIC_FEATURE) }
      project.pluginManager.withPlugin("com.android.feature") { register(registry, PluginType.INSTANT_APP_FEATURE) }
      project.pluginManager.withPlugin("com.android.test") { register(registry, PluginType.TEST) }
    }

    private fun register(registry: ToolingModelBuilderRegistry, pluginType: PluginType) {
        registry.register(LegacyApplicationIdModelBuilder(pluginType))
    }
  }
}
