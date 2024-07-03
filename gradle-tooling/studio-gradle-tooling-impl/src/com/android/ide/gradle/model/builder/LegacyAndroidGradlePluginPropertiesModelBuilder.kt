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

import com.android.ide.gradle.model.LegacyAndroidGradlePluginProperties
import com.android.ide.gradle.model.LegacyAndroidGradlePluginPropertiesModelParameters
import com.android.ide.gradle.model.builder.LegacyAndroidGradlePluginPropertiesModelBuilder.VariantCollectionProvider.AndroidTest
import com.android.ide.gradle.model.builder.LegacyAndroidGradlePluginPropertiesModelBuilder.VariantCollectionProvider.ApplicationVariant
import com.android.ide.gradle.model.builder.LegacyAndroidGradlePluginPropertiesModelBuilder.VariantCollectionProvider.DynamicFeature
import com.android.ide.gradle.model.builder.LegacyAndroidGradlePluginPropertiesModelBuilder.VariantCollectionProvider.InstantAppFeature
import com.android.ide.gradle.model.builder.LegacyAndroidGradlePluginPropertiesModelBuilder.VariantCollectionProvider.LibraryVariant
import com.android.ide.gradle.model.impl.LegacyAndroidGradlePluginPropertiesImpl
import org.gradle.api.DomainObjectSet
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import java.lang.reflect.Method
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * An injected Gradle tooling model builder to fetch information from legacy versions of the Android Gradle plugin
 *
 * In particular, the Application ID and namespaces from AGP versions that don't report it directly in the model.
 *
 * This model should not be requested when AGP >= 7.4 is used, as the information is held directly in the model.
 */
class LegacyAndroidGradlePluginPropertiesModelBuilder(private val pluginType: PluginType) : ParameterizedToolingModelBuilder<LegacyAndroidGradlePluginPropertiesModelParameters> {

  override fun getParameterType(): Class<LegacyAndroidGradlePluginPropertiesModelParameters> = LegacyAndroidGradlePluginPropertiesModelParameters::class.java

  override fun canBuild(modelName: String): Boolean {
    return modelName == LegacyAndroidGradlePluginProperties::class.java.name
  }

  override fun buildAll(modelName: String, project: Project): Nothing = error("parameter required")

  override fun buildAll(modelName: String, parameters: LegacyAndroidGradlePluginPropertiesModelParameters, project: Project): LegacyAndroidGradlePluginProperties {
    check (modelName == LegacyAndroidGradlePluginProperties::class.java.name) { "Only valid model is ${LegacyAndroidGradlePluginProperties::class.java.name}" }
    val problems = mutableListOf<Exception>()
    val applicationIdMap = fetchApplicationIds(parameters, project, problems)
    val (namespace, androidTestNamespace) = fetchNamespace(parameters, project, problems)
    val dataBindingEnabled = fetchIsDataBindingEnabled(parameters, project, problems)
    return LegacyAndroidGradlePluginPropertiesImpl(applicationIdMap, namespace, androidTestNamespace, dataBindingEnabled, problems)
  }

  private fun fetchApplicationIds(parameters: LegacyAndroidGradlePluginPropertiesModelParameters, project: Project, problems: MutableList<Exception>): Map<String, String> {
    if (!parameters.componentToApplicationIdMap) return mapOf()
    val extension = project.extensions.findByName("android") ?: return mapOf()
    val applicationIdMap = mutableMapOf<String, String>()
    for (variantCollectionProvider: VariantCollectionProvider in pluginType.variantCollectionProviders(extension)) {
      if (!variantCollectionProvider.providesApplicationId) continue
      val container = variantCollectionProvider.variants
      for (variant in container) {
        val componentName = variant.invokeMethod<String>("getName")
        val applicationId = variant.javaClass.getMethodCached("getApplicationId").runCatching {
          getOrThrow().invoke(variant) as String
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
    return applicationIdMap
  }

  // This only applies to AGP 4.2 and below
  private fun fetchNamespace(parameters: LegacyAndroidGradlePluginPropertiesModelParameters, project: Project, problems: MutableList<Exception>): Pair<String?, String?> {
    if (!parameters.namespace) return Pair(null, null)
    val extension = project.extensions.findByName("android") ?: return Pair(null, null)
    var namespace : String? = null
    var androidTestNamespace : String? = null

    try {
      for (variantCollectionProvider in pluginType.variantCollectionProviders(extension)) {
        val container = variantCollectionProvider.variants
        for (variant in container) {
          // Use getGenerateBuildConfigProvider if possible to avoid triggering a user-visible deprecation warning,
          // but fall back to getGenerateBuildConfig if getGenerateBuildConfigProvider is not present
          val generateBuildConfigTask =
            variant.invokeMethodIfPresent<Any?>("getGenerateBuildConfigProvider")?.invokeMethod("get") ?:
              variant.invokeMethod<Any?>("getGenerateBuildConfig") ?: continue
          val namespaceObject = generateBuildConfigTask.invokeMethod<Any?>("getBuildConfigPackageName") ?: continue
          // This changed type from String to Property<String>, handle both.
          val componentNamespace = if (namespaceObject is String) namespaceObject else namespaceObject.invokeMethod("get") as String
          if (variantCollectionProvider.isMain) {
            namespace = componentNamespace
          } else {
            // In some versions of AGP the android test namespace depends on the application ID, which means that this can,
            // at least in theory, vary per variant. For the purpose of this model, just return one of the values.
            androidTestNamespace = componentNamespace
          }
          if (namespace != null && androidTestNamespace != null) {
            break
          }
        }
      }
    } catch (e: Exception) {
      problems += RuntimeException("Failed to fetch namespace", e)
    }
    return Pair(namespace, androidTestNamespace)
  }

  // This only applies for AGP model producer version < 9 (i.e. below AGP 8.7)
  private fun fetchIsDataBindingEnabled(parameters: LegacyAndroidGradlePluginPropertiesModelParameters, project: Project,  problems: MutableList<Exception>): Boolean? {
    if (!parameters.dataBinding) return null
    val androidExtension = project.extensions.findByName("android") ?: return null
    try {
      val dataBinding = androidExtension.invokeMethod<Any>("getDataBinding")
      // Most recent form
      dataBinding.invokeMethodIfPresent<Boolean>("getEnable")?.let { return it }

      // For 4.x AGPs, buildFeatures was where the value was set and using dataBinding.isEnabled would result in a spurious deprecation
      // warning during sync.
      val buildFeatures = androidExtension.invokeMethodIfPresent<Any>("getBuildFeatures")
      if (buildFeatures != null) {
        // Simulate the logic in those versions of AGP which fell back to the project property, if set.
        return (buildFeatures.invokeMethodIfPresent<Boolean?>("getDataBinding")) ?:
            project.findProperty("android.defaults.buildfeatures.databinding")?.toBoolean()
               ?: false
      }

      // The original way this was exposed in the AGP DSL.
      return dataBinding.invokeMethod("isEnabled")
    } catch (e: Exception) {
      problems += RuntimeException("Failed to read if data binding is enabled", e)
      return null
    }
  }

  // Modelled from AGP's logic in OptionParsers.kt
  private fun Any.toBoolean(): Boolean? = when (this) {
    is Boolean -> this
    is CharSequence ->
      when (toString().lowercase(Locale.US)) {
        "true" -> true
        "false" -> false
        else -> null
      }
    is Number ->
      when (toInt()) {
        0 -> false
        1 -> true
        else -> null
      }
    else -> null
  }

  sealed class VariantCollectionProvider(private val extensionObject: Any, private val getterName: String, val isMain: Boolean, val providesApplicationId: Boolean) {
    class ApplicationVariant(extensionObject: Any): VariantCollectionProvider(extensionObject, getterName = "getApplicationVariants", isMain = true, providesApplicationId = true)
    class LibraryVariant(extensionObject: Any): VariantCollectionProvider(extensionObject, getterName = "getLibraryVariants", isMain = true, providesApplicationId = false)
    // Don't get main application IDs from dynamic features (see comment on com.android.builder.model.v2.ide.AndroidArtifact.applicationId)
    class DynamicFeature(extensionObject: Any): VariantCollectionProvider(extensionObject, getterName = "getApplicationVariants", isMain = true, providesApplicationId = false)
    // Only return the main application ID for base features (equivalent to apps in the new dynamic feature world)
    class InstantAppFeature(extensionObject: Any, isBaseFeature: Boolean): VariantCollectionProvider(extensionObject, getterName = "getFeatureVariants", isMain = true, providesApplicationId = isBaseFeature)
    class AndroidTest(extensionObject: Any): VariantCollectionProvider(extensionObject, getterName = "getTestVariants", isMain = false, providesApplicationId = true)

    val variants: DomainObjectSet<*> get() = extensionObject.invokeMethod(getterName)
  }

  enum class PluginType(val variantCollectionProviders: (Any) -> Set<VariantCollectionProvider>) {
    APPLICATION({ setOf(ApplicationVariant(it), AndroidTest(it)) }),
    LIBRARY({ setOf(LibraryVariant(it), AndroidTest(it)) }),
    TEST({ setOf(ApplicationVariant(it)) }),
    DYNAMIC_FEATURE({ setOf(DynamicFeature(it), AndroidTest(it)) }),
    INSTANT_APP_FEATURE({ setOf(InstantAppFeature(it, it.invokeMethod<Boolean?>("getBaseFeature") == true), AndroidTest(it)) }
    ),
  }

  companion object {

    private data class MethodCacheKey(val clazz: Class<*>, val methodName: String)

    private val methodCache: MutableMap<MethodCacheKey, Result<Method>> = ConcurrentHashMap()

    private fun Class<*>.getMethodCached(name: String) =
      methodCache.computeIfAbsent(MethodCacheKey(this, name)) { key -> runCatching { key.clazz.getMethod(key.methodName) } }

    internal fun <T> Any.invokeMethod(methodName: String): T {
      @Suppress("UNCHECKED_CAST")
      return javaClass.getMethodCached(methodName).getOrThrow().invoke(this) as T
    }

    private fun <T> Any.invokeMethodIfPresent(methodName: String): T? {
      @Suppress("UNCHECKED_CAST")
      return javaClass.getMethodCached(methodName).getOrNull()?.invoke(this) as T?
    }

    fun maybeRegister(project: Project, registry: ToolingModelBuilderRegistry) {
      project.pluginManager.withPlugin("com.android.application") { register(registry, PluginType.APPLICATION) }
      project.pluginManager.withPlugin("com.android.library") { register(registry, PluginType.LIBRARY) }
      project.pluginManager.withPlugin("com.android.dynamic-feature") { register(registry, PluginType.DYNAMIC_FEATURE) }
      project.pluginManager.withPlugin("com.android.feature") { register(registry, PluginType.INSTANT_APP_FEATURE) }
      project.pluginManager.withPlugin("com.android.test") { register(registry, PluginType.TEST) }
    }

    private fun register(registry: ToolingModelBuilderRegistry, pluginType: PluginType) {
        registry.register(LegacyAndroidGradlePluginPropertiesModelBuilder(pluginType))
    }
  }
}
